package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Mouse;

/**
 * Keystrokes: phím nhún theo lò xo, nền lan ra từ tâm, phát sáng, vệt sáng quét,
 * hai vòng sóng, thanh CPS mượt và hiệu ứng hiện lần lượt từng phím.
 */
public class KeystrokesModule extends HudModule {
    private static final int U = 22, G = 2, SPACE_H = 12;
    private static final int W = 3 * U + 2 * G;
    private static final int KEYS = 9;

    // Thông số lò xo (scale của phím): K = độ cứng, C = ma sát.
    private static final float SPRING_K = 380f, SPRING_C = 20f;
    private static final float PRESS_SCALE = 0.88f;

    private final BoolSetting showMouse = add(new BoolSetting("show_mouse", "Hiện nút chuột", true));
    private final BoolSetting showCps = add(new BoolSetting("show_cps", "Hiện CPS", true));
    private final BoolSetting showSpace = add(new BoolSetting("show_space", "Hiện phím cách", true));
    private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", "Màu khi bấm", 0xFFFFFF));
    private final SliderSetting pressedOpacity = add(new SliderSetting("pressed_opacity", "Độ mờ khi bấm", 50, 5, 100, 5, "%"));
    private final ColorSetting pressedTextColor = add(new ColorSetting("pressed_text_color", "Màu chữ khi bấm", 0x000000));
    private final BoolSetting glow = add(new BoolSetting("glow", "Hiệu ứng phát sáng", true));
    private final BoolSetting shine = add(new BoolSetting("shine", "Vệt sáng quét qua phím", true));
    private final BoolSetting cpsBar = add(new BoolSetting("cps_bar", "Thanh CPS", true));

    /** Mức nhấn mượt (0 = nhả, 1 = đang bấm) dùng cho màu và nền. */
    private final float[] anim = new float[KEYS];
    /** Scale của phím theo lò xo (1 = bình thường, nhỏ hơn khi bấm, vọt lên khi nhả). */
    private final float[] spring = new float[KEYS];
    private final float[] vel = new float[KEYS];
    /** Hai vòng sóng lan ra khi bấm (0 -> 1; số âm = đang chờ trễ). */
    private final float[] ripple = new float[KEYS];
    private final float[] ripple2 = new float[KEYS];
    /** Vệt sáng quét ngang qua phím (0 -> 1). */
    private final float[] shineT = new float[KEYS];
    private final boolean[] wasDown = new boolean[KEYS];
    /** CPS làm mượt: [0] = chuột trái, [1] = chuột phải. */
    private final float[] cpsSmooth = new float[2];

    private long lastNanos = 0L;
    private float dt = 0.016f;
    /** Thời gian kể từ lúc HUD vừa hiện, dùng để hiện lần lượt từng phím. */
    private float introTime = 99f;

    /** Màu nền phím khi bấm (do người dùng tự chỉnh màu + độ mờ). */
    private int pressed() {
        int alpha = Math.round(pressedOpacity.getInt() * 255f / 100f);
        return (alpha << 24) | (pressedColor.rgb() & 0xFFFFFF);
    }

