package dev.lunarlite.module;

import dev.lunarlite.setting.SliderSetting;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
 * Quét chunk đang tải -> lưu lưới 8x8 ô (màu + độ cao) -> vẽ địa hình dạng cột bên ngoài tầm vẽ của game.
 * Giới hạn bản đầu: chỉ thấy vùng đã từng tải; cache chỉ nằm trong RAM; chưa hoà fog với vanilla.
 */
public class LodModule extends Module {
    private final SliderSetting distance = add(new SliderSetting("distance", "LOD Distance", 64, 16, 128, 4, " chunk"));

    /** 8x8 surface samples per chunk; h < 0 means unknown. */
    private static final int GRID = 8;
    private static final int CELL = 16 / GRID;
    private static final class Data {
        final short[] h = new short[GRID * GRID];
        final int[] c = new int[GRID * GRID];
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
    private volatile int generation;
    private int listId = -1;
    private int uploadedVertices;
    private int uploadGeneration = -1;
    private int builtGeneration = -1;
    private int builtRd = -1, builtRange = -1;

    private final FloatBuffer fogBuf = BufferUtils.createFloatBuffer(4);
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
        uploadedVertices = 0;
        generation++;
        pending = null;
        builtCx = Integer.MIN_VALUE;
        builtRd = builtRange = -1;
        dirty = true;
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
            generation++;
            lastWorld = w;
            dirty = true;
            cache.clear();
            scanned.clear();
            pending = null;
            freeList();
            uploadedVertices = 0;
            builtCx = Integer.MIN_VALUE;
            builtRd = builtRange = -1;
        }
        tickCount++;
        // Keep the scan timestamps bounded without periodically rescanning the entire world.
        if (scanned.size() > 12000) {
            Iterator<Map.Entry<Long, Integer>> it = scanned.entrySet().iterator();
            int removed = 0;
            while (it.hasNext() && removed < 512) {
                Map.Entry<Long, Integer> e = it.next();
                if (tickCount - e.getValue() > 1200) { it.remove(); removed++; }
            }
        }
        // Evict distant entries incrementally instead of deleting the entire visible LOD.
        if (cache.size() > 12000 && tickCount % 40 == 0) {
            int px = ((int)Math.floor(mc.thePlayer.posX)) >> 4;
            int pz = ((int)Math.floor(mc.thePlayer.posZ)) >> 4;
            int limit = distance.getInt() + 16;
            for (Long k : cache.keySet()) {
                int dx = (int)(k.longValue() >> 32) - px;
                int dz = (int)(long)k - pz;
                if (dx * dx + dz * dz > limit * limit) { cache.remove(k); scanned.remove(k); dirty = true; }
            }
        }

        int pcx = ((int) Math.floor(mc.thePlayer.posX)) >> 4;
        int pcz = ((int) Math.floor(mc.thePlayer.posZ)) >> 4;
        int rd = Math.max(2, mc.gameSettings.renderDistanceChunks);

        int side = rd * 2 + 1, total = side * side, sampled = 0;
        for (int i = 0; i < 48 && sampled < 5; i++) {
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

        boolean moved = pcx != builtCx || pcz != builtCz || rd != builtRd || distance.getInt() != builtRange;
        if (!building && ((moved && tickCount - lastBuildTick > 30) || (dirty && tickCount - lastBuildTick > 80))) {
            startBuild(w, pcx, pcz, rd, mc.thePlayer.posX, mc.thePlayer.posZ);
        }
    }

