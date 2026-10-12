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

public class KeystrokesModule extends HudModule {
    private static final int U = 22, G = 2, SPACE_H = 12;
    private static final int W = 3 * U + 2 * G;
    private static final int KEYS = 9;

    private final BoolSetting showMouse = add(new BoolSetting("show_mouse", "Hiện nút chuột", true));
    private final BoolSetting showCps = add(new BoolSetting("show_cps", "Hiện CPS", true));
    private final BoolSetting showSpace = add(new BoolSetting("show_space", "Hiện phím cách", true));
    private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", "Màu khi bấm", 0xFFFFFF));
    private final SliderSetting pressedOpacity = add(new SliderSetting("pressed_opacity", "Độ mờ khi bấm", 50, 5, 100, 5, "%"));
    private final ColorSetting pressedTextColor = add(new ColorSetting("pressed_text_color", "Màu chữ khi bấm", 0x000000));

    /** Mức nhấn (0 = nhả, 1 = đang bấm), mượt theo thời gian. */
    private final float[] anim = new float[KEYS];
    /** Hiệu ứng "nảy" lên khi vừa nhả phím (1 -> 0). */
    private final float[] pop = new float[KEYS];
    /** Vòng sóng lan ra khi vừa bấm (0 -> 1, >=1 là xong). */
    private final float[] ripple = new float[KEYS];
    private final boolean[] wasDown = new boolean[KEYS];
    private long lastNanos = 0L;
    private float dt = 0.016f;

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
    }

    @Override public int getWidth(boolean preview) { return W; }

    @Override
    public int getHeight(boolean preview) {
        int h = U + G + U;
        if (showMouse.value) h += G + U;
        if (showSpace.value) h += G + SPACE_H;
        return h;
    }

    /** Cập nhật animation cho một phím (gọi đúng 1 lần mỗi frame). Không phụ thuộc FPS. */
    private void step(int idx, boolean down) {
        // Bấm thì phản hồi nhanh, nhả thì trôi chậm hơn một chút cho mượt.
        float speed = down ? 30f : 14f;
        float k = 1f - (float) Math.exp(-speed * dt);
        anim[idx] += ((down ? 1f : 0f) - anim[idx]) * k;

        if (down && !wasDown[idx]) ripple[idx] = 0f;          // vừa bấm -> bắn sóng
        if (!down && wasDown[idx]) pop[idx] = 1f;             // vừa nhả -> nảy lên
        wasDown[idx] = down;

        if (ripple[idx] < 1f) ripple[idx] = Math.min(1f, ripple[idx] + dt * 3.2f);
        if (pop[idx] > 0f) pop[idx] = Math.max(0f, pop[idx] - dt * 5f);
    }

    /** Phím nhún xuống khi bấm, nảy nhẹ khi nhả, kèm vòng sóng lan ra. */
    private void key(int idx, int x, int y, int w, int h, String label, boolean down, String sub) {
        step(idx, down);
        boolean fx = AnimationsModule.hud();

        float sc = 1f;
        float dy = 0f;
        if (fx) {
            // Lún xuống 7%, rồi nảy vượt lên ~4% khi nhả (giảm dần về 1).
            sc = 1f - 0.07f * anim[idx] + 0.04f * pop[idx] * (float) Math.sin(pop[idx] * Math.PI);
            dy = 0.8f * anim[idx];
        }
        float cx = x + w / 2f, cy = y + h / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy + dy, 0f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.translate(-cx, -cy, 0f);
        if (fx) drawRipple(idx, x, y, w, h);
        keyInner(idx, x, y, w, h, label, sub);
        GlStateManager.popMatrix();
    }

    /** Khung viền mờ dần, nở ra ngoài phím. */
    private void drawRipple(int idx, int x, int y, int w, int h) {
        float t = ripple[idx];
        if (t >= 1f) return;
        float ease = 1f - (1f - t) * (1f - t);
        int e = 1 + Math.round(ease * 4f);
        int baseAlpha = Math.round(pressedOpacity.getInt() * 255f / 100f);
        int a = Math.round(baseAlpha * (1f - t) * 0.9f);
        if (a <= 2) return;
        int c = (a << 24) | (pressedColor.rgb() & 0xFFFFFF);
        Draw.ring(x - e, y - e, x + w + e, y + h + e, c);
    }

    private void keyInner(int idx, int x, int y, int w, int h, String label, String sub) {
        float a = anim[idx];
        FontRenderer fr = fr();

        int idle = background.value ? bgColor() : 0;
        fillRect(x, y, w, h, Draw.lerp(idle, pressed(), a));
        if (border.value) drawBorder(x, y, w, h);

        if (label == null) { // thanh phím cách
            int lc = Draw.lerp(textColor(x + y), pressedText(), a);
            // Thanh co lại hai bên một chút khi bấm.
            int inset = 14 + Math.round(3f * a);
            Draw.rrect(x + inset, y + 5, x + w - inset, y + 7, 1, lc);
            return;
        }

        int tc = Draw.lerp(textColor(x + y), pressedText(), a);
        int ty = sub == null ? y + (h - 8) / 2 : y + 3;
        fr.drawString(label, x + (w - fr.getStringWidth(label)) / 2, ty, tc, shadow.value && a < 0.5f);
        if (sub != null) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.6f, 0.6f, 1f);
            float s = 1f / 0.6f;
            fr.drawString(sub, (int) ((x + (w - fr.getStringWidth(sub) * 0.6f) / 2f) * s), (int) ((y + 13) * s),
                    Draw.lerp(0xFFAAAAAA, 0xFF333333, a), false);
            GlStateManager.popMatrix();
        }
    }

    private static boolean down(KeyBinding k, boolean preview) {
        return !preview && k.isKeyDown();
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();

        long now = System.nanoTime();
        dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        key(0, x + U + G, y, U, U, "W", down(mc.gameSettings.keyBindForward, preview), null);
        int r2 = y + U + G;
        key(1, x, r2, U, U, "A", down(mc.gameSettings.keyBindLeft, preview), null);
        key(2, x + U + G, r2, U, U, "S", down(mc.gameSettings.keyBindBack, preview), null);
        key(3, x + 2 * (U + G), r2, U, U, "D", down(mc.gameSettings.keyBindRight, preview), null);

        int yy = r2 + U + G;
        if (showMouse.value) {
            int mw = (W - G) / 2;
            key(4, x, yy, mw, U, "LMB", !preview && Mouse.isButtonDown(0), showCps.value ? CpsModule.left() + " CPS" : null);
            key(5, x + mw + G, yy, mw, U, "RMB", !preview && Mouse.isButtonDown(1), showCps.value ? CpsModule.right() + " CPS" : null);
            yy += U + G;
        }
        if (showSpace.value) {
            key(6, x, yy, W, SPACE_H, null, down(mc.gameSettings.keyBindJump, preview), null);
        }
    }
}
