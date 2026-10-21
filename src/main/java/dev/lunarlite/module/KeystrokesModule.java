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
 * Keystrokes: phím rơi vào có nảy, nhún theo lò xo, nền dâng lên như chất lỏng, viền kính,
 * tia lửa bắn lên, phím bên cạnh nhún theo, xung sáng khi nhả, vệt sáng quét, hai vòng sóng,
 * thanh CPS mượt có vạch đỉnh và số CPS chạy mượt. CPS chỉ hiện ra khi đang click.
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
    private final BoolSetting fillUp = add(new BoolSetting("fill_up", "Nền dâng lên từ dưới khi bấm", true));
    private final BoolSetting particles = add(new BoolSetting("particles", "Tia lửa bắn lên khi bấm", true));
    private final BoolSetting wave = add(new BoolSetting("wave", "Phím bên cạnh nhún theo", true));
    private final BoolSetting releasePulse = add(new BoolSetting("release_pulse", "Xung sáng khi nhả phím", true));
    private final BoolSetting cpsRoll = add(new BoolSetting("cps_roll", "Số CPS chạy mượt", true));
    private final BoolSetting cpsPeakMark = add(new BoolSetting("cps_peak", "Vạch đỉnh CPS", true));

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
    /** Mức hiện của CPS (0 = ẩn, 1 = hiện đủ): chỉ hiện khi đang click, ngừng click thì mờ dần. */
    private final float[] cpsVis = new float[2];

    /** Tia lửa: PN hạt mỗi phím, toạ độ tương đối so với góc trên-trái của phím. */
    private static final int PN = 5;
    private final float[] ppx = new float[KEYS * PN];
    private final float[] ppy = new float[KEYS * PN];
    private final float[] pvx = new float[KEYS * PN];
    private final float[] pvy = new float[KEYS * PN];
    private final float[] plife = new float[KEYS * PN];
    private final java.util.Random rng = new java.util.Random();
    /** Xung sáng khi nhả (1 -> 0) và mức ảnh hưởng từ phím bên cạnh (0 -> 1). */
    private final float[] trail = new float[KEYS];
    private final float[] infl = new float[KEYS];
    /** Đỉnh CPS gần đây (trôi chậm về giá trị hiện tại). */
    private final float[] cpsPeak = new float[2];
    /** Phím nào nằm cạnh phím nào: 0 W, 1 A, 2 S, 3 D, 4 LMB, 5 RMB, 6 Space. */
    private static final int[][] NEIGH = { {1, 2, 3}, {0, 2}, {0, 1, 3}, {0, 2}, {5}, {4}, {4, 5} };

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

    /** Easing "back": vọt quá một chút rồi về đúng chỗ. */
    private static float back(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
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
    private void step(int idx, boolean down, boolean fx, int w, int h) {
        if (!fx) { // tắt animation HUD: đổi trạng thái tức thì
            anim[idx] = down ? 1f : 0f;
            spring[idx] = 1f;
            vel[idx] = 0f;
            ripple[idx] = ripple2[idx] = shineT[idx] = 1f;
            trail[idx] = 0f;
            for (int p = 0; p < PN; p++) plife[idx * PN + p] = 0f;
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
            if (particles.value) {
                for (int p = 0; p < PN; p++) {
                    int i = idx * PN + p;
                    ppx[i] = w * (0.15f + 0.7f * rng.nextFloat());
                    ppy[i] = h * 0.25f;
                    pvx[i] = (rng.nextFloat() - 0.5f) * 36f;
                    pvy[i] = -(24f + rng.nextFloat() * 36f);
                    plife[i] = 0.45f + 0.3f * rng.nextFloat();
                }
            }
        }
        if (!down && wasDown[idx]) {
            vel[idx] += 1.2f; // cú hích khi nhả
            if (releasePulse.value) trail[idx] = 1f;
        }
        wasDown[idx] = down;

        if (ripple[idx] < 1f) ripple[idx] = Math.min(1f, ripple[idx] + dt * 2.8f);
        if (ripple2[idx] < 1f) ripple2[idx] = Math.min(1f, ripple2[idx] + dt * 2.8f);
        if (shineT[idx] < 1f) shineT[idx] = Math.min(1f, shineT[idx] + dt * 2.6f);
        if (trail[idx] > 0f) trail[idx] = Math.max(0f, trail[idx] - dt * 2.4f);

        for (int p = 0; p < PN; p++) {
            int i = idx * PN + p;
            if (plife[i] <= 0f) continue;
            plife[i] -= dt;
            ppx[i] += pvx[i] * dt;
            ppy[i] += pvy[i] * dt;
            pvy[i] += 70f * dt; // trọng lực nhẹ: bay lên rồi chậm dần
        }
    }

    // ------------------------------------------------------------------ vẽ phím

    /** Một phím: rơi vào có nảy, nhún lò xo, nhún theo phím bên cạnh, phát sáng, sóng lan, tia lửa. */
    private void key(int idx, int x, int y, int w, int h, String label, boolean down, String sub, boolean preview) {
        boolean fx = AnimationsModule.hud();
        step(idx, down, fx, w, h);

        float raw = (!fx || preview) ? 1f : Math.max(0f, Math.min(1f, (introTime - idx * 0.05f) / 0.4f));
        if (raw <= 0.01f) return;
        float b = back(raw);

        float sc = fx ? spring[idx] : 1f;
        sc *= 0.55f + 0.45f * b;
        float dy = fx ? (0.9f * anim[idx] - (1f - b) * 9f) : 0f;
        if (fx && wave.value) {
            float in = infl[idx] * (1f - anim[idx]);   // phím đang bấm thì không bị kéo theo
            sc *= 1f - 0.035f * in;
            dy += 0.8f * in;
        }
        float cx = x + w / 2f, cy = y + h / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy + dy, 0f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.translate(-cx, -cy, 0f);
        if (fx) {
            if (glow.value) drawGlow(idx, x, y, w, h);
            drawRipple(ripple[idx], 1f, 4f, x, y, w, h);
            drawRipple(ripple2[idx], 0.55f, 7f, x, y, w, h);
            if (releasePulse.value && trail[idx] > 0.02f) {
                int e = Math.round((1f - trail[idx]) * 3f);
                int al = Math.round(150f * trail[idx]);
                if (al > 2) Draw.ring(x - e, y - e, x + w + e, y + h + e, (al << 24) | (accentColor(x + y) & 0xFFFFFF));
            }
        }
        keyInner(idx, x, y, w, h, label, sub, fx);
        if (fx && particles.value) drawParticles(idx, x, y);
        GlStateManager.popMatrix();
    }

    private void drawParticles(int idx, int x, int y) {
        int rgb = pressedColor.rgb() & 0xFFFFFF;
        int acc = accentColor(x + y) & 0xFFFFFF;
        for (int p = 0; p < PN; p++) {
            int i = idx * PN + p;
            float l = plife[i];
            if (l <= 0f) continue;
            int al = Math.round(230f * Math.min(1f, l / 0.4f));
            if (al < 8) continue;
            int px = x + Math.round(ppx[i]), py = y + Math.round(ppy[i]);
            int sz = (p & 1) == 0 ? 2 : 1;
            Gui.drawRect(px, py, px + sz, py + sz, (al << 24) | ((p & 1) == 0 ? rgb : acc));
        }
    }

    /** Tô nền khi bấm: bo góc nếu đủ cao, còn lại tô phẳng. */
    private void fill(int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1) return;
        if (rounded.value && background.value && y2 - y1 >= 6) Draw.rrect(x1, y1, x2, y2, 3, argb);
        else Gui.drawRect(x1, y1, x2, y2, argb);
    }

    /** Quầng sáng mềm quanh phím đang bấm (nhiều lớp mờ dần ra ngoài). */
    private void drawGlow(int idx, int x, int y, int w, int h) {
        float a = anim[idx];
        if (a < 0.03f) return;
        int base = Math.round(pressedOpacity.getInt() * 255f / 100f);
        int rgb = pressedColor.rgb() & 0xFFFFFF;
        for (int i = 4; i >= 1; i--) {
            int al = Math.round(base * a * 0.10f * (5 - i) / 4f);
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
        // Gờ dưới phím (phím nổi): thu lại khi bị nhấn xuống.
        if (fx && background.value && w > 8) {
            int lip = Math.round(2f * (1f - a));
            if (lip > 0) Gui.drawRect(x + 2, y + h - 1, x + w - 2, y + h - 1 + lip, 0x55000000);
        }
        if (e < 0.98f) fillRect(x, y, w, h, Draw.mulAlpha(idle, 1f - e));
        if (a > 0.01f) {
            if (fillUp.value && fx) {
                // Nền dâng lên từ dưới như chất lỏng, mặt nước hơi sáng.
                int fh = Math.max(1, Math.round(h * e));
                fill(x, y + h - fh, x + w, y + h, pressed());
                if (fh < h - 1 && w > 6) {
                    int sa = Math.round(110f * (1f - e));
                    if (sa > 0) Gui.drawRect(x + 1, y + h - fh, x + w - 1, y + h - fh + 1, (sa << 24) | 0xFFFFFF);
                }
            } else {
                int ix = Math.round((1f - e) * w * 0.5f);
                int iy = Math.round((1f - e) * h * 0.5f);
                box(x + ix, y + iy, x + w - ix, y + h - iy, pressed());
            }
        }

        // Mép sáng dạng gradient ở trên: phím như kính, mờ đi khi bị nhấn.
        if (background.value && w > 8) {
            for (int r = 0; r < 3; r++) {
                int hi = Math.round((0x2A >> (r * 1 + (r > 1 ? 1 : 0))) * (1f - a));
                if (hi > 0) Gui.drawRect(x + 3, y + 1 + r, x + w - 3, y + 2 + r, (hi << 24) | 0xFFFFFF);
            }
            // Viền kính mảnh, chuyển sang màu nhấn khi bấm.
            int rim = Draw.lerp(0x1AFFFFFF, Draw.withAlpha(accentColor(x + y), 0xB0), a);
            Draw.ring(x, y, x + w, y + h, rim);
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
            // Thanh dài ra hai bên khi bấm.
            int inset = 14 - Math.round(7f * e);
            Draw.rrect(x + inset, y + 5, x + w - inset, y + 7, 1, lc);
            if (a > 0.02f) Draw.fadeLine(x + 6, y + h - 2, w - 12, accentColor(x + y) & 0xFFFFFF, Math.round(210 * a));
            return;
        }

        int tc = Draw.lerp(textColor(x + y), pressedText(), a);
        // vis: mức hiện của CPS (chỉ phím chuột có sub). Chưa click -> chữ nằm giữa phím; click -> trượt lên nhường chỗ cho CPS.
        float vis = sub == null ? 0f : cpsVis[idx == 4 ? 0 : 1];
        int midY = y + (h - 8) / 2;
        int ty = Math.round(midY + ((y + 3) - midY) * vis);
        fr.drawString(label, x + (w - fr.getStringWidth(label)) / 2, ty, tc, shadow.value && a < 0.5f);

        if (sub != null && vis > 0.05f) {
            // Chữ CPS sáng dần khi click nhanh, mờ dần vào/ra theo vis.
            float cps = cpsSmooth[idx == 4 ? 0 : 1];
            int idleSub = Draw.lerp(0xFFAAAAAA, 0xFFFFFFFF, Math.min(1f, cps / 10f));
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.6f, 0.6f, 1f);
            float s = 1f / 0.6f;
            fr.drawString(sub, (int) ((x + (w - fr.getStringWidth(sub) * 0.6f) / 2f) * s), (int) ((y + 13) * s),
                    Draw.mulAlpha(Draw.lerp(idleSub, 0xFF333333, a), vis), false);
            GlStateManager.popMatrix();

            if (cpsBar.value && w > 10) {
                float frac = Math.min(1f, cps / 15f);
                int barCol = Draw.lerp(Draw.withAlpha(accentColor(x + y), 0xDD), 0xFFFFFFFF, frac * 0.5f);
                Draw.hbar(x + 4, y + h - 4, w - 8, 2, frac, Draw.mulAlpha(barCol, vis), Draw.mulAlpha(0x40888888, vis));
                if (cpsPeakMark.value) {
                    float pf = Math.min(1f, cpsPeak[idx == 4 ? 0 : 1] / 15f);
                    if (pf > frac + 0.04f) {
                        int mx = x + 4 + Math.round((w - 9) * pf);
                        Gui.drawRect(mx, y + h - 5, mx + 1, y + h - 1, Draw.mulAlpha(0xCCFFFFFF, vis));
                    }
                }
            }
        }

        // Gạch chân màu nhấn mọc ra từ giữa phím (phím chữ thường, hoặc phím chuột khi CPS đang ẩn).
        float ul = sub == null ? 1f : 1f - vis;
        if (ul > 0.02f && a > 0.02f) {
            int bw = Math.round((w - 8) * e);
            int bx = x + (w - bw) / 2;
            Gui.drawRect(bx, y + h - 4, bx + bw, y + h - 3, Draw.withAlpha(accentColor(x + y), Math.round(210 * a * ul)));
        }
    }

    private String cpsText(int side, boolean preview) {
        int v = (cpsRoll.value && !preview) ? Math.round(cpsSmooth[side]) : (side == 0 ? CpsModule.left() : CpsModule.right());
        return v + " CPS";
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

        // CPS chỉ hiện khi đang giữ chuột hoặc còn click trong vòng 1 giây gần đây; xem trước (preview) luôn hiện để chỉnh giao diện.
        for (int i = 0; i < 2; i++) {
            boolean active = preview || Mouse.isButtonDown(i) || (i == 0 ? CpsModule.left() : CpsModule.right()) > 0;
            float speed = active ? 20f : 6f;
            cpsVis[i] += ((active ? 1f : 0f) - cpsVis[i]) * (1f - (float) Math.exp(-speed * dt));
        }

        for (int i = 0; i < 2; i++) {
            float pk = cpsPeak[i];
            pk = cpsSmooth[i] > pk ? cpsSmooth[i] : pk + (cpsSmooth[i] - pk) * (1f - (float) Math.exp(-1.2f * dt));
            cpsPeak[i] = pk;
        }
        // Mức ảnh hưởng của phím bên cạnh (phím đang bấm kéo hàng xóm nhún theo).
        for (int i = 0; i < KEYS - 2; i++) {
            float sum = 0f;
            for (int n : NEIGH[i]) sum += anim[n];
            infl[i] = Math.min(1f, sum * 0.5f);
        }

        key(0, x + U + G, y, U, U, "W", down(mc.gameSettings.keyBindForward, preview), null, preview);
        int r2 = y + U + G;
        key(1, x, r2, U, U, "A", down(mc.gameSettings.keyBindLeft, preview), null, preview);
        key(2, x + U + G, r2, U, U, "S", down(mc.gameSettings.keyBindBack, preview), null, preview);
        key(3, x + 2 * (U + G), r2, U, U, "D", down(mc.gameSettings.keyBindRight, preview), null, preview);

        int yy = r2 + U + G;
        if (showMouse.value) {
            int mw = (W - G) / 2;
            key(4, x, yy, mw, U, "LMB", !preview && Mouse.isButtonDown(0),
                    showCps.value ? cpsText(0, preview) : null, preview);
            key(5, x + mw + G, yy, mw, U, "RMB", !preview && Mouse.isButtonDown(1),
                    showCps.value ? cpsText(1, preview) : null, preview);
            yy += U + G;
        }
        if (showSpace.value) {
            key(6, x, yy, W, SPACE_H, null, down(mc.gameSettings.keyBindJump, preview), null, preview);
        }
    }
}
