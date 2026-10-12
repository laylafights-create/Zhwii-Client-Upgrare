package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Bow Trajectory: vẽ đường bay và điểm rơi dự đoán của mũi tên khi đang giương cung
 * (cùng công thức vật lý của EntityArrow 1.8.9). Chỉ HIỂN THỊ, không điều khiển camera / chuột.
 */
public class BowTrajectoryModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFF));
    private final ColorSetting hitColor = add(new ColorSetting("hit_color", "Hit Color", 0xFF5555));
    private final SliderSetting width = add(new SliderSetting("width", "Line Width", 2.0, 1.0, 6.0, 0.5, "px"));
    private final SliderSetting maxTicks = add(new SliderSetting("max_ticks", "Max Flight Time", 120, 40, 200, 10, " tick"));
    private final BoolSetting onlyDrawing = add(new BoolSetting("only_drawing", "Only When Drawing", true));
    private final BoolSetting marker = add(new BoolSetting("marker", "Landing Marker", true));
    private final BoolSetting highlight = add(new BoolSetting("highlight", "Highlight Target", true));
    private final BoolSetting throughWalls = add(new BoolSetting("through_walls", "Through Walls", false));

    public BowTrajectoryModule() {
        super("bowtrajectory", "Bow Trajectory", "Hiện đường bay và điểm rơi của mũi tên khi giương cung.", Category.PVP, false);
        new RainbowSettings(this);
    }

    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float pt) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer pl = mc.thePlayer;
        World world = mc.theWorld;
        Entity cam = mc.getRenderViewEntity();
        if (pl == null || world == null || cam == null) return;
        ItemStack held = pl.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBow)) return;

        // Lực kéo cung giống ItemBow.onPlayerStoppedUsing
        float power;
        if (pl.isUsingItem()) {
            int charge = held.getMaxItemUseDuration() - pl.getItemInUseCount();
            float f = charge / 20f;
            f = (f * f + f * 2f) / 3f;
            if (f < 0.1f) return;
            power = Math.min(f, 1f);
        } else if (onlyDrawing.value) {
            return;
        } else {
            power = 1f;
        }

        // Vị trí / hướng bắn giống constructor EntityArrow(world, shooter, velocity)
        double yr = Math.toRadians(lerp(pl.prevRotationYaw, pl.rotationYaw, pt));
        double pr = Math.toRadians(lerp(pl.prevRotationPitch, pl.rotationPitch, pt));
        double x = lerp(pl.prevPosX, pl.posX, pt) - Math.cos(yr) * 0.16;
        double y = lerp(pl.prevPosY, pl.posY, pt) + pl.getEyeHeight() - 0.10000000149011612;
        double z = lerp(pl.prevPosZ, pl.posZ, pt) - Math.sin(yr) * 0.16;
        double mx = -Math.sin(yr) * Math.cos(pr);
        double my = -Math.sin(pr);
        double mz = Math.cos(yr) * Math.cos(pr);
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        double speed = power * 3.0;
        mx = mx / len * speed;
        my = my / len * speed;
        mz = mz / len * speed;

        List<Vec3> pts = new ArrayList<Vec3>();
        pts.add(new Vec3(x, y, z));
        Entity hitEntity = null;
        Vec3 landing = null;
        int ticks = maxTicks.getInt();
        for (int i = 0; i < ticks; i++) {
            Vec3 from = new Vec3(x, y, z);
            Vec3 to = new Vec3(x + mx, y + my, z + mz);
            MovingObjectPosition blockHit = world.rayTraceBlocks(from, to, false, true, false);
            Vec3 end = blockHit != null ? blockHit.hitVec : to;

            double best = from.distanceTo(end);
            Entity found = null;
            Vec3 foundVec = null;
            AxisAlignedBB sweep = new AxisAlignedBB(x, y, z, x + mx, y + my, z + mz).expand(1.0, 1.0, 1.0);
            List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(pl, sweep);
            for (Entity en : list) {
                if (!en.canBeCollidedWith()) continue;
                AxisAlignedBB bb = en.getEntityBoundingBox().expand(0.3, 0.3, 0.3);
                MovingObjectPosition ip = bb.calculateIntercept(from, end);
                if (ip != null) {
                    double d = from.distanceTo(ip.hitVec);
                    if (d <= best) { best = d; found = en; foundVec = ip.hitVec; }
                }
            }
            if (found != null) {
                hitEntity = found;
                landing = foundVec;
                pts.add(foundVec);
                break;
            }
            if (blockHit != null) {
                landing = blockHit.hitVec;
                pts.add(blockHit.hitVec);
                break;
            }
            x += mx; y += my; z += mz;
            pts.add(new Vec3(x, y, z));
            // EntityArrow.onUpdate: cản không khí 0.99, trọng lực 0.05
            mx *= 0.99; my *= 0.99; mz *= 0.99;
            my -= 0.05;
            if (y < -64) break;
        }

        boolean hitsTarget = hitEntity != null && highlight.value;
        int rgb = hitsTarget ? hitColor.rgb() : (rainbow != null ? rainbow.resolve(color.rgb(), 0f) & 0xFFFFFF : color.rgb());
        draw(pts, landing, hitsTarget ? hitEntity : null, rgb, cam, pt);
    }

    private void draw(List<Vec3> pts, Vec3 landing, Entity target, int rgb, Entity cam, float pt) {
        double cx = lerp(cam.lastTickPosX, cam.posX, pt);
        double cy = lerp(cam.lastTickPosY, cam.posY, pt);
        double cz = lerp(cam.lastTickPosZ, cam.posZ, pt);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        boolean wasFog = GL11.glIsEnabled(GL11.GL_FOG);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableFog();
        GlStateManager.depthMask(false);
        if (throughWalls.value) GlStateManager.disableDepth();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(width.getFloat());

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(3, DefaultVertexFormats.POSITION_COLOR); // GL_LINE_STRIP
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            Vec3 v = pts.get(i);
            int a = 230 - (int) (110f * i / Math.max(1, n - 1)); // mờ dần về cuối
            wr.pos(v.xCoord - cx, v.yCoord - cy, v.zCoord - cz).color(r, g, b, a).endVertex();
        }
        tess.draw();

        if (marker.value && landing != null) {
            double s = 0.18;
            drawBox(new AxisAlignedBB(landing.xCoord - s, landing.yCoord - s, landing.zCoord - s,
                    landing.xCoord + s, landing.yCoord + s, landing.zCoord + s).offset(-cx, -cy, -cz), r, g, b, 255);
        }
        if (target != null) {
            drawBox(target.getEntityBoundingBox().offset(-cx, -cy, -cz), r, g, b, 255);
        }

        GL11.glLineWidth(1f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        if (throughWalls.value) GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        if (wasFog) GlStateManager.enableFog();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawBox(AxisAlignedBB bb, int r, int g, int b, int a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(1, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                corner(wr, bb, i, r, g, b, a);
                corner(wr, bb, i | bit, r, g, b, a);
            }
        }
        tess.draw();
    }

    private void corner(WorldRenderer wr, AxisAlignedBB bb, int c, int r, int g, int b, int a) {
        wr.pos((c & 1) == 0 ? bb.minX : bb.maxX,
               ((c >> 1) & 1) == 0 ? bb.minY : bb.maxY,
               ((c >> 2) & 1) == 0 ? bb.minZ : bb.maxZ).color(r, g, b, a).endVertex();
    }
}
