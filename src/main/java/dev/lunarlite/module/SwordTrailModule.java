package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityCrit2FX;
import net.minecraft.client.particle.EntityCritFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/**
 * <h2>Hạt Sáng - hạt sáng văng ra khi đánh trúng</h2>
 *
 * <h3>Tính năng</h3>
 * <ol>
 *   <li><b>Tia lửa sao chổi</b>: đầu sáng trắng nóng, đuôi thon mờ dần, độ dài đuôi tăng theo tốc độ
 *       nên tia nhanh thì dài, chậm lại thì ngắn lại. Bắn tỏa thành chùm hình sao quay mặt về người chơi,
 *       thêm một chút dội ngược về phía người đánh. Nhấp nháy nhẹ như tàn lửa thật.</li>
 *   <li><b>Tàn lửa trôi</b>: những hạt sáng tròn nhỏ bay chậm, ít chịu trọng lực, lơ lửng lâu hơn
 *       và tỏa quầng mềm quanh điểm va chạm.</li>
 *   <li><b>Vạch trắng nhỏ</b>: nhiều gạch trắng mảnh văng ra từ thân mục tiêu theo mọi hướng, xuất hiện
 *       lệch nhau vài chục mili giây nên trông tự nhiên, rồi rơi nhẹ và mờ dần.</li>
 *   <li><b>Màu chuyển theo tuổi</b>: từ trắng nóng sang màu đã chọn rồi sẫm dần và tắt; hạt co nhỏ dần.
 *       Hỗ trợ màu cầu vồng ({@link RainbowSettings}).</li>
 *   <li><b>Vật lý</b>: có lực cản không khí và trọng lực riêng cho từng loại hạt.</li>
 *   <li><b>Nhẹ</b>: toàn bộ hạt được vẽ gộp thành hai lượt cho mỗi khung hình, giới hạn tối đa
 *       {@value #MAX_PARTICLES} hạt cùng lúc, không tạo đối tượng mới trong lúc vẽ.</li>
 *   <li><b>Gọn</b>: không có vệt chém, chớp sáng, quầng nền hay vòng sóng đi kèm.</li>
 * </ol>
 *
 * <h3>Tùy chỉnh</h3>
 * <ul>
 *   <li>Màu sắc, Độ đục (%), Cỡ hạt (x)</li>
 *   <li>Tia lửa, Số tia lửa</li>
 *   <li>Vạch trắng, Số vạch, Độ văng</li>
 *   <li>Ẩn hạt chí mạng mặc định của game</li>
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

    private final ColorSetting  color        = add(new ColorSetting("color", "Màu sắc", 0x9EEFEB));
    private final SliderSetting opacity      = add(new SliderSetting("opacity", "Độ đục", 100, 10, 100, 5, "%"));
    private final SliderSetting particleSize = add(new SliderSetting("particle_size", "Cỡ hạt", 1.3, 0.5, 2.5, 0.1, "x"));

    private final BoolSetting   sparks       = add(new BoolSetting("sparks", "Tia lửa", true));
    private final SliderSetting sparkCount   = add(new SliderSetting("spark_count", "Số tia lửa", 32, 4, 48, 2, ""));

    private final BoolSetting   crit         = add(new BoolSetting("crit", "Vạch trắng", true));
    private final SliderSetting critCount    = add(new SliderSetting("crit_count", "Số vạch", 24, 4, 60, 2, ""));
    private final BoolSetting   hideVanillaCrit = add(new BoolSetting("hide_vanilla_crit", "Ẩn hạt chí mạng mặc định", true));
    private final SliderSetting critSpread   = add(new SliderSetting("crit_spread", "Độ văng", 1.2, 0.4, 2.5, 0.1, "x"));

    // =====================================================================
    //  Hằng số
    // =====================================================================

    static final int MAX_PARTICLES = 600;
    private static final double FRONT_OFFSET = 0.38; // đẩy điểm nổ ra khỏi tâm hitbox về phía người chơi

    // Loại hạt.
    private static final int SPARK = 0; // tia lửa sao chổi
    private static final int EMBER = 1; // tàn lửa tròn trôi chậm
    private static final int DASH  = 2; // vạch trắng nhỏ

    // Lực cản và trọng lực theo loại hạt.
    private static final double[] DRAG    = {2.1, 2.4, 1.0};
    private static final double[] GRAVITY = {1.9, 0.15, 1.1};

    // Bảng lượng giác cho chấm sáng tròn 8 cạnh.
    private static final int DOT_SEGMENTS = 8;
    private static final double[] DOT_COS = new double[DOT_SEGMENTS + 1];
    private static final double[] DOT_SIN = new double[DOT_SEGMENTS + 1];
    static {
        for (int i = 0; i <= DOT_SEGMENTS; i++) {
            double a = i / (double) DOT_SEGMENTS * Math.PI * 2.0;
            DOT_COS[i] = Math.cos(a);
            DOT_SIN[i] = Math.sin(a);
        }
    }

    // =====================================================================
    //  Dữ liệu hiệu ứng
    // =====================================================================

    /** Một hạt sáng. Vận tốc tính theo block/giây. */
    private static final class Particle {
        final int kind;
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, len, hue, phase;

        Particle(int kind, double x, double y, double z, double vx, double vy, double vz,
                 long t, float life, float len, float hue, float phase) {
            this.kind = kind;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.len = len; this.hue = hue; this.phase = phase;
        }
    }

    /** Hệ trục màn hình: phải (rx, 0, rz), lên (ux, uy, uz), hướng nhìn (fx, fy, fz). */
    private static final class Billboard {
        final double rx, rz, ux, uy, uz, fx, fy, fz;

        Billboard(double rx, double rz, double ux, double uy, double uz) {
            this.rx = rx; this.rz = rz; this.ux = ux; this.uy = uy; this.uz = uz;
            // hướng nhìn = phải x lên
            this.fx = -rz * uy;
            this.fy = rz * ux - rx * uz;
            this.fz = rx * uy;
        }
    }

    /** Trạng thái tính được của một hạt ở thời điểm hiện tại (dùng lại, không cấp phát mới). */
    private static final class Pose {
        double hx, hy, hz;      // vị trí đầu hạt so với camera
        double dx, dy, dz;      // hướng bay (vector đơn vị)
        double speed;           // block/giây
        float a, lifeK;         // độ sáng 0..1, tuổi 0..1
    }

    private final List<Particle> particles = new ArrayList<Particle>();
    private final Pose pose = new Pose();
    private final Random rng = new Random();

    public SwordTrailModule() {
        super("swordtrail", "Hạt Sáng",
                "Hạt sáng văng ra khi đánh trúng: tia lửa sao chổi, tàn lửa trôi và vạch trắng nhỏ.",
                Category.EFFECT, true);
        new RainbowSettings(this);
    }

    @Override
    public void onToggle() {
        particles.clear();
    }

    // =====================================================================
    //  Ẩn hạt chí mạng mặc định của game
    // =====================================================================

    private static Field fxLayersField;
    private static boolean fxLookupFailed;

    /**
     * Chạy ở cuối mỗi tick: hạt chí mạng (đánh chí mạng, đòn sắc bén, mũi tên chí mạng...) vừa được game
     * sinh ra đã nằm trong danh sách của EffectRenderer nhưng chưa được vẽ, nên xóa lúc này thì
     * người chơi không thấy chúng dù chỉ một khung hình.
     */
    @Override
    public void onTick() {
        if (!enabled || !hideVanillaCrit.value) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.effectRenderer == null) return;
        Object layers = fxLayers(mc.effectRenderer);
        if (layers != null) purgeCrit(layers);
    }

    /** Duyệt đệ quy (fxLayers là mảng hai chiều các List) và xóa mọi hạt chí mạng. */
    private static void purgeCrit(Object node) {
        if (node instanceof List) {
            for (Iterator<?> it = ((List<?>) node).iterator(); it.hasNext(); ) {
                Object o = it.next();
                if (o instanceof EntityCritFX || o instanceof EntityCrit2FX) it.remove();
            }
        } else if (node instanceof Object[]) {
            for (Object child : (Object[]) node) {
                if (child != null) purgeCrit(child);
            }
        }
    }

    /** Lấy danh sách hạt của EffectRenderer qua reflection (trường này là private). */
    private static Object fxLayers(EffectRenderer renderer) {
        if (fxLookupFailed) return null;
        try {
            if (fxLayersField == null) {
                fxLayersField = ReflectionHelper.findField(EffectRenderer.class, "fxLayers", "field_78876_b");
            }
            return fxLayersField.get(renderer);
        } catch (Exception ex) {
            fxLookupFailed = true; // không tìm được thì thôi, không làm game lỗi
            return null;
        }
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

        // Đẩy điểm nổ về phía người chơi để hạt bắn ra từ mặt trước mục tiêu.
        cx -= vx * FRONT_OFFSET;
        cy -= vy * FRONT_OFFSET;
        cz -= vz * FRONT_OFFSET;

        // r = hướng ngang-phải so với hướng nhìn, u = hướng "lên" của mặt phẳng nhìn.
        double rx = -vz, rz = vx, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * vy, uy = rz * vx - rx * vz, uz = rx * vy;

        if (sparks.value) spawnSparks(cx, cy, cz, rx, rz, ux, uy, uz, -vx, -vy, -vz);
        if (crit.value) spawnDashes(target);
    }

    /**
     * Chùm tia lửa hình sao trong mặt phẳng (r, u) hướng về người chơi (66%) và tàn lửa trôi chậm (34%).
     * (bx, by, bz) là hướng dội về phía người đánh.
     */
    private void spawnSparks(double cx, double cy, double cz,
                             double rx, double rz, double ux, double uy, double uz,
                             double bx, double by, double bz) {
        long now = System.currentTimeMillis();
        int n = sparkCount.getInt();
        for (int i = 0; i < n && particles.size() < MAX_PARTICLES; i++) {
            double vx, vy, vz, speed;
            int kind;
            float life, scale;
            // Mỗi hạt bật ra từ một điểm lệch nhẹ quanh điểm va chạm, không phải đúng một điểm.
            double ox = cx + rng.nextGaussian() * 0.05;
            double oy = cy + rng.nextGaussian() * 0.05;
            double oz = cz + rng.nextGaussian() * 0.05;
            if (i % 4 != 3) {
                // Tia lửa: thiên về mặt phẳng nhìn nhưng nhiễu 3D mạnh + dội ngược,
                // nên văng lộn xộn tự nhiên chứ không đều như hình sao.
                kind = SPARK;
                double th = rng.nextDouble() * Math.PI * 2.0, ct = Math.cos(th), st = Math.sin(th);
                double flat = 0.55 + rng.nextDouble() * 0.45;
                vx = (rx * ct + ux * st) * flat + rng.nextGaussian() * 0.38 + bx * 0.45;
                vy = (uy * st) * flat + rng.nextGaussian() * 0.38 + by * 0.45 + 0.1;
                vz = (rz * ct + uz * st) * flat + rng.nextGaussian() * 0.38 + bz * 0.45;
                double r = rng.nextDouble();
                speed = 2.0 + r * r * 4.6;             // đa số vừa, thỉnh thoảng có tia rất nhanh
                life = 450f + rng.nextFloat() * 550f;
                scale = 0.7f + rng.nextFloat() * 0.8f; // tia to nhỏ khác nhau
            } else {
                // Tàn lửa trôi: tỏa 3D, chậm, bay lên nhẹ rồi lơ lửng.
                kind = EMBER;
                vx = rng.nextGaussian() * 0.8 + bx * 0.5;
                vy = rng.nextGaussian() * 0.6 + 0.5 + by * 0.4;
                vz = rng.nextGaussian() * 0.8 + bz * 0.5;
                speed = 0.4 + rng.nextDouble() * 1.1;
                life = 800f + rng.nextFloat() * 700f;
                scale = 0.7f + rng.nextFloat() * 0.9f;
            }
            double k = speed / (Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6);
            long delay = (long) (rng.nextDouble() * 30.0); // bật ra lệch nhau vài ms cho tự nhiên
            // Với tia lửa và tàn lửa, trường len dùng làm hệ số kích cỡ riêng của hạt.
            particles.add(new Particle(kind, ox, oy, oz, vx * k, vy * k, vz * k, now + delay,
                    life, scale, i * 23f, rng.nextFloat() * 6.2832f));
        }
    }

    /** Vạch trắng nhỏ văng từ thân mục tiêu theo mọi hướng, xuất hiện lệch nhau vài chục ms. */
    private void spawnDashes(Entity target) {
        long now = System.currentTimeMillis();
        int n = critCount.getInt();
        double spread = critSpread.get();
        double halfW = Math.max(0.2, target.width * 0.5);
        double midY = target.posY + target.height * 0.55;
        for (int i = 0; i < n && particles.size() < MAX_PARTICLES; i++) {
            double x = target.posX + (rng.nextDouble() * 2.0 - 1.0) * halfW;
            double y = target.posY + target.height * (0.15 + rng.nextDouble() * 0.75);
            double z = target.posZ + (rng.nextDouble() * 2.0 - 1.0) * halfW;
            // Văng theo hướng từ tâm thân ra ngoài + nhiễu, nên trông như bị đánh bật ra.
            double vx = (x - target.posX) / halfW * 0.9 + rng.nextGaussian() * 0.6;
            double vy = (y - midY) / Math.max(0.3, target.height * 0.5) * 0.5 + rng.nextGaussian() * 0.6 + 0.3;
            double vz = (z - target.posZ) / halfW * 0.9 + rng.nextGaussian() * 0.6;
            double k = (1.6 + rng.nextDouble() * 2.2) * spread
                    / (Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6);
            long delay = (long) (rng.nextDouble() * 60.0);
            particles.add(new Particle(DASH, x, y, z, vx * k, vy * k, vz * k, now + delay,
                    400f + rng.nextFloat() * 400f, 0.14f + rng.nextFloat() * 0.16f, i * 31f, 0f));
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
            particles.clear();
            return;
        }

        long now = System.currentTimeMillis();
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle q = it.next();
            if (now - q.t > q.life) it.remove();
        }
        if (particles.isEmpty()) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);
        Billboard bb = cameraBillboard();
        float maxA = Math.min(1f, opacity.getFloat() / 100f * 1.15f);
        float sz = particleSize.getFloat();

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        beginGlow();

        // Lượt 1: thân sao chổi / vạch của mọi hạt.
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0, n = particles.size(); i < n; i++) {
            Particle q = particles.get(i);
            if (evaluate(q, now, maxA, cx, cy, cz)) drawComet(wr, q, bb, sz);
        }
        tess.draw();

        // Lượt 2: chấm sáng tròn ở đầu tia lửa và tàn lửa.
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0, n = particles.size(); i < n; i++) {
            Particle q = particles.get(i);
            if (q.kind != DASH && evaluate(q, now, maxA, cx, cy, cz)) drawDot(wr, q, bb, sz);
        }
        tess.draw();

        endGlow();
    }

    /** Tính vị trí, hướng bay và độ sáng của hạt vào {@link #pose}. Trả về false nếu hạt chưa/không cần vẽ. */
    private boolean evaluate(Particle q, long now, float maxA, double cx, double cy, double cz) {
        double age = (now - q.t) / 1000.0;
        if (age < 0) return false; // hạt đang chờ xuất hiện
        float lifeK = (now - q.t) / q.life;
        if (lifeK >= 1f) return false;

        double drag = DRAG[q.kind], grav = GRAVITY[q.kind];
        double dr = Math.exp(-drag * age), disp = (1.0 - dr) / drag;
        Pose o = pose;
        o.hx = q.x + q.vx * disp - cx;
        o.hy = q.y + q.vy * disp - 0.5 * grav * age * age - cy;
        o.hz = q.z + q.vz * disp - cz;

        double cvx = q.vx * dr, cvy = q.vy * dr - grav * age, cvz = q.vz * dr;
        double sp = Math.sqrt(cvx * cvx + cvy * cvy + cvz * cvz);
        if (sp > 1.0E-4) {
            o.dx = cvx / sp; o.dy = cvy / sp; o.dz = cvz / sp;
        } else {
            o.dx = 0; o.dy = 1; o.dz = 0;
        }
        o.speed = sp;

        float inv = 1f - lifeK;
        float fade = inv * (0.55f + 0.45f * inv); // giữ sáng lâu hơn, chỉ tắt nhanh ở cuối đời
        float twinkle = q.kind == DASH ? 1f : 0.86f + 0.14f * (float) Math.sin(age * 38.0 + q.phase);
        o.a = maxA * fade * twinkle;
        o.lifeK = lifeK;
        return o.a > 0.003f;
    }

    /** Sao chổi: đầu sáng ở giữa, mép và đuôi trong suốt (4 tam giác quanh tâm đầu). */
    private void drawComet(WorldRenderer wr, Particle q, Billboard bb, float sz) {
        Pose o = pose;
        float k = o.lifeK;
        int c = rainbow.resolve(color.rgb(), q.hue);
        int r = red(c), g = green(c), b = blue(c);

        double len, wid;
        int hr, hg, hb, er, eg, eb;
        if (q.kind == DASH) {
            len = q.len * sz * 1.2 * (1.0 - 0.4 * k);
            wid = 0.010 * sz;
            hr = er = heat(r, 0.9f); hg = eg = heat(g, 0.9f); hb = eb = heat(b, 0.9f);
        } else {
            boolean spark = q.kind == SPARK;
            float sc = q.len > 0f ? q.len : 1f;
            len = spark ? (0.08 + o.speed * 0.075) * sz * sc : 0.07 * sz * sc;
            wid = (spark ? 0.026 : 0.03) * sz * sc * (1.0 - (spark ? 0.5 : 0.4) * k);
            float hot = spark ? Math.max(0f, 1f - k * 1.7f) : 0.45f * Math.max(0f, 1f - k * 1.6f);
            float dark = 1f - 0.2f * k; // sẫm nhẹ về cuối đời
            hr = Math.round(heat(r, hot) * dark); hg = Math.round(heat(g, hot) * dark); hb = Math.round(heat(b, hot) * dark);
            er = Math.round(r * dark); eg = Math.round(g * dark); eb = Math.round(b * dark);
        }
        int ca = alpha(o.a);

        // Hướng ngang của dải, vuông góc với hướng bay và hướng nhìn.
        double sx = o.dy * bb.fz - o.dz * bb.fy;
        double sy = o.dz * bb.fx - o.dx * bb.fz;
        double sz2 = o.dx * bb.fy - o.dy * bb.fx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz2 * sz2);
        if (sl < 1.0E-4) { sx = bb.rx; sy = 0; sz2 = bb.rz; sl = 1; }
        sx = sx / sl * wid; sy = sy / sl * wid; sz2 = sz2 / sl * wid;

        double hx = o.hx, hy = o.hy, hz = o.hz;
        double tx = hx - o.dx * len, ty = hy - o.dy * len, tz = hz - o.dz * len;       // đuôi
        double fx = hx + o.dx * wid, fy = hy + o.dy * wid, fz = hz + o.dz * wid;       // mũi trước

        // C = tâm đầu; L, R = hai bên; T = đuôi; F = mũi trước.
        tri(wr, hx, hy, hz, hr, hg, hb, ca, hx - sx, hy - sy, hz - sz2, tx, ty, tz, er, eg, eb);
        tri(wr, hx, hy, hz, hr, hg, hb, ca, tx, ty, tz, hx + sx, hy + sy, hz + sz2, er, eg, eb);
        tri(wr, hx, hy, hz, hr, hg, hb, ca, hx + sx, hy + sy, hz + sz2, fx, fy, fz, er, eg, eb);
        tri(wr, hx, hy, hz, hr, hg, hb, ca, fx, fy, fz, hx - sx, hy - sy, hz - sz2, er, eg, eb);
    }

    /** Chấm sáng: quầng mềm màu hiệu ứng ở ngoài + lõi trắng nóng ở trong, giúp hạt nổi bật hơn hẳn. */
    private void drawDot(WorldRenderer wr, Particle q, Billboard bb, float sz) {
        Pose o = pose;
        float k = o.lifeK;
        boolean spark = q.kind == SPARK;
        float sc = q.len > 0f ? q.len : 1f;
        double coreR = (spark ? 0.032 : 0.065) * sz * sc * (1.0 - (spark ? 0.35 : 0.45) * k);

        int c = rainbow.resolve(color.rgb(), q.hue);
        int r = red(c), g = green(c), b = blue(c);
        float hot = spark ? Math.max(0f, 1f - k * 1.7f) : 0.45f * Math.max(0f, 1f - k * 1.6f);
        float dark = 1f - 0.2f * k;
        int hr = Math.round(heat(r, hot) * dark), hg = Math.round(heat(g, hot) * dark), hb = Math.round(heat(b, hot) * dark);
        int er = Math.round(r * dark), eg = Math.round(g * dark), eb = Math.round(b * dark);

        // Quầng ngoài (mờ, rộng) rồi lõi (sáng, nhỏ).
        disc(wr, o, bb, coreR * 3.4, er, eg, eb, alpha(o.a * 0.30f), er, eg, eb);
        disc(wr, o, bb, coreR, hr, hg, hb, alpha(o.a * (spark ? 1.0f : 0.85f)), er, eg, eb);
    }

    /** Đĩa tròn quay mặt về camera: tâm (cr, cg, cb, ca), rìa (er, eg, eb) trong suốt. */
    private static void disc(WorldRenderer wr, Pose o, Billboard bb, double rad,
                             int cr, int cg, int cb, int ca, int er, int eg, int eb) {
        for (int i = 0; i < DOT_SEGMENTS; i++) {
            double a0 = DOT_COS[i] * rad, b0 = DOT_SIN[i] * rad, a1 = DOT_COS[i + 1] * rad, b1 = DOT_SIN[i + 1] * rad;
            wr.pos(o.hx, o.hy, o.hz).color(cr, cg, cb, ca).endVertex();
            wr.pos(o.hx + bb.rx * a0 + bb.ux * b0, o.hy + bb.uy * b0, o.hz + bb.rz * a0 + bb.uz * b0)
                    .color(er, eg, eb, 0).endVertex();
            wr.pos(o.hx + bb.rx * a1 + bb.ux * b1, o.hy + bb.uy * b1, o.hz + bb.rz * a1 + bb.uz * b1)
                    .color(er, eg, eb, 0).endVertex();
        }
    }

    /** Một tam giác: đỉnh tâm (cr, cg, cb, ca) và hai đỉnh mép trong suốt (er, eg, eb). */
    private static void tri(WorldRenderer wr,
                            double cx, double cy, double cz, int cr, int cg, int cb, int ca,
                            double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            int er, int eg, int eb) {
        wr.pos(cx, cy, cz).color(cr, cg, cb, ca).endVertex();
        wr.pos(x1, y1, z1).color(er, eg, eb, 0).endVertex();
        wr.pos(x2, y2, z2).color(er, eg, eb, 0).endVertex();
    }

    // =====================================================================
    //  Trạng thái GL và tiện ích
    // =====================================================================

    /** Trục phải / trục lên của màn hình, để hiệu ứng luôn quay mặt về phía camera. */
    private static Billboard cameraBillboard() {
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        double yaw = Math.toRadians(rm.playerViewY), pitch = Math.toRadians(rm.playerViewX);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        return new Billboard(rx, rz, ly * rz, lz * rx - lx * rz, -ly * rx);
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
}
