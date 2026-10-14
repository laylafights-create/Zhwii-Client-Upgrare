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
 * <h2>Vệt Kiếm - vệt chém mảnh (phong cách VCraft)</h2>
 *
 * <h3>Tính năng</h3>
 * <ol>
 *   <li><b>Kích hoạt khi đánh trúng</b>: chỉ phát hiệu ứng khi nhận sự kiện
 *       {@link #onAttack(Entity)}, không phát khi vung tay thường. Hiệu ứng chỉ mang tính hiển thị.</li>
 *   <li><b>Một vệt chém duy nhất</b>: dải sáng rất mỏng, ngắn gọn, luôn xoay mặt về camera, góc ngẫu nhiên
 *       trong khoảng -30° đến 30°. Dải dài ra một chút, mỏng dần đến gần như biến mất, trôi nhẹ dọc
 *       hướng chém, chuyển động theo smoothstep rồi mờ dần (mặc định 350 ms).</li>
 *   <li><b>Tàn lửa</b>: tia lửa bay có lực cản và trọng lực; nửa chạy dọc đường chém,
 *       nửa là bụi sáng tỏa quanh; chuyển từ trắng nóng sang màu hiệu ứng rồi tắt.</li>
 *   <li><b>Hạt đánh trúng</b>: nhiều vệt trắng nhỏ, mảnh như gạch ngắn bắn tỏa ra mọi hướng quanh
 *       mục tiêu, trôi chậm, rơi nhẹ rồi mờ dần.</li>
 *   <li><b>Không có nền</b>: không có chớp sáng, quầng, sao, vòng sóng hay vệt ngang đi kèm;
 *       khi chém chỉ có vệt chém và bụi lửa búng ra.</li>
 *   <li><b>Màu sắc</b>: chọn màu tùy ý hoặc dùng chế độ cầu vồng ({@link RainbowSettings}).</li>
 * </ol>
 *
 * <h3>Tùy chỉnh</h3>
 * <ul>
 *   <li>Kích hoạt, Màu sắc, Thời gian vệt chém (ms), Độ đục (%), Cỡ vệt chém (x)</li>
 *   <li>Tàn lửa, Số tàn lửa</li>
 *   <li>Hạt đánh trúng, Số hạt, Độ văng hạt</li>
 *   <li>Độ dài lưỡi kiếm, Hiện ở góc nhìn thứ nhất, Hiện khi tay không (chỉ dùng cho vệt vung tay cũ, hiện không hoạt động)</li>
 * </ul>
 *
 * <h3>Cách dùng</h3>
 * Gọi {@link #onAttack(Entity)} khi người chơi đánh trúng sinh vật và {@link #render(float)}
 * từ RenderWorldLastEvent.
 */
public class SwordTrailModule extends Module {

    // =====================================================================
    //  Settings
    // =====================================================================

    private final ModeSetting   trigger    = add(new ModeSetting("trigger", "Kích hoạt", 0, TRIGGER_ON_HIT));
    private final ColorSetting  color      = add(new ColorSetting("color", "Màu sắc", 0x9EEFEB));
    private final SliderSetting length     = add(new SliderSetting("length", "Thời gian vệt chém", 350, 100, 1500, 50, " ms"));
    private final SliderSetting opacity    = add(new SliderSetting("opacity", "Độ đục", 90, 10, 100, 5, "%"));
    private final SliderSetting size       = add(new SliderSetting("size", "Cỡ vệt chém", 1.0, 0.4, 2.5, 0.1, "x"));


    private final BoolSetting   sparks     = add(new BoolSetting("sparks", "Tàn lửa", true));
    private final SliderSetting sparkCount = add(new SliderSetting("spark_count", "Số tàn lửa", 22, 4, 40, 2, ""));

    private final BoolSetting   crit       = add(new BoolSetting("crit", "Hạt đánh trúng", true));
    private final SliderSetting critCount  = add(new SliderSetting("crit_count", "Số hạt", 18, 4, 60, 2, ""));
    private final SliderSetting critSpread = add(new SliderSetting("crit_spread", "Độ văng hạt", 1.0, 0.4, 2.5, 0.1, "x"));

    // Chỉ phục vụ vệt vung tay cũ (xem phần "Legacy" ở cuối file).
    private final SliderSetting reach      = add(new SliderSetting("reach", "Độ dài lưỡi kiếm", 2.6, 1.0, 4.0, 0.1, ""));
    private final BoolSetting   firstPerson = add(new BoolSetting("first_person", "Hiện ở góc nhìn thứ nhất", true));
    private final BoolSetting   emptyHand  = add(new BoolSetting("empty_hand", "Hiện khi tay không", false));

    // =====================================================================
    //  Hằng số
    // =====================================================================

    private static final String TRIGGER_ON_HIT = "Khi đánh trúng";

    // Vệt chém kiểu VCraft nhưng ngắn, mảnh và nhỏ hơn bản gốc.
    private static final double SLASH_START_LENGTH = 1.3;    // chiều dài dải lúc đầu (block)
    private static final double SLASH_END_LENGTH   = 2.3;    // chiều dài dải lúc cuối
    private static final double SLASH_START_THICK  = 0.03;   // bề dày dải lúc đầu
    private static final double SLASH_END_THICK    = 0.001;  // bề dày dải lúc cuối
    private static final double SLASH_TIP_RATIO    = 0.25;   // phần mũi nhọn ở mỗi đầu dải
    private static final double SLASH_DRIFT        = 0.2;    // quãng dải trôi dọc hướng chém
    private static final double SLASH_MAX_ANGLE    = 30.0;   // góc chém ngẫu nhiên trong [-30°, 30°]
    private static final double FRONT_OFFSET  = 0.38;   // đẩy hiệu ứng ra khỏi tâm hitbox về phía người chơi
    private static final double SPARK_DRAG    = 2.4;
    private static final double SPARK_GRAVITY = 1.6;
    private static final double CRIT_DRAG     = 1.1;
    private static final double CRIT_GRAVITY  = 0.9;

    // =====================================================================
    //  Dữ liệu hiệu ứng
    // =====================================================================

    /** Nhát chém tại mặt trước mục tiêu: tâm (c), góc chém so với trục ngang của màn hình (rad). */
    private static final class Slash {
        final double cx, cy, cz, angle, scale;
        final long t;

        Slash(double cx, double cy, double cz, double angle, double scale, long t) {
            this.cx = cx; this.cy = cy; this.cz = cz;
            this.angle = angle; this.scale = scale; this.t = t;
        }
    }

    /** Tàn lửa bắn ra khi đánh trúng. Vận tốc tính theo block/giây. */
    private static final class Spark {
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, size, hue;

        Spark(double x, double y, double z, double vx, double vy, double vz,
              long t, float life, float size, float hue) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.size = size; this.hue = hue;
        }
    }

    /** Vệt trắng nhỏ bắn tỏa quanh mục tiêu khi đánh trúng. Vận tốc tính theo block/giây. */
    private static final class Crit {
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, len, hue;

        Crit(double x, double y, double z, double vx, double vy, double vz,
             long t, float life, float len, float hue) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.len = len; this.hue = hue;
        }
    }

    /** Hệ trục hướng về màn hình (billboard): trục phải (rx, 0, rz) và trục lên (ux, uy, uz). */
    private static final class Billboard {
        final double rx, rz, ux, uy, uz;

        Billboard(double rx, double rz, double ux, double uy, double uz) {
            this.rx = rx; this.rz = rz; this.ux = ux; this.uy = uy; this.uz = uz;
        }
    }

    private final List<Slash> slashes   = new ArrayList<Slash>();
    private final List<Spark> sparkList = new ArrayList<Spark>();
    private final List<Crit>  critList  = new ArrayList<Crit>();
    private final Random rng = new Random();

    public SwordTrailModule() {
        super("swordtrail", "Vệt Kiếm",
                "Vệt kiếm điện ảnh ở phía trước mục tiêu khi đánh trúng, kèm chớp sáng, sóng xung kích và tàn lửa.",
                Category.EFFECT, true);
        new RainbowSettings(this);
    }

    @Override
    public void onToggle() {
        clearAll();
    }

    private void clearAll() {
        pts.clear();
        slashes.clear();
        sparkList.clear();
        critList.clear();
    }

    // =====================================================================
    //  Sự kiện đánh trúng
    // =====================================================================

    /** Gọi khi người chơi đánh trúng một sinh vật. */
    public void onAttack(Entity target) {
        if (!enabled || target == null) return;
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;

        // Hướng nhìn v: từ mắt người chơi tới thân mục tiêu.
        double cx = target.posX, cy = target.posY + target.height * 0.6, cz = target.posZ;
        double vx = cx - p.posX, vy = cy - (p.posY + p.getEyeHeight()), vz = cz - p.posZ;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4) return;
        vx /= vl; vy /= vl; vz /= vl;

        // Đẩy điểm hiệu ứng về phía người chơi để vệt nằm trên mặt trước mục tiêu.
        cx -= vx * FRONT_OFFSET;
        cy -= vy * FRONT_OFFSET;
        cz -= vz * FRONT_OFFSET;

        // r = hướng ngang-phải so với hướng nhìn, u = hướng "lên" của mặt phẳng nhìn.
        double rx = -vz, rz = vx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * vy, uy = rz * vx - rx * vz, uz = rx * vy;

        long now = System.currentTimeMillis();

        // Góc chém ngẫu nhiên kiểu VCraft, tính trong mặt phẳng (r, u) hướng về người chơi.
        double ang = Math.toRadians(randomSlashAngle());
        double ca = Math.cos(ang), sa = Math.sin(ang);
        double dx = rx * ca + ux * sa, dy = uy * sa, dz = rz * ca + uz * sa;

        if (sparks.value) {
            spawnSparks(cx, cy, cz, dx, dy, dz, -vx, -vy, -vz);
        }
        if (crit.value) {
            spawnCrits(target);
        }
        if (!trigger.is(TRIGGER_ON_HIT)) return;

        slashes.add(new Slash(cx, cy, cz, ang, size.get(), now));
    }

    /** Góc chém ngẫu nhiên đều trong [-30°, 30°] như VCraft. */
    private double randomSlashAngle() {
        return (rng.nextDouble() * 2.0 - 1.0) * SLASH_MAX_ANGLE;
    }

    /**
     * Bắn tàn lửa: một nửa chạy dọc đường chém (a), nửa còn lại là bụi sáng tỏa ra
     * và hơi dội về phía người đánh (b).
     */
    private void spawnSparks(double cx, double cy, double cz,
                             double ax, double ay, double az,
                             double bx, double by, double bz) {
        long now = System.currentTimeMillis();
        int n = sparkCount.getInt();
        for (int i = 0; i < n; i++) {
            double vx, vy, vz, speed;
            if (i % 2 == 0) {
                // Tia lửa dọc đường chém, hai chiều, nhanh.
                double sg = rng.nextBoolean() ? 1 : -1;
                vx = ax * sg + rng.nextGaussian() * 0.22;
                vy = ay * sg + rng.nextGaussian() * 0.22;
                vz = az * sg + rng.nextGaussian() * 0.22;
                speed = 1.8 + rng.nextDouble() * 2.8;
            } else {
                // Bụi sáng chậm tạo quầng halo quanh điểm va chạm.
                vx = rng.nextGaussian() * 0.72 + bx * 0.5;
                vy = rng.nextGaussian() * 0.62 + 0.22 + by * 0.5;
                vz = rng.nextGaussian() * 0.72 + bz * 0.5;
                speed = 0.4 + rng.nextDouble() * 1.1;
            }
            double k = speed / (Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6);
            sparkList.add(new Spark(cx, cy, cz, vx * k, vy * k, vz * k, now,
                    460f + rng.nextFloat() * 560f, 0.65f + rng.nextFloat() * 0.6f, i * 23f));
        }
    }

    /** Bắn các vệt trắng nhỏ từ thân mục tiêu, văng ra mọi hướng. */
    private void spawnCrits(Entity target) {
        long now = System.currentTimeMillis();
        int n = critCount.getInt();
        double spread = critSpread.get();
        double halfW = Math.max(0.2, target.width * 0.5);
        for (int i = 0; i < n; i++) {
            // Điểm xuất phát ngẫu nhiên bên trong hitbox.
            double x = target.posX + (rng.nextDouble() * 2.0 - 1.0) * halfW;
            double y = target.posY + target.height * (0.15 + rng.nextDouble() * 0.75);
            double z = target.posZ + (rng.nextDouble() * 2.0 - 1.0) * halfW;
            // Hướng văng ngẫu nhiên, hơi nghiêng lên trên.
            double vx = rng.nextGaussian(), vy = rng.nextGaussian() * 0.8 + 0.25, vz = rng.nextGaussian();
            double k = (1.2 + rng.nextDouble() * 2.0) * spread
                    / (Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6);
            critList.add(new Crit(x, y, z, vx * k, vy * k, vz * k, now,
                    330f + rng.nextFloat() * 330f, 0.10f + rng.nextFloat() * 0.12f, i * 31f));
        }
    }

    // =====================================================================
    //  Render
    // =====================================================================

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        Entity cam = mc.getRenderViewEntity();
        if (!enabled || p == null || cam == null || mc.theWorld == null) {
            clearAll();
            return;
        }

        long now = System.currentTimeMillis();
        pts.clear(); // vệt vung tay cũ không dùng ở chế độ hit-only

        float len = length.getFloat();
        removeExpired(now, len);
        if (slashes.isEmpty() && sparkList.isEmpty() && critList.isEmpty()) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);

        beginGlow();
        Billboard bb = cameraBillboard();
        for (Slash s : slashes) drawSlash(s, now, len, bb, cx, cy, cz);
        if (!sparkList.isEmpty() || !critList.isEmpty()) drawImpact(now, bb, cx, cy, cz);
        GL11.glLineWidth(1f);
        endGlow();
    }

    private void removeExpired(long now, float slashLife) {
        for (Iterator<Slash> it = slashes.iterator(); it.hasNext(); ) {
            if (now - it.next().t > slashLife) it.remove();
        }
        for (Iterator<Spark> it = sparkList.iterator(); it.hasNext(); ) {
            Spark k = it.next();
            if (now - k.t > k.life) it.remove();
        }
        for (Iterator<Crit> it = critList.iterator(); it.hasNext(); ) {
            Crit c = it.next();
            if (now - c.t > c.life) it.remove();
        }
    }

    /** Bật trạng thái GL cho hiệu ứng phát sáng (blend cộng màu, không ghi depth). */
    private static void beginGlow() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
    }

    private static void endGlow() {
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------------
    //  Nhát chém tại mục tiêu (kiểu VCraft)
    // ---------------------------------------------------------------------

    // Bốn mốc dọc theo dải: mũi trái, đầu thân trái, đầu thân phải, mũi phải.
    private static final double[] STATION_POS   = {-1.0, -1.0 + 2.0 * SLASH_TIP_RATIO, 1.0 - 2.0 * SLASH_TIP_RATIO, 1.0};
    private static final double[] STATION_THICK = {0.0, 1.0, 1.0, 0.0};
    private static final double[] STATION_ALPHA = {0.0, 1.0, 1.0, 0.0};

    /**
     * Dải sáng dẹt xoay mặt về camera: dài ra, mỏng dần, trôi nhẹ dọc hướng chém và mờ dần theo smoothstep.
     */
    private void drawSlash(Slash s, long now, float life, Billboard bb, double cx, double cy, double cz) {
        float age = now - s.t;
        if (age < 0f) return;

        float e = smoothStep(Math.max(0f, Math.min(1f, age / life)));
        float maxA = Math.min(1f, opacity.getFloat() / 100f * (1f - e));
        if (maxA <= 0f) return;

        double sc = s.scale;
        double halfLen = mix(SLASH_START_LENGTH, SLASH_END_LENGTH, e) * 0.5 * sc;
        double halfThick = mix(SLASH_START_THICK, SLASH_END_THICK, e) * 0.5 * sc;

        // Trục chém d và pháp tuyến n nằm trong mặt phẳng màn hình.
        double ca = Math.cos(s.angle), sa = Math.sin(s.angle);
        double[] d = {bb.rx * ca + bb.ux * sa, bb.uy * sa, bb.rz * ca + bb.uz * sa};
        double[] n = {-bb.rx * sa + bb.ux * ca, bb.uy * ca, -bb.rz * sa + bb.uz * ca};

        double drift = e * SLASH_DRIFT * sc;
        double px = s.cx + d[0] * drift - cx, py = s.cy + d[1] * drift - cy, pz = s.cz + d[2] * drift - cz;

        int c = rainbow.resolve(color.rgb(), 0f);
        int r = red(c), g = green(c), b = blue(c);
        int lr = heat(r, 0.83f), lg = heat(g, 0.83f), lb = heat(b, 0.83f); // lõi gần trắng

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        slashBand(wr, tess, px, py, pz, d, n, halfLen, halfThick, 0.0, r, g, b, maxA);
        slashBand(wr, tess, px, py, pz, d, n, halfLen * 0.98, halfThick * 0.35, 0.0, lr, lg, lb, maxA);
    }

    /**
     * Một dải theo bốn mốc: mũi nhọn - thân - thân - mũi nhọn. Đường tâm sáng nhất, mép trong suốt. {@code off} dịch cả dải theo pháp tuyến.
     */
    private static void slashBand(WorldRenderer wr, Tessellator tess,
                                  double px, double py, double pz, double[] d, double[] n,
                                  double halfLen, double halfThick, double off,
                                  int r, int g, int b, float a) {
        for (int side = -1; side <= 1; side += 2) {
            wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < STATION_POS.length; i++) {
                double u = STATION_POS[i] * halfLen;
                double mx = px + d[0] * u + n[0] * off, my = py + d[1] * u + n[1] * off, mz = pz + d[2] * u + n[2] * off;
                double w = halfThick * STATION_THICK[i] * side;
                wr.pos(mx, my, mz).color(r, g, b, alpha(a * (float) STATION_ALPHA[i])).endVertex();
                wr.pos(mx + n[0] * w, my + n[1] * w, mz + n[2] * w).color(r, g, b, 0).endVertex();
            }
            tess.draw();
        }
    }

    // ---------------------------------------------------------------------
    //  Chớp sáng, sóng xung kích, tàn lửa
    // ---------------------------------------------------------------------

    /** Trục phải / trục lên của màn hình, để hiệu ứng luôn quay mặt về phía camera. */
    private static Billboard cameraBillboard() {
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        double yaw = Math.toRadians(rm.playerViewY), pitch = Math.toRadians(rm.playerViewX);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        return new Billboard(rx, rz, ly * rz, lz * rx - lx * rz, -ly * rx);
    }

    private void drawImpact(long now, Billboard bb, double cx, double cy, double cz) {
        float maxA = opacity.getFloat() / 100f;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        for (Spark k : sparkList) drawSpark(k, now, maxA, bb, wr, tess, cx, cy, cz);
        for (Crit c : critList) drawCrit(c, now, maxA, wr, tess, cx, cy, cz);
    }

    /** Tàn lửa: vệt dài theo vận tốc (đuôi mờ) + hạt phát sáng, từ trắng nóng chuyển sang màu hiệu ứng rồi tắt. */
    private void drawSpark(Spark k, long now, float maxA, Billboard bb, WorldRenderer wr, Tessellator tess,
                           double cx, double cy, double cz) {
        double age = (now - k.t) / 1000.0;
        if (age < 0) return;

        float lifeK = (now - k.t) / k.life;
        float a = (1f - lifeK) * (1f - lifeK) * Math.min(1f, maxA * 1.15f);

        // Vị trí và vận tốc hiện tại, có lực cản và trọng lực.
        double dr = Math.exp(-SPARK_DRAG * age), disp = (1.0 - dr) / SPARK_DRAG;
        double hx = k.x + k.vx * disp - cx;
        double hy = k.y + k.vy * disp - 0.5 * SPARK_GRAVITY * age * age - cy;
        double hz = k.z + k.vz * disp - cz;
        double cvx = k.vx * dr, cvy = k.vy * dr - SPARK_GRAVITY * age, cvz = k.vz * dr;

        // Điểm đuôi của vệt.
        double tl = 0.055 + 0.03 * k.size;
        double tx = hx - cvx * tl, ty = hy - cvy * tl, tz = hz - cvz * tl;

        int c = rainbow.resolve(color.rgb(), k.hue);
        int r = red(c), g = green(c), b = blue(c);
        float hot = Math.max(0f, 1f - lifeK * 2.2f); // ban đầu trắng nóng
        int hr = heat(r, hot), hg = heat(g, hot), hb = heat(b, hot);

        GL11.glLineWidth(2.4f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(tx, ty, tz).color(r, g, b, 0).endVertex();
        wr.pos(hx, hy, hz).color(hr, hg, hb, alpha(a * 0.95f)).endVertex();
        tess.draw();

        double gs = 0.075 * k.size * (1.0 - 0.3 * lifeK);
        billboardFan(wr, tess, bb, hx, hy, hz, gs * 1.2, gs * 1.2, r, g, b, a * 0.8f, hr, hg, hb); // chấm sáng nhỏ ở đầu tia
    }

    /** Vệt trắng nhỏ: một gạch ngắn nằm dọc hướng bay, đầu sáng, đuôi mờ, tắt dần theo tuổi. */
    private void drawCrit(Crit c, long now, float maxA, WorldRenderer wr, Tessellator tess,
                          double cx, double cy, double cz) {
        double age = (now - c.t) / 1000.0;
        if (age < 0) return;

        float lifeK = (now - c.t) / c.life;
        float a = Math.min(1f, maxA * 1.2f) * (1f - lifeK * lifeK);
        if (a <= 0f) return;

        // Vị trí và vận tốc hiện tại, có lực cản và trọng lực nhẹ.
        double dr = Math.exp(-CRIT_DRAG * age), disp = (1.0 - dr) / CRIT_DRAG;
        double hx = c.x + c.vx * disp - cx;
        double hy = c.y + c.vy * disp - 0.5 * CRIT_GRAVITY * age * age - cy;
        double hz = c.z + c.vz * disp - cz;
        double cvx = c.vx * dr, cvy = c.vy * dr - CRIT_GRAVITY * age, cvz = c.vz * dr;
        double sp = Math.sqrt(cvx * cvx + cvy * cvy + cvz * cvz);
        if (sp < 1.0E-4) return;

        double len = c.len * (1.0 - 0.4 * lifeK);
        double tx = hx - cvx / sp * len, ty = hy - cvy / sp * len, tz = hz - cvz / sp * len;

        // Gần như trắng, chỉ ngả nhẹ theo màu đã chọn.
        int col = rainbow.resolve(color.rgb(), c.hue);
        int r = heat(red(col), 0.82f), g = heat(green(col), 0.82f), b = heat(blue(col), 0.82f);

        GL11.glLineWidth(2.2f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(tx, ty, tz).color(r, g, b, alpha(a * 0.35f)).endVertex();
        wr.pos(hx, hy, hz).color(r, g, b, alpha(a)).endVertex();
        tess.draw();
    }

    // =====================================================================
    //  Hình học phát sáng
    // =====================================================================

    /** {@link #fan} nằm trong mặt phẳng màn hình. */
    private static void billboardFan(WorldRenderer wr, Tessellator tess, Billboard bb,
                                     double px, double py, double pz, double la, double lb,
                                     int r, int g, int b, float a, int cr, int cg, int cb) {
        fan(wr, tess, px, py, pz, bb.rx, 0, bb.rz, bb.ux, bb.uy, bb.uz, la, lb, r, g, b, a, cr, cg, cb);
    }

    /** Tam giác quạt phát sáng: tâm (màu cr/cg/cb, alpha a) mờ dần ra rìa, nằm trong mặt phẳng (a-axis, b-axis). */
    private static void fan(WorldRenderer wr, Tessellator tess, double px, double py, double pz,
                            double ax, double ay, double az, double bx, double by, double bz,
                            double la, double lb, int r, int g, int b, float a, int cr, int cg, int cb) {
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px, py, pz).color(cr, cg, cb, alpha(a)).endVertex();
        for (int i = 0; i <= 16; i++) {
            double ang = i / 16.0 * Math.PI * 2.0, ca = Math.cos(ang) * la, sa = Math.sin(ang) * lb;
            wr.pos(px + ax * ca + bx * sa, py + ay * ca + by * sa, pz + az * ca + bz * sa)
                    .color(r, g, b, 0).endVertex();
        }
        tess.draw();
    }

    // =====================================================================
    //  Tiện ích
    // =====================================================================

    private static float smoothStep(float x) {
        return x * x * (3f - 2f * x);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int red(int c)   { return (c >> 16) & 255; }
    private static int green(int c) { return (c >> 8) & 255; }
    private static int blue(int c)  { return c & 255; }

    /** Đổi alpha 0..1 sang 0..255, có kẹp giá trị. */
    private static int alpha(float a) {
        return Math.round(255f * Math.max(0f, Math.min(1f, a)));
    }

    /** Pha kênh màu về phía trắng theo hệ số t (0 = giữ nguyên, 1 = trắng). */
    private static int heat(int channel, float t) {
        return channel + Math.round((255 - channel) * t);
    }

    // =====================================================================
    //  Legacy: vệt vung tay (không dùng ở chế độ hit-only)
    // =====================================================================

    /** Một điểm của vệt vung tay. */
    private static final class Pt {
        final double bx, by, bz, tx, ty, tz;
        final long t;

        Pt(double bx, double by, double bz, double tx, double ty, double tz, long t) {
            this.bx = bx; this.by = by; this.bz = bz;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.t = t;
        }
    }

    private final List<Pt> pts = new ArrayList<Pt>();

    @SuppressWarnings("unused")
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
        pts.add(new Pt(ex + dx * 0.8, ey + dy * 0.8, ez + dz * 0.8,
                ex + dx * r, ey + dy * r, ez + dz * r, now));
    }

    @SuppressWarnings("unused")
    private void drawSwing(long now, float len, double cx, double cy, double cz) {
        float maxA = opacity.getFloat() / 100f;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < pts.size(); i++) {
            Pt q = pts.get(i);
            float fade = Math.max(0f, Math.min(1f, 1f - (now - q.t) / len));
            int c = rainbow.resolve(color.rgb(), i * 12f);
            int r = red(c), g = green(c), b = blue(c);
            wr.pos(q.bx - cx, q.by - cy, q.bz - cz).color(r, g, b, alpha(maxA * fade * 0.25f)).endVertex();
            wr.pos(q.tx - cx, q.ty - cy, q.tz - cz).color(r, g, b, alpha(maxA * fade)).endVertex();
        }
        tess.draw();
    }
}
