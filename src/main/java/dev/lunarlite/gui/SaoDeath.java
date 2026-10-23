package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

/** Hiệu ứng chết kiểu Sword Art Online: màn hình tối dần, chữ "YOU ARE DEAD" và hai nút giả lập màn Game Over. */
public final class SaoDeath {
    private SaoDeath() {}

    public static boolean enabled = true;

    private static boolean dead;
    private static long since;

    /** Gọi mỗi tick với trạng thái "người chơi đã chết". */
    public static void update(boolean isDead) {
        boolean now = enabled && isDead;
        if (now && !dead) since = System.currentTimeMillis();
        dead = now;
    }

    public static boolean active() {
        return enabled && dead;
    }

    private static float progress() {
        float t = (System.currentTimeMillis() - since) / 1200f;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public static void render(int w, int h, int mx, int my, boolean gameOverScreen) {
        if (!active()) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;
        float p = progress();

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        // nền tối dần
        Draw.rrect(0, 0, w, h, 0, ((int) (170 * p) << 24) | 0x0A0306);
        // dải sáng ngang giữa màn hình
        int bandA = (int) (110 * p);
        Draw.rrect(0, h / 2 - 22, w, h / 2 + 22, 0, (bandA << 24) | 0x000000);
        Draw.fadeLine(0, h / 2 - 22, w, 0xFF3355, (int) (200 * p));
        Draw.fadeLine(0, h / 2 + 22, w, 0xFF3355, (int) (200 * p));

        int ta = (int) (255 * p);
        if (ta > 4) {
            String title = "YOU ARE DEAD";
            float s = 2.0f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(w / 2f, h / 2f - 8f, 0f);
            GlStateManager.scale(s, s, 1f);
            fr.drawStringWithShadow(title, -fr.getStringWidth(title) / 2f, 0f, (ta << 24) | 0xFFFFFF);
            GlStateManager.popMatrix();
        }

        if (gameOverScreen && p > 0.6f) {
            int a = (int) (255 * ((p - 0.6f) / 0.4f));
            drawButton(fr, w / 2 - 100, h / 4 + 72, "Hồi sinh", mx, my, a);
            drawButton(fr, w / 2 - 100, h / 4 + 96, "Về màn hình chính", mx, my, a);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawButton(FontRenderer fr, int x, int y, String text, int mx, int my, int a) {
        boolean hv = Draw.in(mx, my, x, y, 200, 20);
        int bg = ((int) (a * (hv ? 0.75f : 0.5f)) << 24) | (hv ? 0x3A1620 : 0x15090D);
        Draw.rrect(x, y, x + 200, y + 20, 3, bg);
        Draw.outline(x, y, x + 200, y + 20, ((int) (a * 0.6f) << 24) | 0xFF3355);
        fr.drawStringWithShadow(text, x + 100 - fr.getStringWidth(text) / 2f, y + 6, (a << 24) | 0xFFFFFF);
    }
}
