package dev.lunarlite.gui;

import dev.lunarlite.module.Category;
import dev.lunarlite.module.Module;
import net.minecraft.client.gui.Gui;

/**
 * Biểu tượng phẳng kiểu menu Sword Art Online: nét đậm 2px, đơn sắc, vẽ bằng hình chữ nhật
 * trong khung 16x16 (không dùng ảnh). Dùng cho nút tròn và các hàng trong menu.
 */
public final class SaoIcons {
    private SaoIcons() {}

    // ------------------------------------------------------------------ chọn biểu tượng

    public static String cat(Category c) {
        if (c == null) return "grid";
        switch (c) {
            case HUD: return "monitor";
            case PVP: return "swords";
            case VISUAL: return "eye";
            case EFFECT: return "sparkle";
            case UTILITY: return "sliders";
            default: return "grid";
        }
    }

    public static String forModule(Module m) {
        String id = m.id;
        if ("fps".equals(id) || "ping".equals(id)) return "chart";
        if ("cps".equals(id)) return "mouse";
        if ("keystrokes".equals(id)) return "keys";
        if ("bowtrajectory".equals(id) || "aimassist".equals(id) || "enemy_detector".equals(id)
                || "targetinfo".equals(id)) return "crosshair";
        if ("combo".equals(id) || "killboard".equals(id)) return "swords";
        if ("armor".equals(id) || "hide_armor".equals(id)) return "shield";
        if ("potions".equals(id)) return "drop";
        if ("itempickup".equals(id) || "itemphysics".equals(id) || "blockoutline".equals(id)) return "cube";
        if ("serverip".equals(id) || "scoreboard".equals(id)) return "monitor";
        if ("togglesprint".equals(id) || "velocity".equals(id) || "bridge".equals(id)) return "chevron";
        if ("zoom".equals(id)) return "magnifier";
        if ("fullbright".equals(id)) return "sun";
        if ("farhorizons".equals(id)) return "mountain";
        if ("animations".equals(id) || "hotbar_animation".equals(id)) return "clock";
        if ("chatbar".equals(id)) return "bubble";
        if ("guitheme".equals(id)) return "sparkle";
        return cat(m.category);
    }

    // ------------------------------------------------------------------ hình cơ bản

    private static void px(int x, int y, int w, int h, int c) {
        Gui.drawRect(x, y, x + w, y + h, c);
    }

