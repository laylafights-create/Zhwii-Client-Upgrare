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
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/**
 * Vệt chém sáng thẳng. Chế độ "On Hit": khi đánh trúng kẻ địch, vệt chém hiện ở mặt trước mục tiêu, hướng về phía người chơi.
 * Chỉ kích hoạt khi nhận sự kiện đánh trúng; hiệu ứng chỉ mang tính hiển thị.
 */
public class SwordTrailModule extends Module {
    private final ModeSetting trigger = add(new ModeSetting("trigger", "Trigger", 0, "On Hit"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting length = add(new SliderSetting("length", "Trail Length", 380, 100, 1500, 50, " ms"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 90, 10, 100, 5, "%"));
    private final SliderSetting size = add(new SliderSetting("size", "Slash Size", 1.1, 0.4, 2.5, 0.1, "x"));
    private final BoolSetting glow = add(new BoolSetting("glow", "Glow", true));
    private final SliderSetting reach = add(new SliderSetting("reach", "Blade Length", 2.6, 1.0, 4.0, 0.1, ""));
    private final BoolSetting sparks = add(new BoolSetting("sparks", "Hit Sparks", true));
    private final SliderSetting sparkCount = add(new SliderSetting("spark_count", "Spark Count", 18, 4, 40, 2, ""));
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

    /** Một vệt chém thẳng ở mặt trước mục tiêu: tâm c, trục d (hướng chém), pháp tuyến n (bề ngang vệt). */
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

    /** Hạt sáng bắn ra khi đánh trúng. Vận tốc tính theo block/giây. */
    private static final class Spark {
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, size, hue;
        Spark(double x, double y, double z, double vx, double vy, double vz, long t, float life, float size, float hue) {
            this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.size = size; this.hue = hue;
        }
    }

    /** Chớp sáng tại điểm va chạm. */
    private static final class Flash {
        final double x, y, z, rot;
        final long t;
        Flash(double x, double y, double z, double rot, long t) { this.x = x; this.y = y; this.z = z; this.rot = rot; this.t = t; }
    }

    private static final int SEGMENTS = 36;
    private static final float FLASH_MS = 160f;
    private static final double SPARK_DRAG = 2.6, SPARK_GRAVITY = 0.0;
    private static final float SWEEP_MS = 110f;

    private final List<Pt> pts = new ArrayList<Pt>();
    private final List<Slash> slashes = new ArrayList<Slash>();
    private final List<Spark> sparkList = new ArrayList<Spark>();
    private final List<Flash> flashes = new ArrayList<Flash>();
    private final Random rng = new Random();

    public SwordTrailModule() {
        super("swordtrail", "Sword Trail", "Vệt kiếm thẳng ở phía trước mục tiêu khi đánh trúng, kèm chớp sáng và bụi phát sáng.", Category.EFFECT, true);
        new RainbowSettings(this);
    }

    @Override
    public void onToggle() { pts.clear(); slashes.clear(); sparkList.clear(); flashes.clear(); }

    /** Gọi khi người chơi đánh trúng một sinh vật. */
    public void onAttack(Entity target) {
        if (!enabled || target == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null) return;

        double cx = target.posX, cy = target.posY + target.height * 0.6, cz = target.posZ;
        double vx = cx - p.posX, vy = cy - (p.posY + p.getEyeHeight()), vz = cz - p.posZ;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4) return;
        vx /= vl; vy /= vl; vz /= vl;
        // Đẩy điểm hiệu ứng ra khỏi tâm hitbox về phía người chơi để vệt nằm trên mặt trước mục tiêu.
        // Hướng v (vx, vy, vz) đi từ người chơi tới mục tiêu, nên trừ v để dịch về phía người chơi.
        double frontOffset = 0.38;
        cx -= vx * frontOffset;
        cy -= vy * frontOffset;
        cz -= vz * frontOffset;
        // r = ngang-phải so với hướng nhìn, u = hướng "lên" của mặt phẳng nhìn
        double rx = -vz, rz = vx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * vy, uy = rz * vx - rx * vz, uz = rx * vy;

        long nowMs = System.currentTimeMillis();
        flashes.add(new Flash(cx, cy, cz, rng.nextDouble() * Math.PI, nowMs));
        double base = pickAngle();
        if (sparks.value) {
            double ba = Math.toRadians(base), bca = Math.cos(ba), bsa = Math.sin(ba);
            spawnSparks(cx, cy, cz, rx * bca + ux * bsa, uy * bsa, rz * bca + uz * bsa, -vx, -vy, -vz);
        }
        if (!trigger.is("On Hit")) return;
        // A straight, tapered slash sweeps across the front face of the target on confirmed hit.
        double ang = Math.toRadians(base);
        double ca = Math.cos(ang), sa = Math.sin(ang);
        double dx = rx * ca + ux * sa, dy = uy * sa, dz = rz * ca + uz * sa;
        double nx = -rx * sa + ux * ca, ny = uy * ca, nz = -rz * sa + uz * ca;
        double off = (rng.nextDouble() - 0.5) * 0.2;
        slashes.add(new Slash(cx + nx * off, cy + ny * off, cz + nz * off, dx, dy, dz, nx, ny, nz,
                0.24 + rng.nextDouble() * 0.10, size.get() * (0.9 + rng.nextDouble() * 0.2), nowMs, 0f));
    }

    private int lastSign = 1;

    /**
     * Góc chém (độ, 0 = ngang, 90 = dọc). Phần lớn là chém chéo 30-60 độ, đôi khi ngang hoặc gần dọc;
     * thường đổi chiều so với lần trước để các nhát liên tiếp trông như chém qua chém lại.
     */
    private double pickAngle() {
        double roll = rng.nextDouble(), mag;
        if (roll < 0.6) mag = 30 + rng.nextDouble() * 30;
        else if (roll < 0.85) mag = rng.nextDouble() * 14;
        else mag = 62 + rng.nextDouble() * 18;
        int sign = rng.nextDouble() < 0.75 ? -lastSign : lastSign;
        lastSign = sign;
        return sign * mag;
    }

    /** Bắn hạt: một nửa chạy dọc đường chém, nửa còn lại tỏa theo mọi hướng, hơi dội về phía người đánh. */
    private void spawnSparks(double cx, double cy, double cz, double ax, double ay, double az, double bx, double by, double bz) {
        long now = System.currentTimeMillis();
        int n = sparkCount.getInt();
        for (int i = 0; i < n; i++) {
            double vx, vy, vz, speed;
            if (i % 2 == 0) { // dọc đường chém, hai chiều
                double sg = rng.nextBoolean() ? 1 : -1;
                vx = ax * sg + rng.nextGaussian() * 0.25; vy = ay * sg + rng.nextGaussian() * 0.25; vz = az * sg + rng.nextGaussian() * 0.25;
                speed = 2.2 + rng.nextDouble() * 3.8;
            } else {
                // Softer, slower motes create a dust-like halo around the bright impact.
                vx = rng.nextGaussian() * 0.72 + bx * 0.45;
                vy = rng.nextGaussian() * 0.62 + 0.18 + by * 0.45;
                vz = rng.nextGaussian() * 0.72 + bz * 0.45;
                speed = 0.65 + rng.nextDouble() * 1.9;
            }
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6;
            double k = speed / l;
            sparkList.add(new Spark(cx, cy, cz, vx * k, vy * k, vz * k, now,
                    420f + rng.nextFloat() * 480f, 0.70f + rng.nextFloat() * 0.55f, i * 23f));
        }
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        Entity cam = mc.getRenderViewEntity();
        if (!enabled || p == null || cam == null || mc.theWorld == null) { pts.clear(); slashes.clear(); sparkList.clear(); flashes.clear(); return; }

        long now = System.currentTimeMillis();
        // Impact effect: emit only from onAttack(), never from a normal swing.
        pts.clear();

        float len = length.getFloat();
        Iterator<Pt> it = pts.iterator();
        while (it.hasNext()) if (now - it.next().t > len) it.remove();
        Iterator<Slash> si = slashes.iterator();
        while (si.hasNext()) if (now - si.next().t > len) si.remove();
        Iterator<Spark> ki = sparkList.iterator();
        while (ki.hasNext()) { Spark k = ki.next(); if (now - k.t > k.life) ki.remove(); }
        Iterator<Flash> fi = flashes.iterator();
        while (fi.hasNext()) if (now - fi.next().t > FLASH_MS) fi.remove();
        if (slashes.isEmpty() && sparkList.isEmpty() && flashes.isEmpty()) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0); // cộng màu: vệt sáng rực hơn
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        for (Slash s : slashes) drawSlash(s, now, len, cx, cy, cz);
        if (!flashes.isEmpty() || !sparkList.isEmpty()) drawImpact(now, cx, cy, cz);
        GL11.glLineWidth(1f);

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
        // Quét nhanh ở đầu, giảm tốc mượt ở cuối; phần đuôi rút dần.
        float head = -1f + 2f * (1f - (1f - k) * (1f - k));
        float tail = age > SWEEP_MS
                ? -1f + 2f * Math.min(1f, (age - SWEEP_MS) / Math.max(1f, len - SWEEP_MS))
                : -1f;
        if (head - tail < 0.02f) return;
        float rest = age > SWEEP_MS ? (age - SWEEP_MS) / Math.max(1f, len - SWEEP_MS) : 0f;
        float fade = (1f - rest) * (1f - rest);
        float maxA = opacity.getFloat() / 100f * fade;
        double half = 1.35 * s.scale;
        double thick = 0.115 * s.scale; // vệt chém thanh mảnh hơn

        int c = rainbow.resolve(color.rgb(), s.hue);
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        // Lõi gần trắng tạo cảm giác bloom như hiệu ứng chém fantasy.
        int lr = r + (255 - r) * 4 / 5;
        int lg = g + (255 - g) * 4 / 5;
        int lb = b + (255 - b) * 4 / 5;

        if (glow.value) {
            strip(s, tail, head, half * 1.02, thick * 2.5, r, g, b, maxA * 0.10f, cx, cy, cz);
            strip(s, tail, head, half, thick * 1.55, r, g, b, maxA * 0.20f, cx, cy, cz);
        }
        // Dải màu chính và đường lõi sáng mảnh ở giữa.
        strip(s, tail, head, half, thick, r, g, b, maxA * 0.82f, cx, cy, cz);
        strip(s, tail, head, half * 0.985, thick * 0.22, lr, lg, lb, maxA, cx, cy, cz);
    }

