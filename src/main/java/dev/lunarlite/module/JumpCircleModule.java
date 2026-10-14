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

/** Sóng năng lượng lan ra dưới chân: vòng sáng + dải mờ phía trong, tuỳ chọn 2 lớp và khi tiếp đất. */
public class JumpCircleModule extends Module {
    private final ModeSetting trigger = add(new ModeSetting("trigger", "Trigger", 0, "Jump", "Land", "Both"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting radius = add(new SliderSetting("radius_v2", "Max Radius", 0.85, 0.3, 3.0, 0.05, ""));
    private final SliderSetting duration = add(new SliderSetting("duration_v2", "Duration", 450, 150, 1500, 50, " ms"));
    private final SliderSetting width = add(new SliderSetting("width_v2", "Line Width", 1.5, 0.5, 6.0, 0.25, ""));
    private final BoolSetting doubleRing = add(new BoolSetting("double_v2", "Double Ring", false));
    private final BoolSetting fill = add(new BoolSetting("fill", "Fill", true));

    private static final int MAX_RINGS = 8, STEPS = 48;

    private static final class Ring {
        final double x, y, z;
        final long t;
        Ring(double x, double y, double z, long t) { this.x = x; this.y = y; this.z = z; this.t = t; }
    }

    private final List<Ring> rings = new ArrayList<Ring>();
    private boolean wasOnGround = true;

    public JumpCircleModule() {
        super("jumpcircle", "Jump Circle", "Sóng năng lượng lan ra dưới chân khi nhảy / tiếp đất.", Category.EFFECT, false);
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
            if (now - r.t > dur * 1.25f) { it.remove(); continue; }
            drawWave(r, (now - r.t) / dur, 1.0, cx, cy, cz);
            if (doubleRing.value) drawWave(r, (now - r.t) / dur - 0.18f, 0.7, cx, cy, cz);
        }

        GL11.glLineWidth(1f);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawWave(Ring ring, float k, double scale, double cx, double cy, double cz) {
        if (k <= 0f || k >= 1f) return;
        float e = 1f - (1f - k) * (1f - k) * (1f - k) * (1f - k);   // ease-out quart: bung nhanh rồi lướt chậm
        double rad = radius.get() * scale * (0.15 + 0.85 * e);
        float a = (1f - k) * (1f - k) * Math.min(1f, k * 12f);       // mờ dần mượt, hiện lên nhẹ ở đầu
        int c = rainbow.resolve(color.rgb(), (float) (scale * 60.0));
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        double ox = ring.x - cx, oy = ring.y - cy, oz = ring.z - cz;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        if (fill.value) { // dải mờ: trong suốt ở trong, sáng dần ra mép
            wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= STEPS; i++) {
                double ang = i / (double) STEPS * Math.PI * 2.0, co = Math.cos(ang), si = Math.sin(ang);
                wr.pos(ox + co * rad * 0.7, oy, oz + si * rad * 0.7).color(r, g, b, 0).endVertex();
                wr.pos(ox + co * rad, oy, oz + si * rad).color(r, g, b, Math.round(70f * a)).endVertex();
            }
            tess.draw();
        }
        for (int pass = 0; pass < 2; pass++) { // pass 0: hào quang rộng, pass 1: lõi sáng
            GL11.glLineWidth(width.getFloat() * (pass == 0 ? 2.2f : 1f));
            int al = Math.round(255f * a * (pass == 0 ? 0.22f : 0.9f));
            int lr = pass == 0 ? r : r + (255 - r) / 2, lg = pass == 0 ? g : g + (255 - g) / 2, lb = pass == 0 ? b : b + (255 - b) / 2;
            wr.begin(3, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= STEPS; i++) {
                double ang = i / (double) STEPS * Math.PI * 2.0;
                wr.pos(ox + Math.cos(ang) * rad, oy, oz + Math.sin(ang) * rad).color(lr, lg, lb, al).endVertex();
            }
            tess.draw();
        }
    }
}
