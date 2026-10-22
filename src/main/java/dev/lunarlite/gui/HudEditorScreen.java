package dev.lunarlite.gui;

import dev.lunarlite.Lang;
import dev.lunarlite.LunarLite;
import dev.lunarlite.module.AnimationsModule;
import dev.lunarlite.module.HudModule;
import dev.lunarlite.module.Module;
import dev.lunarlite.module.ThemeModule;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.Setting;
import dev.lunarlite.setting.SliderSetting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Chỉnh vị trí HUD, cùng phong cách với MenuScreen (nền tối, nút phẳng, công tắc trượt,
 * thanh cuộn kiểu Sodium, hàng trượt vào lần lượt, rê chuột sáng dần + vạch màu nhấn).
 *  - Kéo thả để di chuyển (tự hít vào mép / giữa màn hình), lăn chuột để đổi cỡ, phím mũi tên để nhích.
 *  - Bấm vào một HUD: nó được chọn, một bảng cài đặt của HUD đó hiện ở góc màn hình
 *    (kích cỡ, mọi tuỳ chỉnh, thao tác, đặt lại) và có dấu X bên cạnh khung để xoá (= tắt) HUD đó.
 *    HUD đã xoá có thể bật lại trong Menu.
 */
public class HudEditorScreen extends GuiScreen {
    private static final int SNAP = 5;
    private static final int PW = 196, RH = 20, GAP = 4, HEAD = 26, XS = 11, BAR_W = 6;
    private static final int BH = 20, WIDE = 200;
    /** Khoảng hở phía dưới dành cho dòng gợi ý + nút quay lại. */
    private static final int FOOT = 46;
    private static final int K_SCALE = 0, K_BOOL = 1, K_MODE = 2, K_COLOR = 3, K_SLIDER = 4, K_ACTION = 5, K_RESET = 6;
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };

    /** Một hàng trong bảng cài đặt. */
    private static class PRow {
        int kind;
        Setting s;
        Module.Action a;
        int x, y, w;
    }

    private HudModule selected, dragging;
    private int offX, offY;
    private boolean guideV, guideH;

    private final int[] bBack = new int[4];
    private final int[] bX = new int[4];           // dấu X xoá HUD
    private final List<PRow> prows = new ArrayList<PRow>();
    private boolean panelOn;
    private int px, py, ph, viewTop, viewH, contentH;
    private float panelScroll, panelTarget;
    private SliderSetting dragSlider;
    private int dragX, dragW;

    // vị trí bảng do người dùng tự kéo (nhớ giữa các lần mở editor)
    private static boolean panelCustom;
    private static int customX, customY;
    private boolean dragPanel;
    private int panOffX, panOffY;

    // ---- animation (giống MenuScreen)
    private final long openStart = System.currentTimeMillis();
    private long selAt = System.currentTimeMillis();            // lúc chọn HUD -> các hàng trượt vào lần lượt
    private final Map<String, Float> hovAnim = new HashMap<String, Float>();
    private final Map<String, Float> swAnim = new HashMap<String, Float>();
    private float backHov, barAnim;
    private boolean dragBar;
    private int dragOff;
    private int curMx, curMy;

    // ------------------------------------------------------------------ tiện ích

    private HudModule hudAt(int mx, int my, ScaledResolution sr) {
        List<Module> mods = LunarLite.MODULES;
        for (int i = mods.size() - 1; i >= 0; i--) {
            Module m = mods.get(i);
            if (!(m instanceof HudModule) || !m.enabled) continue;
            HudModule h = (HudModule) m;
            int rx = h.renderX(sr, true), ry = h.renderY(sr, true);
            if (Draw.in(mx, my, rx, ry, h.boxW(true), h.boxH(true))) return h;
        }
        return null;
    }

    private boolean hit(int[] b, int mx, int my) {
        return Draw.in(mx, my, b[0], b[1], b[2], b[3]);
    }

    private static int presetIndex(int rgb) {
        for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
            if (ColorSetting.PRESETS[i] == (rgb & 0xFFFFFF)) return i;
        }
        return -1;
    }

    private static String colorName(int rgb) {
        int i = presetIndex(rgb);
        return Lang.vi(i < 0 || i >= COLOR_NAMES.length ? "Custom" : COLOR_NAMES[i]);
    }

    private static boolean overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    /** Chọn một HUD: reset cuộn và chạy lại animation trượt vào của bảng. */
    private void select(HudModule h) {
        if (h != selected) {
            panelScroll = panelTarget = 0f;
            selAt = System.currentTimeMillis();
            hovAnim.clear();
            swAnim.clear();
        }
        selected = h;
    }

    // ------------------------------------------------------------------ animation

    private float smooth() {
        return AnimationsModule.menu() ? 0.3f : 1f;
    }

    /** Đưa giá trị đang lưu tiến dần tới đích (khởi tạo bằng {@code init} lần đầu). */
    private static float approach(Map<String, Float> m, String key, float init, float target, float k) {
        Float c = m.get(key);
        float v = c == null ? init : c;
        v += (target - v) * k;
        if (Math.abs(target - v) < 0.01f) v = target;
        m.put(key, v);
        return v;
    }

    /** Hàng thứ i trượt vào lần lượt khi vừa chọn HUD. */
    private float stagger(int i) {
        if (!AnimationsModule.menu()) return 1f;
        float t = (System.currentTimeMillis() - selAt) * AnimationsModule.speed() / 240f - Math.min(i, 10) * 0.06f;
        return AnimationsModule.ease(t);
    }

    /** 0 -> 1 khi mở editor. */
    private float openProgress() {
        if (!AnimationsModule.menu()) return 1f;
        return AnimationsModule.ease((System.currentTimeMillis() - openStart) * AnimationsModule.speed() / 220f);
    }

    private String rowKey(PRow r) {
        return r.kind + ":" + (r.s != null ? r.s.label : r.a != null ? r.a.label : "");
    }

    // ------------------------------------------------------------------ thành phần vẽ chung với menu

    /** Công tắc nhỏ BẬT/TẮT (giống MenuScreen); a = 0..1 để trượt mượt. */
    private static void drawSwitch(int tx, int ty, float a) {
        Gui.drawRect(tx - 1, ty - 1, tx + 19, ty + 9, 0xFF000000);
        Gui.drawRect(tx, ty, tx + 18, ty + 8, Draw.lerp(0xFF7A2A2A, 0xFF2E9B2E, a));
        int kx = tx + 1 + Math.round(10 * a);
        Gui.drawRect(kx, ty + 1, kx + 6, ty + 7, 0xFFEEEEEE);
    }

    /** Hình viên thuốc (bo tròn 1px hai đầu), không chồng alpha. */
    private static void pill(int x1, int y1, int x2, int y2, int c) {
        if (y2 - y1 < 3 || x2 - x1 < 3) {
            Gui.drawRect(x1, y1, x2, y2, c);
            return;
        }
        Gui.drawRect(x1 + 1, y1, x2 - 1, y1 + 1, c);
        Gui.drawRect(x1, y1 + 1, x2, y2 - 1, c);
        Gui.drawRect(x1 + 1, y2 - 1, x2 - 1, y2, c);
    }

    /** Nút phẳng kiểu menu: nền mờ, rê chuột sáng dần + vạch màu nhấn bên trái. */
    private float drawFlatBtn(int[] b, int x, int y, int w, int h, String label, boolean over, float hov) {
        if (b != null) { b[0] = x; b[1] = y; b[2] = w; b[3] = h; }
        hov += ((over ? 1f : 0f) - hov) * smooth();
        if (Math.abs((over ? 1f : 0f) - hov) < 0.01f) hov = over ? 1f : 0f;
        Gui.drawRect(x, y, x + w, y + h, Draw.lerp(0x22FFFFFF, 0x3CFFFFFF, hov));
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0x18000000);
        if (hov > 0.02f) Gui.drawRect(x, y, x + Math.round(2 * hov), y + h, Draw.ACCENT);
        String t = fontRendererObj.trimStringToWidth(label, w - 8);
        fontRendererObj.drawStringWithShadow(t, x + (w - fontRendererObj.getStringWidth(t)) / 2f, y + (h - 8) / 2f, 0xFFFFFF);
        return hov;
    }

    // ------------------------------------------------------------------ bảng cài đặt

    private void buildRows() {
        prows.clear();
        if (selected == null) return;
        PRow sc = new PRow();
        sc.kind = K_SCALE;
        prows.add(sc);
        for (Setting st : selected.settings) {
            if (!st.isVisible() || st == selected.scale) continue;
            PRow r = new PRow();
            r.s = st;
            if (st instanceof SliderSetting) r.kind = K_SLIDER;
            else if (st instanceof BoolSetting) r.kind = K_BOOL;
            else if (st instanceof ModeSetting) r.kind = K_MODE;
            else if (st instanceof ColorSetting) r.kind = K_COLOR;
            else continue;
            prows.add(r);
        }
        for (Module.Action a : selected.actions()) {
            PRow r = new PRow();
            r.kind = K_ACTION;
            r.a = a;
            prows.add(r);
        }
        PRow reset = new PRow();
        reset.kind = K_RESET;
        prows.add(reset);
    }

    /** Tính vị trí bảng (chọn góc không đè lên HUD đang chọn) và dấu X. Gọi mỗi khung hình / mỗi lần bấm. */
    private void layoutPanel(ScaledResolution sr) {
        panelOn = selected != null && selected.enabled;
        bX[0] = bX[1] = -100;
        bX[2] = bX[3] = 0;
        if (!panelOn) {
            prows.clear();
            return;
        }
        int rx = selected.renderX(sr, true), ry = selected.renderY(sr, true);
        int bw = selected.boxW(true), bh = selected.boxH(true);

        int xx = rx + bw + 4;
        if (xx + XS > width - 2) xx = width - XS - 2;
        int xy = Math.max(2, ry - 2);
        bX[0] = xx; bX[1] = xy; bX[2] = XS; bX[3] = XS;

        buildRows();
        contentH = prows.size() * (RH + GAP) + 2;
        ph = Math.max(HEAD + 40, Math.min(HEAD + 8 + contentH, height - 36 - FOOT));
        viewH = ph - HEAD - 8;
        float maxScroll = Math.max(0, contentH - viewH);
        panelScroll = Math.max(0f, Math.min(maxScroll, panelScroll));
        panelTarget = Math.max(0f, Math.min(maxScroll, panelTarget));

        int bottomY = height - ph - FOOT;
        int[][] cand = {
            { width - PW - 8, bottomY }, { 8, bottomY },
            { width - PW - 8, 36 }, { 8, 36 }
        };
        int[] pick = cand[0];
        for (int[] c : cand) {
            if (!overlap(c[0], c[1], PW, ph, rx - 4, ry - 4, bw + 8 + XS + 10, bh + 8)) { pick = c; break; }
        }
        if (panelCustom) {
            px = Math.max(0, Math.min(width - PW, customX));
            py = Math.max(0, Math.min(height - ph, customY));
        } else {
            px = pick[0];
            py = pick[1];
        }
        viewTop = py + HEAD + 4;

        int rw = PW - 12 - (contentH > viewH ? BAR_W + 2 : 0);
        int sc = Math.round(panelScroll);
        for (int i = 0; i < prows.size(); i++) {
            PRow r = prows.get(i);
            r.x = px + 6;
            r.w = rw;
            r.y = viewTop + i * (RH + GAP) - sc;
        }
    }

    private String valueText(PRow r) {
        switch (r.kind) {
            case K_SCALE: return String.format(Locale.ROOT, "%.2fx", selected.getScale());
            case K_MODE: return Lang.vi(((ModeSetting) r.s).get());
            case K_COLOR: return colorName(((ColorSetting) r.s).value);
            case K_SLIDER: return ((SliderSetting) r.s).display();
            default: return "";
        }
    }

    private void drawRow(PRow r, int i, int mx, int my) {
        FontRenderer fr = fontRendererObj;
        String key = rowKey(r);
        float k = smooth();
        float st = stagger(i);
        boolean inView = mx >= px && mx < px + PW && my >= viewTop && my < viewTop + viewH;
        boolean over = st > 0.7f && inView && Draw.in(mx, my, r.x, r.y, r.w, RH);
        float hov = approach(hovAnim, key, 0f, over && r.kind != K_SCALE ? 1f : 0f, k);

        int x = r.x, y = r.y, w = r.w, h = RH;
        int ty = y + (h - 8) / 2;

        GlStateManager.pushMatrix();
        GlStateManager.translate((1f - st) * 22f, 0f, 0f);

        // nền
        Gui.drawRect(x, y, x + w, y + h, Draw.lerp(0x22FFFFFF, 0x3CFFFFFF, hov));
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0x18000000);
        if (r.kind == K_SLIDER) {
            int fw = (int) Math.round(((SliderSetting) r.s).ratio() * w);
            Gui.drawRect(x, y, x + fw, y + h - 1, Draw.withAlpha(Draw.ACCENT, 0x55));
            Gui.drawRect(Math.max(x, x + fw - 2), y, x + Math.max(2, fw), y + h - 1, Draw.ACCENT);
        }
        if (hov > 0.02f) Gui.drawRect(x, y, x + Math.round(2 * hov), y + h, Draw.ACCENT);

        // chữ
        switch (r.kind) {
            case K_SCALE: {
                fr.drawStringWithShadow("Kích cỡ", x + 7, ty, 0xFFFFFF);
                int mxn = x + w - 2 * RH, pxn = x + w - RH;
                String val = valueText(r);
                fr.drawStringWithShadow(val, mxn - 6 - fr.getStringWidth(val), ty, Draw.ACCENT_LIGHT);
                boolean hm = st > 0.7f && inView && Draw.in(mx, my, mxn, y, RH - 1, RH);
                boolean hp = st > 0.7f && inView && Draw.in(mx, my, pxn, y, RH, RH);
                Gui.drawRect(mxn, y, mxn + RH - 1, y + h - 1, hm ? Draw.ACCENT_DIM : 0x33000000);
                Gui.drawRect(pxn, y, pxn + RH, y + h - 1, hp ? Draw.ACCENT_DIM : 0x33000000);
                fr.drawStringWithShadow("-", mxn + (RH - 1 - fr.getStringWidth("-")) / 2f, ty, 0xFFFFFF);
                fr.drawStringWithShadow("+", pxn + (RH - fr.getStringWidth("+")) / 2f, ty, 0xFFFFFF);
                break;
            }
            case K_BOOL: {
                float swT = ((BoolSetting) r.s).value ? 1f : 0f;
                float sw = approach(swAnim, key, swT, swT, k);
                fr.drawStringWithShadow(fr.trimStringToWidth(Lang.vi(r.s.label), w - 40), x + 7, ty, 0xFFFFFF);
                drawSwitch(x + w - 26, ty, sw);
                break;
            }
            case K_MODE:
            case K_COLOR:
            case K_SLIDER: {
                String right = valueText(r);
                int extra = r.kind == K_COLOR ? 18 : 0;
                int rr = x + w - 7 - extra;
                int rw = fr.getStringWidth(right);
                String left = fr.trimStringToWidth(Lang.vi(r.s.label), Math.max(10, w - 14 - rw - 10 - extra));
                fr.drawStringWithShadow(left, x + 7, ty, 0xFFFFFF);
                fr.drawStringWithShadow(right, rr - rw, ty, Draw.ACCENT_LIGHT);
                if (r.kind == K_COLOR) {
                    int sx = x + w - 17, sy = y + (h - 10) / 2;
                    Gui.drawRect(sx - 1, sy - 1, sx + 11, sy + 11, 0xFF000000);
                    Gui.drawRect(sx, sy, sx + 10, sy + 10, 0xFF000000 | ((ColorSetting) r.s).value);
                }
                break;
            }
            default: {
                String label = r.kind == K_ACTION ? r.a.label : "Đặt lại tuỳ chỉnh";
                String t = fr.trimStringToWidth(label, w - 8);
                fr.drawStringWithShadow(t, x + (w - fr.getStringWidth(t)) / 2f, ty, 0xFFFFFF);
                break;
            }
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ---- thanh cuộn kiểu Sodium (giống MenuScreen)

    private int barX() {
        return px + PW - BAR_W - 1;
    }

    private int thumbH() {
        return Math.max(20, viewH * viewH / Math.max(1, contentH));
    }

    private int thumbY(float scroll) {
        return viewTop + Math.round((viewH - thumbH()) * (scroll / Math.max(1, contentH - viewH)));
    }

    private void scrollbar() {
        int x = barX(), y = viewTop;
        int bh = thumbH(), by = thumbY(panelScroll);
        boolean hover = curMx >= x - 3 && curMx < x + BAR_W + 2 && curMy >= y && curMy < y + viewH;
        float target = dragBar || hover ? 1f : 0f;
        barAnim += (target - barAnim) * smooth();
        if (Math.abs(target - barAnim) < 0.02f) barAnim = target;

        int tw = 3 + Math.round(2 * barAnim);
        int tx1 = x + 1 + Math.round((5 - tw) / 2f);
        pill(x + 1, y + 2, x + 6, y + viewH - 2, 0x55000000); // rãnh
        int alpha = dragBar ? 0xFF : 0x99 + Math.round(0x33 * barAnim);
        pill(tx1, by + 2, tx1 + tw, Math.min(y + viewH - 2, by + bh - 2), (alpha << 24) | 0xFFFFFF);
    }

    /** Bấm chuột trái vào thanh cuộn: bắt đầu kéo (bấm ngoài tay nắm thì nhảy tới đó). */
    private boolean startBarDrag(int mx, int my) {
        if (contentH <= viewH) return false;
        int x = barX();
        if (mx < x - 3 || mx >= x + BAR_W + 2 || my < viewTop || my >= viewTop + viewH) return false;
        int by = thumbY(panelScroll), bh = thumbH();
        dragBar = true;
        dragOff = (my >= by && my < by + bh) ? my - by : bh / 2;
        dragBarTo(my);
        return true;
    }

    private void dragBarTo(int my) {
        int range = Math.max(1, viewH - thumbH());
        float v = (my - dragOff - viewTop) / (float) range * (contentH - viewH);
        v = Math.max(0f, Math.min(Math.max(0, contentH - viewH), v));
        panelScroll = panelTarget = v;
    }

    private void drawPanel(int mx, int my) {
        if (!panelOn) return;
        FontRenderer fr = fontRendererObj;
        float e = AnimationsModule.menu()
                ? AnimationsModule.ease((System.currentTimeMillis() - selAt) * AnimationsModule.speed() / 200f) : 1f;

        // nền + hai đường kẻ trên / dưới (như khung của menu), hiện dần khi chọn HUD
        Gui.drawRect(px, py, px + PW, py + ph, Draw.mulAlpha(0x99000000, e));
        Gui.drawRect(px, py, px + PW, py + 1, 0x33FFFFFF);
        Gui.drawRect(px, py + ph - 1, px + PW, py + ph, 0x33FFFFFF);

        // tiêu đề
        GlStateManager.pushMatrix();
        GlStateManager.translate(px + 8, py + 7, 0f);
        GlStateManager.scale(1.2f, 1.2f, 1f);
        fr.drawStringWithShadow(fr.trimStringToWidth(Lang.vi(selected.name), 100), 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
        String cat = selected.category.label;
        fr.drawStringWithShadow(cat, px + PW - 8 - fr.getStringWidth(cat), py + 9, 0x909090);
        int uw = Math.round((PW - 12) * e);
        Gui.drawRect(px + 6, py + HEAD - 2, px + 6 + uw, py + HEAD - 1, Draw.ACCENT_DIM);

        // thanh kéo: giữ chuột trái vào tiêu đề để kéo bảng tới bất kỳ đâu, chuột phải = về vị trí tự động
        int gx = px + PW - 8 - fr.getStringWidth(cat) - 12 - 34, gy = py + 10;
        boolean gh = dragPanel || Draw.in(mx, my, px, py, PW, HEAD);
        pill(gx, gy, gx + 34, gy + 5, gh ? 0xFFFFFFFF : 0x80FFFFFF);

        // các hàng (cắt theo vùng cuộn)
        Draw.scissor(mc, px, viewTop, PW, viewH);
        for (int i = 0; i < prows.size(); i++) {
            PRow r = prows.get(i);
            if (r.y + RH < viewTop || r.y > viewTop + viewH) continue;
            drawRow(r, i, mx, my);
        }
        Draw.endScissor();

        if (contentH > viewH) scrollbar();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ vẽ

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ThemeModule.apply();
        curMx = mx;
        curMy = my;
        float op = openProgress();
        ScaledResolution sr = new ScaledResolution(mc);

        // cuộn mượt
        panelScroll += (panelTarget - panelScroll) * (AnimationsModule.menu() ? 0.35f : 1f);
        if (Math.abs(panelTarget - panelScroll) < 0.4f) panelScroll = panelTarget;

        drawGradientRect(0, 0, width, height, Draw.mulAlpha(0x98101010, op), Draw.mulAlpha(0xA8101010, op));
        layoutPanel(sr);

        // đường giữa mờ
        Gui.drawRect(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
        Gui.drawRect(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);
        if (guideV) Gui.drawRect(width / 2, 0, width / 2 + 1, height, Draw.withAlpha(Draw.ACCENT, 0xAA));
        if (guideH) Gui.drawRect(0, height / 2, width, height / 2 + 1, Draw.withAlpha(Draw.ACCENT, 0xAA));

        HudModule hover = hudAt(mx, my, sr);
        for (Module m : LunarLite.MODULES) {
            if (!(m instanceof HudModule) || !m.enabled) continue;
            HudModule h = (HudModule) m;
            h.render(sr, true);
            int rx = h.renderX(sr, true), ry = h.renderY(sr, true);
            int w = h.boxW(true), hh = h.boxH(true);
            int col = h == dragging ? Draw.ACCENT_LIGHT : (h == selected ? Draw.ACCENT : (h == hover ? 0xAAFFFFFF : 0x40FFFFFF));
            Draw.outline(rx - 2, ry - 2, rx + w + 2, ry + hh + 2, col);
            if (h == selected) {
                String tag = Lang.vi(h.name) + "  " + String.format(Locale.ROOT, "%.2fx", h.getScale());
                int tw = fontRendererObj.getStringWidth(tag) + 10;
                int ty = ry - 15 < 2 ? ry + hh + 4 : ry - 15;
                Gui.drawRect(rx - 2, ty, rx - 2 + tw, ty + 12, 0xCC000000);
                Gui.drawRect(rx - 2, ty, rx, ty + 12, Draw.ACCENT);
                fontRendererObj.drawStringWithShadow(tag, rx + 4, ty + 2, 0xFFFFFF);
            }
        }

        // dấu X: xoá (= tắt) HUD đang chọn
        if (panelOn) {
            boolean ox = hit(bX, mx, my);
            int x = bX[0], y = bX[1];
            Gui.drawRect(x - 1, y - 1, x + XS + 1, y + XS + 1, 0xFF000000);
            Gui.drawRect(x, y, x + XS, y + XS, ox ? 0xFFFF5555 : 0xFFCC3333);
            for (int i = 2; i < XS - 2; i++) {
                Gui.drawRect(x + i, y + i, x + i + 1, y + i + 1, 0xFFFFFFFF);
                Gui.drawRect(x + XS - 1 - i, y + i, x + XS - i, y + i + 1, 0xFFFFFFFF);
            }
        }

        // tiêu đề (giống menu: chữ giữa + gạch chân màu nhấn nở ra)
        int cx = width / 2;
        String title = "Zhwii Client  -  Sửa vị trí HUD";
        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, (1f - op) * 10f, 0f);
        drawCenteredString(fontRendererObj, title, cx, 10, 0xFFFFFF);
        int uw = Math.round((fontRendererObj.getStringWidth(title) / 2f + 8) * op);
        Gui.drawRect(cx - uw, 19, cx + uw, 20, Draw.ACCENT);
        GlStateManager.popMatrix();

        // dòng gợi ý ngay trên nút quay lại (cùng vị trí với dòng mô tả của menu)
        int doneY = height - 28;
        String hint = fontRendererObj.trimStringToWidth(
                "Kéo để di chuyển  |  Lăn chuột để đổi cỡ  |  Phím mũi tên để nhích  |  Chuột phải để đặt lại vị trí",
                width - 20);
        drawCenteredString(fontRendererObj, hint, cx, doneY - 13, 0xE0E0E0);

        // nút quay lại menu (nút phẳng giống nút "Xong")
        int bx = cx - WIDE / 2;
        backHov = drawFlatBtn(bBack, bx, doneY, WIDE, BH, "Quay lại menu",
                Draw.in(mx, my, bx, doneY, WIDE, BH), backHov);

        drawPanel(mx, my);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ chuột

    private void resize(HudModule h, double delta) {
        h.scale.set(h.scale.value + delta);
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    private void setSlider(int mx) {
        if (dragSlider == null) return;
        float r = (mx - (dragX + 4)) / (float) (dragW - 8);
        dragSlider.setRatio(Math.max(0f, Math.min(1f, r)));
    }

    private void panelClick(int mx, int my, int btn) {
        if (my < viewTop || my >= viewTop + viewH) return;
        for (PRow r : prows) {
            if (!Draw.in(mx, my, r.x, r.y, r.w, RH)) continue;
            click();
            switch (r.kind) {
                case K_SCALE:
                    if (mx >= r.x + r.w - RH) resize(selected, 0.05);
                    else if (mx >= r.x + r.w - 2 * RH) resize(selected, -0.05);
                    break;
                case K_BOOL:
                    ((BoolSetting) r.s).toggle();
                    break;
                case K_MODE:
                    if (btn == 1) ((ModeSetting) r.s).prev();
                    else ((ModeSetting) r.s).next();
                    break;
                case K_COLOR: {
                    ColorSetting col = (ColorSetting) r.s;
                    int n = ColorSetting.PRESETS.length, i = presetIndex(col.value);
                    int next = btn != 1 ? (i + 1) % n : (i < 0 ? n - 1 : (i + n - 1) % n);
                    col.value = ColorSetting.PRESETS[next];
                    break;
                }
                case K_SLIDER:
                    if (btn == 0) {
                        dragSlider = (SliderSetting) r.s;
                        dragX = r.x;
                        dragW = r.w;
                        setSlider(mx);
                    }
                    break;
                case K_ACTION:
                    if (btn == 0) r.a.run.run();
                    break;
                default:
                    selected.resetSettings();
                    selected.resetPosition();
                    break;
            }
            return;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        layoutPanel(sr);

        if (btn == 0 && hit(bBack, mx, my)) {
            click();
            mc.displayGuiScreen(new MenuScreen());
            return;
        }

        // bảng cài đặt của HUD đang chọn
        if (panelOn && Draw.in(mx, my, px, py, PW, ph)) {
            if (my < py + HEAD) {
                if (btn == 0) {
                    dragPanel = true;
                    panOffX = mx - px;
                    panOffY = my - py;
                    panelCustom = true;
                    customX = px;
                    customY = py;
                } else if (btn == 1) {
                    panelCustom = false;
                }
                return;
            }
            if (btn == 0 && startBarDrag(mx, my)) return;
            if (btn == 0 || btn == 1) panelClick(mx, my, btn);
            return;
        }

        // dấu X: xoá (tắt) HUD
        if (panelOn && btn == 0 && hit(bX, mx, my)) {
            click();
            if (selected.enabled) selected.toggle();
            selected = null;
            dragging = null;
            return;
        }

        HudModule h = hudAt(mx, my, sr);
        if (h == null) {
            if (btn == 0) selected = null;
            return;
        }
        select(h);
        if (btn == 1) {
            h.resetPosition();
        } else if (btn == 0) {
            dragging = h;
            offX = mx - h.renderX(sr, true);
            offY = my - h.renderY(sr, true);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (dragBar && btn == 0) {
            dragBarTo(my);
            return;
        }
        if (dragPanel) {
            customX = Math.max(0, Math.min(width - PW, mx - panOffX));
            customY = Math.max(0, Math.min(height - ph, my - panOffY));
            return;
        }
        if (dragSlider != null) {
            setSlider(mx);
            return;
        }
        if (dragging == null) return;
        int bw = dragging.boxW(true), bh = dragging.boxH(true);
        int nx = mx - offX, ny = my - offY;
        guideV = guideH = false;

        int cxp = (width - bw) / 2, cyp = (height - bh) / 2;
        if (Math.abs(nx - cxp) <= SNAP) { nx = cxp; guideV = true; }
        else if (Math.abs(nx) <= SNAP) nx = 0;
        else if (Math.abs(nx - (width - bw)) <= SNAP) nx = width - bw;

        if (Math.abs(ny - cyp) <= SNAP) { ny = cyp; guideH = true; }
        else if (Math.abs(ny) <= SNAP) ny = 0;
        else if (Math.abs(ny - (height - bh)) <= SNAP) ny = height - bh;

        dragging.x = Math.max(0, Math.min(width - bw, nx));
        dragging.y = Math.max(0, Math.min(height - bh, ny));
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        dragging = null;
        dragSlider = null;
        dragPanel = false;
        dragBar = false;
        guideV = guideH = false;
        super.mouseReleased(mx, my, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        ScaledResolution sr = new ScaledResolution(mc);
        layoutPanel(sr);
        if (panelOn && Draw.in(mx, my, px, py, PW, ph)) {
            panelTarget += (d > 0 ? -1 : 1) * 2f * (RH + GAP);
            panelTarget = Math.max(0f, Math.min(Math.max(0, contentH - viewH), panelTarget));
            return;
        }
        HudModule h = hudAt(mx, my, sr);
        if (h == null) h = selected;
        if (h != null) {
            resize(h, d > 0 ? 0.05 : -0.05);
            select(h);
        }
    }

    // ------------------------------------------------------------------ phím

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(new MenuScreen());
            return;
        }
        if (LunarLite.keyMenu != null && keyCode == LunarLite.keyMenu.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (selected == null) return;
        int step = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) ? 5 : 1;
        if (keyCode == Keyboard.KEY_LEFT) selected.x = Math.max(0, selected.x - step);
        else if (keyCode == Keyboard.KEY_RIGHT) selected.x += step;
        else if (keyCode == Keyboard.KEY_UP) selected.y = Math.max(0, selected.y - step);
        else if (keyCode == Keyboard.KEY_DOWN) selected.y += step;
    }

    @Override
    public void onGuiClosed() {
        LunarLite.saveConfig();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
