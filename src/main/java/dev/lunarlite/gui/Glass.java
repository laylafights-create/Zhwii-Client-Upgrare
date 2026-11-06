package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

/**
 * Bảng màu + hàm vẽ dùng chung cho các màn hình phụ (nhập / xuất, sửa HUD):
 * nền tối kiểu pixel, viền đôi cắt góc, màu nhấn cam - đồng bộ với {@link MenuScreen}.
 * (Giữ tên hằng số cũ như BLUE / CARD để mã gọi không phải đổi; giá trị là bảng màu mới.)
 */
public final class Glass {
    private Glass() {}

    public static final int INK = 0xFFE8EDF5;
    public static final int INK2 = 0xFFA3AFC0;
    public static final int INK3 = 0xFF6B7788;
    /** Màu nhấn (tên cũ BLUE): cam. */
    public static final int BLUE = 0xFFFFA21F;
    public static final int BLUE_HI = 0xFFFFC266;
    public static final int BLUE_DK = 0xFFE0850A;
    public static final int BLUE_RING = 0xFFFFC266;
    public static final int RED = 0xFFFF5A64;
    public static final int GREEN = 0xFF2FD487;

    public static final int PANEL = 0xE2121A22;
    public static final int CARD = 0xB0182029;
    public static final int HOV = 0xD02A3442;
    public static final int EDGE = 0xFF3A4452;
    public static final int EDGE_HI = 0xFF6B7A8E;
    public static final int SHADE = 0x00000000;
    public static final int TINT = CARD;
    public static final int TINT_HOV = HOV;
    /** Nền tối pha màu nhấn khi nút / hàng đang bật. */
    public static final int ON_FILL = 0xE0382400;
    public static final int ON_FILL_HI = 0xE0583800;

    public static final int NORMAL = 0, PRIMARY = 1, DANGER = 2;

    /** Nền tối pha màu {@code col} (k = 0..1), dùng cho ô đang bật. */
    public static int tint(int col, float k) {
        return Draw.lerp(0xE0101820, 0xFF000000 | col, k);
    }

    /** Hộp pixel: nền, viền ngoài 1px cắt góc, viền trong mờ, quầng sáng ngoài nếu glowA > 0. */
    public static void pbox(int x, int y, int w, int h, int fill, int edge, int glowA) {
        if (w < 4 || h < 4) return;
        if (glowA > 0) {
            int rgb = edge & 0xFFFFFF;
            Draw.outline(x - 1, y - 1, x + w + 1, y + h + 1, (glowA << 24) | rgb);
            Draw.outline(x - 2, y - 2, x + w + 2, y + h + 2, ((glowA / 2) << 24) | rgb);
        }
        Gui.drawRect(x + 1, y, x + w - 1, y + h, fill);
        Gui.drawRect(x, y + 1, x + 1, y + h - 1, fill);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, fill);
        Draw.ring(x, y, x + w, y + h, edge);
        if (w > 8 && h > 8) Draw.ring(x + 1, y + 1, x + w - 1, y + h - 1, Draw.mulAlpha(edge, 0.35f));
    }

    /** Tên cũ của {@link #pbox}: {@code r} bị bỏ qua (pixel không bo góc). */
    public static void glass(int x, int y, int w, int h, int r, int fill, int edge) {
        pbox(x, y, w, h, fill, edge, 0);
    }

    /** Khung tối lớn của một màn hình + bốn ô vuông cam ở góc. */
    public static void panel(int x, int y, int w, int h) {
        pbox(x, y, w, h, PANEL, 0xFF3C4552, 0);
        Gui.drawRect(x - 3, y - 3, x + 3, y + 3, BLUE);
        Gui.drawRect(x + w - 3, y - 3, x + w + 3, y + 3, BLUE);
        Gui.drawRect(x - 3, y + h - 3, x + 3, y + h + 3, BLUE);
        Gui.drawRect(x + w - 3, y + h - 3, x + w + 3, y + h + 3, BLUE);
    }

    /** Ba vạch chấm dọc trang trí. */
    public static void dashes(int x, int cy, int color) {
        for (int k = -1; k <= 1; k++) Gui.drawRect(x, cy + k * 4 - 1, x + 1, cy + k * 4 + 1, color);
    }

    /** Công tắc pixel: núm vuông trượt, viền + nền cam khi bật (a = 0..1). */
    public static void toggle(int x, int y, float a, float hov) {
        int edge = Draw.lerp(Draw.lerp(EDGE, EDGE_HI, hov), BLUE, a);
        pbox(x, y, 28, 14, Draw.lerp(CARD, ON_FILL, a), edge, 0);
        int kx = x + 3 + Math.round(13 * a);
        Gui.drawRect(kx, y + 3, kx + 8, y + 11, Draw.lerp(0xFF8794A6, BLUE_HI, a));
    }

    /** Nút: PRIMARY = nền cam tối + viền cam phát sáng, DANGER = viền đỏ, NORMAL = xám tối. */
    public static void button(FontRenderer fr, int x, int y, int w, int h, String label, float hov, int style) {
        int tc;
        if (style == PRIMARY) {
            pbox(x, y, w, h, Draw.lerp(ON_FILL, ON_FILL_HI, hov), Draw.lerp(BLUE_DK, BLUE_HI, hov), 0x30);
            tc = 0xFFFFFFFF;
        } else if (style == DANGER) {
            pbox(x, y, w, h, Draw.lerp(0xB0301418, 0xD0481C22, hov), Draw.lerp(0xFFB03A44, RED, hov), 0);
            tc = RED;
        } else {
            pbox(x, y, w, h, Draw.lerp(CARD, HOV, hov), Draw.lerp(EDGE, EDGE_HI, hov), 0);
            tc = INK;
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
