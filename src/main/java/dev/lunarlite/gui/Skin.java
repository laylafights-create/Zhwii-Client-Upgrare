package dev.lunarlite.gui;

import net.minecraft.client.gui.FontRenderer;

/**
 * Giao diện chung (menu + màn nhập/xuất): nền tối, ô bo góc, một màu nhấn lấy từ {@link Draw#ACCENT}.
 * Mọi hàm chỉ dùng {@link Draw#rrect} nên cùng một phong cách bo góc ở mọi nơi.
 */
public final class Skin {
    private Skin() {}

    // ---- bảng màu (tất cả ARGB)
    public static final int BG = 0xF2101016;      // nền khung chính
    public static final int RAISED = 0xFF181822;  // ô nổi (thẻ, tab, nút)
    public static final int HOVER = 0xFF222230;   // ô khi rê chuột
    public static final int FIELD = 0xFF12121A;   // ô lõm (giá trị, rãnh)
    public static final int STROKE = 0xFF2C2C3C;  // viền mảnh
    public static final int TEXT = 0xFFF1F1F6;
    public static final int SOFT = 0xFFC4C4D4;
    public static final int DIM = 0xFF8E8EA8;
    public static final int GREEN = 0xFF4ADE80;
    public static final int RED = 0xFFFF6B7A;

    public static final int NORMAL = 0, PRIMARY = 1, DANGER = 2;

    /** Hộp bo góc có viền 1px: vẽ viền trước rồi vẽ nền thụt vào 1px. */
    public static void box(int x, int y, int w, int h, int r, int border, int fill) {
        Draw.rrect(x, y, x + w, y + h, r, border);
        Draw.rrect(x + 1, y + 1, x + w - 1, y + h - 1, Math.max(0, r - 1), fill);
    }

    /** Hàng tuỳ chỉnh: nền nổi nhẹ, sáng lên khi rê chuột. */
    public static void row(int x, int y, int w, int h, float hov) {
        Draw.rrect(x, y, x + w, y + h, 4, Draw.lerp(0xFF16161F, 0xFF1F1F2C, hov));
    }

    /** Bóng đổ mềm quanh khung có kích thước w x h đặt tại (0,0). */
    public static void shadow(int w, int h) {
        for (int i = 6; i >= 1; i--) {
            Draw.rrect(-i, -i + 2, w + i, h + i + 2, 7 + i, 0x08000000);
        }
    }

    /** Màu chữ / núm dễ đọc khi nằm trên nền màu nhấn (tối nếu nhấn sáng, trắng nếu nhấn tối). */
    public static int onAccent() {
        int c = Draw.ACCENT;
        int lum = (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
        return lum > 165 ? 0xFF10101A : 0xFFFFFFFF;
    }

    /** Ô vuông bo góc chứa biểu tượng pixel 24x24 (size >= 28). */
    public static void tile(int x, int y, int size, String icon, int color) {
        int c = color | 0xFF000000;
        box(x, y, size, size, 6, Draw.lerp(STROKE, c, 0.55f), Draw.lerp(RAISED, c, 0.16f));
        Icons.draw(icon, x + (size - 24) / 2, y + (size - 24) / 2, 3, c);
    }

    /** Nút bo góc; {@code hov} 0..1 là mức rê chuột (đã làm mượt bởi nơi gọi). */
    public static void button(FontRenderer fr, int x, int y, int w, int h, String label, float hov, int style) {
        int acc = Draw.ACCENT;
        int border, fill, tc;
        boolean shadow = true;
        if (style == PRIMARY) {
            fill = Draw.lerp(acc, 0xFFFFFFFF, hov * 0.2f);
            border = fill;
            tc = onAccent();
            shadow = false;
        } else if (style == DANGER) {
            border = Draw.lerp(STROKE, RED, hov);
            fill = Draw.lerp(RAISED, 0xFF2A1A20, hov);
            tc = Draw.lerp(0xFFD98B93, RED, hov);
        } else {
            border = Draw.lerp(STROKE, acc, hov);
            fill = Draw.lerp(RAISED, Draw.lerp(RAISED, acc, 0.28f), hov);
            tc = Draw.lerp(SOFT, 0xFFFFFFFF, hov);
        }
        box(x, y, w, h, 4, border, fill);
        String t = fr.trimStringToWidth(label, w - 10);
        fr.drawString(t, x + (w - fr.getStringWidth(t)) / 2f, y + (h - 8) / 2, tc, shadow);
    }
}
