package dev.lunarlite.effect;

import java.awt.Color;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * Hiệu ứng vệt sáng khi chém kiếm. Lớp độc lập: không biết gì về Module hay menu,
 * chỉ đọc các trường cấu hình bên dưới, gọi tick() mỗi client tick và render() mỗi frame.
 */
public class SwordTrailEffect {
    public static final int STATIC = 0, RAINBOW = 1, GRADIENT = 2;

    // ===== Cấu hình (bên ngoài gán giá trị) =====
    public int colorMode = STATIC;
    public int color = 0x55FFFF;
    public int color2 = 0xAA55FF;
    public int length = 12;        // tuổi thọ vệt (tick)
    public double reach = 3.2;     // độ dài vệt tính từ người chơi
    public float sweep = 130f;     // góc quét ngang (độ)
    public float opacity = 0.8f;   // 0..1

    private static final int SUBSTEPS = 4;
    private static final double INNER = 0.7;
    private static final float TILT_DEG = 25f;

    private static class Point {
        final Vec3 inner, outer;
        final float hue;
        int age = 0;
        Point(Vec3 i, Vec3 o, float h) { inner = i; outer = o; hue = h; }
    }

    private final List<List<Point>> trails = new ArrayList<List<Point>>();
    private List<Point> current;

    public void clear() {
        trails.clear();
        current = null;
    }

    /** Gọi mỗi client tick. active = false thì chỉ làm mờ dần các vệt cũ. */
    public void tick(boolean active) {
        for (Iterator<List<Point>> it = trails.iterator(); it.hasNext(); ) {
            List<Point> t = it.next();
            for (Iterator<Point> pi = t.iterator(); pi.hasNext(); ) {
                if (++pi.next().age > length) pi.remove();
            }
            if (t.isEmpty()) it.remove();
        }
        if (!active) { current = null; return; }

        EntityPlayer pl = Minecraft.getMinecraft().thePlayer;
        if (pl == null) { current = null; return; }
        ItemStack held = pl.getHeldItem();
        boolean swinging = held != null && held.getItem() instanceof ItemSword
                && pl.isSwingInProgress && pl.swingProgress > 0f;
        if (!swinging) { current = null; return; }

        if (current == null) {
            current = new ArrayList<Point>();
            trails.add(current);
        }
        float cur = pl.swingProgress, prev = pl.prevSwingProgress;
        if (cur > prev && !current.isEmpty()) {
            for (int i = 1; i <= SUBSTEPS; i++) addSample(pl, prev + (cur - prev) * i / SUBSTEPS);
        } else {
            addSample(pl, cur);
        }
    }

    private void addSample(EntityPlayer pl, float progress) {
        float yaw = (float) Math.toRadians(pl.rotationYaw + (0.5f - progress) * sweep);
        float pitch = (float) Math.toRadians(pl.rotationPitch + (0.5f - progress) * TILT_DEG);
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);

        Vec3 origin = new Vec3(pl.posX, pl.posY + pl.getEyeHeight() * 0.8, pl.posZ);
        Vec3 inner = origin.addVector(dx * INNER, dy * INNER, dz * INNER);
        Vec3 outer = origin.addVector(dx * reach, dy * reach, dz * reach);
        float hue = ((System.currentTimeMillis() % 2500L) / 2500f + progress * 0.25f) % 1f;
        current.add(new Point(inner, outer, hue));
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private float[] colorFor(Point p, float fresh) {
        if (colorMode == RAINBOW) return rgb(Color.HSBtoRGB(p.hue, 0.75f, 1f) & 0xFFFFFF);
        float[] a = rgb(color);
        if (colorMode == GRADIENT) {
            float[] b = rgb(color2);
            return new float[]{
                    b[0] + (a[0] - b[0]) * fresh,
                    b[1] + (a[1] - b[1]) * fresh,
                    b[2] + (a[2] - b[2]) * fresh};
        }
        return a;
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        if (trails.isEmpty()) return;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return;

        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        int max = Math.max(1, length);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        for (List<Point> t : trails) {
            if (t.size() < 2) continue;

            // Thân vệt
            wr.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (Point p : t) {
                float f = Math.max(0f, 1f - (p.age + pt) / max);
                float[] c = colorFor(p, f);
                wr.pos(p.inner.xCoord - cx, p.inner.yCoord - cy, p.inner.zCoord - cz)
                        .color(c[0], c[1], c[2], f * 0.05f * opacity).endVertex();
                wr.pos(p.outer.xCoord - cx, p.outer.yCoord - cy, p.outer.zCoord - cz)
                        .color(c[0], c[1], c[2], f * 0.6f * opacity).endVertex();
            }
            tess.draw();

            // Mép sáng ở mũi kiếm
            wr.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (Point p : t) {
                float f = Math.max(0f, 1f - (p.age + pt) / max);
                float[] c = colorFor(p, f);
                double ix = p.outer.xCoord + (p.inner.xCoord - p.outer.xCoord) * 0.12;
                double iy = p.outer.yCoord + (p.inner.yCoord - p.outer.yCoord) * 0.12;
                double iz = p.outer.zCoord + (p.inner.zCoord - p.outer.zCoord) * 0.12;
                wr.pos(ix - cx, iy - cy, iz - cz).color(c[0], c[1], c[2], f * 0.7f * opacity).endVertex();
                wr.pos(p.outer.xCoord - cx, p.outer.yCoord - cy, p.outer.zCoord - cz)
                        .color(1f, 1f, 1f, f * 0.9f * opacity).endVertex();
            }
            tess.draw();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.popMatrix();
    }
}
