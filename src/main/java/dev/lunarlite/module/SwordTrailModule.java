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
 * Vệt chém điện ảnh. Chế độ "On Hit": khi đánh trúng kẻ địch, một nhát chém cong hình lưỡi liềm
 * quét qua mặt trước mục tiêu, kèm chớp sáng anamorphic, vòng sóng xung kích và tàn lửa bay.
 * Chỉ kích hoạt khi nhận sự kiện đánh trúng; hiệu ứng chỉ mang tính hiển thị.
 */
public class SwordTrailModule extends Module {
    private final ModeSetting trigger = add(new ModeSetting("trigger", "Trigger", 0, "On Hit"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting length = add(new SliderSetting("length", "Trail Length", 420, 100, 1500, 50, " ms"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 90, 10, 100, 5, "%"));
    private final SliderSetting size = add(new SliderSetting("size", "Slash Size", 1.1, 0.4, 2.5, 0.1, "x"));
    private final BoolSetting glow = add(new BoolSetting("glow", "Glow", true));
    private final BoolSetting streak = add(new BoolSetting("streak", "Anamorphic Streak", true));
    private final BoolSetting shockwave = add(new BoolSetting("shockwave", "Shockwave Ring", true));
    private final BoolSetting chromatic = add(new BoolSetting("chromatic", "Chromatic Edge", true));
    private final SliderSetting reach = add(new SliderSetting("reach", "Blade Length", 2.6, 1.0, 4.0, 0.1, ""));
    private final BoolSetting sparks = add(new BoolSetting("sparks", "Hit Sparks", true));
    private final SliderSetting sparkCount = add(new SliderSetting("spark_count", "Spark Count", 22, 4, 40, 2, ""));
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

    /** Một nhát chém ở mặt trước mục tiêu: tâm c, trục d (hướng chém), pháp tuyến n (bề ngang), bulge (độ cong). */
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

    /** Tàn lửa bắn ra khi đánh trúng. Vận tốc tính theo block/giây. */
    private static final class Spark {
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, size, hue;
        Spark(double x, double y, double z, double vx, double vy, double vz, long t, float life, float size, float hue) {
            this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.size = size; this.hue = hue;
        }
    }

    /** Chớp sáng + vòng xung kích tại điểm va chạm. */
    private static final class Flash {
        final double x, y, z, rot, streakTilt;
        final long t;
        Flash(double x, double y, double z, double rot, double streakTilt, long t) {
            this.x = x; this.y = y; this.z = z; this.rot = rot; this.streakTilt = streakTilt; this.t = t;
        }
    }

    private static final int SEGMENTS = 44;
    private static final float FLASH_MS = 260f;
    private static final double SPARK_DRAG = 2.4, SPARK_GRAVITY = 1.6;
    private static final float SWEEP_MS = 120f;

    private final List<Pt> pts = new ArrayList<Pt>();
    private final List<Slash> slashes = new ArrayList<Slash>();
    private final List<Spark> sparkList = new ArrayList<Spark>();
    private final List<Flash> flashes = new ArrayList<Flash>();
    private final Random rng = new Random();

    public SwordTrailModule() {
        super("swordtrail", "Sword Trail", "Vệt kiếm điện ảnh ở phía trước mục tiêu khi đánh trúng, kèm chớp sáng, sóng xung kích và tàn lửa.", Category.EFFECT, true);
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
        flashes.add(new Flash(cx, cy, cz, rng.nextDouble() * Math.PI, 0.0, nowMs));
        double base = pickAngle();
        if (sparks.value) {
            double ba = Math.toRadians(base), bca = Math.cos(ba), bsa = Math.sin(ba);
            spawnSparks(cx, cy, cz, rx * bca + ux * bsa, uy * bsa, rz * bca + uz * bsa, -vx, -vy, -vz);
        }
        if (!trigger.is("On Hit")) return;
        double ang = Math.toRadians(base);
        double ca = Math.cos(ang), sa = Math.sin(ang);
        double dx = rx * ca + ux * sa, dy = uy * sa, dz = rz * ca + uz * sa;
        double nx = -rx * sa + ux * ca, ny = uy * ca, nz = -rz * sa + uz * ca;
        double off = (rng.nextDouble() - 0.5) * 0.16;
        // bulge mang dấu theo lastSign để nhát chém cong về cùng phía với chiều vung.
        double bulge = (0.30 + rng.nextDouble() * 0.12) * lastSign;
        slashes.add(new Slash(cx + nx * off, cy + ny * off, cz + nz * off, dx, dy, dz, nx, ny, nz,
                bulge, size.get() * (0.9 + rng.nextDouble() * 0.2), nowMs, 0f));
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

    /** Bắn tàn lửa: một nửa chạy dọc đường chém, nửa còn lại là bụi sáng tỏa ra và hơi dội về phía người đánh. */
    private void spawnSparks(double cx, double cy, double cz, double ax, double ay, double az, double bx, double by, double bz) {
        long now = System.currentTimeMillis();
        int n = sparkCount.getInt();
        for (int i = 0; i < n; i++) {
            double vx, vy, vz, speed;
            if (i % 2 == 0) { // tia lửa dọc đường chém, hai chiều, nhanh
                double sg = rng.nextBoolean() ? 1 : -1;
                vx = ax * sg + rng.nextGaussian() * 0.22; vy = ay * sg + rng.nextGaussian() * 0.22; vz = az * sg + rng.nextGaussian() * 0.22;
                speed = 2.8 + rng.nextDouble() * 4.6;
            } else {
                // Bụi sáng chậm tạo quầng halo quanh điểm va chạm.
                vx = rng.nextGaussian() * 0.72 + bx * 0.5;
                vy = rng.nextGaussian() * 0.62 + 0.22 + by * 0.5;
                vz = rng.nextGaussian() * 0.72 + bz * 0.5;
                speed = 0.55 + rng.nextDouble() * 1.7;
            }
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6;
            double k = speed / l;
            sparkList.add(new Spark(cx, cy, cz, vx * k, vy * k, vz * k, now,
                    460f + rng.nextFloat() * 560f, 0.65f + rng.nextFloat() * 0.6f, i * 23f));
        }
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        Entity cam = mc.getRenderViewEntity();
        if (!enabled || p == null || cam == null || mc.theWorld == null) { pts.clear(); slashes.clear(); sparkList.clear(); flashes.clear(); return; }

        long now = System.currentTimeMillis();
        // Hiệu ứng va chạm: chỉ phát từ onAttack(), không phát khi vung tay thường.
        pts.clear();

        float len = length.getFloat();
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
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0); // cộng màu: ánh sáng bùng rực
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

    // ---------------------------------------------------------------- nhát chém tại mục tiêu

    private static float easeOutCubic(float x) { float i = 1f - x; return 1f - i * i * i; }

    private void drawSlash(Slash s, long now, float len, double cx, double cy, double cz) {
        float age = now - s.t;
        if (age < 0f) return;
        float k = Math.min(1f, age / SWEEP_MS);
        // Đầu quét cực nhanh rồi giảm tốc (ease-out cubic); đuôi bám theo bằng ease-in, rút dần như vệt sáng tan.
        float head = -1f + 2f * easeOutCubic(k);
        float rest = age > SWEEP_MS ? Math.min(1f, (age - SWEEP_MS) / Math.max(1f, len - SWEEP_MS)) : 0f;
        float tail = -1f + 2f * (float) Math.pow(rest, 1.6);
        if (head - tail < 0.02f) return;
        // Chớp bừng ở khoảnh khắc chạm, sau đó tắt dần mượt.
        float pop = 1f + 0.55f * (float) Math.exp(-age / 45f);
        float fade = (1f - rest) * (1f - rest);
        float maxA = Math.min(1f, opacity.getFloat() / 100f * fade * pop);
        double half = 1.35 * s.scale;
        double thick = 0.105 * s.scale * (1.0 + 0.35 * Math.exp(-age / 60.0)); // lúc đầu dày hơn rồi thanh lại

        int c = rainbow.resolve(color.rgb(), s.hue);
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        // Lõi gần trắng (bloom) và quầng ngoài ngả màu sâu hơn để có chiều sâu như grading điện ảnh.
        int lr = r + (255 - r) * 5 / 6, lg = g + (255 - g) * 5 / 6, lb = b + (255 - b) * 5 / 6;
        int dr = r * 3 / 4, dg = g * 3 / 4, db = Math.min(255, b + 30);

        if (glow.value) {
            strip(s, tail, head, half * 1.03, thick * 4.2, 0.0, dr, dg, db, maxA * 0.08f, cx, cy, cz);
            strip(s, tail, head, half * 1.01, thick * 2.6, 0.0, r, g, b, maxA * 0.14f, cx, cy, cz);
            strip(s, tail, head, half, thick * 1.6, 0.0, r, g, b, maxA * 0.24f, cx, cy, cz);
        }
        if (chromatic.value) {
            // Viền lệch kênh màu (đỏ / lam) hai bên nhát chém, mô phỏng sắc sai của ống kính.
            strip(s, tail, head, half, thick * 0.9, thick * 0.85, 255, 70, 110, maxA * 0.30f, cx, cy, cz);
            strip(s, tail, head, half, thick * 0.9, -thick * 0.85, 70, 170, 255, maxA * 0.30f, cx, cy, cz);
        }
        // Vệt gió mảnh song song, ngắn hơn, tạo cảm giác tốc độ.
        float sh = head, st = tail + (head - tail) * 0.35f;
        strip(s, st, sh, half * 0.9, thick * 0.10, thick * 3.2, r, g, b, maxA * 0.35f, cx, cy, cz);
        strip(s, st, sh, half * 0.8, thick * 0.08, -thick * 3.6, r, g, b, maxA * 0.28f, cx, cy, cz);
        // Dải màu chính và đường lõi trắng nóng.
        strip(s, tail, head, half, thick, 0.0, r, g, b, maxA * 0.85f, cx, cy, cz);
        strip(s, tail, head, half * 0.99, thick * 0.26, 0.0, lr, lg, lb, maxA, cx, cy, cz);
    }

    /**
     * Vẽ dải chém cong từ u = from đến u = to (u thuộc [-1, 1]); nhọn ở hai đầu, đuôi mờ dần, đầu sáng nhất.
     * off dịch cả dải theo pháp tuyến để vẽ viền / vệt gió.
     */
    private void strip(Slash s, float from, float to, double half, double thick, double off,
                       int r, int g, int b, float maxA, double cx, double cy, double cz) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            float f = i / (float) SEGMENTS;
            double u = from + (to - from) * f;
            double bend = off; // vệt chém thẳng, không uốn cong (off chỉ dùng để dịch viền / vệt gió)
            double px = s.cx + s.dx * u * half + s.nx * bend - cx;
            double py = s.cy + s.dy * u * half + s.ny * bend - cy;
            double pz = s.cz + s.dz * u * half + s.nz * bend - cz;
            // Bề ngang: thu nhọn hai đầu, phình về phía đầu quét như lưỡi kiếm.
            double w = thick * Math.sin(Math.PI * Math.pow(f, 1.3));
            int a = Math.round(255f * Math.max(0f, Math.min(1f, maxA * (float) Math.pow(f, 1.15))));
            wr.pos(px - s.nx * w, py - s.ny * w, pz - s.nz * w).color(r, g, b, a).endVertex();
            wr.pos(px + s.nx * w, py + s.ny * w, pz + s.nz * w).color(r, g, b, a).endVertex();
        }
        tess.draw();
    }

    // ---------------------------------------------------------------- chớp sáng, sóng xung kích, tàn lửa

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
            float fast = Math.min(1f, k * 1.6f);                    // chớp lõi tắt nhanh hơn vòng xung kích
            float a = (1f - fast) * (1f - fast) * maxA;
            double sz = size.get() * (0.20 + 0.34 * easeOutCubic(fast));
            int c = rainbow.resolve(color.rgb(), 0f);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            double px = f.x - cx, py = f.y - cy, pz = f.z - cz;

            // Quầng bloom tròn, lõi trắng.
            if (a > 0f) {
                fan(wr, tess, px, py, pz, rx, 0, rz, ux, uy, uz, sz * 1.15, sz * 1.15, r, g, b, a * 0.50f, 255, 255, 255);
                fan(wr, tess, px, py, pz, rx, 0, rz, ux, uy, uz, sz * 0.38, sz * 0.38, 255, 255, 255, a * 0.9f, 255, 255, 255);
            }
            // Vệt sáng anamorphic ngang (đặc trưng ống kính điện ảnh), ngả lam.
            if (streak.value && a > 0f) {
                int sr = Math.min(255, r / 2 + 70), sg = Math.min(255, g / 2 + 110), sb = 255;
                double len = size.get() * (1.5 + 2.2 * easeOutCubic(fast));
                fan(wr, tess, px, py, pz, rx, 0, rz, ux, uy, uz, len, sz * 0.045, sr, sg, sb, a * 0.85f, 255, 255, 255);
                fan(wr, tess, px, py, pz, rx, 0, rz, ux, uy, uz, len * 0.55, sz * 0.10, sr, sg, sb, a * 0.35f, 255, 255, 255);
            }
            // Sao 4 cánh xoay ngẫu nhiên.
            for (int i = 0; i < 2; i++) {
                double ang = i * Math.PI / 2.0;
                double dxr = Math.cos(f.rot + ang), dyr = Math.sin(f.rot + ang);
                double ax = rx * dxr + ux * dyr, ay = uy * dyr, az = rz * dxr + uz * dyr;
                double bxr = -dyr, byr = dxr;
                double qx = rx * bxr + ux * byr, qy = uy * byr, qz = rz * bxr + uz * byr;
                fan(wr, tess, px, py, pz, ax, ay, az, qx, qy, qz, sz * (i == 0 ? 2.6 : 1.7), sz * 0.10, r, g, b, a, 255, 255, 255);
            }
            // Vòng sóng xung kích lan ra, mờ dần.
            if (shockwave.value) {
                float rk = easeOutCubic(k);
                double rad = size.get() * (0.15 + 1.05 * rk);
                float ra = (1f - k) * (1f - k) * maxA * 0.55f;
                ring(wr, tess, px, py, pz, rx, 0, rz, ux, uy, uz, rad, size.get() * 0.045 * (1.0 - 0.5 * k), r, g, b, ra);
            }
        }

