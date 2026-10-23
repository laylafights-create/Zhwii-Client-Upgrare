package dev.lunarlite.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

/**
 * Hiệu ứng anime khi đánh trúng: chớp sao lớn, vệt chém, sóng tròn, cánh hoa anh đào và sao nhỏ bắn ra.
 * Vẽ trong thế giới 3D (RenderWorldLastEvent) bằng các hình luôn quay mặt về camera.
 */
public final class AnimeHit {
    private AnimeHit() {}

    private static final int FLASH = 0, SLASH = 1, RING = 2, PETAL = 3, STAR = 4;
    private static final int MAX = 420;
    private static final Random R = new Random();

    private static final class P {
        int type;
        double x, y, z;
        float vx, vy, vz;
        float size, rot, spin, life, delay;
        long born;
        int c1, c2;
    }

    private static final ArrayList<P> LIST = new ArrayList<P>();

    public static void clear() {
        LIST.clear();
    }

    private static P make(int type, double x, double y, double z, float size, float life, float delay) {
        P p = new P();
        p.type = type;
        p.x = x;
        p.y = y;
        p.z = z;
        p.size = size;
        p.life = life;
        p.delay = delay;
        p.rot = R.nextFloat() * 6.2832f;
        p.born = System.currentTimeMillis();
        p.c1 = 0xFFFFFF;
        p.c2 = 0xFF7FB2;
        LIST.add(p);
        return p;
    }

    /** Tạo hiệu ứng tại thân mục tiêu vừa bị đánh. {@code amount}: cường độ (số hạt nhân theo). */
    public static void spawn(EntityLivingBase t, float amount, boolean petals, boolean slash, boolean stars) {
        Minecraft mc = Minecraft.getMinecraft();
        if (t == null || mc.thePlayer == null) return;
        double dx = mc.thePlayer.posX - t.posX, dz = mc.thePlayer.posZ - t.posZ;
        double dl = Math.sqrt(dx * dx + dz * dz) + 1e-4;
        double off = t.width * 0.5 + 0.2;
        double x = t.posX + dx / dl * off;
        double y = t.posY + t.height * 0.65;
        double z = t.posZ + dz / dl * off;
        float body = Math.max(0.8f, Math.min(1.6f, t.height * 0.55f));

        make(FLASH, x, y, z, 0.95f * body, 0.30f, 0f);
        make(RING, x, y, z, 0.95f * body, 0.42f, 0f);
        if (slash) {
            float base = (R.nextBoolean() ? 1f : -1f) * (0.5f + R.nextFloat() * 0.5f);
            for (int i = 0; i < 3; i++) {
                P s = make(SLASH, x, y, z, (1.1f + R.nextFloat() * 0.5f) * body, 0.28f, i * 0.045f);
                s.rot = base + (i - 1) * 0.42f;
            }
        }
        // cánh hoa + sao bung ra từ khắp thân người bị đánh, văng toả ra xung quanh
        float w = Math.max(0.4f, t.width), h = Math.max(0.4f, t.height);
        if (petals) {
            int n = Math.round(18f * amount);
            for (int i = 0; i < n; i++) {
                double sx = t.posX + (R.nextFloat() - 0.5f) * w;
                double sy = t.posY + R.nextFloat() * h;
                double sz = t.posZ + (R.nextFloat() - 0.5f) * w;
                P p = make(PETAL, sx, sy, sz, 0.07f + R.nextFloat() * 0.06f, 0.9f + R.nextFloat() * 0.6f, 0f);
                float ox = (float) (sx - t.posX), oz = (float) (sz - t.posZ);
                float ol = (float) Math.sqrt(ox * ox + oz * oz);
                if (ol < 0.02f) { // đúng trục thân: chọn hướng ngẫu nhiên
                    float a = R.nextFloat() * 6.2832f;
                    ox = (float) Math.cos(a);
                    oz = (float) Math.sin(a);
                    ol = 1f;
                }
                float sp = 1.6f + R.nextFloat() * 2.2f;
                p.vx = ox / ol * sp + (R.nextFloat() - 0.5f) * 0.8f;
                p.vz = oz / ol * sp + (R.nextFloat() - 0.5f) * 0.8f;
                p.vy = 0.3f + R.nextFloat() * 1.5f;
                p.spin = (R.nextFloat() - 0.5f) * 14f;
                p.c1 = 0xFFE9F1;
                p.c2 = 0xFF8FBB;
            }
        }
        if (stars) {
            int n = Math.round(12f * amount);
            for (int i = 0; i < n; i++) {
                double sx = t.posX + (R.nextFloat() - 0.5f) * w;
                double sy = t.posY + R.nextFloat() * h;
                double sz = t.posZ + (R.nextFloat() - 0.5f) * w;
                P p = make(STAR, sx, sy, sz, 0.09f + R.nextFloat() * 0.11f, 0.45f + R.nextFloat() * 0.45f, 0f);
                float ox = (float) (sx - t.posX), oz = (float) (sz - t.posZ);
                float ol = (float) Math.sqrt(ox * ox + oz * oz);
                if (ol < 0.02f) {
                    float a = R.nextFloat() * 6.2832f;
                    ox = (float) Math.cos(a);
                    oz = (float) Math.sin(a);
                    ol = 1f;
                }
                float sp = 1.2f + R.nextFloat() * 1.8f;
                p.vx = ox / ol * sp;
                p.vz = oz / ol * sp;
                p.vy = (R.nextFloat() - 0.3f) * 1.2f;
                p.spin = (R.nextFloat() - 0.5f) * 6f;
                p.c2 = R.nextBoolean() ? 0xFFC93C : 0xFF7FB2;
            }
        }
        while (LIST.size() > MAX) LIST.remove(0);
    }

