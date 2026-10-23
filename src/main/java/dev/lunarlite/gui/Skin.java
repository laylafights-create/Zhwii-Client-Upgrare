package dev.lunarlite.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Giao diện chung kiểu Sword Art Online (menu + HUD editor + màn nhập/xuất):
 * xanh đen trong suốt kiểu hologram, viền bạc ánh xanh mảnh, vệt sáng mép trên, góc khung có "dấu ngoặc",
 * một màu nhấn (mặc định xanh SAO) lấy từ {@link Draw#ACCENT}, xanh lá như thanh HP.
 * Mọi hàm chỉ dùng {@link Draw#rrect} / {@link Gui#drawRect}.
 */
public final class Skin {
    private Skin() {}

    // ---- bảng màu (tất cả ARGB)
    public static final int BG = 0xF20B1420;      // nền khung chính
    public static final int RAISED = 0xFF142333;  // ô nổi (thẻ, tab, nút)
    public static final int HOVER = 0xFF1D3548;   // ô khi rê chuột
    public static final int FIELD = 0xFF09111A;   // ô lõm (giá trị, rãnh)
    public static final int STROKE = 0xFF397A91;  // viền mảnh
    public static final int SILVER = 0xFFB8EFFF;  // viền sáng (khung chính, thẻ)
    public static final int TEXT = 0xFFF4F5F7;
    public static final int SOFT = 0xFFC5E8F2;
    public static final int DIM = 0xFF7197A8;
    public static final int GREEN = 0xFF8DFFCF;   // xanh HP của SAO
    public static final int RED = 0xFFFF6B6B;

    public static final int NORMAL = 0, PRIMARY = 1, DANGER = 2;

    /** Hộp bo góc có viền 1px: vẽ viền trước rồi vẽ nền thụt vào 1px. */
    public static void box(int x, int y, int w, int h, int r, int border, int fill) {
        Draw.rrect(x, y, x + w, y + h, r, border);
        Draw.rrect(x + 1, y + 1, x + w - 1, y + h - 1, Math.max(0, r - 1), fill);
    }

    /** Hộp có thêm vệt sáng mảnh ở mép trong phía trên (cảm giác kính / nhựa bóng). */
    public static void glossBox(int x, int y, int w, int h, int r, int border, int fill) {
        box(x, y, w, h, r, border, fill);
        if (w > 12) Gui.drawRect(x + r + 1, y + 1, x + w - r - 1, y + 2, 0x2CFFFFFF);
    }

    /** Bốn dấu ngoặc ở góc (kiểu cửa sổ hologram của SAO). */
    public static void brackets(int x, int y, int w, int h, int len, int color) {
        int x2 = x + w, y2 = y + h;
        Gui.drawRect(x, y, x + len, y + 1, color);
        Gui.drawRect(x, y, x + 1, y + len, color);
        Gui.drawRect(x2 - len, y, x2, y + 1, color);
        Gui.drawRect(x2 - 1, y, x2, y + len, color);
        Gui.drawRect(x, y2 - 1, x + len, y2, color);
        Gui.drawRect(x, y2 - len, x + 1, y2, color);
        Gui.drawRect(x2 - len, y2 - 1, x2, y2, color);
        Gui.drawRect(x2 - 1, y2 - len, x2, y2, color);
    }

    /** Hình thoi nhỏ (r = nửa đường chéo) tại tâm (cx, cy). */
    public static void diamond(int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            Gui.drawRect(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }

    /** Hàng tuỳ chỉnh: nền xám, vạch màu nhấn bên trái và viền mờ khi rê chuột. */
    public static void row(int x, int y, int w, int h, float hov) {
        Draw.rrect(x, y, x + w, y + h, 4, Draw.lerp(0xFF101D2B, 0xFF1D3548, hov));
        Gui.drawRect(x + 1, y + h - 1, x + w - 1, y + h, Draw.lerp(0x22B8EFFF, 0x55B8EFFF, hov));
        Gui.drawRect(x, y + 3, x + 2, y + h - 3, Draw.lerp(0x00000000 | (Draw.ACCENT & 0xFFFFFF), Draw.ACCENT, hov));
    }

    /** Bóng đổ mềm quanh khung có kích thước w x h đặt tại (0,0). */
    public static void shadow(int w, int h) {
        for (int i = 7; i >= 1; i--) {
            Draw.rrect(-i, -i + 3, w + i, h + i + 3, 7 + i, 0x0A000000);
        }
    }

    /** Màu chữ / núm dễ đọc khi nằm trên nền màu nhấn (tối nếu nhấn sáng, trắng nếu nhấn tối). */
    public static int onAccent() {
        int c = Draw.ACCENT;
        int lum = (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
        return lum > 165 ? 0xFF1A1208 : 0xFFFFFFFF;
    }

    /** Ô vuông bo góc chứa biểu tượng pixel 24x24 (size >= 28). */
    public static void tile(int x, int y, int size, String icon, int color) {
        int c = color | 0xFF000000;
        glossBox(x, y, size, size, 6, Draw.lerp(SILVER, c, 0.5f), Draw.lerp(RAISED, c, 0.14f));
        Icons.draw(icon, x + (size - 24) / 2, y + (size - 24) / 2, 3, c);
    }

    /** Nút bo góc; {@code hov} 0..1 là mức rê chuột (đã làm mượt bởi nơi gọi). */
    public static void button(FontRenderer fr, int x, int y, int w, int h, String label, float hov, int style) {
        int acc = Draw.ACCENT;
        int border, fill, tc;
        boolean shadow = true;
        if (style == PRIMARY) {
            border = Draw.lerp(Draw.lerp(acc, 0xFFFFFFFF, 0.35f), 0xFFFFFFFF, hov);
            fill = Draw.lerp(acc, Draw.lerp(acc, 0xFFFFFFFF, 0.25f), hov);
            tc = onAccent();
            shadow = false;
        } else if (style == DANGER) {
            border = Draw.lerp(0xFF8A4A4A, RED, hov);
            fill = Draw.lerp(RAISED, 0xFF3A2224, hov);
            tc = Draw.lerp(0xFFE0A0A0, RED, hov);
        } else {
            border = Draw.lerp(SILVER, acc, hov);
            fill = Draw.lerp(RAISED, HOVER, hov);
            tc = Draw.lerp(SOFT, 0xFFFFFFFF, hov);
        }
        glossBox(x, y, w, h, 5, border, fill);
        String t = fr.trimStringToWidth(label, w - 10);
        fr.drawString(t, x + (w - fr.getStringWidth(t)) / 2f, y + (h - 8) / 2, tc, shadow);
    }
}
