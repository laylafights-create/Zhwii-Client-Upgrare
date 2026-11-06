package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

/**
 * Bảng màu + hàm vẽ dùng chung cho các màn hình phụ (nhập / xuất, sửa HUD):
 * kính trắng mờ, chữ navy đậm, màu nhấn xanh dương - đồng bộ với {@link MenuScreen}.
 */
public final class Glass {
    private Glass() {}

    public static final int INK = 0xFF13233A;
    public static final int INK2 = 0xFF44546A;
    public static final int INK3 = 0xFF7A8798;
    public static final int BLUE = 0xFF2F7DF6;
    public static final int BLUE_HI = 0xFF5A9BFF;
    public static final int BLUE_DK = 0xFF1F5FC4;
    public static final int BLUE_RING = 0xFFA9CCFF;
    public static final int RED = 0xFFD9404B;
    public static final int GREEN = 0xFF1F9D63;

    public static final int PANEL = 0x66FFFFFF;
    public static final int CARD = 0xB8FFFFFF;
    public static final int HOV = 0xDCFFFFFF;
    public static final int EDGE = 0xF2FFFFFF;
    public static final int SHADE = 0x22000000;
    public static final int TINT = 0x1A0B2A5A;
    public static final int TINT_HOV = 0x340B2A5A;

    public static final int NORMAL = 0, PRIMARY = 1, DANGER = 2;

    /** Ô kính trắng mờ: nền trong suốt, viền sáng 1px, vệt sáng mép trên, bóng nhẹ mép dưới. */
    public static void glass(int x, int y, int w, int h, int r, int fill, int edge) {
        Draw.rrect(x, y, x + w, y + h, r, fill);
        Draw.ring(x, y, x + w, y + h, edge);
        if (w > 12 && h > 8) Gui.drawRect(x + 4, y + 1, x + w - 4, y + 2, 0x55FFFFFF);
        if (w > 12) Gui.drawRect(x + 3, y + h, x + w - 3, y + h + 1, SHADE);
    }

    /** Nút: PRIMARY = nền xanh chữ trắng, DANGER = chữ đỏ, NORMAL = kính xám xanh nhạt. */
    public static void button(FontRenderer fr, int x, int y, int w, int h, String label, float hov, int style) {
        int tc;
        if (style == PRIMARY) {
            Draw.rrect(x, y, x + w, y + h, 3, Draw.lerp(BLUE, BLUE_HI, hov));
            Draw.ring(x, y, x + w, y + h, BLUE_RING);
            Gui.drawRect(x + 3, y + 1, x + w - 3, y + 2, 0x66FFFFFF);
            tc = 0xFFFFFFFF;
        } else {
            glass(x, y, w, h, 3, Draw.lerp(TINT, TINT_HOV, hov), EDGE);
            tc = style == DANGER ? RED : INK;
        }
        String t = fr.trimStringToWidth(label, w - 8);
        fr.drawString(t, x + (w - fr.getStringWidth(t)) / 2f, y + (h - 8) / 2f, tc, false);
    }

    /** Vẽ một vật phẩm / block Minecraft tại (x, y), cạnh 16 * s điểm ảnh. */
    public static void item(Minecraft mc, ItemStack st, float x, float y, float s) {
        if (st == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(st, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