    // ---------------------------------------------------------------- vẽ

    private static double rx, ry, rz, ux, uy, uz; // trục phải / lên của camera
    private static double cx, cy, cz;

    private static void v(WorldRenderer wr, double wx, double wy, double wz, float px, float py, int rgb, float a) {
        int al = Math.round(255f * Math.max(0f, Math.min(1f, a)));
        wr.pos(wx - cx + rx * px + ux * py, wy - cy + ry * px + uy * py, wz - cz + rz * px + uz * py)
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al).endVertex();
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** Gọi từ RenderWorldLastEvent. */
    public static void render(float pt) {
        if (LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity rv = mc.getRenderViewEntity();
        if (rv == null) return;
        cx = rv.lastTickPosX + (rv.posX - rv.lastTickPosX) * pt;
        cy = rv.lastTickPosY + (rv.posY - rv.lastTickPosY) * pt;
        cz = rv.lastTickPosZ + (rv.posZ - rv.lastTickPosZ) * pt;
        double yaw = Math.toRadians(mc.getRenderManager().playerViewY);
        double pit = Math.toRadians(mc.getRenderManager().playerViewX);
        double c = Math.cos(yaw), s = Math.sin(yaw), sp = Math.sin(pit), cp = Math.cos(pit);
        rx = c;
        ry = 0;
        rz = s;
        ux = -s * sp;
        uy = cp;
        uz = c * sp;

        long now = System.currentTimeMillis();
        for (Iterator<P> it = LIST.iterator(); it.hasNext(); ) {
            P p = it.next();
            if ((now - p.born) / 1000f >= p.life + p.delay) it.remove();
        }
        if (LIST.isEmpty()) return;

        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);

        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); // cánh hoa: trộn thường
            else GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);             // chớp / chém / sao: cộng sáng
            Tessellator t = Tessellator.getInstance();
            WorldRenderer wr = t.getWorldRenderer();
            wr.begin(4, DefaultVertexFormats.POSITION_COLOR);
            boolean any = false;
            for (P p : LIST) {
                if ((p.type == PETAL) != (pass == 0)) continue;
                float age = (now - p.born) / 1000f - p.delay;
                if (age < 0f || age >= p.life) continue;
                draw(wr, p, age);
                any = true;
            }
            if (any) t.draw();
            else wr.finishDrawing();
        }

        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (!blend) GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        if (lit) GlStateManager.enableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void draw(WorldRenderer wr, P p, float age) {
        float u = age / p.life;
        float fade = 1f - u;
        double d = (1f - Math.exp(-3.0 * age)) / 3.0; // lao nhanh rồi giảm tốc
        double wx = p.x + p.vx * d, wz = p.z + p.vz * d;
        double wy = p.y + p.vy * d - (p.type == PETAL ? 0.9 * age * age : 0.0);
        switch (p.type) {
            case FLASH: {
                float r = p.size * (0.35f + 0.65f * ease(u * 3.2f));
                star(wr, wx, wy, wz, r, r * 0.20f, p.rot + u * 0.6f, 0xFFFFFF, p.c2, (float) Math.pow(fade, 1.4));
                star(wr, wx, wy, wz, r * 0.6f, r * 0.12f, p.rot + 0.785f, 0xFFFFFF, 0xFFC4DE, (float) Math.pow(fade, 1.4) * 0.9f);
                break;
            }
            case SLASH: {
                float len = p.size * ease(age / 0.09f);
                float w = 0.035f * p.size * (1f - u * 0.6f);
                float cs = (float) Math.cos(p.rot), sn = (float) Math.sin(p.rot);
                float a = fade;
                float hx = cs * len, hy = sn * len, nx = -sn * w, ny = cs * w;
                v(wr, wx, wy, wz, -hx, -hy, 0xFFFFFF, 0f);
                v(wr, wx, wy, wz, nx, ny, 0xFFFFFF, a);
                v(wr, wx, wy, wz, -nx, -ny, 0xFFFFFF, a);
                v(wr, wx, wy, wz, hx, hy, 0xFFFFFF, 0f);
                v(wr, wx, wy, wz, nx, ny, 0xFFFFFF, a);
                v(wr, wx, wy, wz, -nx, -ny, 0xFFFFFF, a);
                // lớp ánh hồng mỏng quanh vệt chém
                float w2 = w * 3.2f, n2x = -sn * w2, n2y = cs * w2;
                v(wr, wx, wy, wz, -hx, -hy, p.c2, 0f);
                v(wr, wx, wy, wz, n2x, n2y, p.c2, a * 0.35f);
                v(wr, wx, wy, wz, -n2x, -n2y, p.c2, a * 0.35f);
                v(wr, wx, wy, wz, hx, hy, p.c2, 0f);
                v(wr, wx, wy, wz, n2x, n2y, p.c2, a * 0.35f);
                v(wr, wx, wy, wz, -n2x, -n2y, p.c2, a * 0.35f);
                break;
            }
            case RING: {
                float r1 = p.size * 0.15f + p.size * 0.85f * ease(u);
                float r2 = r1 + 0.06f * (1f - u) + 0.01f;
                final int seg = 28;
                for (int i = 0; i < seg; i++) {
                    double a0 = Math.PI * 2.0 * i / seg, a1 = Math.PI * 2.0 * (i + 1) / seg;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                    float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    float al = fade * 0.85f;
                    v(wr, wx, wy, wz, c0 * r1, s0 * r1, p.c2, al);
                    v(wr, wx, wy, wz, c0 * r2, s0 * r2, p.c2, al);
                    v(wr, wx, wy, wz, c1 * r1, s1 * r1, p.c2, al);
                    v(wr, wx, wy, wz, c1 * r1, s1 * r1, p.c2, al);
                    v(wr, wx, wy, wz, c0 * r2, s0 * r2, p.c2, al);
                    v(wr, wx, wy, wz, c1 * r2, s1 * r2, p.c2, al);
                }
                break;
            }
            case PETAL: {
                float a = Math.min(1f, Math.min(u * 10f, fade * 3f)) * 0.95f;
                float rot = p.rot + p.spin * age;
                float flut = 0.35f + 0.65f * Math.abs((float) Math.cos(age * 7f + p.rot));
                float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                final int seg = 9;
                float px0 = 0, py0 = 0, pxp = 0, pyp = 0;
                for (int k = 0; k <= seg; k++) {
                    double ang = Math.PI * 2.0 * k / seg;
                    float lx = (float) Math.cos(ang) * p.size;
                    float ly = (float) Math.sin(ang) * p.size * 0.62f * flut;
                    if (Math.cos(ang) > 0.85) lx -= p.size * 0.28f;
                    float qx = lx * cr - ly * sr, qy = lx * sr + ly * cr;
                    if (k > 0) {
                        v(wr, wx, wy, wz, 0f, 0f, p.c1, a);
                        v(wr, wx, wy, wz, pxp, pyp, p.c2, a);
                        v(wr, wx, wy, wz, qx, qy, p.c2, a);
                    }
                    pxp = qx;
                    pyp = qy;
                }
                break;
            }
            case STAR: {
                float tw = 0.6f + 0.4f * (float) Math.sin(age * 22f + p.rot);
                float r = p.size * (0.5f + 0.5f * fade);
                star(wr, wx, wy, wz, r, r * 0.2f, p.rot + p.spin * age, 0xFFFFFF, p.c2, fade * tw);
                break;
            }
            default:
                break;
        }
    }

    /** Sao 4 cánh: mỗi cánh là một tam giác, đầu cánh trong suốt. */
    private static void star(WorldRenderer wr, double wx, double wy, double wz, float r, float th, float rot, int core, int edge, float a) {
        float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
        for (int k = 0; k < 4; k++) {
            float ax = k == 0 ? 1f : k == 1 ? 0f : k == 2 ? -1f : 0f;
            float ay = k == 0 ? 0f : k == 1 ? 1f : k == 2 ? 0f : -1f;
            float tx = ax * r, ty = ay * r;          // đầu cánh
            float sx = -ay * th, sy = ax * th;       // hai điểm chân cánh
            v(wr, wx, wy, wz, tx * cr - ty * sr, tx * sr + ty * cr, edge, 0f);
            v(wr, wx, wy, wz, sx * cr - sy * sr, sx * sr + sy * cr, core, a);
            v(wr, wx, wy, wz, -sx * cr + sy * sr, -sx * sr - sy * cr, core, a);
        }
    }
}
