package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Items;
import org.lwjgl.opengl.GL11;

/**
 * <h2>Hiệu Ứng Ném - tia sáng theo vật phẩm ném</h2>
 *
 * <h3>Tính năng</h3>
 * <ol>
 *   <li><b>Tia sáng</b>: thuốc ném, trứng gà, cục tuyết và ngọc ender khi bay để lại một dải sáng
 *       quay mặt về camera, lõi trắng nóng, quầng màu bên ngoài, thon dần và mờ dần về phía đuôi.</li>
 *   <li><b>Màu theo từng loại</b>: thuốc ném lấy đúng màu của loại thuốc (hồi máu đỏ, tốc độ xanh nhạt,
 *       độc xanh lá...), trứng gà vàng kem, cục tuyết xanh băng, ngọc ender tím. Ba loại sau chỉnh được màu.</li>
 *   <li><b>Đầu sáng</b>: một chấm sáng có quầng ở vật phẩm đang bay.</li>
 *   <li><b>Tia lửa khi chạm</b>: khi vật phẩm vỡ hoặc chạm đất, các tia lửa bật ra theo hướng ngược chiều bay,
 *       có lực cản không khí và trọng lực, cùng màu với tia sáng.</li>
 *   <li><b>Mờ dần khi ở sát mắt</b>: đoạn tia ngay trước mặt lúc vừa ném được làm mờ để không che tầm nhìn.</li>
 *   <li><b>Chỉ hiển thị</b>: module tự quét vật phẩm ném trong thế giới mỗi khung hình,
 *       không can thiệp vào đường bay hay gửi gì lên server.</li>
 * </ol>
 *
 * <h3>Cách dùng</h3>
 * Đăng ký module trong LunarLite và gọi {@link #render(float)} từ RenderWorldLastEvent.
 */
public class ThrowableTrailModule extends Module {

    // =====================================================================
    //  Settings
    // =====================================================================

    private final BoolSetting   potionOn   = add(new BoolSetting("potion", "Thuốc ném", true));
    private final BoolSetting   eggOn      = add(new BoolSetting("egg", "Trứng gà", true));
    private final BoolSetting   snowOn     = add(new BoolSetting("snowball", "Cục tuyết", true));
    private final BoolSetting   pearlOn    = add(new BoolSetting("pearl", "Ngọc ender", true));

    private final ColorSetting  eggColor   = add(new ColorSetting("egg_color", "Màu trứng gà", 0xFFD66B));
    private final ColorSetting  snowColor  = add(new ColorSetting("snow_color", "Màu cục tuyết", 0xBFEFFF));
    private final ColorSetting  pearlColor = add(new ColorSetting("pearl_color", "Màu ngọc ender", 0xB26BFF));

    private final SliderSetting opacity    = add(new SliderSetting("opacity", "Độ đục", 100, 10, 100, 5, "%"));
    private final SliderSetting width      = add(new SliderSetting("width", "Độ dày tia", 1.0, 0.5, 3.0, 0.1, "x"));
    private final SliderSetting length     = add(new SliderSetting("length", "Độ dài tia", 600, 150, 2000, 50, " ms"));
    private final BoolSetting   head       = add(new BoolSetting("head", "Đầu sáng", true));

    private final BoolSetting   impact     = add(new BoolSetting("impact", "Tia lửa khi chạm", true));
    private final SliderSetting impactCount = add(new SliderSetting("impact_count", "Số tia lửa", 20, 4, 60, 2, ""));

    private final BoolSetting   mineOnly   = add(new BoolSetting("mine_only", "Chỉ vật phẩm của tôi", false));

    // =====================================================================
    //  Hằng số
    // =====================================================================

    private static final int POTION = 0, EGG = 1, SNOW = 2, PEARL = 3;

    private static final double POINT_MIN_DIST2 = 0.0009; // khoảng cách tối thiểu giữa hai điểm của tia
    private static final long   POINT_MIN_MS    = 12L;
    private static final double SPARK_DRAG      = 1.8;
    private static final double SPARK_GRAVITY   = 1.6;

    private static final int WATER_POTION_COLOR = 0x385DC6;

    // =====================================================================
    //  Dữ liệu hiệu ứng
    // =====================================================================

    /** Một điểm của tia sáng. */
    private static final class Pt {
        final double x, y, z;
        final long t;

        Pt(double x, double y, double z, long t) {
            this.x = x; this.y = y; this.z = z; this.t = t;
        }
    }

