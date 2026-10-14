package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/** Vòng phép thuật dưới chân: các vòng tròn, ngôi sao 6 cánh và ký tự xoay ngược chiều nhau, sáng lên rồi tan dần. */
public class JumpCircleModule extends Module {
    private final ModeSetting trigger = add(new ModeSetting("trigger", "Trigger", 0, "Jump", "Land", "Both"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting radius = add(new SliderSetting("radius_v3", "Max Radius", 1.0, 0.4, 3.0, 0.05, ""));
    private final SliderSetting duration = add(new SliderSetting("duration_v3", "Duration", 850, 300, 2500, 50, " ms"));
    private final SliderSetting width = add(new SliderSetting("width_v3", "Line Width", 1.4, 0.5, 5.0, 0.25, ""));
    private final BoolSetting star = add(new BoolSetting("star_v3", "Star Pattern", true));
    private final BoolSetting fill = add(new BoolSetting("fill_v3", "Glow Fill", true));

    private static final int MAX_RINGS = 8, STEPS = 48;

    private static final class Ring {
        final double x, y, z;
        final long t;
        Ring(double x, double y, double z, long t) { this.x = x; this.y = y; this.z = z; this.t = t; }
    }

    private final List<Ring> rings = new ArrayList<Ring>();
    private boolean wasOnGround = true;

    public JumpCircleModule() {
        super("jumpcircle", "Jump Circle", "Vòng phép thuật xoay dưới chân khi nhảy / tiếp đất.", Category.EFFECT, false);
        new RainbowSettings(this);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null) { rings.clear(); return; }
        if (!enabled) { rings.clear(); wasOnGround = p.onGround; return; }
        boolean jump = !trigger.is("Land"), land = !trigger.is("Jump");
        if (jump && wasOnGround && !p.onGround && p.motionY > 0.0) addRing(p);
        if (land && !wasOnGround && p.onGround) addRing(p);
        wasOnGround = p.onGround;
    }

    private void addRing(EntityPlayer p) {
        if (rings.size() >= MAX_RINGS) rings.remove(0);
        rings.add(new Ring(p.posX, p.posY + 0.03, p.posZ, System.currentTimeMillis()));
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        if (!enabled || rings.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity cam = mc.getRenderViewEntity();
        if (cam == null) return;
        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);
        long now = System.currentTimeMillis();
        float dur = duration.getFloat();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0); // cộng màu cho sáng rực
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring r = it.next();
            if (now - r.t > dur) { it.remove(); continue; }
            drawMagic(r, (now - r.t) / dur, cx, cy, cz);
        }

        GL11.glLineWidth(1f);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static float smooth(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

    private void drawMagic(Ring ring, float k, double cx, double cy, double cz) {
        float appear = Math.min(1f, k / 0.2f);
        float ease = 1f - (1f - appear) * (1f - appear) * (1f - appear);
        double rad = radius.get() * (0.6 + 0.4 * ease) * (1.0 + 0.12 * smooth((k - 0.6f) / 0.4f));
        float a = smooth(k * 8f) * (1f - smooth((k - 0.6f) / 0.4f));
        if (a <= 0.01f) return;
        double rot = k * 2.4;                                       // nửa vòng quay trong suốt thời gian sống
        int c = rainbow.resolve(color.rgb(), 0f);
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int wr = r + (255 - r) / 2, wg = g + (255 - g) / 2, wb = b + (255 - b) / 2;
        double ox = ring.x - cx, oy = ring.y - cy, oz = ring.z - cz;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer w = tess.getWorldRenderer();

        if (fill.value) { // đĩa sáng mờ: trong suốt ở tâm, đậm dần ra mép
            w.begin(5, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= STEPS; i++) {
                double ang = i / (double) STEPS * Math.PI * 2.0, co = Math.cos(ang), si = Math.sin(ang);
                w.pos(ox + co * rad * 0.3, oy, oz + si * rad * 0.3).color(r, g, b, 0).endVertex();
                w.pos(ox + co * rad * 0.97, oy, oz + si * rad * 0.97).color(r, g, b, Math.round(60f * a)).endVertex();
            }
            tess.draw();
        }

        for (int pass = 0; pass < 2; pass++) { // pass 0: hào quang rộng, pass 1: lõi sáng
            GL11.glLineWidth(width.getFloat() * (pass == 0 ? 2.4f : 1f));
            int al = Math.round(255f * a * (pass == 0 ? 0.25f : 0.95f));
            int lr = pass == 0 ? r : wr, lg = pass == 0 ? g : wg, lb = pass == 0 ? b : wb;

            circle(w, tess, ox, oy, oz, rad, lr, lg, lb, al);                 // vòng ngoài
            circle(w, tess, ox, oy, oz, rad * 0.86, lr, lg, lb, al * 2 / 3);   // vòng trong của dải ký tự
            circle(w, tess, ox, oy, oz, rad * 0.42, lr, lg, lb, al * 2 / 3);   // vòng lõi

            // dải ký tự: các vạch ngắn/dài xen kẽ, xoay thuận chiều
            w.begin(1, DefaultVertexFormats.POSITION_COLOR);
            int ticks = 36;
            for (int i = 0; i < ticks; i++) {
                double ang = rot + i / (double) ticks * Math.PI * 2.0;
                double in = (i % 3 == 0) ? 0.86 : (i % 3 == 1 ? 0.9 : 0.93), out = 0.985;
                double co = Math.cos(ang), si = Math.sin(ang);
                w.pos(ox + co * rad * in, oy, oz + si * rad * in).color(lr, lg, lb, al).endVertex();
                w.pos(ox + co * rad * out, oy, oz + si * rad * out).color(lr, lg, lb, al).endVertex();
            }
            tess.draw();

            if (star.value) { // ngôi sao 6 cánh xoay ngược chiều
                double sr = rad * 0.84;
                for (int t = 0; t < 2; t++) {
                    w.begin(3, DefaultVertexFormats.POSITION_COLOR);
                    for (int i = 0; i <= 3; i++) {
                        double ang = -rot * 1.3 + t * Math.PI / 3.0 + i * Math.PI * 2.0 / 3.0 + Math.PI / 6.0;
                        w.pos(ox + Math.cos(ang) * sr, oy, oz + Math.sin(ang) * sr).color(lr, lg, lb, al).endVertex();
                    }
                    tess.draw();
                }
            }
        }

        // chấm sáng tại 6 đỉnh sao
        if (star.value) {
            double sr = rad * 0.84, dot = rad * 0.055;
            for (int i = 0; i < 6; i++) {
                double ang = -rot * 1.3 + i * Math.PI / 3.0 + Math.PI / 6.0;
                double px = ox + Math.cos(ang) * sr, pz = oz + Math.sin(ang) * sr;
                w.begin(6, DefaultVertexFormats.POSITION_COLOR);
                w.pos(px, oy, pz).color(255, 255, 255, Math.round(255f * a)).endVertex();
                for (int j = 0; j <= 10; j++) {
                    double q = j / 10.0 * Math.PI * 2.0;
                    w.pos(px + Math.cos(q) * dot, oy, pz + Math.sin(q) * dot).color(r, g, b, 0).endVertex();
                }
                tess.draw();
            }
        }
    }

    private static void circle(WorldRenderer w, Tessellator tess, double ox, double oy, double oz, double rad, int r, int g, int b, int a) {
        w.begin(3, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= STEPS; i++) {
            double ang = i / (double) STEPS * Math.PI * 2.0;
            w.pos(ox + Math.cos(ang) * rad, oy, oz + Math.sin(ang) * rad).color(r, g, b, a).endVertex();
        }
        tess.draw();
    }
}
