package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Vệt chém sáng. Chế độ "On Hit": mỗi lần chém trúng kẻ địch sẽ quét ra vài vệt chém cong (có lõi sáng + hào quang)
 * ngay tại mục tiêu. Chế độ "On Swing": vệt sáng theo đường vung tay. Chỉ hiển thị, không ảnh hưởng gameplay.
 */
public class SwordTrailModule extends Module {
    private final ModeSetting trigger = add(new ModeSetting("trigger", "Trigger", 0, "On Hit", "On Swing"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting length = add(new SliderSetting("length", "Trail Length", 380, 100, 1500, 50, " ms"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 90, 10, 100, 5, "%"));
    private final SliderSetting size = add(new SliderSetting("size", "Slash Size", 1.0, 0.4, 2.5, 0.1, "x"));
    private final SliderSetting count = add(new SliderSetting("count", "Slashes Per Hit", 2, 1, 4, 1, ""));
    private final BoolSetting glow = add(new BoolSetting("glow", "Glow", true));
    private final SliderSetting reach = add(new SliderSetting("reach", "Blade Length", 2.6, 1.0, 4.0, 0.1, ""));
    private final BoolSetting firstPerson = add(new BoolSetting("first_person", "Show In First Person", true));
    private final BoolSetting emptyHand = add(new BoolSetting("empty_hand", "Show With Empty Hand", false));

    /** Một điểm của vệt vung tay. */
    private static final class Pt {
        final double bx, by, bz, tx, ty, tz;
        final long t;
        Pt(double bx, double by, double bz, double tx, double ty, double tz, long t) {
            this.bx = bx; this.by = by; this.bz = bz; this.tx = tx; this.ty = ty; this.tz = tz; this.t = t;
        }
    }

    /** Một vệt chém cong tại mục tiêu: tâm c, trục d (hướng chém), pháp tuyến n (bề ngang vệt). */
    private static final class Slash {
        final double cx, cy, cz, dx, dy, dz, nx, ny, nz, bulge, scale;
        final long t;
        final float hue;
        Slash(double cx, double cy, double cz, double dx, double dy, double dz,
              double nx, double ny, double nz, double bulge, double scale, long t, float hue) {
            this.cx = cx; this.cy = cy; this.cz = cz; this.dx = dx; this.dy = dy; this.dz = dz;
            this.nx = nx; this.ny = ny; this.nz = nz; this.bulge = bulge; this.scale = scale; this.t = t; this.hue = hue;
        }
    }

    private static final int SEGMENTS = 28;
    private static final float SWEEP_MS = 110f;

    private final List<Pt> pts = new ArrayList<Pt>();
    private final List<Slash> slashes = new ArrayList<Slash>();
    private final Random rng = new Random();

    public SwordTrailModule() {
        super("swordtrail", "Sword Trail", "Vệt chém sáng khi đánh trúng kẻ địch (hoặc theo đường vung tay).", Category.EFFECT, true);
        new RainbowSettings(this);
    }

    @Override
    public void onToggle() { pts.clear(); slashes.clear(); }

    /** Gọi khi người chơi đánh trúng một sinh vật. */
    public void onAttack(Entity target) {
        if (!enabled || target == null || !trigger.is("On Hit")) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null) return;

        double cx = target.posX, cy = target.posY + target.height * 0.6, cz = target.posZ;
        double vx = cx - p.posX, vy = cy - (p.posY + p.getEyeHeight()), vz = cz - p.posZ;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4) return;
        vx /= vl; vy /= vl; vz /= vl;
        // r = ngang-phải so với hướng nhìn, u = hướng "lên" của mặt phẳng nhìn
        double rx = -vz, rz = vx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * vy, uy = rz * vx - rx * vz, uz = rx * vy;

        int n = count.getInt();
        double base = (rng.nextBoolean() ? 1 : -1) * (25 + rng.nextInt(30));
        for (int i = 0; i < n; i++) {
            // các vệt cùng một cú chém tỏa ra nhiều góc, nhìn như chém chéo liên tiếp
            double ang = Math.toRadians(base + (i - (n - 1) / 2.0) * (50.0 / Math.max(1, n - 1)) * (i % 2 == 0 ? 1 : -1) + rng.nextInt(16) - 8);
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double dx = rx * ca + ux * sa, dy = uy * sa, dz = rz * ca + uz * sa;
            double nx = -rx * sa + ux * ca, ny = uy * ca, nz = -rz * sa + uz * ca;
            double off = (rng.nextDouble() - 0.5) * 0.25;
            slashes.add(new Slash(cx + nx * off, cy + ny * off, cz + nz * off, dx, dy, dz, nx, ny, nz,
                    (rng.nextBoolean() ? 1 : -1) * (0.25 + rng.nextDouble() * 0.2),
                    size.get() * (0.85 + rng.nextDouble() * 0.3), System.currentTimeMillis() + i * 25L, i * 40f));
        }
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        Entity cam = mc.getRenderViewEntity();
        if (!enabled || p == null || cam == null || mc.theWorld == null) { pts.clear(); slashes.clear(); return; }

        long now = System.currentTimeMillis();
        if (trigger.is("On Swing")) recordSwing(p, pt, now);
        else pts.clear();

        float len = length.getFloat();
        Iterator<Pt> it = pts.iterator();
        while (it.hasNext()) if (now - it.next().t > len) it.remove();
        Iterator<Slash> si = slashes.iterator();
        while (si.hasNext()) if (now - si.next().t > len) si.remove();
        if (pts.size() < 2 && slashes.isEmpty()) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0); // cộng màu: vệt sáng rực hơn
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        if (pts.size() >= 2) drawSwing(now, len, cx, cy, cz);
        for (Slash s : slashes) drawSlash(s, now, len, cx, cy, cz);

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------- vệt chém tại mục tiêu

