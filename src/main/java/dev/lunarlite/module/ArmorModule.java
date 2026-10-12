package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class ArmorModule extends HudModule {
    private static final int ROW = 18;

    private final BoolSetting showHeld = add(new BoolSetting("show_held", "Show Held Item", true));
    private final ModeSetting durability = add(new ModeSetting("durability", "Durability", 0, "Number", "Percent", "Off"));
    private final ModeSetting durColor = add(new ModeSetting("dur_color", "Durability Color", 0, "Gradient", "Custom"));

    public ArmorModule() {
        super("armor", "Armor Status", "Giáp đang mặc và vật phẩm cầm trên tay, kèm độ bền.", Category.PVP, true, 400, 4);
    }

    private int slots() { return showHeld.value ? 5 : 4; }

    @Override public int getWidth(boolean preview) { return durability.is("Off") ? 20 : 20 + 34; }
    @Override public int getHeight(boolean preview) { return ROW * slots() - 2; }

    private ItemStack[] stacks(boolean preview) {
        ItemStack[] out = new ItemStack[5];
        if (preview) {
            out[0] = new ItemStack(Items.diamond_sword);
            out[1] = new ItemStack(Items.diamond_helmet);
            out[2] = new ItemStack(Items.diamond_chestplate);
            out[3] = new ItemStack(Items.diamond_leggings);
            out[4] = new ItemStack(Items.diamond_boots);
            return out;
        }
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return out;
        out[0] = p.getHeldItem();
        for (int i = 0; i < 4; i++) out[i + 1] = p.inventory.armorInventory[3 - i];
        return out;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack[] items = stacks(preview);
        drawBox(x - 2, y - 2, getWidth(preview) + 4, getHeight(preview) + 4);
        int row = 0;
        for (int i = showHeld.value ? 0 : 1; i < items.length; i++, row++) {
            ItemStack s = items[i];
            if (s == null) continue;
            int iy = y + row * ROW;
            Draw.rrect(x - 1, iy - 1, x + getWidth(preview) + 1, iy + 16, 3, 0x1AFFFFFF);

            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(s, x, iy);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            GlStateManager.disableLighting();

            String text = null;
            float frac = 1f;
            if (s.isItemStackDamageable() && !durability.is("Off")) {
                int left = s.getMaxDamage() - s.getItemDamage();
                frac = Math.max(0f, (float) left / s.getMaxDamage());
                text = durability.is("Percent") ? Math.round(frac * 100f) + "%" : String.valueOf(left);
            } else if (!s.isItemStackDamageable() && s.stackSize > 1) {
                text = String.valueOf(s.stackSize);
            }
            if (text != null) {
                int base = durColor.is("Gradient") && s.isItemStackDamageable()
                        ? (java.awt.Color.HSBtoRGB(frac / 3f, 1f, 1f) & 0xFFFFFF) : color.rgb();
                boolean dmg = s.isItemStackDamageable() && !durability.is("Off");
                rainbow.drawString(mc.fontRendererObj, text, x + 20, iy + (dmg ? 2 : 5), base, shadow.value, iy);
                if (dmg) {
                    int bc = durColor.is("Gradient") ? Draw.lerp(0xFFFF5C5C, 0xFF5CE08A, frac) : (0xFF000000 | color.rgb());
                    Draw.hbar(x + 20, iy + 12, 30, 2, frac, bc, 0x66000000);
                }
            }
        }
    }
}
