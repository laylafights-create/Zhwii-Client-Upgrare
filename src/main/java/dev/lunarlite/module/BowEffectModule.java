package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityBubbleFX;
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
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/**
 * <h2>Hiệu Ứng Cung - vệt mũi tên và tia lửa khi trúng</h2>
 *
 * <h3>Tính năng</h3>
 * <ol>
 *   <li><b>Vệt mũi tên</b>: mũi tên bay để lại một vệt mảnh, sáng ở đầu và mờ dần về phía đuôi,
 *       kèm một lớp quầng mỏng bên ngoài. Hỗ trợ màu cầu vồng ({@link RainbowSettings}).</li>
 *   <li><b>Đầu sáng</b>: một chấm sáng nhỏ ở mũi tên đang bay.</li>
 *   <li><b>Tia lửa khi trúng</b>: khi mũi tên cắm vào block hoặc trúng sinh vật, bụi lửa búng ra
 *       theo hướng ngược chiều bay, có lực cản và trọng lực, chuyển từ trắng nóng sang màu hiệu ứng rồi tắt.
 *       Không có chớp sáng hay vòng sóng đi kèm.</li>
 *   <li><b>Ẩn hạt mặc định của cung</b>: khi module bật, các hạt có sẵn của Minecraft quanh mũi tên
 *       (hạt chí mạng và bong bóng dưới nước) bị xóa ngay trong tick đó, trước khi kịp được vẽ,
 *       để chỉ còn hiệu ứng của module.</li>
 *   <li><b>Chỉ mũi tên của tôi</b>: tùy chọn chỉ hiện hiệu ứng cho mũi tên do bạn bắn.</li>
 *   <li><b>Tự nhận biết mũi tên</b>: không cần sự kiện riêng, module tự quét mũi tên trong thế giới
 *       mỗi khung hình. Hiệu ứng chỉ mang tính hiển thị.</li>
 * </ol>
 *
 * <h3>Tùy chỉnh</h3>
 * <ul>
 *   <li>Màu sắc, Độ đục (%)</li>
 *   <li>Vệt mũi tên, Độ dài vệt (ms), Đầu sáng</li>
 *   <li>Tia lửa khi trúng, Số tia lửa</li>
 *   <li>Ẩn hạt mặc định của cung, Chỉ mũi tên của tôi</li>
 * </ul>
 *
 * <h3>Cách dùng</h3>
 * Đăng ký module giống {@code SwordTrailModule} và gọi {@link #render(float)} từ RenderWorldLastEvent.
 */
public class BowEffectModule extends Module {

    // =====================================================================
    //  Settings
    // =====================================================================

    private final ColorSetting  color      = add(new ColorSetting("color", "Màu sắc", 0x9EEFEB));
    private final SliderSetting opacity    = add(new SliderSetting("opacity", "Độ đục", 90, 10, 100, 5, "%"));

    private final BoolSetting   trail      = add(new BoolSetting("trail", "Vệt mũi tên", true));
    private final SliderSetting trailLen   = add(new SliderSetting("trail_length", "Độ dài vệt", 400, 100, 1500, 50, " ms"));
    private final BoolSetting   head       = add(new BoolSetting("head", "Đầu sáng", true));

    private final BoolSetting   impact     = add(new BoolSetting("impact", "Tia lửa khi trúng", true));
    private final SliderSetting impactCount = add(new SliderSetting("impact_count", "Số tia lửa", 16, 4, 48, 2, ""));

    private final BoolSetting   hideVanilla = add(new BoolSetting("hide_vanilla", "Ẩn hạt mặc định của cung", true));
    private final BoolSetting   mineOnly   = add(new BoolSetting("mine_only", "Chỉ mũi tên của tôi", true));

    // =====================================================================
    //  Hằng số
    // =====================================================================

    private static final double STOP_SPEED2   = 4.0E-4;  // dưới ngưỡng này coi như mũi tên đã dừng
    private static final double POINT_MIN_DIST2 = 0.0016; // khoảng cách tối thiểu giữa hai điểm vệt
    private static final long   POINT_MIN_MS  = 12L;
    private static final double DUST_DRAG     = 1.8;
    private static final double DUST_GRAVITY  = 1.4;

    // =====================================================================
    //  Dữ liệu hiệu ứng
    // =====================================================================

    /** Một điểm của vệt mũi tên. */
    private static final class Pt {
        final double x, y, z;
        final long t;
        final float hue;