    /** Vẽ dải chém thẳng từ u = from đến u = to; nhọn ở hai đầu, đuôi mờ dần, đầu sáng nhất. */
    private void strip(Slash s, float from, float to, double half, double thick,
                       int r, int g, int b, float maxA, double cx, double cy, double cz) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            float f = i / (float) SEGMENTS;
            double u = from + (to - from) * f;
            double bend = 0.0; // vệt chém thẳng, không uốn cong
            double px = s.cx + s.dx * u * half + s.nx * bend - cx;
            double py = s.cy + s.dy * u * half + s.ny * bend - cy;
            double pz = s.cz + s.dz * u * half + s.nz * bend - cz;
            double w = thick * Math.sin(Math.PI * Math.pow(f, 1.4)); // dải thẳng, thu nhọn ở hai đầu
            int a = Math.round(255f * Math.max(0f, Math.min(1f, maxA * (float) Math.pow(f, 1.2))));
            wr.pos(px - s.nx * w, py - s.ny * w, pz - s.nz * w).color(r, g, b, a).endVertex();
            wr.pos(px + s.nx * w, py + s.ny * w, pz + s.nz * w).color(r, g, b, a).endVertex();
        }
        tess.draw();
    }

    // ---------------------------------------------------------------- chớp sáng + hạt sáng khi đánh trúng

    private void drawImpact(long now, double cx, double cy, double cz) {
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        double yaw = Math.toRadians(rm.playerViewY), pitch = Math.toRadians(rm.playerViewX);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);                                  // trục phải của màn hình
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        double ux = ly * rz, uy = lz * rx - lx * rz, uz = -ly * rx;                     // trục lên của màn hình
        float maxA = opacity.getFloat() / 100f;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        for (Flash f : flashes) {
            float k = (now - f.t) / FLASH_MS;
            if (k < 0f || k >= 1f) continue;
            float a = (1f - k) * (1f - k) * maxA;
            double sz = size.get() * (0.22 + 0.3 * (1f - (1f - k) * (1f - k)));
            int c = rainbow.resolve(color.rgb(), 0f);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            double px = f.x - cx, py = f.y - cy, pz = f.z - cz;
            fan(wr, tess, px, py, pz, rx, 0, rz, ux, uy, uz, sz, sz, r, g, b, a * 0.55f, 255, 255, 255);
            for (int i = 0; i < 2; i++) { // sao 4 cánh, xoay ngẫu nhiên
                double ang = i * Math.PI / 2.0;
                double dxr = Math.cos(f.rot + ang), dyr = Math.sin(f.rot + ang);
                double ax = rx * dxr + ux * dyr, ay = uy * dyr, az = rz * dxr + uz * dyr;
                double bxr = -dyr, byr = dxr;
                double qx = rx * bxr + ux * byr, qy = uy * byr, qz = rz * bxr + uz * byr;
                fan(wr, tess, px, py, pz, ax, ay, az, qx, qy, qz, sz * 2.4, sz * 0.12, r, g, b, a, 255, 255, 255);
            }
        }

        // hạt sáng: vệt ngắn (đuôi mờ) + chấm phát sáng ở đầu
        for (Spark k : sparkList) {
            double age = (now - k.t) / 1000.0;
            if (age < 0) continue;
            float lifeK = (now - k.t) / k.life;
            float a = (1f - lifeK) * (1f - lifeK) * Math.min(1f, maxA * 1.15f);
            double dr = Math.exp(-SPARK_DRAG * age), disp = (1.0 - dr) / SPARK_DRAG;
            double hx = k.x + k.vx * disp - cx, hy = k.y + k.vy * disp - 0.5 * SPARK_GRAVITY * age * age - cy, hz = k.z + k.vz * disp - cz;
            double cvx = k.vx * dr, cvy = k.vy * dr - SPARK_GRAVITY * age, cvz = k.vz * dr;
            double tx = hx - cvx * 0.05, ty = hy - cvy * 0.05, tz = hz - cvz * 0.05;
            int c = rainbow.resolve(color.rgb(), k.hue);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int wr2 = r + (255 - r) * 3 / 4, wg = g + (255 - g) * 3 / 4, wb = b + (255 - b) * 3 / 4;
            // Chỉ vẽ một đuôi màu; bỏ lớp đuôi trắng gây cảm giác có mảng/nền trắng.
            GL11.glLineWidth(2.2f);
            wr.begin(1, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(tx, ty, tz).color(r, g, b, 0).endVertex();
            wr.pos(hx, hy, hz).color(r, g, b, Math.round(255f * a * 0.9f)).endVertex();
            tess.draw();
            // Hạt lớn hơn rõ rệt, lõi dùng màu hiệu ứng thay vì trắng gắt.
            double gs = 0.17 * k.size * (1.0 - 0.18 * lifeK);
            fan(wr, tess, hx, hy, hz, rx, 0, rz, ux, uy, uz, gs * 2.8, gs * 2.8,
                    r, g, b, a, wr2, wg, wb);
        }
    }

    /** Tam giác quạt phát sáng: tâm (màu cr/cg/cb, alpha a) mờ dần ra rìa, nằm trong mặt phẳng (a-axis, b-axis). */
    private static void fan(WorldRenderer wr, Tessellator tess, double px, double py, double pz,
                            double ax, double ay, double az, double bx, double by, double bz,
                            double la, double lb, int r, int g, int b, float a, int cr, int cg, int cb) {
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px, py, pz).color(cr, cg, cb, Math.round(255f * Math.max(0f, Math.min(1f, a)))).endVertex();
        for (int i = 0; i <= 12; i++) {
            double ang = i / 12.0 * Math.PI * 2.0, ca = Math.cos(ang) * la, sa = Math.sin(ang) * lb;
            wr.pos(px + ax * ca + bx * sa, py + ay * ca + by * sa, pz + az * ca + bz * sa).color(r, g, b, 0).endVertex();
        }
        tess.draw();
    }

    // ---------------------------------------------------------------- legacy swing helpers (not called in hit-only mode)

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
