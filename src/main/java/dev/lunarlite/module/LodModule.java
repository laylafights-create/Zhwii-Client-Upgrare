package dev.lunarlite.module;

import dev.lunarlite.setting.SliderSetting;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Far Horizons - LOD địa hình xa (kiểu Distant Horizons, bản đầu sơ sài).
 * Quét chunk đang tải -> lưu lưới 4x4 ô (màu + độ cao) -> vẽ địa hình dạng cột bên ngoài tầm vẽ của game.
 * Giới hạn bản đầu: chỉ thấy vùng đã từng tải; cache chỉ nằm trong RAM; chưa hoà fog với vanilla.
 */
public class LodModule extends Module {
    private final SliderSetting distance = add(new SliderSetting("distance", "LOD Distance", 48, 16, 96, 4, " chunk"));

    /** Dữ liệu 1 chunk: 4x4 ô, mỗi ô 4x4 block. h < 0 = ô trống. */
    private static final class Data {
        final short[] h = new short[16];
        final int[] c = new int[16];
    }

    private static final class Mesh {
        final float[] a;
        final int n;
        Mesh(float[] a, int n) { this.a = a; this.n = n; }
    }

    private static final class Buf {
        float[] a = new float[1 << 16];
        int n;
        void v(float r, float g, float b, float x, float y, float z) {
            if (n + 6 > a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = r; a[n++] = g; a[n++] = b; a[n++] = x; a[n++] = y; a[n++] = z;
        }
    }