        Pt(double x, double y, double z, long t, float hue) {
            this.x = x; this.y = y; this.z = z; this.t = t; this.hue = hue;
        }
    }

    /** Trạng thái theo dõi của một mũi tên. */
    private static final class Tracked {
        final List<Pt> pts = new ArrayList<Pt>();
        double lx, ly, lz;      // vị trí mới nhất
        double vx, vy, vz;      // vận tốc cuối cùng khi còn bay
        long lastAdd;
        int count;
        boolean landed, seen;
    }

    /** Tia lửa búng ra khi mũi tên trúng. Vận tốc tính theo block/giây. */
    private static final class Dust {
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, len, hue;

        Dust(double x, double y, double z, double vx, double vy, double vz,
             long t, float life, float len, float hue) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.len = len; this.hue = hue;
        }
    }

    /** Hệ trục hướng về màn hình (billboard). */
    private static final class Billboard {
        final double rx, rz, ux, uy, uz;

        Billboard(double rx, double rz, double ux, double uy, double uz) {
            this.rx = rx; this.rz = rz; this.ux = ux; this.uy = uy; this.uz = uz;
        }
    }

    private final Map<Integer, Tracked> tracked = new HashMap<Integer, Tracked>();
    private final List<Dust> dusts = new ArrayList<Dust>();
    private final Random rng = new Random();

    public BowEffectModule() {
        super("bowfx", "Hiệu Ứng Cung",
                "Vệt sáng theo mũi tên bay và tia lửa búng ra khi mũi tên trúng.",
                Category.EFFECT, true);
        new RainbowSettings(this);
    }

    @Override
    public void onToggle() {
        clearAll();
    }

    private void clearAll() {
        tracked.clear();
        dusts.clear();
    }

    // =====================================================================
    //  Ẩn hạt mặc định của cung
    // =====================================================================

    private static Field fxLayersField;
    private static boolean fxLookupFailed;

    /**
     * Chạy ở cuối mỗi tick: hạt do mũi tên vừa sinh ra đã nằm trong danh sách của EffectRenderer
     * nhưng chưa được vẽ, nên xóa lúc này thì người chơi không thấy chúng dù chỉ một khung hình.
     */
    @Override
    public void onTick() {
        if (!enabled || !hideVanilla.value) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null || mc.effectRenderer == null) return;

        // Mũi tên đang bay mà module có vẽ hiệu ứng riêng: (x, y, z, bán kính).
        List<double[]> arrows = new ArrayList<double[]>();
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityArrow)) continue;
            EntityArrow a = (EntityArrow) o;
            if (a.isDead) continue;
            if (mineOnly.value && a.shootingEntity != p) continue;
            double sp = Math.sqrt(a.motionX * a.motionX + a.motionY * a.motionY + a.motionZ * a.motionZ);
            // Hạt sinh dọc quãng đường mũi tên vừa bay trong tick này nên bán kính lấy theo tốc độ.
            arrows.add(new double[]{a.posX, a.posY, a.posZ, sp + 1.0});
        }
        if (arrows.isEmpty()) return;

        List<EntityFX>[] layers = fxLayers(mc.effectRenderer);
        if (layers == null) return;
        for (List<EntityFX> layer : layers) {
            if (layer == null) continue;
            for (Iterator<EntityFX> it = layer.iterator(); it.hasNext(); ) {
                EntityFX fx = it.next();
                if (!isArrowParticle(fx)) continue;
                for (double[] a : arrows) {
                    double dx = fx.posX - a[0], dy = fx.posY - a[1], dz = fx.posZ - a[2];
                    if (dx * dx + dy * dy + dz * dz <= a[3] * a[3]) {
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    /** Hạt mặc định mà mũi tên sinh ra: chí mạng khi bay và bong bóng khi ở dưới nước. */
    private static boolean isArrowParticle(EntityFX fx) {
        return fx instanceof EntityCritFX || fx instanceof EntityCrit2FX || fx instanceof EntityBubbleFX;
    }

    /** Lấy danh sách hạt của EffectRenderer qua reflection (trường này là private). */
    @SuppressWarnings("unchecked")
    private static List<EntityFX>[] fxLayers(EffectRenderer renderer) {
        if (fxLookupFailed) return null;
        try {
            if (fxLayersField == null) {
                fxLayersField = ReflectionHelper.findField(EffectRenderer.class, "fxLayers", "field_78876_b");
            }
            return (List<EntityFX>[]) fxLayersField.get(renderer);
        } catch (Exception ex) {
            fxLookupFailed = true; // không tìm được thì thôi, không làm game lỗi
            return null;
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
        float len = trailLen.getFloat();

        updateArrows(mc, p, pt, now, len);
        removeExpiredDust(now);
        if (tracked.isEmpty() && dusts.isEmpty()) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);
        float maxA = opacity.getFloat() / 100f;
        Billboard bb = cameraBillboard();

        beginGlow();
        if (trail.value) {
            for (Tracked t : tracked.values()) drawTrail(t, now, len, maxA, bb, cx, cy, cz);
        }
        for (Dust d : dusts) drawDust(d, now, maxA, cx, cy, cz);
        GL11.glLineWidth(1f);
        endGlow();
    }

    // ---------------------------------------------------------------------
    //  Theo dõi mũi tên
    // ---------------------------------------------------------------------

    private void updateArrows(Minecraft mc, EntityPlayer p, float pt, long now, float len) {
        for (Tracked t : tracked.values()) t.seen = false;

        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityArrow)) continue;
            EntityArrow a = (EntityArrow) o;
            if (a.isDead) continue;
            if (mineOnly.value && a.shootingEntity != p) continue;

            Tracked t = tracked.get(a.getEntityId());
            if (t == null) {
                t = new Tracked();
                tracked.put(a.getEntityId(), t);
            }
            t.seen = true;
            if (t.landed) continue;

            double x = EffectUtil.lerp(a.lastTickPosX, a.posX, pt);
            double y = EffectUtil.lerp(a.lastTickPosY, a.posY, pt);
            double z = EffectUtil.lerp(a.lastTickPosZ, a.posZ, pt);
            double sp2 = a.motionX * a.motionX + a.motionY * a.motionY + a.motionZ * a.motionZ;

            if (sp2 < STOP_SPEED2 && t.pts.size() >= 2) { // cắm vào block
                land(t);
                continue;
            }
            if (sp2 >= STOP_SPEED2) {
                t.vx = a.motionX; t.vy = a.motionY; t.vz = a.motionZ;
            }

            double dx = x - t.lx, dy = y - t.ly, dz = z - t.lz;
            boolean first = t.pts.isEmpty();
            if (first || dx * dx + dy * dy + dz * dz > POINT_MIN_DIST2 || now - t.lastAdd >= POINT_MIN_MS) {
                t.pts.add(new Pt(x, y, z, now, t.count++ * 10f));
                t.lastAdd = now;
            }
            t.lx = x; t.ly = y; t.lz = z;
        }

        for (Iterator<Tracked> it = tracked.values().iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            for (Iterator<Pt> pi = t.pts.iterator(); pi.hasNext(); ) {
                if (now - pi.next().t > len) pi.remove();
            }
            if (!t.seen) {            // mũi tên đã biến mất (trúng sinh vật hoặc bị xóa)
                land(t);
                if (t.pts.isEmpty()) it.remove();
            }
        }
    }

    /** Đánh dấu mũi tên đã dừng và bắn tia lửa đúng một lần. */
    private void land(Tracked t) {
        if (t.landed) return;
        t.landed = true;
        if (impact.value && t.count >= 2) spawnImpact(t);
    }

    /** Tia lửa búng ra ngược chiều bay, tỏa quanh điểm trúng. */
    private void spawnImpact(Tracked t) {
        double sp = Math.sqrt(t.vx * t.vx + t.vy * t.vy + t.vz * t.vz);
        double bx = sp > 1.0E-6 ? -t.vx / sp : 0, by = sp > 1.0E-6 ? -t.vy / sp : 1, bz = sp > 1.0E-6 ? -t.vz / sp : 0;
        long now = System.currentTimeMillis();
        int n = impactCount.getInt();
        for (int i = 0; i < n; i++) {
            double vx = bx * 0.8 + rng.nextGaussian() * 0.8;
            double vy = by * 0.8 + rng.nextGaussian() * 0.8 + 0.2;
            double vz = bz * 0.8 + rng.nextGaussian() * 0.8;
            double k = (1.4 + rng.nextDouble() * 2.2) / (Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6);
            dusts.add(new Dust(t.lx, t.ly, t.lz, vx * k, vy * k, vz * k, now,
                    350f + rng.nextFloat() * 400f, 0.08f + rng.nextFloat() * 0.12f, i * 29f));
        }
    }

    private void removeExpiredDust(long now) {
        for (Iterator<Dust> it = dusts.iterator(); it.hasNext(); ) {
            Dust d = it.next();
            if (now - d.t > d.life) it.remove();
        }
    }

    // ---------------------------------------------------------------------
    //  Vẽ
    // ---------------------------------------------------------------------

    /** Vệt mảnh: lớp quầng mỏng bên ngoài + đường lõi sáng, đuôi mờ dần. */
    private void drawTrail(Tracked t, long now, float len, float maxA, Billboard bb,
                           double cx, double cy, double cz) {
        int n = t.pts.size();
        if (n < 2) return;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        for (int pass = 0; pass < 2; pass++) {
            GL11.glLineWidth(pass == 0 ? 3.4f : 1.6f);
            wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                Pt q = t.pts.get(i);
                float fade = Math.max(0f, Math.min(1f, 1f - (now - q.t) / len));
                int c = rainbow.resolve(color.rgb(), q.hue);
                float whiten = pass == 0 ? 0f : 0.6f;
                int r = heat(red(c), whiten), g = heat(green(c), whiten), b = heat(blue(c), whiten);
                float a = maxA * fade * fade * (pass == 0 ? 0.22f : 0.95f);
                wr.pos(q.x - cx, q.y - cy, q.z - cz).color(r, g, b, alpha(a)).endVertex();
            }
            tess.draw();
        }

        if (head.value && !t.landed) {
            Pt q = t.pts.get(n - 1);
            int c = rainbow.resolve(color.rgb(), q.hue);
            fan(wr, tess, bb, q.x - cx, q.y - cy, q.z - cz, 0.045,
                    red(c), green(c), blue(c), maxA * 0.9f);
        }
    }

    /** Tia lửa: gạch ngắn theo vận tốc, đầu sáng, đuôi mờ; từ trắng nóng chuyển sang màu hiệu ứng. */
    private void drawDust(Dust d, long now, float maxA, double cx, double cy, double cz) {
        double age = (now - d.t) / 1000.0;
        if (age < 0) return;
        float lifeK = (now - d.t) / d.life;
        float a = Math.min(1f, maxA * 1.15f) * (1f - lifeK) * (1f - lifeK);
        if (a <= 0f) return;

        double dr = Math.exp(-DUST_DRAG * age), disp = (1.0 - dr) / DUST_DRAG;
        double hx = d.x + d.vx * disp - cx;
        double hy = d.y + d.vy * disp - 0.5 * DUST_GRAVITY * age * age - cy;
        double hz = d.z + d.vz * disp - cz;
        double cvx = d.vx * dr, cvy = d.vy * dr - DUST_GRAVITY * age, cvz = d.vz * dr;
        double sp = Math.sqrt(cvx * cvx + cvy * cvy + cvz * cvz);
        if (sp < 1.0E-4) return;

        double len = d.len * (1.0 - 0.4 * lifeK);
        double tx = hx - cvx / sp * len, ty = hy - cvy / sp * len, tz = hz - cvz / sp * len;

        int c = rainbow.resolve(color.rgb(), d.hue);
        float hot = Math.max(0f, 1f - lifeK * 2.2f);
        int r = red(c), g = green(c), b = blue(c);
        int hr = heat(r, hot), hg = heat(g, hot), hb = heat(b, hot);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GL11.glLineWidth(2.0f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(tx, ty, tz).color(r, g, b, 0).endVertex();
        wr.pos(hx, hy, hz).color(hr, hg, hb, alpha(a * 0.95f)).endVertex();
        tess.draw();
    }

    /** Chấm sáng tròn nhỏ quay mặt về camera, lõi sáng mờ dần ra rìa. */
    private static void fan(WorldRenderer wr, Tessellator tess, Billboard bb,
                            double px, double py, double pz, double radius,
                            int r, int g, int b, float a) {
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px, py, pz).color(255, 255, 255, alpha(a)).endVertex();
        for (int i = 0; i <= 12; i++) {
            double ang = i / 12.0 * Math.PI * 2.0, ca = Math.cos(ang) * radius, sa = Math.sin(ang) * radius;
            wr.pos(px + bb.rx * ca + bb.ux * sa, py + bb.uy * sa, pz + bb.rz * ca + bb.uz * sa)
                    .color(r, g, b, 0).endVertex();
        }
        tess.draw();
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