    private static void line(int x0, int y0, int x1, int y1, int th, int c) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            px(x0, y0, th, th, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private static void frame(int x, int y, int w, int h, int th, int c) {
        px(x, y, w, th, c);
        px(x, y + h - th, w, th, c);
        px(x, y + th, th, h - 2 * th, c);
        px(x + w - th, y + th, th, h - 2 * th, c);
    }

    private static void disc(int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.25));
            px(cx - hw, cy + dy, hw * 2, 1, c);
        }
    }

    private static void ring(int cx, int cy, int r, int th, int c) {
        int ri = r - th;
        for (int dy = -r; dy <= r; dy++) {
            int ow = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.25));
            if (Math.abs(dy) < ri) {
                int iw = (int) Math.round(Math.sqrt(ri * ri - dy * dy + 0.25));
                px(cx - ow, cy + dy, ow - iw, 1, c);
                px(cx + iw, cy + dy, ow - iw, 1, c);
            } else {
                px(cx - ow, cy + dy, ow * 2, 1, c);
            }
        }
    }

    // ------------------------------------------------------------------ biểu tượng (khung 16x16 tại x, y)

    public static void draw(String g, int x, int y, int c) {
        if ("grid".equals(g)) {
            for (int r = 0; r < 2; r++) {
                for (int q = 0; q < 2; q++) Draw.rrect(x + 1 + q * 8, y + 1 + r * 8, x + 7 + q * 8, y + 7 + r * 8, 1, c);
            }
        } else if ("monitor".equals(g)) {
            frame(x + 1, y + 2, 14, 9, 2, c);
            px(x + 7, y + 11, 2, 2, c);
            px(x + 4, y + 13, 8, 2, c);
        } else if ("swords".equals(g)) {
            line(x + 2, y + 2, x + 13, y + 13, 2, c);
            line(x + 13, y + 2, x + 2, y + 13, 2, c);
            px(x + 1, y + 8, 5, 2, c);
            px(x + 10, y + 8, 5, 2, c);
        } else if ("eye".equals(g)) {
            for (int dx = -7; dx <= 7; dx++) {
                int h = (int) Math.round(4.5 * Math.sqrt(1 - (dx / 7.5) * (dx / 7.5)));
                px(x + 8 + dx, y + 8 - h, 1, 2, c);
                px(x + 8 + dx, y + 8 + h - 2, 1, 2, c);
            }
            disc(x + 8, y + 8, 3, c);
        } else if ("sparkle".equals(g)) {
            for (int d = -7; d <= 7; d++) {
                int w = (int) Math.round(2.6 * (1 - Math.abs(d) / 7.5));
                px(x + 8 - w, y + 8 + d, w * 2 + 1, 1, c);
                px(x + 8 + d, y + 8 - w, 1, w * 2 + 1, c);
            }
        } else if ("sliders".equals(g)) {
            px(x + 1, y + 2, 14, 2, c);
            px(x + 1, y + 7, 14, 2, c);
            px(x + 1, y + 12, 14, 2, c);
            disc(x + 10, y + 3, 2, c);
            disc(x + 5, y + 8, 2, c);
            disc(x + 11, y + 13, 2, c);
        } else if ("gear".equals(g)) {
            ring(x + 8, y + 8, 5, 2, c);
            px(x + 7, y, 2, 3, c);
            px(x + 7, y + 13, 2, 3, c);
            px(x, y + 7, 3, 2, c);
            px(x + 13, y + 7, 3, 2, c);
            px(x + 2, y + 2, 2, 2, c);
            px(x + 12, y + 2, 2, 2, c);
            px(x + 2, y + 12, 2, 2, c);
            px(x + 12, y + 12, 2, 2, c);
            ring(x + 8, y + 8, 2, 1, c);
        } else if ("chart".equals(g)) {
            px(x + 2, y + 8, 3, 6, c);
            px(x + 6, y + 4, 3, 10, c);
            px(x + 10, y + 1, 3, 13, c);
            px(x + 1, y + 14, 14, 1, c);
        } else if ("mouse".equals(g)) {
            frame(x + 4, y + 1, 8, 14, 2, c);
            px(x + 7, y + 2, 2, 5, c);
            px(x + 4, y + 7, 8, 2, c);
        } else if ("keys".equals(g)) {
            px(x + 5, y + 1, 6, 6, c);
            px(x + 0, y + 9, 5, 5, c);
            px(x + 5, y + 9, 6, 5, c);
            px(x + 11, y + 9, 5, 5, c);
        } else if ("shield".equals(g)) {
            for (int dy = 0; dy < 14; dy++) {
                int hw = dy < 8 ? 6 : (int) Math.round(6 * (1 - (dy - 8) / 6.5));
                px(x + 8 - hw, y + 1 + dy, hw * 2, 1, c);
            }
        } else if ("drop".equals(g)) {
            for (int dy = 0; dy < 7; dy++) px(x + 8 - (int) Math.round(dy * 0.65), y + 1 + dy, (int) Math.round(dy * 1.3) + 1, 1, c);
            disc(x + 8, y + 10, 5, c);
        } else if ("cube".equals(g)) {
            line(x + 8, y + 1, x + 14, y + 4, 2, c);
            line(x + 14, y + 4, x + 14, y + 11, 2, c);
            line(x + 14, y + 11, x + 8, y + 14, 2, c);
            line(x + 8, y + 14, x + 2, y + 11, 2, c);
            line(x + 2, y + 11, x + 2, y + 4, 2, c);
            line(x + 2, y + 4, x + 8, y + 1, 2, c);
            line(x + 2, y + 4, x + 8, y + 7, 2, c);
            line(x + 14, y + 4, x + 8, y + 7, 2, c);
            line(x + 8, y + 7, x + 8, y + 14, 2, c);
        } else if ("crosshair".equals(g)) {
            ring(x + 8, y + 8, 5, 2, c);
            px(x + 7, y, 2, 4, c);
            px(x + 7, y + 12, 2, 4, c);
            px(x, y + 7, 4, 2, c);
            px(x + 12, y + 7, 4, 2, c);
        } else if ("chevron".equals(g)) {
            line(x + 2, y + 3, x + 7, y + 8, 2, c);
            line(x + 7, y + 8, x + 2, y + 13, 2, c);
            line(x + 8, y + 3, x + 13, y + 8, 2, c);
            line(x + 13, y + 8, x + 8, y + 13, 2, c);
        } else if ("magnifier".equals(g)) {
            ring(x + 6, y + 6, 5, 2, c);
            line(x + 10, y + 10, x + 14, y + 14, 2, c);
        } else if ("sun".equals(g)) {
            disc(x + 8, y + 8, 3, c);
            px(x + 7, y, 2, 3, c);
            px(x + 7, y + 13, 2, 3, c);
            px(x, y + 7, 3, 2, c);
            px(x + 13, y + 7, 3, 2, c);
            px(x + 2, y + 2, 2, 2, c);
            px(x + 12, y + 2, 2, 2, c);
            px(x + 2, y + 12, 2, 2, c);
            px(x + 12, y + 12, 2, 2, c);
        } else if ("mountain".equals(g)) {
            for (int dy = 0; dy < 11; dy++) px(x + 6 - (int) Math.round(dy * 0.6), y + 3 + dy, (int) Math.round(dy * 1.2) + 1, 1, c);
            for (int dy = 0; dy < 7; dy++) px(x + 11 - (int) Math.round(dy * 0.6), y + 7 + dy, (int) Math.round(dy * 1.2) + 1, 1, c);
        } else if ("clock".equals(g)) {
            ring(x + 8, y + 8, 7, 2, c);
            px(x + 7, y + 4, 2, 5, c);
            px(x + 7, y + 8, 5, 2, c);
        } else if ("bubble".equals(g)) {
            Draw.rrect(x + 1, y + 2, x + 15, y + 11, 3, c);
            for (int dy = 0; dy < 4; dy++) px(x + 4, y + 11 + dy, 4 - dy, 1, c);
        } else if ("check".equals(g)) {
            line(x + 2, y + 8, x + 6, y + 12, 2, c);
            line(x + 6, y + 12, x + 14, y + 4, 2, c);
        } else if ("arrows".equals(g)) {
            line(x + 4, y + 2, x + 4, y + 13, 2, c);
            line(x + 1, y + 5, x + 4, y + 2, 2, c);
            line(x + 7, y + 5, x + 4, y + 2, 2, c);
            line(x + 11, y + 2, x + 11, y + 13, 2, c);
            line(x + 8, y + 10, x + 11, y + 13, 2, c);
            line(x + 14, y + 10, x + 11, y + 13, 2, c);
        } else {
            disc(x + 8, y + 8, 4, c);
        }
    }
}