    private int pressedText() {
        return 0xFF000000 | (pressedTextColor.rgb() & 0xFFFFFF);
    }

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Hiện phím WASD, chuột và phím cách khi bạn bấm.", Category.PVP, true, 4, 140);
        color.withDefault(0xFF5555);
        border.withDefault(false);
        for (int i = 0; i < KEYS; i++) {
            spring[i] = 1f;
            ripple[i] = 1f;
            ripple2[i] = 1f;
            shineT[i] = 1f;
        }
    }

    @Override public int getWidth(boolean preview) { return W; }

    @Override
    public int getHeight(boolean preview) {
        int h = U + G + U;
        if (showMouse.value) h += G + U;
        if (showSpace.value) h += G + SPACE_H;
        return h;
    }

    // ------------------------------------------------------------------ animation

    /** Cập nhật animation cho một phím (gọi đúng 1 lần mỗi frame). Không phụ thuộc FPS. */
    private void step(int idx, boolean down, boolean fx) {
        if (!fx) { // tắt animation HUD: đổi trạng thái tức thì
            anim[idx] = down ? 1f : 0f;
            spring[idx] = 1f;
            vel[idx] = 0f;
            ripple[idx] = ripple2[idx] = shineT[idx] = 1f;
            wasDown[idx] = down;
            return;
        }

        // Màu / nền: bấm thì phản hồi nhanh, nhả thì trôi chậm hơn cho mượt.
        float speed = down ? 32f : 12f;
        float k = 1f - (float) Math.exp(-speed * dt);
        anim[idx] += ((down ? 1f : 0f) - anim[idx]) * k;

        // Lò xo: lún xuống khi bấm, nhả ra thì nảy vọt lên rồi lắc về 1.
        float target = down ? PRESS_SCALE : 1f;
        float acc = SPRING_K * (target - spring[idx]) - SPRING_C * vel[idx];
        vel[idx] += acc * dt;
        spring[idx] += vel[idx] * dt;
        spring[idx] = Math.max(0.75f, Math.min(1.15f, spring[idx]));

        if (down && !wasDown[idx]) {
            ripple[idx] = 0f;
            ripple2[idx] = -0.15f;
            shineT[idx] = 0f;
        }
        if (!down && wasDown[idx]) vel[idx] += 1.2f; // cú hích khi nhả
        wasDown[idx] = down;

        if (ripple[idx] < 1f) ripple[idx] = Math.min(1f, ripple[idx] + dt * 2.8f);
        if (ripple2[idx] < 1f) ripple2[idx] = Math.min(1f, ripple2[idx] + dt * 2.8f);
        if (shineT[idx] < 1f) shineT[idx] = Math.min(1f, shineT[idx] + dt * 2.6f);
    }

    // ------------------------------------------------------------------ vẽ phím

    /** Một phím: hiện lần lượt, nhún lò xo, phát sáng, sóng lan, rồi vẽ nội dung. */
    private void key(int idx, int x, int y, int w, int h, String label, boolean down, String sub, boolean preview) {
        boolean fx = AnimationsModule.hud();
        step(idx, down, fx);

        float intro = (!fx || preview) ? 1f : AnimationsModule.ease((introTime - idx * 0.045f) / 0.28f);
        if (intro <= 0.01f) return;

        float sc = fx ? spring[idx] : 1f;
        sc *= 0.7f + 0.3f * intro;
        float dy = fx ? (0.9f * anim[idx] + (1f - intro) * 5f) : 0f;
        float cx = x + w / 2f, cy = y + h / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy + dy, 0f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.translate(-cx, -cy, 0f);
        if (fx) {
            if (glow.value) drawGlow(idx, x, y, w, h);
            drawRipple(ripple[idx], 1f, 4f, x, y, w, h);
            drawRipple(ripple2[idx], 0.55f, 7f, x, y, w, h);
        }
        keyInner(idx, x, y, w, h, label, sub, fx);
        GlStateManager.popMatrix();
    }

    /** Quầng sáng mềm quanh phím đang bấm (nhiều lớp mờ dần ra ngoài). */
    private void drawGlow(int idx, int x, int y, int w, int h) {
        float a = anim[idx];
        if (a < 0.03f) return;
        int base = Math.round(pressedOpacity.getInt() * 255f / 100f);
        int rgb = pressedColor.rgb() & 0xFFFFFF;
        for (int i = 5; i >= 1; i--) {
            int al = Math.round(base * a * 0.12f * (6 - i) / 5f);
            if (al < 2) continue;
            Draw.rrect(x - i, y - i, x + w + i, y + h + i, 3 + i, (al << 24) | rgb);
        }
    }

    /** Khung viền mờ dần, nở ra ngoài phím. */
    private void drawRipple(float t, float strength, float reach, int x, int y, int w, int h) {
        if (t <= 0f || t >= 1f) return;
        float ease = 1f - (1f - t) * (1f - t);
        int e = 1 + Math.round(ease * reach);
        int baseAlpha = Math.round(pressedOpacity.getInt() * 255f / 100f);
        int a = Math.round(baseAlpha * (1f - t) * 0.9f * strength);
        if (a <= 2) return;
        Draw.ring(x - e, y - e, x + w + e, y + h + e, (a << 24) | (pressedColor.rgb() & 0xFFFFFF));
    }

    /** Vẽ hình chữ nhật theo kiểu bo góc của module. */
    private void box(int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1) return;
        if (rounded.value && background.value) Draw.rrect(x1, y1, x2, y2, 3, argb);
        else Gui.drawRect(x1, y1, x2, y2, argb);
    }

    private void keyInner(int idx, int x, int y, int w, int h, String label, String sub, boolean fx) {
        float a = anim[idx];
        float e = 1f - (1f - a) * (1f - a); // ease-out
        FontRenderer fr = fr();

        // Nền thường mờ dần, nền khi bấm lan ra từ tâm phím.
        int idle = background.value ? bgColor() : 0;
        if (e < 0.98f) fillRect(x, y, w, h, Draw.mulAlpha(idle, 1f - e));
        if (a > 0.01f) {
            int ix = Math.round((1f - e) * w * 0.5f);
            int iy = Math.round((1f - e) * h * 0.5f);
            box(x + ix, y + iy, x + w - ix, y + h - iy, pressed());
        }

        // Mép sáng ở trên, bóng đổ ở dưới: tạo cảm giác phím nổi, biến mất khi bị nhấn.
        if (background.value) {
            int hi = Math.round(0x26 * (1f - a));
            int lo = Math.round(0x40 * (1f - a));
            if (hi > 0 && w > 8) Gui.drawRect(x + 3, y + 1, x + w - 3, y + 2, (hi << 24) | 0xFFFFFF);
            if (lo > 0 && w > 8) Gui.drawRect(x + 3, y + h - 2, x + w - 3, y + h - 1, lo << 24);
        }

        // Vệt sáng chéo quét ngang qua phím ngay khi vừa bấm.
        if (fx && shine.value && shineT[idx] < 1f) {
            float t = shineT[idx];
            int sw = Math.max(4, w / 4);
            float te = 1f - (1f - t) * (1f - t);
            int px = x - sw + Math.round((w + sw) * te);
            int x1 = Math.max(x + 1, px), x2 = Math.min(x + w - 1, px + sw);
            int sa = Math.round(0x50 * (1f - t));
            if (x2 > x1 && sa > 0) Gui.drawRect(x1, y + 1, x2, y + h - 1, (sa << 24) | 0xFFFFFF);
        }

        if (border.value) drawBorder(x, y, w, h);

        if (label == null) { // thanh phím cách
            int lc = Draw.lerp(textColor(x + y), pressedText(), a);
            // Thanh co lại hai bên một chút khi bấm.
            int inset = 14 + Math.round(3f * a);
            Draw.rrect(x + inset, y + 5, x + w - inset, y + 7, 1, lc);
            if (a > 0.02f) Draw.fadeLine(x + 6, y + h - 2, w - 12, accentColor(x + y) & 0xFFFFFF, Math.round(210 * a));
            return;
        }

        int tc = Draw.lerp(textColor(x + y), pressedText(), a);
        int ty = sub == null ? y + (h - 8) / 2 : y + 3;
        fr.drawString(label, x + (w - fr.getStringWidth(label)) / 2, ty, tc, shadow.value && a < 0.5f);

        if (sub != null) {
            // Chữ CPS sáng dần khi click nhanh.
            float cps = cpsSmooth[idx == 4 ? 0 : 1];
            int idleSub = Draw.lerp(0xFFAAAAAA, 0xFFFFFFFF, Math.min(1f, cps / 10f));
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.6f, 0.6f, 1f);
            float s = 1f / 0.6f;
            fr.drawString(sub, (int) ((x + (w - fr.getStringWidth(sub) * 0.6f) / 2f) * s), (int) ((y + 13) * s),
                    Draw.lerp(idleSub, 0xFF333333, a), false);
            GlStateManager.popMatrix();

            if (cpsBar.value && w > 10) {
                float frac = Math.min(1f, cps / 15f);
                Draw.hbar(x + 4, y + h - 4, w - 8, 2, frac, Draw.withAlpha(accentColor(x + y), 0xDD), 0x40888888);
            }
        } else if (a > 0.02f) {
            // Gạch chân màu nhấn mọc ra từ giữa phím.
            int bw = Math.round((w - 8) * e);
            int bx = x + (w - bw) / 2;
            Gui.drawRect(bx, y + h - 4, bx + bw, y + h - 3, Draw.withAlpha(accentColor(x + y), Math.round(210 * a)));
        }
    }

    private static boolean down(KeyBinding k, boolean preview) {
        return !preview && k.isKeyDown();
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();

        long now = System.nanoTime();
        if (lastNanos == 0L || now - lastNanos > 150_000_000L) {
            // HUD vừa hiện lại: chạy lại hiệu ứng hiện lần lượt từng phím.
            introTime = 0f;
            dt = 0.016f;
        } else {
            dt = Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
            introTime += dt;
        }
        lastNanos = now;

        // CPS làm mượt để thanh và màu chữ trượt êm thay vì nhảy số.
        float ks = 1f - (float) Math.exp(-10f * dt);
        cpsSmooth[0] += ((preview ? 0 : CpsModule.left()) - cpsSmooth[0]) * ks;
        cpsSmooth[1] += ((preview ? 0 : CpsModule.right()) - cpsSmooth[1]) * ks;

        key(0, x + U + G, y, U, U, "W", down(mc.gameSettings.keyBindForward, preview), null, preview);
        int r2 = y + U + G;
        key(1, x, r2, U, U, "A", down(mc.gameSettings.keyBindLeft, preview), null, preview);
        key(2, x + U + G, r2, U, U, "S", down(mc.gameSettings.keyBindBack, preview), null, preview);
        key(3, x + 2 * (U + G), r2, U, U, "D", down(mc.gameSettings.keyBindRight, preview), null, preview);

        int yy = r2 + U + G;
        if (showMouse.value) {
            int mw = (W - G) / 2;
            key(4, x, yy, mw, U, "LMB", !preview && Mouse.isButtonDown(0),
                    showCps.value ? CpsModule.left() + " CPS" : null, preview);
            key(5, x + mw + G, yy, mw, U, "RMB", !preview && Mouse.isButtonDown(1),
                    showCps.value ? CpsModule.right() + " CPS" : null, preview);
            yy += U + G;
        }
        if (showSpace.value) {
            key(6, x, yy, W, SPACE_H, null, down(mc.gameSettings.keyBindJump, preview), null, preview);
        }
    }
}
