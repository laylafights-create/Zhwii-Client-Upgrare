package com.example.cleaninv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Forge 1.8.9 - Bỏ nền tối + animation phóng to khi mở kho đồ / rương.
 * Cách hoạt động: thay GuiInventory / GuiChest gốc bằng bản con
 * ghi đè drawDefaultBackground() (bỏ nền tối) và drawScreen() (animation).
 */
@Mod(modid = "cleaninv", name = "CleanInv", version = "1.0", clientSideOnly = true)
public class CleanInv {

    // Chỉnh ở đây
    static final float DURATION_MS = 150f; // thời gian animation
    static final float START_SCALE = 0.85f; // phóng to từ 85% lên 100%

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (e.gui == null) return;
        Minecraft mc = Minecraft.getMinecraft();

        if (e.gui.getClass() == GuiInventory.class) {
            e.gui = new AnimInventory(mc.thePlayer);
        } else if (e.gui.getClass() == GuiChest.class) {
            ContainerChest c = (ContainerChest) ((GuiChest) e.gui).inventorySlots;
            e.gui = new AnimChest(mc.thePlayer.inventory, c.getLowerChestInventory());
        }
    }

    // ---------- helper ----------
    static float ease(long start) {
        float t = Math.min(1f, (System.currentTimeMillis() - start) / DURATION_MS);
        float inv = 1f - t;
        return 1f - inv * inv * inv; // ease-out cubic
    }

    // ---------- Kho đồ ----------
    public static class AnimInventory extends GuiInventory {
        private final long start = System.currentTimeMillis();

        public AnimInventory(EntityPlayer p) {
            super(p);
        }

        @Override
        public void drawDefaultBackground() {
            // để trống = không vẽ nền tối
        }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            float s = START_SCALE + (1f - START_SCALE) * ease(start);
            float cx = width / 2f, cy = height / 2f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.scale(s, s, 1f);
            GlStateManager.translate(-cx, -cy, 0);
            super.drawScreen((int) ((mx - cx) / s + cx), (int) ((my - cy) / s + cy), pt);
            GlStateManager.popMatrix();
        }
    }

    // ---------- Rương ----------
    public static class AnimChest extends GuiChest {
        private final long start = System.currentTimeMillis();

        public AnimChest(IInventory upper, IInventory lower) {
            super(upper, lower);
        }

        @Override
        public void drawDefaultBackground() {
        }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            float s = START_SCALE + (1f - START_SCALE) * ease(start);
            float cx = width / 2f, cy = height / 2f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.scale(s, s, 1f);
            GlStateManager.translate(-cx, -cy, 0);
            super.drawScreen((int) ((mx - cx) / s + cx), (int) ((my - cy) / s + cy), pt);
            GlStateManager.popMatrix();
        }
    }
}
