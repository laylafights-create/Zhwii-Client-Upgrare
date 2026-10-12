package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

/** Combo card: big number tinted by tier (white -> gold -> red), "COMBO" label, and a bar draining until reset. */
public class ComboModule extends TextHudModule {
    private static int combo;
    private static long lastHit;
    private int prevHurt;

    private final SliderSetting timeout = add(new SliderSetting("timeout", "Reset After", 3, 1, 8, 1, "s"));

    private static final String LABEL = "COMBO";
    private static final float NUM_SCALE = 2f;
    private static final int PAD_X = 7, PAD_Y = 5, GAP = 6, BAR_H = 2;
    private static final int NUM_H = 16; // 8px font * NUM_SCALE
    private static final int PREVIEW_COMBO = 7;

    // cache: only rebuilt when the number changes (no per-frame string concat / colour math)
    private int cachedCombo = -1;
    private String numText = "";
    private int numTextW, numBoxW, numColor;
    private int labelW = -1;

    public ComboModule() {
        super("combo", "Combo", "Số đòn đánh liên tiếp mà không bị đánh lại.", Category.PVP, false, 4, 64);
    }

    public static void onAttack() {
        combo++;
        lastHit = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        if (p.hurtTime > prevHurt) combo = 0; // mình vừa bị đánh -> reset combo
        prevHurt = p.hurtTime;
        if (combo > 0 && System.currentTimeMillis() - lastHit > timeout.getInt() * 1000L) combo = 0;
    }

    private static int tierColor(int c) {
        if (c <= 10) return Draw.lerp(0xFFFFFFFF, 0xFFFFD25C, Math.max(0, c - 1) / 9f);
        return Draw.lerp(0xFFFFD25C, 0xFFFF5C5C, Math.min(1f, (c - 10) / 15f));
    }

    private void ensureCache(int c) {
        if (labelW < 0) labelW = fr().getStringWidth(LABEL);
        if (c == cachedCombo) return;
        cachedCombo = c;
        numText = "x" + c;
        numTextW = fr().getStringWidth(numText);
        // reserve at least 3 chars ("x00") so the card doesn't jitter when going 9 -> 10
        numBoxW = Math.round(Math.max(numTextW, fr().getStringWidth("x00")) * NUM_SCALE);
        numColor = tierColor(c);
    }

    private int shown(boolean preview) {
        return combo <= 0 && preview ? PREVIEW_COMBO : combo;
    }

    @Override public int getWidth(boolean preview) {
        ensureCache(Math.max(1, shown(preview)));
        return PAD_X * 2 + numBoxW + GAP + labelW;
    }

    @Override public int getHeight(boolean preview) {
        return PAD_Y * 2 + NUM_H + 3 + BAR_H;
    }

    private static void fillRound(int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 >= 2) Draw.rrect(x1, y1, x2, y2, 1, color);
        else if (x2 > x1) Gui.drawRect(x1, y1, x2, y2, color);
    }

    /** Mỗi đòn trúng làm khung "nảy" lên một chút. */
    @Override
    protected void draw(int x, int y, boolean preview) {
        int c = shown(preview);
        if (c <= 0) return; // chỉ hiện khi đang có combo; trình sửa HUD vẫn hiện mẫu để kéo thả

        long now = System.currentTimeMillis();
        int w = getWidth(preview), h = getHeight(preview); // also refreshes the cache for c

        float b = (preview || !AnimationsModule.hud()) ? 0f
                : Math.max(0f, 1f - (now - lastHit) * AnimationsModule.speed() / 200f);
        boolean bounce = b > 0f;
        if (bounce) {
            float cx = x + w / 2f, cy = y + h / 2f, k = 1f + 0.22f * b;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0f);
            GlStateManager.scale(k, k, 1f);
            GlStateManager.translate(-cx, -cy, 0f);
        }

        drawBox(x, y, w, h);

        // big number, centred in its reserved box
        float nx = x + PAD_X + (numBoxW - numTextW * NUM_SCALE) / 2f;
        float ny = y + PAD_Y;
        GlStateManager.pushMatrix();
        GlStateManager.translate(nx, ny, 0f);
        GlStateManager.scale(NUM_SCALE, NUM_SCALE, 1f);
        fr().drawString(numText, 0, 0, numColor, shadow.value);
        GlStateManager.popMatrix();

        // label, vertically centred against the number
        fr().drawString(LABEL, x + PAD_X + numBoxW + GAP, y + PAD_Y + (NUM_H - 8) / 2, 0xFFB8B8B8, false);

        // time-left bar
        float remain = preview ? 0.7f
                : Math.max(0f, 1f - (now - lastHit) / (timeout.getInt() * 1000f));
        int bx = x + PAD_X, bw = w - PAD_X * 2, by = y + h - PAD_Y - BAR_H;
        Draw.rrect(bx, by, bx + bw, by + BAR_H, 1, 0x55000000);
        fillRound(bx, by, bx + Math.round(bw * remain), by + BAR_H, numColor);

        if (bounce) GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    @Override
    protected String[] getLines(boolean preview) {
        if (combo <= 0) return preview ? new String[] { "x7 COMBO" } : new String[0];
        return new String[] { "x" + combo + " COMBO" };
    }
}