        // Tàn lửa: vệt dài theo vận tốc (đuôi mờ) + hạt phát sáng, chuyển từ trắng nóng sang màu hiệu ứng rồi tắt.
        for (Spark k : sparkList) {
            double age = (now - k.t) / 1000.0;
            if (age < 0) continue;
            float lifeK = (now - k.t) / k.life;
            float a = (1f - lifeK) * (1f - lifeK) * Math.min(1f, maxA * 1.15f);
            double dr = Math.exp(-SPARK_DRAG * age), disp = (1.0 - dr) / SPARK_DRAG;
            double hx = k.x + k.vx * disp - cx;
            double hy = k.y + k.vy * disp - 0.5 * SPARK_GRAVITY * age * age - cy;
            double hz = k.z + k.vz * disp - cz;
            double cvx = k.vx * dr, cvy = k.vy * dr - SPARK_GRAVITY * age, cvz = k.vz * dr;
            double tl = 0.055 + 0.03 * k.size;
            double tx = hx - cvx * tl, ty = hy - cvy * tl, tz = hz - cvz * tl;
            int c = rainbow.resolve(color.rgb(), k.hue);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            float hot = Math.max(0f, 1f - lifeK * 2.2f);              // ban đầu trắng nóng
            int hr = r + Math.round((255 - r) * hot), hg = g + Math.round((255 - g) * hot), hb = b + Math.round((255 - b) * hot);
            GL11.glLineWidth(2.4f);
            wr.begin(1, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(tx, ty, tz).color(r, g, b, 0).endVertex();
            wr.pos(hx, hy, hz).color(hr, hg, hb, Math.round(255f * a * 0.95f)).endVertex();
            tess.draw();
            double gs = 0.15 * k.size * (1.0 - 0.3 * lifeK);
            fan(wr, tess, hx, hy, hz, rx, 0, rz, ux, uy, uz, gs * 2.6, gs * 2.6, r, g, b, a * 0.8f, hr, hg, hb);
        }
    }

