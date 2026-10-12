package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;

/**
 * HUD dạng thẻ bo góc + vài dòng chữ (FPS, CPS, Ping...): vạch nhấn bên trái.
 * Nếu {@link #hasTitle()} thì dòng đầu là tiêu đề trong một dải riêng (Scoreboard).
 */
public abstract class TextHudModule extends HudModule {
    protected static final int PAD_X = 6, PAD_Y = 5, LINE_H = 10, ACCENT_W = 2;
    /** Chiều cao dải tiêu đề + khoảng cách tới dòng đầu tiên của nội dung. */
    private static final int TITLE_BLOCK = LINE_H + 8;

    protected TextHudModule(String id, String name, String desc, Category cat, boolean def, int x, int y) {
        super(id, name, desc, cat, def, x, y);
    }

    /** Trả về mảng rỗng nếu không có gì để vẽ. */
    protected abstract String[] getLines(boolean preview);

    /** Màu riêng cho từng dòng (ARGB), hoặc 0 để dùng màu của module. */
    protected int lineColor(int index) { return 0; }

    /** True nếu dòng đầu tiên là tiêu đề (vẽ trong dải riêng, căn giữa). */
    protected boolean hasTitle() { return false; }

    private boolean titled(String[] lines) { return hasTitle() && lines.length > 1; }

    @Override
    public int getWidth(boolean preview) {
        String[] lines = getLines(preview);
        if (lines.length == 0) return 0;
        int w = 0;
        for (String s : lines) w = Math.max(w, fr().getStringWidth(s));
        return w + PAD_X * 2 + ACCENT_W;
    }

    @Override
    public int getHeight(boolean preview) {
        String[] lines = getLines(preview);
        int n = lines.length;
        if (n == 0) return 0;
        if (titled(lines)) return TITLE_BLOCK + (n - 1) * LINE_H + 3;
        return n * LINE_H + 2 * PAD_Y - 2;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        String[] lines = getLines(preview);
        if (lines.length == 0) return;
        int w = getWidth(preview), h = getHeight(preview);
        drawBox(x, y, w, h);

        boolean titled = titled(lines);
        int ac = accentColor(0);
        int first = 0, textX = x + PAD_X + ACCENT_W, textY = y + PAD_Y;
        if (titled) {
            Draw.rrect(x + 2, y + 2, x + w - 2, y + LINE_H + 5, 3, 0x40000000);
            Draw.fadeLine(x + 6, y + LINE_H + 5, w - 12, ac & 0xFFFFFF, 0xCC);
            String t = lines[0];
            fr().drawString(t, x + (w - fr().getStringWidth(t)) / 2f, y + 4, 0xFFFFFFFF, shadow.value);
            first = 1;
            textX = x + PAD_X;
            textY = y + TITLE_BLOCK;
        } else if (h > 10) {
            Draw.rrect(x + 3, y + 3, x + 3 + ACCENT_W, y + h - 3, 1, ac);
        }
        for (int i = first; i < lines.length; i++) {
            int ly = textY + (i - first) * LINE_H;
            int lc = rainbow.active() ? 0 : lineColor(i);
            if (lc != 0) {
                fr().drawString(lines[i], textX, ly, 0xFF000000 | lc, shadow.value);
            } else {
                drawText(lines[i], textX, ly, i * LINE_H);
            }
        }
    }
}