    /** Trạng thái theo dõi của một vật phẩm ném. */
    private static final class Tracked {
        final List<Pt> pts = new ArrayList<Pt>();
        int kind;
        int potionColor = WATER_POTION_COLOR;
        boolean mine, seen, landed;
        double lx, ly, lz;      // vị trí mới nhất
        double vx, vy, vz;      // vận tốc cuối cùng khi còn bay
        long lastAdd;
    }

    /** Tia lửa bật ra khi chạm. Vận tốc tính theo block/giây. */
    private static final class Spark {
        final double x, y, z, vx, vy, vz;
        final long t;
        final float life, len;
        final int color;

        Spark(double x, double y, double z, double vx, double vy, double vz,
              long t, float life, float len, int color) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.t = t; this.life = life; this.len = len; this.color = color;
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
    private final List<Spark> sparks = new ArrayList<Spark>();
    private final Random rng = new Random();

    public ThrowableTrailModule() {
        super("throwfx", "Hiệu Ứng Ném",
                "Tia sáng theo màu từng loại cho thuốc ném, trứng gà, cục tuyết và ngọc ender.",
                Category.EFFECT, true);
    }

    @Override
    public void onToggle() {
        clearAll();
    }

    private void clearAll() {
        tracked.clear();
        sparks.clear();
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
        float len = length.getFloat();

        update(mc, p, pt, now, len);
        removeExpiredSparks(now);
        if (tracked.isEmpty() && sparks.isEmpty()) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);
        float maxA = opacity.getFloat() / 100f;
        double wk = width.get();
        Billboard bb = cameraBillboard();

        beginGlow();
        for (Tracked t : tracked.values()) drawTrail(t, now, len, maxA, wk, bb, cx, cy, cz);
        for (Spark s : sparks) drawSpark(s, now, maxA, cx, cy, cz);
        GL11.glLineWidth(1f);
        endGlow();
    }

    // ---------------------------------------------------------------------
    //  Theo dõi vật phẩm ném
    // ---------------------------------------------------------------------