    /** Tam giác quạt phát sáng: tâm (màu cr/cg/cb, alpha a) mờ dần ra rìa, nằm trong mặt phẳng (a-axis, b-axis). */
    private static void fan(WorldRenderer wr, Tessellator tess, double px, double py, double pz,
                            double ax, double ay, double az, double bx, double by, double bz,
                            double la, double lb, int r, int g, int b, float a, int cr, int cg, int cb) {
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px, py, pz).color(cr, cg, cb, Math.round(255f * Math.max(0f, Math.min(1f, a)))).endVertex();
        for (int i = 0; i <= 16; i++) {
            double ang = i / 16.0 * Math.PI * 2.0, ca = Math.cos(ang) * la, sa = Math.sin(ang) * lb;
            wr.pos(px + ax * ca + bx * sa, py + ay * ca + by * sa, pz + az * ca + bz * sa).color(r, g, b, 0).endVertex();
        }
        tess.draw();
    }

    /** Vòng sáng mềm: mép trong và mép ngoài trong suốt, giữa vòng sáng nhất. */
    private static void ring(WorldRenderer wr, Tessellator tess, double px, double py, double pz,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double rad, double wid, int r, int g, int b, float a) {
        if (a <= 0f) return;
        int mid = Math.round(255f * Math.max(0f, Math.min(1f, a)));
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 40; i++) {
            double ang = i / 40.0 * Math.PI * 2.0, ca = Math.cos(ang), sa = Math.sin(ang);
            double ix = (rad - wid) * ca, iy = (rad - wid) * sa, ox = (rad + wid) * ca, oy = (rad + wid) * sa;
            double mx = rad * ca, my = rad * sa;
            wr.pos(px + ax * ix + bx * iy, py + ay * ix + by * iy, pz + az * ix + bz * iy).color(r, g, b, 0).endVertex();
            wr.pos(px + ax * mx + bx * my, py + ay * mx + by * my, pz + az * mx + bz * my).color(255, 255, 255, mid).endVertex();
        }
        tess.draw();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 40; i++) {
            double ang = i / 40.0 * Math.PI * 2.0, ca = Math.cos(ang), sa = Math.sin(ang);
            double ox = (rad + wid) * ca, oy = (rad + wid) * sa, mx = rad * ca, my = rad * sa;
            wr.pos(px + ax * mx + bx * my, py + ay * mx + by * my, pz + az * mx + bz * my).color(255, 255, 255, mid).endVertex();
            wr.pos(px + ax * ox + bx * oy, py + ay * ox + by * oy, pz + az * ox + bz * oy).color(r, g, b, 0).endVertex();
        }
        tess.draw();
    }

    // ---------------------------------------------------------------- legacy swing helpers (không dùng ở chế độ hit-only)

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
