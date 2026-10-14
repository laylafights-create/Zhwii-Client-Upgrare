package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
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

/** Vệt sáng (kiểu kiếm khí) theo đường vung tay của bạn. Chỉ hiển thị, không ảnh hưởng gameplay. */
public class SwordTrailModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting length = add(new SliderSetting("length", "Trail Length", 350, 100, 1500, 50, " ms"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 80, 10, 100, 5, "%"));
    private final SliderSetting reach = add(new SliderSetting("reach", "Blade Length", 2.6, 1.0, 4.0, 0.1, ""));
    private final BoolSetting firstPerson = add(new BoolSetting("first_person", "Show In First Person", true));
    private final BoolSetting emptyHand = add(new BoolSetting("empty_hand", "Show With Empty Hand", false));

    private static final class Pt {
        final double bx, by, bz, tx, ty, tz;
        final long t;
        Pt(double bx, double by, double bz, double tx, double ty, double tz, long t) {
            this.bx = bx; this.by = by; this.bz = bz; this.tx = tx; this.ty = ty; this.tz = tz; this.t = t;
        }
    }

    private final List<Pt> pts = new ArrayList<Pt>();

    public SwordTrailModule() {
        super("swordtrail", "Sword Trail", "Vệt sáng theo đường vung tay / kiếm của bạn.", Category.EFFECT, false);
        new RainbowSettings(this);
    }

    @Override
    public void onToggle() { pts.clear(); }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        Entity cam = mc.getRenderViewEntity();
        if (!enabled || p == null || cam == null || mc.theWorld == null) { pts.clear(); return; }

        long now = System.currentTimeMillis();
        boolean fp = mc.gameSettings.thirdPersonView == 0;
        if (p.isSwingInProgress && (firstPerson.value || !fp) && (emptyHand.value || p.getHeldItem() != null)) {
            float s = p.getSwingProgress(pt);
            if (s > 0f && s < 1f && (pts.isEmpty() || now - pts.get(pts.size() - 1).t >= 10L)) {
                double yaw = Math.toRadians(EffectUtil.lerp(p.prevRotationYaw, p.rotationYaw, pt) + (0.5f - s) * 110f);
                double pit = Math.toRadians(EffectUtil.lerp(p.prevRotationPitch, p.rotationPitch, pt) + (s - 0.5f) * 40f);
                double dx = -Math.sin(yaw) * Math.cos(pit), dy = -Math.sin(pit), dz = Math.cos(yaw) * Math.cos(pit);
                double ex = EffectUtil.lerp(p.prevPosX, p.posX, pt);
                double ey = EffectUtil.lerp(p.prevPosY, p.posY, pt) + p.getEyeHeight() - 0.3;
                double ez = EffectUtil.lerp(p.prevPosZ, p.posZ, pt);
                double r = reach.get();
                pts.add(new Pt(ex + dx * 0.8, ey + dy * 0.8, ez + dz * 0.8, ex + dx * r, ey + dy * r, ez + dz * r, now));
            }
        }

        float len = length.getFloat();
        Iterator<Pt> it = pts.iterator();
        while (it.hasNext()) if (now - it.next().t > len) it.remove();
        if (pts.size() < 2) return;

        double cx = EffectUtil.lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = EffectUtil.lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = EffectUtil.lerp(cam.lastTickPosZ, cam.posZ, pt);
        float maxA = opacity.getFloat() / 100f;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < pts.size(); i++) {
            Pt q = pts.get(i);
            float fade = 1f - (now - q.t) / len;
            fade = Math.max(0f, Math.min(1f, fade));
            int c = rainbow.resolve(color.rgb(), i * 12f);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int aTip = Math.round(255f * maxA * fade);
            int aBase = Math.round(255f * maxA * fade * 0.25f);
            wr.pos(q.bx - cx, q.by - cy, q.bz - cz).color(r, g, b, aBase).endVertex();
            wr.pos(q.tx - cx, q.ty - cy, q.tz - cz).color(r, g, b, aTip).endVertex();
        }
        tess.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
