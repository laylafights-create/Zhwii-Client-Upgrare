package dev.lunarlite.module;

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
import org.lwjgl.opengl.GL11;

/** Vòng sáng lan ra dưới chân mỗi khi bạn nhảy. */
public class JumpCircleModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final SliderSetting radius = add(new SliderSetting("radius", "Max Radius", 1.6, 0.5, 4.0, 0.1, ""));
    private final SliderSetting duration = add(new SliderSetting("duration", "Duration", 700, 200, 2000, 50, " ms"));
    private final SliderSetting width = add(new SliderSetting("width", "Line Width", 3.0, 1.0, 8.0, 0.5, ""));

    private static final class Ring {
        final double x, y, z;
        final long t;
        Ring(double x, double y, double z, long t) { this.x = x; this.y = y; this.z = z; this.t = t; }
    }

    private final List<Ring> rings = new ArrayList<Ring>();
    private boolean wasOnGround = true;

    public JumpCircleModule() {
        super("jumpcircle", "Jump Circle", "Vòng sáng lan ra dưới chân khi nhảy.", Category.EFFECT, false);
        new RainbowSettings(this);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null) { rings.clear(); return; }
        if (!enabled) { rings.clear(); wasOnGround = p.onGround; return; }
        if (wasOnGround && !p.onGround && p.motionY > 0.0) {
            rings.add(new Ring(p.posX, p.posY + 0.02, p.posZ, System.currentTimeMillis()));
        }
        wasOnGround = p.onGround;
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
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(width.getFloat());

        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring r = it.next();
            float k = (now - r.t) / dur;
            if (k >= 1f) { it.remove(); continue; }
            float e = 1f - (1f - k) * (1f - k);
            double rad = radius.get() * e;
            int c = rainbow.resolve(color.rgb(), 0f);
            int alpha = Math.round(255f * (1f - k));
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(3, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= 40; i++) {
                double a = i / 40.0 * Math.PI * 2.0;
                wr.pos(r.x - cx + Math.cos(a) * rad, r.y - cy, r.z - cz + Math.sin(a) * rad)
                  .color((c >> 16) & 255, (c >> 8) & 255, c & 255, alpha).endVertex();
            }
            tess.draw();
        }

        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
