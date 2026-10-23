package dev.lunarlite.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

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

    // =====================================================================================
    // Bộ vẽ "SAO sáng": xám trắng, viền xám, nút tròn, điểm nhấn cam (cùng phong cách với trang chủ menu)
    // =====================================================================================

    public static final int INK = 0xFF22262B;       // chữ chính trên nền sáng
    public static final int INK_SOFT = 0xFF50565C;  // chữ phụ
    public static final int INK_DIM = 0xFF7C838A;   // chữ mờ / viền đậm
    public static final int ORANGE = 0xFFF0A30A;    // cam SAO (nút OK / mục đang chọn)
    public static final int OK_GREEN = 0xFF3FBF7F;  // xanh HP, trạng thái BẬT

    /** Gui.drawGradientRect là hàm protected nên phải đi qua một lớp con. */
    private static final class Grad extends Gui {
        static final Grad I = new Grad();
        static void fill(int x1, int y1, int x2, int y2, int c1, int c2) {
            I.drawGradientRect(x1, y1, x2, y2, c1, c2);
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    /** Khung chuyển màu dọc. */
    public static void vgrad(int x1, int y1, int x2, int y2, int c1, int c2) {
        Grad.fill(x1, y1, x2, y2, c1, c2);
    }

    /** Khung SAO không nền: chỉ có vạch màu nhấn dưới tiêu đề và dấu ngoặc ở bốn góc. */
    public static void saoWindow(int x, int y, int w, int h, int headH, long now) {
        Draw.fadeLine(x + 10, y + headH + 1, w - 20, Draw.ACCENT & 0xFFFFFF, 0x90);
        int brA = 120 + Math.round(70f * (float) Math.sin(now / 520.0));
        brackets(x - 3, y - 3, w + 6, h + 6, 8, (brA << 24) | (Draw.ACCENT & 0xFFFFFF));
    }

    /** Nút tròn có biểu tượng (kiểu nút menu SAO): viền trắng, thân tối, vệt phản chiếu phía trên. */
    public static void saoOrb(float cx, float cy, float r, String icon, int body) {
        Draw.disc(cx + 0.5f, cy + 2f, r + 2f, 0x30000000, 0x00000000);
        Draw.disc(cx, cy, r, 0xFFFFFFFF);
        Draw.disc(cx, cy, r - 1.3f, Draw.lerp(body, 0xFFFFFFFF, 0.22f), Draw.lerp(body, 0xFF000000, 0.2f));
        Draw.disc(cx, cy - (r - 1.3f) * 0.5f, (r - 1.3f) * 0.5f, 0x40FFFFFF, 0x00FFFFFF);
        SaoIcons.draw(icon, Math.round(cx) - 8, Math.round(cy) - 8, 0xFFFFFFFF);
    }

    /** Viên BẬT / TẮT màu xanh - xám. */
    public static void saoPill(FontRenderer fr, int x, int y, int w, int h, boolean on, long now) {
        if (on) {
            int ga = 0x18 + Math.round(0x18 * (0.5f + 0.5f * (float) Math.sin(now / 420.0)));
            Draw.rrect(x - 2, y - 2, x + w + 2, y + h + 2, h / 2 + 2, (ga << 24) | 0x3FBF7F);
        }
        Draw.rrect(x, y, x + w, y + h, h / 2, on ? OK_GREEN : 0xFF8E949B);
        Gui.drawRect(x + h / 2, y + 1, x + w - h / 2, y + 2, on ? 0x66FFFFFF : 0x44FFFFFF);
        String st = on ? "BẬT" : "TẮT";
        fr.drawStringWithShadow(st, x + (w - fr.getStringWidth(st)) / 2f, y + (h - 8) / 2f, 0xFFFFFFFF);
    }

    /** Hàng tuỳ chỉnh không nền: chỉ có đường kẻ mảnh; rê chuột thì sáng nhẹ + vạch màu nhấn. */
    public static void saoRow(int x, int y, int w, int h, float hv) {
        vgrad(x, y, x + w, y + h, Draw.lerp(0xDEF4F4F5, 0xF2FFFFFF, hv), Draw.lerp(0xD6E4E6E8, 0xEEF2F3F4, hv));
        Gui.drawRect(x, y, x + w, y + 1, 0xCCFFFFFF);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0x55808890);
        Gui.drawRect(x, y + h, x + w, y + h + 1, 0x22000000);
        if (hv > 0.02f) {
            Gui.drawRect(x, y + h - 1, x + Math.round(w * hv), y + h, Draw.mulAlpha(Draw.ACCENT, hv));
            Gui.drawRect(x, y, x + 2, y + h, Draw.mulAlpha(Draw.ACCENT, hv));
        }
    }

    /** Vòng tròn phẳng có màu + glyph trắng (icon của từng hàng, giống nút Lock / Shutdown / Restart của SAO). */
    public static void saoDisc(float cx, float cy, float r, String icon, int base) {
        Draw.disc(cx + 0.5f, cy + 1.2f, r + 1f, 0x28000000, 0x00000000);
        Draw.disc(cx, cy, r, Draw.lerp(base, 0xFFFFFFFF, 0.18f), Draw.lerp(base, 0xFF000000, 0.08f));
        Draw.disc(cx, cy - r * 0.45f, r * 0.45f, 0x30FFFFFF, 0x00FFFFFF);
        SaoIcons.draw(icon, Math.round(cx) - 8, Math.round(cy) - 8, 0xFFFFFFFF);
    }

    /** Ô lõm sáng chứa giá trị (chế độ, số). */
    public static void saoField(int x, int y, int w, int h, float hv) {
        Draw.rrect(x, y, x + w, y + h, 3, Draw.lerp(0xFF9AA0A6, Draw.ACCENT, hv));
        Draw.rrect(x + 1, y + 1, x + w - 1, y + h - 1, 2, 0xFFEEF0F2);
        Gui.drawRect(x + 2, y + 1, x + w - 2, y + 2, 0xFFD6DADE);
    }

    /** Công tắc viên thuốc: xám -> xanh, núm tròn trắng; a = 0..1. */
    public static void saoSwitch(int tx, int sy, float a) {
        Draw.rrect(tx, sy, tx + 24, sy + 12, 6, Draw.lerp(0xFF8E949B, OK_GREEN, a));
        Gui.drawRect(tx + 6, sy + 1, tx + 18, sy + 2, 0x44FFFFFF);
        float cx = tx + 6f + 12f * a, cy = sy + 6f;
        Draw.disc(cx, cy + 0.6f, 5.4f, 0x30000000);
        Draw.disc(cx, cy, 4.8f, 0xFFFFFFFF, 0xFFDDE0E3);
    }

    /** Nút SAO: xám bóng hai tông (thường), cam (chính - nút OK), xám chữ đỏ (nguy hiểm). */
    public static void saoButton(FontRenderer fr, int x, int y, int w, int h, String label, float hov, int style) {
        int border, top, bot, tc;
        boolean shadow = false;
        if (style == PRIMARY) {
            border = Draw.lerp(0xFFFFE2A8, 0xFFFFFFFF, hov);
            top = Draw.lerp(0xFFFFB92E, 0xFFFFCB5C, hov);
            bot = Draw.lerp(0xFFE08E00, 0xFFF0A30A, hov);
            tc = 0xFFFFFFFF;
            shadow = true;
        } else if (style == DANGER) {
            border = Draw.lerp(0xFFB07A7A, 0xFFD64545, hov);
            top = Draw.lerp(0xFFF2F4F6, 0xFFFFFFFF, hov);
            bot = Draw.lerp(0xFFD4D8DC, 0xFFE6E9EC, hov);
            tc = Draw.lerp(0xFF9A3B3B, 0xFFD64545, hov);
        } else {
            border = Draw.lerp(0xFF8E949B, Draw.ACCENT, hov);
            top = Draw.lerp(0xFFF2F4F6, 0xFFFFFFFF, hov);
            bot = Draw.lerp(0xFFD4D8DC, 0xFFE6E9EC, hov);
            tc = Draw.lerp(INK_SOFT, INK, hov);
        }
        Draw.rrect(x, y + 1, x + w, y + h + 1, 5, 0x30000000);
        Draw.rrect(x, y, x + w, y + h, 5, border);
        Draw.rrect(x + 1, y + 1, x + w - 1, y + h - 1, 4, bot);
        Draw.rrect(x + 1, y + 1, x + w - 1, y + h / 2, 4, top);
        Gui.drawRect(x + 5, y + 1, x + w - 5, y + 2, 0x66FFFFFF);
        String t = fr.trimStringToWidth(label, w - 10);
        fr.drawString(t, x + (w - fr.getStringWidth(t)) / 2f, y + (h - 8) / 2, tc, shadow);
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