    private Data sample(Chunk ch) {
        Data d = new Data();
        boolean any = false;
        for (int cz = 0; cz < GRID; cz++) {
            for (int cx = 0; cx < GRID; cx++) {
                int lx = cx * CELL + CELL / 2, lz = cz * CELL + CELL / 2, i = cz * GRID + cx;
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
        final int version = generation;
        final Map<Long, Data> snapshot = new HashMap<Long, Data>(cache);
        builtCx = pcx;
        builtCz = pcz;
        builtRd = rd;
        builtRange = range;
        lastBuildTick = tickCount;
        dirty = false;
        building = true;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Mesh result = buildMesh(pcx, pcz, rd, range, skip, snapshot);
                    if (version == generation) { pending = result; builtGeneration = version; }
                } catch (Throwable ignored) {
                    dirty = true;
                } finally {
                    building = false;
                }
            }
        });
    }

    private Mesh buildMesh(int pcx, int pcz, int rd, int range, Set<Long> skip, Map<Long, Data> snapshot) {
        Buf o = new Buf();
        for (Map.Entry<Long, Data> e : snapshot.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k >> 32), cz = (int) k;
            int dx = cx - pcx, dz = cz - pcz;
            if ((long)dx * dx + (long)dz * dz > (long)range * range) continue;
            if (Math.abs(dx) <= rd && Math.abs(dz) <= rd && skip.contains(k)) continue;
            double dist = Math.sqrt(dx * dx + dz * dz) * 16.0;
            int step = dist < 256 ? 1 : (dist < 640 ? 2 : (dist < 1152 ? 4 : 8));
            emitChunk(o, cx, cz, e.getValue(), step, snapshot);
        }
        return new Mesh(Arrays.copyOf(o.a, o.n), o.n);
    }

    private int rawH(int gcx, int gcz, Map<Long, Data> snapshot) {
        Data d = snapshot.get(key(Math.floorDiv(gcx, GRID), Math.floorDiv(gcz, GRID)));
        if (d == null) return -2;
        return d.h[Math.floorMod(gcz, GRID) * GRID + Math.floorMod(gcx, GRID)];
    }

    private void emitChunk(Buf o, int cx, int cz, Data d, int s, Map<Long, Data> snapshot) {
        for (int gz = 0; gz < GRID; gz += s) {
            for (int gx = 0; gx < GRID; gx += s) {
                int cnt = 0, sh = 0, sr = 0, sg = 0, sb = 0;
                for (int z = gz; z < gz + s; z++) {
                    for (int x = gx; x < gx + s; x++) {
                        int i = z * GRID + x;
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

                float x0 = cx * 16 + gx * CELL, z0 = cz * 16 + gz * CELL;
                float x1 = x0 + s * CELL, z1 = z0 + s * CELL;
                // Slight offset to avoid coplanar overlap at the vanilla/LOD boundary.
                float yt = h - 0.5f;

                o.v(r, g, b, x0, yt, z0); o.v(r, g, b, x0, yt, z1);
                o.v(r, g, b, x1, yt, z1); o.v(r, g, b, x1, yt, z0);

                int gcx = cx * GRID + gx, gcz = cz * GRID + gz;
                for (int k = 0; k < s; k++) {
                    side(o, r * 0.6f, g * 0.6f, b * 0.6f, false, x0, z0 + k * CELL, yt, rawH(gcx - 1, gcz + k, snapshot));
                    side(o, r * 0.6f, g * 0.6f, b * 0.6f, false, x1, z0 + k * CELL, yt, rawH(gcx + s, gcz + k, snapshot));
                    side(o, r * 0.8f, g * 0.8f, b * 0.8f, true, x0 + k * CELL, z0, yt, rawH(gcx + k, gcz - 1, snapshot));
                    side(o, r * 0.8f, g * 0.8f, b * 0.8f, true, x0 + k * CELL, z1, yt, rawH(gcx + k, gcz + s, snapshot));
                }
            }
        }
    }

    /** Draw only known downhill neighbours; unknown terrain is not a cliff. */
    private void side(Buf o, float r, float g, float b, boolean alongX, float x, float z, float yt, int nh) {
        // Missing neighbours are unvisited terrain, not cliffs. Avoid black curtains.
        if (nh < 0) return;
        float yn = nh - 0.5f;
        if (yn >= yt - 0.01f) return;
        if (alongX) {
            o.v(r, g, b, x, yn, z); o.v(r, g, b, x, yt, z);
            o.v(r, g, b, x + CELL, yt, z); o.v(r, g, b, x + CELL, yn, z);
        } else {
            o.v(r, g, b, x, yn, z); o.v(r, g, b, x, yt, z);
            o.v(r, g, b, x, yt, z + CELL); o.v(r, g, b, x, yn, z + CELL);
        }
    }

    // ------------------------------------------------------------------ vẽ (luồng chính)

    private void upload() {
        Mesh m = pending;
        if (m == null) return;
        pending = null;
        if (builtGeneration != generation || !enabled) return;
        if (listId < 0) listId = GLAllocation.generateDisplayLists(1);
        uploadedVertices = m.n / 6;
        // Display lists must capture actual vertex values, not a pointer to a temporary buffer.
        GL11.glNewList(listId, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i + 5 < m.n; i += 6) {
            GL11.glColor3f(m.a[i], m.a[i + 1], m.a[i + 2]);
            GL11.glVertex3f(m.a[i + 3], m.a[i + 4], m.a[i + 5]);
        }
        GL11.glEnd();
        GL11.glEndList();
        uploadGeneration = generation;
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        upload();
        if (listId < 0 || uploadedVertices == 0 || uploadGeneration != generation) return;
        if (mc.thePlayer.isInsideOfMaterial(Material.water) || mc.thePlayer.isInsideOfMaterial(Material.lava)) return;

        Entity v = mc.getRenderViewEntity();
        if (v == null) return;
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
        double cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt;
        double cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;

        float range = distance.getFloat() * 16f;
        float vanilla = Math.max(2, mc.gameSettings.renderDistanceChunks) * 16f;
        float transition = Math.min(48f, Math.max(16f, vanilla * 0.18f));
        float fogStart = Math.min(range - 16f, Math.max(vanilla - transition, range * 0.58f));

        // Match the current world sky instead of the previous OpenGL clear color.
        fogBuf.clear();
        net.minecraft.util.Vec3 sky = mc.theWorld.getSkyColor(v, pt);
        fogBuf.put((float)sky.xCoord).put((float)sky.yCoord).put((float)sky.zCoord).put(1f).flip();

        // mở rộng far plane của ma trận chiếu hiện tại (giữ nguyên FOV / tỉ lệ)
        projBuf.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projBuf);
        float[] m = new float[16];
        projBuf.get(m);
        float a = m[10], b = m[14];
        float near = b / (a - 1f);
        if (near <= 0f || Float.isNaN(near) || Float.isInfinite(near)) return;
        float far = range * 1.12f + 96f;
        m[10] = -(far + near) / (far - near);
        m[14] = -2f * far * near / (far - near);
        projBuf.clear();
        projBuf.put(m).flip();

        // Preserve both texture units; lightmap must not tint untextured LOD.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_FOG_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmapOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

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
        GL11.glDepthMask(false); // Do not contaminate vanilla depth after RenderWorldLastEvent.
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Vertex colors are already shaded; fixed-function lighting without normals turns them black.
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);

        GL11.glEnable(GL11.GL_FOG);
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GL11.glFogf(GL11.GL_FOG_START, fogStart);
        GL11.glFogf(GL11.GL_FOG_END, range);
        GL11.glFog(GL11.GL_FOG_COLOR, fogBuf);

        // The camera frustum is still applied by the original projection; depth tests
        // against vanilla geometry while writes stay disabled.
        GL11.glCallList(listId);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (lightmapOn) GL11.glEnable(GL11.GL_TEXTURE_2D);
        else GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