    private void drawSlash(Slash s, long now, float len, double cx, double cy, double cz) {
        float age = now - s.t;
        if (age < 0f) return;
        float k = Math.min(1f, age / SWEEP_MS);
        float head = -1f + 2f * (1f - (1f - k) * (1f - k));            // đầu vệt lao nhanh rồi chậm lại
        float tail = age > SWEEP_MS ? -1f + 2f * Math.min(1f, (age - SWEEP_MS) / Math.max(1f, len - SWEEP_MS)) : -1f;
        if (head - tail < 0.02f) return;
        float fade = age > SWEEP_MS ? 1f - (age - SWEEP_MS) / Math.max(1f, len - SWEEP_MS) * 0.6f : 1f;
        float maxA = opacity.getFloat() / 100f * fade;
        double half = 1.35 * s.scale;
        double thick = 0.16 * s.scale;

        int c = rainbow.resolve(color.rgb(), s.hue);
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        // lõi: trộn màu về phía trắng
        int lr = r + (255 - r) * 3 / 4, lg = g + (255 - g) * 3 / 4, lb = b + (255 - b) * 3 / 4;

        if (glow.value) strip(s, tail, head, half, thick * 2.6, r, g, b, maxA * 0.4f, cx, cy, cz);
        strip(s, tail, head, half, thick, r, g, b, maxA * 0.85f, cx, cy, cz);
        strip(s, tail, head, half, thick * 0.4, lr, lg, lb, maxA, cx, cy, cz);
    }

    /** Vẽ dải cong từ u = from đến u = to; nhọn ở hai đầu, đuôi mờ dần, đầu sáng nhất. */
    private void strip(Slash s, float from, float to, double half, double thick,
                       int r, int g, int b, float maxA, double cx, double cy, double cz) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            float f = i / (float) SEGMENTS;
            double u = from + (to - from) * f;
            double bend = s.bulge * half * (1.0 - u * u);               // độ cong: giữa vồng lên, hai đầu thu về
            double px = s.cx + s.dx * u * half + s.nx * bend - cx;
            double py = s.cy + s.dy * u * half + s.ny * bend - cy;
            double pz = s.cz + s.dz * u * half + s.nz * bend - cz;
            double w = thick * Math.sin(Math.PI * Math.min(1.0, 0.12 + 0.88 * f)) * (1.0 - 0.5 * Math.abs(u)); // thon ở hai đầu
            int a = Math.round(255f * Math.max(0f, Math.min(1f, maxA * (0.1f + 0.9f * f))));
            wr.pos(px - s.nx * w, py - s.ny * w, pz - s.nz * w).color(r, g, b, a).endVertex();
            wr.pos(px + s.nx * w, py + s.ny * w, pz + s.nz * w).color(r, g, b, a).endVertex();
        }
        tess.draw();
    }

    // ---------------------------------------------------------------- vệt theo đường vung tay (On Swing)

    private void recordSwing(EntityPlayer p, float pt, long now) {
        boolean fp = Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
        if (!p.isSwingInProgress || !(firstPerson.value || !fp) || !(emptyHand.value || p.getHeldItem() != null)) return;
        float s = p.getSwingProgress(pt);
        if (s <= 0f || s >= 1f || (!pts.isEmpty() && now - pts.get(pts.size() - 1).t < 10L)) return;
        double yaw = Math.toRadians(EffectUtil.lerp(p.prevRotationYaw, p.rotationYaw, pt) + (0.5f - s) * 110f);
        double pit = Math.toRadians(EffectUtil.lerp(p.prevRotationPitch, p.rotationPitch, pt) + (s - 0.5f) * 40f);
        double dx = -Math.sin(yaw) * Math.cos(pit), dy = -Math.sin(pit), dz = Math.cos(yaw) * Math.cos(pit);
        double ex = EffectUtil.lerp(p.prevPosX, p.posX, pt);
        double ey = EffectUtil.lerp(p.prevPosY, p.posY, pt) + p.getEyeHeight() - 0.3;
        double ez = EffectUtil.lerp(p.prevPosZ, p.posZ, pt);
        double r = reach.get();
        pts.add(new Pt(ex + dx * 0.8, ey + dy * 0.8, ez + dz * 0.8, ex + dx * r, ey + dy * r, ez + dz * r, now));
    }

    private void drawSwing(long now, float len, double cx, double cy, double cz) {
        float maxA = opacity.getFloat() / 100f;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < pts.size(); i++) {
            Pt q = pts.get(i);
            float fade = Math.max(0f, Math.min(1f, 1f - (now - q.t) / len));
            int c = rainbow.resolve(color.rgb(), i * 12f);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            wr.pos(q.bx - cx, q.by - cy, q.bz - cz).color(r, g, b, Math.round(255f * maxA * fade * 0.25f)).endVertex();
            wr.pos(q.tx - cx, q.ty - cy, q.tz - cz).color(r, g, b, Math.round(255f * maxA * fade)).endVertex();
        }
        tess.draw();
    }
}