    private void update(Minecraft mc, EntityPlayer p, float pt, long now, float len) {
        for (Tracked t : tracked.values()) t.seen = false;

        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityThrowable)) continue;
            EntityThrowable e = (EntityThrowable) o;
            if (e.isDead) continue;
            int kind = kindOf(e);
            if (kind < 0 || !kindEnabled(kind)) continue;

            Tracked t = tracked.get(e.getEntityId());
            if (t == null) {
                t = new Tracked();
                t.kind = kind;
                t.mine = isMine(e, p);
                if (e instanceof EntityPotion) t.potionColor = potionColor((EntityPotion) e);
                tracked.put(e.getEntityId(), t);
            }
            t.seen = true;
            if (t.landed) continue;
            if (mineOnly.value && !t.mine) continue;

            double x = EffectUtil.lerp(e.lastTickPosX, e.posX, pt);
            double y = EffectUtil.lerp(e.lastTickPosY, e.posY, pt);
            double z = EffectUtil.lerp(e.lastTickPosZ, e.posZ, pt);
            t.vx = e.motionX; t.vy = e.motionY; t.vz = e.motionZ;

            double dx = x - t.lx, dy = y - t.ly, dz = z - t.lz;
            if (t.pts.isEmpty() || dx * dx + dy * dy + dz * dz > POINT_MIN_DIST2 || now - t.lastAdd >= POINT_MIN_MS) {
                t.pts.add(new Pt(x, y, z, now));
                t.lastAdd = now;
            }
            t.lx = x; t.ly = y; t.lz = z;
        }

        for (Iterator<Tracked> it = tracked.values().iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            for (Iterator<Pt> pi = t.pts.iterator(); pi.hasNext(); ) {
                if (now - pi.next().t > len) pi.remove();
            }
            if (!t.seen) {            // vật phẩm đã biến mất (vỡ, chạm đất hoặc bị xóa)
                land(t);
                if (t.pts.isEmpty()) it.remove();
            }
        }
    }

    private static int kindOf(EntityThrowable e) {
        if (e instanceof EntityPotion) return POTION;
        if (e instanceof EntityEnderPearl) return PEARL;
        if (e instanceof EntitySnowball) return SNOW;
        if (e instanceof EntityEgg) return EGG;
        return -1;
    }

    private boolean kindEnabled(int kind) {
        switch (kind) {
            case POTION: return potionOn.value;
            case EGG:    return eggOn.value;
            case SNOW:   return snowOn.value;
            default:     return pearlOn.value;
        }
    }

    /**
     * Vật phẩm do mình ném? Thuốc ném không gửi kèm người ném xuống client nên
     * khi không biết người ném thì coi là của mình nếu vừa xuất hiện sát người chơi.
     */
    private static boolean isMine(EntityThrowable e, EntityPlayer p) {
        EntityLivingBase thrower = e.getThrower();
        if (thrower != null) return thrower == p;
        return e.getDistanceSqToEntity(p) < 9.0;
    }

    /** Màu chất lỏng của loại thuốc đang bay. */
    private static int potionColor(EntityPotion e) {
        try {
            return Items.potionitem.getColorFromDamage(e.getPotionDamage()) & 0xFFFFFF;
        } catch (Throwable ex) {
            return WATER_POTION_COLOR;
        }
    }

    /** Màu hiệu ứng của một vật phẩm, đã được làm sáng lên để dễ thấy. */
    private int colorOf(Tracked t) {
        int c;
        switch (t.kind) {
            case POTION: c = t.potionColor; break;
            case EGG:    c = eggColor.rgb(); break;
            case SNOW:   c = snowColor.rgb(); break;
            default:     c = pearlColor.rgb(); break;
        }
        return brighten(c);
    }

    /** Các màu thuốc tối (độc, gây sát thương...) sẽ không thấy rõ khi phát sáng, nên nâng kênh sáng nhất lên. */
    private static int brighten(int c) {
        int r = red(c), g = green(c), b = blue(c);
        int m = Math.max(r, Math.max(g, b));
        if (m <= 0) return 0xFFFFFF;
        if (m < 210) {
            float k = 210f / m;
            r = Math.min(255, Math.round(r * k));
            g = Math.min(255, Math.round(g * k));
            b = Math.min(255, Math.round(b * k));
        }
        return (r << 16) | (g << 8) | b;
    }

    /** Đánh dấu vật phẩm đã dừng và bắn tia lửa đúng một lần. */
    private void land(Tracked t) {
        if (t.landed) return;
        t.landed = true;
        if (!impact.value || t.pts.size() < 2) return;
        if (mineOnly.value && !t.mine) return;
        spawnImpact(t);
    }

    /** Tia lửa bật ra ngược chiều bay, tỏa quanh điểm chạm. */
    private void spawnImpact(Tracked t) {
        double sp = Math.sqrt(t.vx * t.vx + t.vy * t.vy + t.vz * t.vz);
        double bx = sp > 1.0E-6 ? -t.vx / sp : 0, by = sp > 1.0E-6 ? -t.vy / sp : 1, bz = sp > 1.0E-6 ? -t.vz / sp : 0;
        int color = colorOf(t);
        long now = System.currentTimeMillis();
        int n = impactCount.getInt();
        for (int i = 0; i < n; i++) {
            double vx = bx * 0.7 + rng.nextGaussian() * 0.8;
            double vy = by * 0.7 + rng.nextGaussian() * 0.8 + 0.3;
            double vz = bz * 0.7 + rng.nextGaussian() * 0.8;
            double r = rng.nextDouble();
            double k = (1.6 + r * r * 3.4) / (Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6);
            sparks.add(new Spark(t.lx, t.ly, t.lz, vx * k, vy * k, vz * k, now,
                    400f + rng.nextFloat() * 450f, 0.10f + rng.nextFloat() * 0.14f, color));
        }
    }

    private void removeExpiredSparks(long now) {
        for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            if (now - s.t > s.life) it.remove();
        }
    }

    // ---------------------------------------------------------------------
    //  Vẽ
    // ---------------------------------------------------------------------

    /**
     * Tia sáng dạng dải quay mặt về camera: một lượt quầng màu rộng và mờ, một lượt lõi hẹp và sáng.
     * Dải thon dần và mờ dần về phía đuôi; đoạn sát camera được làm mờ để không che mắt.
     */
    private void drawTrail(Tracked t, long now, float len, float maxA, double wk, Billboard bb,
                           double cx, double cy, double cz) {
        int n = t.pts.size();
        if (n < 2) return;
        int c = colorOf(t);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        for (int pass = 0; pass < 2; pass++) {
            double baseHalf = (pass == 0 ? 0.070 : 0.020) * wk;
            float whiten = pass == 0 ? 0.0f : 0.65f;
            int r = heat(red(c), whiten), g = heat(green(c), whiten), b = heat(blue(c), whiten);

            wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < n; i++) {
                Pt q = t.pts.get(i);
                double px = q.x - cx, py = q.y - cy, pz = q.z - cz;

                // Hướng của tia tại điểm này (sai phân trung tâm).
                Pt a = t.pts.get(Math.max(0, i - 1)), d = t.pts.get(Math.min(n - 1, i + 1));
                double dx = d.x - a.x, dy = d.y - a.y, dz = d.z - a.z;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl < 1.0E-7) { dx = 0; dy = 1; dz = 0; } else { dx /= dl; dy /= dl; dz /= dl; }

                // Hướng ngang của dải: vuông góc với hướng bay và hướng nhìn (từ camera tới điểm).
                double sx = dy * pz - dz * py, sy = dz * px - dx * pz, sz = dx * py - dy * px;
                double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0E-7) { sx = bb.rx; sy = 0; sz = bb.rz; sl = 1; }

                float fade = Math.max(0f, Math.min(1f, 1f - (now - q.t) / len));
                double dist = Math.sqrt(px * px + py * py + pz * pz);
                float near = (float) Math.max(0.0, Math.min(1.0, (dist - 0.6) / 1.8));
                double hw = baseHalf * (0.2 + 0.8 * fade) / sl;
                float al = maxA * fade * fade * near * (pass == 0 ? 0.30f : 1.0f);
                int ca = alpha(al);

                wr.pos(px + sx * hw, py + sy * hw, pz + sz * hw).color(r, g, b, ca).endVertex();
                wr.pos(px - sx * hw, py - sy * hw, pz - sz * hw).color(r, g, b, ca).endVertex();
            }
            tess.draw();
        }

        if (head.value && !t.landed) {
            Pt q = t.pts.get(n - 1);
            double px = q.x - cx, py = q.y - cy, pz = q.z - cz;
            double dist = Math.sqrt(px * px + py * py + pz * pz);
            float near = (float) Math.max(0.0, Math.min(1.0, (dist - 0.6) / 1.8));
            float a = maxA * near;
            fan(wr, tess, bb, px, py, pz, 0.17 * wk, c, 255, 255, 255, alpha(a * 0.35f));
            fan(wr, tess, bb, px, py, pz, 0.055 * wk, c, 255, 255, 255, alpha(a));
        }
    }

    /** Tia lửa: gạch ngắn theo vận tốc, đầu sáng, đuôi mờ; từ trắng nóng chuyển sang màu hiệu ứng. */
    private void drawSpark(Spark s, long now, float maxA, double cx, double cy, double cz) {
        double age = (now - s.t) / 1000.0;
        if (age < 0) return;
        float lifeK = (now - s.t) / s.life;
        float a = Math.min(1f, maxA * 1.15f) * (1f - lifeK) * (1f - lifeK);
        if (a <= 0f) return;

        double dr = Math.exp(-SPARK_DRAG * age), disp = (1.0 - dr) / SPARK_DRAG;
        double hx = s.x + s.vx * disp - cx;
        double hy = s.y + s.vy * disp - 0.5 * SPARK_GRAVITY * age * age - cy;
        double hz = s.z + s.vz * disp - cz;
        double cvx = s.vx * dr, cvy = s.vy * dr - SPARK_GRAVITY * age, cvz = s.vz * dr;
        double sp = Math.sqrt(cvx * cvx + cvy * cvy + cvz * cvz);
        if (sp < 1.0E-4) return;

        double len = s.len * (1.0 - 0.4 * lifeK);
        double tx = hx - cvx / sp * len, ty = hy - cvy / sp * len, tz = hz - cvz / sp * len;

        float hot = Math.max(0f, 1f - lifeK * 2.0f);
        int r = red(s.color), g = green(s.color), b = blue(s.color);
        int hr = heat(r, hot), hg = heat(g, hot), hb = heat(b, hot);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GL11.glLineWidth(2.6f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(tx, ty, tz).color(r, g, b, 0).endVertex();
        wr.pos(hx, hy, hz).color(hr, hg, hb, alpha(a)).endVertex();
        tess.draw();
    }

    /** Chấm sáng tròn quay mặt về camera: tâm (cr, cg, cb, ca), rìa màu (color) trong suốt. */
    private static void fan(WorldRenderer wr, Tessellator tess, Billboard bb,
                            double px, double py, double pz, double radius, int color,
                            int cr, int cg, int cb, int ca) {
        int er = red(color), eg = green(color), eb = blue(color);
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px, py, pz).color(cr, cg, cb, ca).endVertex();
        for (int i = 0; i <= 14; i++) {
            double ang = i / 14.0 * Math.PI * 2.0, ca2 = Math.cos(ang) * radius, sa = Math.sin(ang) * radius;
            wr.pos(px + bb.rx * ca2 + bb.ux * sa, py + bb.uy * sa, pz + bb.rz * ca2 + bb.uz * sa)
                    .color(er, eg, eb, 0).endVertex();
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
