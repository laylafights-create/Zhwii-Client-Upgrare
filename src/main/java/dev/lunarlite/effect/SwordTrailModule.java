package com.example.swordtrail;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sword Trail module (Forge 1.8.9) - original implementation.
 *  K = bật/tắt vệt sáng
 *  J = đổi chế độ màu (Xanh -> Cầu vồng -> Gradient xanh-tím)
 */
@Mod(modid = SwordTrailModule.MODID, version = "1.1", acceptedMinecraftVersions = "[1.8.9]")
public class SwordTrailModule {
    public static final String MODID = "swordtrailmodule";

    // ===== Cấu hình =====
    private static final int MAX_AGE = 12;       // tuổi thọ vệt (tick)
    private static final int SUBSTEPS = 4;       // số mẫu nội suy mỗi tick (càng cao càng mượt)
    private static final double INNER = 0.7;     // gần tay
    private static final double OUTER = 3.2;     // mũi kiếm
    private static final float SWEEP_DEG = 130f; // góc quét ngang
    private static final float TILT_DEG = 25f;   // độ nghiêng dọc của cung chém

    private static final String[] MODE_NAMES = {"Xanh", "Cau vong", "Gradient xanh-tim"};

    private static class Point {
        final Vec3 inner, outer;
        final float hue; // dùng cho chế độ cầu vồng
        int age = 0;
        Point(Vec3 i, Vec3 o, float h) { inner = i; outer = o; hue = h; }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<List<Point>> trails = new ArrayList<List<Point>>();
    private List<Point> current = null;

    private boolean enabled = true;
    private int mode = 0;

    private KeyBinding keyToggle;
    private KeyBinding keyMode;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        keyToggle = new KeyBinding("Bat/tat vet sang", Keyboard.KEY_K, "Sword Trail");
        keyMode = new KeyBinding("Doi che do mau", Keyboard.KEY_J, "Sword Trail");
        ClientRegistry.registerKeyBinding(keyToggle);
        ClientRegistry.registerKeyBinding(keyMode);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void say(String s) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[SwordTrail] " + s));
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (keyToggle.isPressed()) {
            enabled = !enabled;
            if (!enabled) { trails.clear(); current = null; }
            say(enabled ? "BAT" : "TAT");
        }
        if (keyMode.isPressed()) {
            mode = (mode + 1) % MODE_NAMES.length;
            say("Che do: " + MODE_NAMES[mode]);
        }
    }

    private void addSample(EntityPlayer pl, float progress) {
        float offset = (0.5f - progress) * SWEEP_DEG;
        float yaw = (float) Math.toRadians(pl.rotationYaw + offset);
        // cung chém hơi chéo: đầu cung cao, cuối cung thấp
        float tilt = (0.5f - progress) * TILT_DEG;
        float pitch = (float) Math.toRadians(pl.rotationPitch + tilt);

        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);

        Vec3 origin = new Vec3(pl.posX, pl.posY + pl.getEyeHeight() * 0.8, pl.posZ);
        Vec3 inner = origin.addVector(dx * INNER, dy * INNER, dz * INNER);
        Vec3 outer = origin.addVector(dx * OUTER, dy * OUTER, dz * OUTER);
        float hue = ((System.currentTimeMillis() % 2500L) / 2500f + progress * 0.25f) % 1f;
        current.add(new Point(inner, outer, hue));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || mc.thePlayer == null) return;

        for (Iterator<List<Point>> it = trails.iterator(); it.hasNext(); ) {
            List<Point> t = it.next();
            for (Iterator<Point> pi = t.iterator(); pi.hasNext(); ) {
                if (++pi.next().age > MAX_AGE) pi.remove();
            }
            if (t.isEmpty()) it.remove();
        }

        if (!enabled) return;

        EntityPlayer pl = mc.thePlayer;
        ItemStack held = pl.getHeldItem();
        boolean swinging = held != null && held.getItem() instanceof ItemSword
                && pl.isSwingInProgress && pl.swingProgress > 0f;
        if (!swinging) { current = null; return; }

        if (current == null) {
            current = new ArrayList<Point>();
            trails.add(current);
        }

        float cur = pl.swingProgress;
        float prev = pl.prevSwingProgress;
        if (cur > prev && current.size() > 0) {
            for (int i = 1; i <= SUBSTEPS; i++) {
                addSample(pl, prev + (cur - prev) * i / SUBSTEPS);
            }
        } else {
            addSample(pl, cur);
        }
    }

    private float[] colorFor(Point p, float freshness) {
        switch (mode) {
            case 1: {
                int rgb = Color.HSBtoRGB(p.hue, 0.75f, 1f);
                return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
            }
            case 2: {
                // freshness 1 = mới (xanh), 0 = cũ (tím)
                float r = 0.25f + (1f - freshness) * 0.6f;
                float g = 0.65f - (1f - freshness) * 0.4f;
                return new float[]{r, g, 1f};
            }
            default:
                return new float[]{0.3f, 0.8f, 1f};
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (trails.isEmpty()) return;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;

        float pt = e.partialTicks;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

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

            // Lớp 1: thân vệt (mờ, rộng)
            wr.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (Point p : t) {
                float f = Math.max(0f, 1f - (p.age + pt) / MAX_AGE);
                float[] c = colorFor(p, f);
                wr.pos(p.inner.xCoord - cx, p.inner.yCoord - cy, p.inner.zCoord - cz)
                        .color(c[0], c[1], c[2], f * 0.05f).endVertex();
                wr.pos(p.outer.xCoord - cx, p.outer.yCoord - cy, p.outer.zCoord - cz)
                        .color(c[0], c[1], c[2], f * 0.55f).endVertex();
            }
            tess.draw();

            // Lớp 2: mép sáng ở mũi kiếm (mỏng, rực)
            wr.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (Point p : t) {
                float f = Math.max(0f, 1f - (p.age + pt) / MAX_AGE);
                float[] c = colorFor(p, f);
                double ix = p.outer.xCoord + (p.inner.xCoord - p.outer.xCoord) * 0.12;
                double iy = p.outer.yCoord + (p.inner.yCoord - p.outer.yCoord) * 0.12;
                double iz = p.outer.zCoord + (p.inner.zCoord - p.outer.zCoord) * 0.12;
                wr.pos(ix - cx, iy - cy, iz - cz).color(c[0], c[1], c[2], f * 0.7f).endVertex();
                wr.pos(p.outer.xCoord - cx, p.outer.yCoord - cy, p.outer.zCoord - cz)
                        .color(1f, 1f, 1f, f * 0.9f).endVertex();
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
