package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * Hiệu ứng vỡ vụn kiểu SAO cho mọi sinh vật (quái, động vật) và người chơi khác khi chết:
 * thân xác biến mất ngay, thay bằng các mảnh đa giác xanh băng phát sáng bay toả ra, xoay, bay lên rồi tan.
 * Vẽ trong thế giới 3D (RenderWorldLastEvent), mảnh bị khối che đúng như vật thể thật.
 */
public final class SaoShatter {
    private SaoShatter() {}

    /** Bật / tắt (lưu trong config). */
    public static boolean enabled = true;

    private static final int MAX = 700;
    private static final int[] PALETTE = {0xFFFFFF, 0xDDF6FF, 0xA8E6FF, 0x7FD3FF, 0xBFD9FF};
    private static final float[] OFF = {0f, 2.4f, 4.2f};
    private static final float[] RAD = {1f, 0.7f, 0.55f};
    private static final Random R = new Random();

    private static final class Shard {
        double x, y, z;
        float vx, vy, vz;
        float size, rot, spin, life;
        float ux, uy, uz, wx, wy, wz; // hai trục của mặt phẳng mảnh
        long born;
        int color;
    }

    private static final ArrayList<Shard> LIST = new ArrayList<Shard>();

    /** Thân xác của sinh vật đang chết bị ẩn (thay bằng mảnh vỡ). Không áp dụng cho chính người chơi. */
    public static boolean hides(EntityLivingBase e) {
        return enabled && e != null && (e.deathTime > 0 || e.getHealth() <= 0f) && e != Minecraft.getMinecraft().thePlayer;
    }

    public static void clear() {
        LIST.clear();
    }

    /** Bung mảnh vỡ quanh thân sinh vật vừa bắt đầu chết; số mảnh theo cỡ cơ thể. */
    public static void burst(EntityLivingBase e) {
        if (!enabled || e == null) return;
        float w = Math.max(0.3f, e.width), h = Math.max(0.3f, e.height);
        int n = Math.round(Math.min(48f, 14f + w * h * 14f));
        float body = 0.6f + 0.4f * Math.max(w, h);
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            Shard s = new Shard();
            s.x = e.posX + (R.nextFloat() - 0.5f) * w;
            s.y = e.posY + R.nextFloat() * h;
            s.z = e.posZ + (R.nextFloat() - 0.5f) * w;
            float dx = (float) (s.x - e.posX), dz = (float) (s.z - e.posZ);
            float len = (float) Math.sqrt(dx * dx + dz * dz) + 1e-3f;
            float sp = 0.5f + R.nextFloat() * 1.6f;
            s.vx = dx / len * sp * 0.6f + (R.nextFloat() - 0.5f) * 0.6f;
            s.vz = dz / len * sp * 0.6f + (R.nextFloat() - 0.5f) * 0.6f;
            s.vy = 0.4f + R.nextFloat() * 1.4f;
            s.size = (0.05f + R.nextFloat() * R.nextFloat() * 0.22f) * body;
            s.rot = R.nextFloat() * 6.28f;
            s.spin = (R.nextFloat() - 0.5f) * 10f;
            s.life = 0.9f + R.nextFloat() * 1.1f;
            s.born = now;
            s.color = PALETTE[R.nextInt(PALETTE.length)];
            // mặt phẳng ngẫu nhiên: pháp tuyến n -> u = n x a, w = n x u
            float nx = R.nextFloat() - 0.5f, ny = R.nextFloat() - 0.5f, nz = R.nextFloat() - 0.5f;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-4f;
            nx /= nl; ny /= nl; nz /= nl;
            float ax = 0f, ay = 1f, az = 0f;
            if (Math.abs(ny) > 0.9f) { ax = 1f; ay = 0f; }
            float ux = ny * az - nz * ay, uy = nz * ax - nx * az, uz = nx * ay - ny * ax;
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-4f;
            ux /= ul; uy /= ul; uz /= ul;
            s.ux = ux; s.uy = uy; s.uz = uz;
            s.wx = ny * uz - nz * uy;
            s.wy = nz * ux - nx * uz;
            s.wz = nx * uy - ny * ux;
            LIST.add(s);
        }
        while (LIST.size() > MAX) LIST.remove(0);
    }

    /** Gọi từ RenderWorldLastEvent. */
    public static void render(float pt) {
        if (LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity rv = mc.getRenderViewEntity();
        if (rv == null) return;
        double cx = rv.lastTickPosX + (rv.posX - rv.lastTickPosX) * pt;
        double cy = rv.lastTickPosY + (rv.posY - rv.lastTickPosY) * pt;
        double cz = rv.lastTickPosZ + (rv.posZ - rv.lastTickPosZ) * pt;
        long now = System.currentTimeMillis();

        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0); // cộng sáng: mảnh phát sáng
        GlStateManager.depthMask(false);

        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(4, DefaultVertexFormats.POSITION_COLOR);
        boolean any = false;
        for (Iterator<Shard> it = LIST.iterator(); it.hasNext(); ) {
            Shard s = it.next();
            float age = (now - s.born) / 1000f;
            if (age >= s.life) {
                it.remove();
                continue;
            }
            float u = age / s.life;
            float k = 2.4f;
            float d = (1f - (float) Math.exp(-k * age)) / k; // lao nhanh rồi giảm tốc
            double px = s.x + s.vx * d - cx;
            double py = s.y + s.vy * d - cy;
            double pz = s.z + s.vz * d - cz;
            float al = (float) Math.pow(1f - u, 1.2) * 0.95f;
            int a = Math.round(al * 255f);
            int r = (s.color >> 16) & 255, g = (s.color >> 8) & 255, b = s.color & 255;
            float rr0 = s.size * (1f - 0.3f * u);
            for (int j = 0; j < 3; j++) {
                float ang = s.rot + s.spin * age + OFF[j];
                float c = (float) Math.cos(ang) * rr0 * RAD[j], sn = (float) Math.sin(ang) * rr0 * RAD[j];
                wr.pos(px + c * s.ux + sn * s.wx, py + c * s.uy + sn * s.wy, pz + c * s.uz + sn * s.wz)
                        .color(r, g, b, a).endVertex();
            }
            any = true;
        }
        if (any) t.draw();
        else wr.finishDrawing();

        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (!blend) GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        if (lit) GlStateManager.enableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