    private final ConcurrentHashMap<Long, Data> cache = new ConcurrentHashMap<Long, Data>();
    private final Map<Long, Integer> scanned = new HashMap<Long, Integer>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "FarHorizons-Mesher");
            t.setDaemon(true);
            return t;
        }
    });

    private World lastWorld;
    private int tickCount, scanIdx, lastBuildTick = -9999;
    private int builtCx = Integer.MIN_VALUE, builtCz;
    private boolean dirty;
    private volatile boolean building;
    private volatile Mesh pending;
    private int listId = -1;

    private final FloatBuffer clearBuf = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer fogBuf = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer ambBuf = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);

    public LodModule() {
        super("farhorizons", "Far Horizons", "Vẽ địa hình xa bằng LOD ngoài tầm vẽ của game (kiểu Distant Horizons).", Category.VISUAL, false);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @Override
    public void onToggle() {
        freeList();
        pending = null;
        builtCx = Integer.MIN_VALUE;
    }

    private void freeList() {
        if (listId >= 0) {
            GLAllocation.deleteDisplayLists(listId);
            listId = -1;
        }
    }

    // ------------------------------------------------------------------ quét chunk

    @Override
    public void onTick() {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null || mc.thePlayer == null) return;
        if (w != lastWorld) {
            lastWorld = w;
            cache.clear();
            scanned.clear();
            pending = null;
            freeList();
            builtCx = Integer.MIN_VALUE;
        }
        tickCount++;
        if (scanned.size() > 30000) scanned.clear();

        int pcx = ((int) Math.floor(mc.thePlayer.posX)) >> 4;
        int pcz = ((int) Math.floor(mc.thePlayer.posZ)) >> 4;
        int rd = Math.max(2, mc.gameSettings.renderDistanceChunks);

        int side = rd * 2 + 1, total = side * side, sampled = 0;
        for (int i = 0; i < 24 && sampled < 3; i++) {
            scanIdx = (scanIdx + 1) % total;
            int cx = pcx - rd + scanIdx % side;
            int cz = pcz - rd + scanIdx / side;
            long k = key(cx, cz);
            Integer last = scanned.get(k);
            if (last != null && tickCount - last < (cache.containsKey(k) ? 1200 : 100)) continue;
            if (!w.getChunkProvider().chunkExists(cx, cz)) continue;
            Chunk ch = w.getChunkFromChunkCoords(cx, cz);
            if (ch == null || ch.isEmpty()) continue;
            scanned.put(k, tickCount);
            Data d = sample(ch);
            if (d != null) {
                cache.put(k, d);
                dirty = true;
            }
            sampled++;
        }

        boolean moved = pcx != builtCx || pcz != builtCz;
        if (!building && ((moved && tickCount - lastBuildTick > 40) || (dirty && tickCount - lastBuildTick > 100))) {
            startBuild(w, pcx, pcz, rd, mc.thePlayer.posX, mc.thePlayer.posZ);
        }
    }

    private Data sample(Chunk ch) {
        Data d = new Data();
        boolean any = false;
        for (int cz = 0; cz < 4; cz++) {
            for (int cx = 0; cx < 4; cx++) {
                int lx = cx * 4 + 2, lz = cz * 4 + 2, i = cz * 4 + cx;
                d.h[i] = -1;
                for (int y = Math.min(255, ch.getHeightValue(lx, lz)); y >= 0; y--) {
                    IBlockState s = ch.getBlockState(new BlockPos(lx, y, lz));
                    Block b = s.getBlock();
                    if (b.getMaterial() == Material.air) continue;
                    MapColor mc = b.getMapColor(s);
                    if (mc == null || mc == MapColor.airColor) continue;
                    d.h[i] = (short) y;
                    d.c[i] = mc.colorValue;
                    any = true;
                    break;
                }
            }
        }
        return any ? d : null;
    }

    // ------------------------------------------------------------------ dựng mesh (luồng nền)

    private void startBuild(World w, final int pcx, final int pcz, final int rd, double px, double pz) {
        final Set<Long> skip = new HashSet<Long>();
        for (int dz = -rd; dz <= rd; dz++) {
            for (int dx = -rd; dx <= rd; dx++) {
                if (w.getChunkProvider().chunkExists(pcx + dx, pcz + dz)) skip.add(key(pcx + dx, pcz + dz));
            }
        }
        final int range = distance.getInt();
        builtCx = pcx;
        builtCz = pcz;
        lastBuildTick = tickCount;
        dirty = false;
        building = true;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    pending = buildMesh(pcx, pcz, rd, range, skip);
                } catch (Throwable ignored) {
                } finally {
                    building = false;
                }
            }
        });
    }

    private Mesh buildMesh(int pcx, int pcz, int rd, int range, Set<Long> skip) {
        Buf o = new Buf();
        for (Map.Entry<Long, Data> e : cache.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k >> 32), cz = (int) k;
            int dx = cx - pcx, dz = cz - pcz;
            if (dx * dx + dz * dz > range * range) continue;
            if (Math.abs(dx) <= rd && Math.abs(dz) <= rd && skip.contains(k)) continue;
            double dist = Math.sqrt(dx * dx + dz * dz) * 16.0;
            int step = dist < 160 ? 1 : (dist < 352 ? 2 : 4);
            emitChunk(o, cx, cz, e.getValue(), step);
        }
        return new Mesh(o.a, o.n);
    }

    private int rawH(int gcx, int gcz) {
        Data d = cache.get(key(gcx >> 2, gcz >> 2));
        if (d == null) return -2;
        return d.h[(gcz & 3) * 4 + (gcx & 3)];
    }

    private void emitChunk(Buf o, int cx, int cz, Data d, int s) {
        for (int gz = 0; gz < 4; gz += s) {
            for (int gx = 0; gx < 4; gx += s) {
                int cnt = 0, sh = 0, sr = 0, sg = 0, sb = 0;
                for (int z = gz; z < gz + s; z++) {
                    for (int x = gx; x < gx + s; x++) {
                        int i = z * 4 + x;
                        if (d.h[i] < 0) continue;
                        int c = d.c[i];
                        sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255;
                        sh += d.h[i];
                        cnt++;
                    }
                }
                if (cnt == 0) continue;
                int h = Math.round((float) sh / cnt);
                float lum = 0.75f + 0.25f * Math.min(1f, h / 110f);
                float r = sr / (float) cnt / 255f * lum;
                float g = sg / (float) cnt / 255f * lum;
                float b = sb / (float) cnt / 255f * lum;

                float x0 = cx * 16 + gx * 4, z0 = cz * 16 + gz * 4;
                float x1 = x0 + s * 4, z1 = z0 + s * 4;
                // hạ thấp 1.5 block để không z-fight với chunk thật của game
                float yt = h - 0.5f;

                o.v(r, g, b, x0, yt, z0); o.v(r, g, b, x0, yt, z1);
                o.v(r, g, b, x1, yt, z1); o.v(r, g, b, x1, yt, z0);

                int gcx = cx * 4 + gx, gcz = cz * 4 + gz;
                for (int k = 0; k < s; k++) {
                    side(o, r * 0.6f, g * 0.6f, b * 0.6f, false, x0, z0 + k * 4, yt, rawH(gcx - 1, gcz + k));
                    side(o, r * 0.6f, g * 0.6f, b * 0.6f, false, x1, z0 + k * 4, yt, rawH(gcx + s, gcz + k));
                    side(o, r * 0.8f, g * 0.8f, b * 0.8f, true, x0 + k * 4, z0, yt, rawH(gcx + k, gcz - 1));
                    side(o, r * 0.8f, g * 0.8f, b * 0.8f, true, x0 + k * 4, z1, yt, rawH(gcx + k, gcz + s));
                }
            }
        }
    }

    /** Mặt bên rộng 4 block, từ đỉnh ô xuống đỉnh ô kế bên (hoặc 12 block nếu bên kia trống). */
    private void side(Buf o, float r, float g, float b, boolean alongX, float x, float z, float yt, int nh) {
        float yn = nh >= 0 ? nh - 0.5f : yt - 12f;
        if (yn >= yt - 0.01f) return;
        if (alongX) {
            o.v(r, g, b, x, yn, z); o.v(r, g, b, x, yt, z);
            o.v(r, g, b, x + 4, yt, z); o.v(r, g, b, x + 4, yn, z);
        } else {
            o.v(r, g, b, x, yn, z); o.v(r, g, b, x, yt, z);
            o.v(r, g, b, x, yt, z + 4); o.v(r, g, b, x, yn, z + 4);
        }
    }

    // ------------------------------------------------------------------ vẽ (luồng chính)

    private void upload() {
        Mesh m = pending;
        if (m == null) return;
        pending = null;
        if (listId < 0) listId = GLAllocation.generateDisplayLists(1);
        FloatBuffer fb = BufferUtils.createFloatBuffer(Math.max(6, m.n));
        fb.put(m.a, 0, m.n);
        fb.flip();
        if (OpenGlHelper.useVbo()) OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glInterleavedArrays(GL11.GL_C3F_V3F, 0, fb);
        GL11.glNewList(listId, GL11.GL_COMPILE);
        if (m.n > 0) GL11.glDrawArrays(GL11.GL_QUADS, 0, m.n / 6);
        GL11.glEndList();
        GL11.glPopClientAttrib();
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        upload();
        if (listId < 0) return;
        if (mc.thePlayer.isInsideOfMaterial(Material.water) || mc.thePlayer.isInsideOfMaterial(Material.lava)) return;

        Entity v = mc.getRenderViewEntity();
        if (v == null) return;
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
        double cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt;
        double cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;

        float range = distance.getFloat() * 16f;
        float vanilla = Math.max(2, mc.gameSettings.renderDistanceChunks) * 16f;
        float fogStart = vanilla < range * 0.9f ? vanilla : range * 0.5f;

        // màu fog = màu clear hiện tại của game (đã gồm ngày/đêm/mưa)
        clearBuf.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearBuf);
        fogBuf.clear();
        fogBuf.put(clearBuf.get(0)).put(clearBuf.get(1)).put(clearBuf.get(2)).put(1f).flip();

        float bright = Math.max(0.15f, Math.min(1f, mc.theWorld.getSunBrightness(pt)));
        ambBuf.clear();
        ambBuf.put(bright).put(bright).put(bright).put(1f).flip();

        // mở rộng far plane của ma trận chiếu hiện tại (giữ nguyên FOV / tỉ lệ)
        projBuf.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projBuf);
        float[] m = new float[16];
        projBuf.get(m);
        float a = m[10], b = m[14];
        float near = b / (a - 1f);
        float far = range * 1.25f + 64f;
        m[10] = -(far + near) / (far - near);
        m[14] = -2f * far * near / (far - near);
        projBuf.clear();
        projBuf.put(m).flip();

        // đơn vị texture 1 (lightmap) có thể đang bật -> tắt tạm
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmapOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_FOG_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadMatrix(projBuf);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glTranslated(-cx, -cy, -cz);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        GL11.glDisable(GL11.GL_LIGHT0);
        GL11.glDisable(GL11.GL_LIGHT1);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, GL11.GL_TRUE);
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, ambBuf);

        GL11.glEnable(GL11.GL_FOG);
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GL11.glFogf(GL11.GL_FOG_START, fogStart);
        GL11.glFogf(GL11.GL_FOG_END, range);
        GL11.glFog(GL11.GL_FOG_COLOR, fogBuf);

        GL11.glCallList(listId);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();

        if (lightmapOn) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }
}
