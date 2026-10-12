package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import org.lwjgl.opengl.GL11;

/**
 * Viền block đang nhìn vào đổi màu cầu vồng (port từ mod "Rainbow Block Outline"):
 * Solid (cả khung cùng một màu), Gradient (mỗi góc một màu), Pulse (nhấp nháy), Custom (màu RGB tự chọn).
 */
public class BlockOutlineModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", 1, "Solid", "Gradient", "Pulse", "Custom"));
    private final SliderSetting width = add(new SliderSetting("width", "Line Width", 3.0, 1.0, 10.0, 0.5, ""));
    private final SliderSetting brightness = add(new SliderSetting("brightness", "Brightness", 1.0, 0.2, 1.0, 0.05, ""));
    private final SliderSetting speed = add(new SliderSetting("speed", "Speed", 1.0, 0.1, 5.0, 0.1, "x"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 100, 10, 100, 5, "%"));
    private final BoolSetting throughWalls = add(new BoolSetting("through_walls", "Through Walls", false));
    private final SliderSetting red = add(new SliderSetting("custom_red", "Custom Red", 255, 0, 255, 5, ""));
    private final SliderSetting green = add(new SliderSetting("custom_green", "Custom Green", 255, 0, 255, 5, ""));
    private final SliderSetting blue = add(new SliderSetting("custom_blue", "Custom Blue", 255, 0, 255, 5, ""));

    public BlockOutlineModule() {
        super("blockoutline", "Block Outline", "Viền block đang nhìn vào đổi màu cầu vồng.", Category.VISUAL, true);
    }

    /** Gọi từ sự kiện vẽ viền block của Forge; huỷ viền gốc và tự vẽ viền mới. */
    public void onHighlight(DrawBlockHighlightEvent e) {
        if (!enabled) return;
        MovingObjectPosition t = e.target;
        if (t == null || t.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        World w = Minecraft.getMinecraft().theWorld;
        EntityPlayer p = e.player;
        if (w == null || p == null) return;
        BlockPos pos = t.getBlockPos();
        Block block = w.getBlockState(pos).getBlock();
        if (block.getMaterial() == Material.air || !w.getWorldBorder().contains(pos)) return;

        e.setCanceled(true);
        block.setBlockBoundsBasedOnState(w, pos);
        float pt = e.partialTicks;
        double dx = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double dy = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double dz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        AxisAlignedBB bb = block.getSelectedBoundingBox(w, pos).expand(0.002D, 0.002D, 0.002D).offset(-dx, -dy, -dz);
        draw(bb);
    }

    /** Màu (ARGB) của một góc; corner là 3 bit x/y/z (0 hoặc 1). */
    private int cornerColor(int corner) {
        double now = (System.currentTimeMillis() % 400000L) / 4000.0 * speed.value;
        float bri = brightness.getFloat();
        float alpha = opacity.getFloat() / 100f;
        int rgb;
        if (style.is("Custom")) {
            rgb = ((int) (red.value * bri) << 16) | ((int) (green.value * bri) << 8) | (int) (blue.value * bri);
        } else {
            double h = now;
            if (style.is("Gradient")) h -= ((corner & 1) + ((corner >> 1) & 1) + ((corner >> 2) & 1)) * 0.18;
            h -= Math.floor(h);
            rgb = RainbowSettings.hsb((float) h, 1f, bri) & 0xFFFFFF;
        }
        if (style.is("Pulse")) {
            double s = Math.sin(System.currentTimeMillis() / 1000.0 * speed.value * 4.0);
            alpha *= 0.35f + 0.65f * (0.5f + 0.5f * (float) s);
        }
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | rgb;
    }

    private void vertex(WorldRenderer wr, AxisAlignedBB bb, int corner) {
        int c = cornerColor(corner);
        wr.pos((corner & 1) == 0 ? bb.minX : bb.maxX,
               ((corner >> 1) & 1) == 0 ? bb.minY : bb.maxY,
               ((corner >> 2) & 1) == 0 ? bb.minZ : bb.maxZ)
          .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    private void draw(AxisAlignedBB bb) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        if (throughWalls.value) GlStateManager.disableDepth();
        GL11.glLineWidth(width.getFloat());

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(1, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                vertex(wr, bb, i);
                vertex(wr, bb, i | bit);
            }
        }
        tess.draw();

        GL11.glLineWidth(1f);
        if (throughWalls.value) GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
