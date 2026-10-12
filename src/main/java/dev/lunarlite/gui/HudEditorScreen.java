package dev.lunarlite.gui;

import dev.lunarlite.Lang;
import dev.lunarlite.LunarLite;
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
import java.util.List;
import java.util.Locale;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Chỉnh vị trí HUD, cùng phong cách với MenuScreen (nền tối, vạch màu nhấn, khung 66000000).
 *  - Kéo thả để di chuyển (tự hít vào mép / giữa màn hình), lăn chuột để đổi cỡ, phím mũi tên để nhích.
 *  - Bấm vào một HUD: nó được chọn, một bảng cài đặt của HUD đó hiện ở góc màn hình
 *    (kích cỡ, mọi tuỳ chỉnh, thao tác, đặt lại) và có dấu X bên cạnh khung để xoá (= tắt) HUD đó.
 *    HUD đã xoá có thể bật lại trong Menu.
 */
public class HudEditorScreen extends GuiScreen {
    private static final int SNAP = 5;
    private static final int PW = 184, RH = 18, GAP = 3, HEAD = 24, XS = 11, BAR_W = 5;
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
    private float panelScroll;
    private SliderSetting dragSlider;
    private int dragX, dragW;

    // vị trí bảng do người dùng tự kéo (nhớ giữa các lần mở editor)
    private static boolean panelCustom;
    private static int customX, customY;
    private boolean dragPanel;
    private int panOffX, panOffY;

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

    private static String onoff(boolean v) {
        return v ? "§aBẬT" : "§cTẮT";
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

    /** Nút kiểu menu: nền tối, rê chuột thì sáng lên và có vạch màu nhấn bên trái. */
    private void drawBtn(int[] b, int x, int y, int w, int h, String label, boolean hover) {
        if (b != null) { b[0] = x; b[1] = y; b[2] = w; b[3] = h; }
        Gui.drawRect(x, y, x + w, y + h, 0x99000000);
        Gui.drawRect(x, y, x + w, y + 1, 0x33FFFFFF);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0x33FFFFFF);
        if (hover) {
            Gui.drawRect(x, y, x + w, y + h, Draw.withAlpha(0xFFFFFF, 38));
            Gui.drawRect(x, y, x + 2, y + h, Draw.ACCENT);
        }
        fontRendererObj.drawStringWithShadow(label, x + (w - fontRendererObj.getStringWidth(label)) / 2f,
                y + (h - 8) / 2f, 0xFFFFFF);
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
        contentH = prows.size() * (RH + GAP);
        ph = Math.max(HEAD + 40, Math.min(HEAD + 8 + contentH, height - 36 - 10));
        viewH = ph - HEAD - 8;
        panelScroll = Math.max(0f, Math.min(Math.max(0, contentH - viewH), panelScroll));

        int[][] cand = {
            { width - PW - 8, height - ph - 8 }, { 8, height - ph - 8 },
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

    private String rowText(PRow r) {
        switch (r.kind) {
            case K_SCALE: return "Kích cỡ: " + String.format(Locale.ROOT, "%.2fx", selected.getScale());
            case K_BOOL: return Lang.vi(r.s.label) + ": " + onoff(((BoolSetting) r.s).value);
            case K_MODE: return Lang.vi(r.s.label) + ": " + Lang.vi(((ModeSetting) r.s).get());
            case K_COLOR: return Lang.vi(r.s.label) + ": " + colorName(((ColorSetting) r.s).value);
            case K_SLIDER: return Lang.vi(r.s.label) + ": " + ((SliderSetting) r.s).display();
            case K_ACTION: return r.a.label;
            default: return "Đặt lại tuỳ chỉnh";
        }
    }

    private void drawRow(PRow r, int mx, int my) {
        boolean inView = mx >= px && mx < px + PW && my >= viewTop && my < viewTop + viewH;
        boolean over = inView && Draw.in(mx, my, r.x, r.y, r.w, RH);
        Gui.drawRect(r.x, r.y, r.x + r.w, r.y + RH, 0x80000000);
        Gui.drawRect(r.x, r.y, r.x + r.w, r.y + 1, 0x22FFFFFF);

        if (r.kind == K_SLIDER) {
            int fw = (int) Math.round((r.w - 2) * (double) ((SliderSetting) r.s).ratio());
            Gui.drawRect(r.x + 1, r.y + 1, r.x + 1 + fw, r.y + RH - 1, Draw.ACCENT_FILL);
        }
        if (over && r.kind != K_SCALE) {
            Gui.drawRect(r.x, r.y, r.x + r.w, r.y + RH, Draw.withAlpha(0xFFFFFF, 38));
            Gui.drawRect(r.x, r.y, r.x + 2, r.y + RH, Draw.ACCENT);
        }

        int ty = r.y + (RH - 8) / 2;
        if (r.kind == K_SCALE) {
            fontRendererObj.drawStringWithShadow(rowText(r), r.x + 6, ty, 0xFFFFFF);
            int mxn = r.x + r.w - 2 * RH, pxn = r.x + r.w - RH;
            boolean hm = inView && Draw.in(mx, my, mxn, r.y, RH - 1, RH);
            boolean hp = inView && Draw.in(mx, my, pxn, r.y, RH, RH);
            Gui.drawRect(mxn, r.y, mxn + RH - 1, r.y + RH, hm ? Draw.ACCENT_DIM : 0x55000000);
            Gui.drawRect(pxn, r.y, pxn + RH, r.y + RH, hp ? Draw.ACCENT_DIM : 0x55000000);
            fontRendererObj.drawStringWithShadow("-", mxn + (RH - 1 - fontRendererObj.getStringWidth("-")) / 2f, ty, 0xFFFFFF);
            fontRendererObj.drawStringWithShadow("+", pxn + (RH - fontRendererObj.getStringWidth("+")) / 2f, ty, 0xFFFFFF);
            return;
        }
        int room = r.w - (r.kind == K_COLOR ? 30 : 10);
        String t = fontRendererObj.trimStringToWidth(rowText(r), room);
        fontRendererObj.drawStringWithShadow(t, r.x + (r.kind == K_COLOR ? 6 : (r.w - fontRendererObj.getStringWidth(t)) / 2), ty,
                0xFFFFFF);
        if (r.kind == K_COLOR) {
            int sx = r.x + r.w - 16, sy = r.y + 4;
            Gui.drawRect(sx - 1, sy - 1, sx + 11, sy + 11, 0xFF000000);
            Gui.drawRect(sx, sy, sx + 10, sy + 10, 0xFF000000 | ((ColorSetting) r.s).value);
        }
    }

    private void drawPanel(int mx, int my) {
        if (!panelOn) return;
        Gui.drawRect(px, py, px + PW, py + ph, 0xA0000000);
        Gui.drawRect(px, py, px + PW, py + 1, 0x33FFFFFF);
        Gui.drawRect(px, py + ph - 1, px + PW, py + ph, 0x33FFFFFF);

        // tiêu đề
        GlStateManager.pushMatrix();
        GlStateManager.translate(px + 8, py + 7, 0f);
        GlStateManager.scale(1.2f, 1.2f, 1f);
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(Lang.vi(selected.name), 105), 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
        String cat = selected.category.label;
        fontRendererObj.drawStringWithShadow(cat, px + PW - 8 - fontRendererObj.getStringWidth(cat), py + 8, 0x909090);
        Gui.drawRect(px + 6, py + HEAD - 2, px + PW - 6, py + HEAD - 1, Draw.ACCENT_DIM);

        // thanh trắng: giữ chuột trái vào đây (hoặc vào cả tiêu đề) để kéo bảng tới bất kỳ đâu, chuột phải = về vị trí tự động
        int gx = px + PW - 8 - fontRendererObj.getStringWidth(cat) - 12 - 34, gy = py + 8;
        boolean gh = dragPanel || Draw.in(mx, my, px, py, PW, HEAD);
        Gui.drawRect(gx - 1, gy - 1, gx + 35, gy + 6, 0x66000000);
        Gui.drawRect(gx, gy, gx + 34, gy + 5, gh ? 0xFFFFFFFF : 0xB0FFFFFF);

        // các hàng (cắt theo vùng cuộn)
        Draw.scissor(mc, px, viewTop, PW, viewH);
        for (PRow r : prows) {
            if (r.y + RH < viewTop || r.y > viewTop + viewH) continue;
            drawRow(r, mx, my);
        }
        Draw.endScissor();

        if (contentH > viewH) {
            int bx = px + PW - BAR_W - 2;
            int th = Math.max(16, viewH * viewH / Math.max(1, contentH));
            int ty = viewTop + Math.round((viewH - th) * (panelScroll / Math.max(1, contentH - viewH)));
            Gui.drawRect(bx, viewTop, bx + BAR_W, viewTop + viewH, 0x33FFFFFF);
            Gui.drawRect(bx, ty, bx + BAR_W, ty + th, Draw.withAlpha(Draw.ACCENT, 0xDD));
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ vẽ

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ThemeModule.apply();
        ScaledResolution sr = new ScaledResolution(mc);
        drawGradientRect(0, 0, width, height, 0x98101010, 0xA8101010);
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

        // tiêu đề + gợi ý (giống menu)
        int cx = width / 2;
        String title = "Zhwii Client  -  Sửa vị trí HUD";
        drawCenteredString(fontRendererObj, title, cx, 10, 0xFFFFFF);
        int uw = fontRendererObj.getStringWidth(title) / 2 + 8;
        Gui.drawRect(cx - uw, 19, cx + uw, 20, Draw.ACCENT);
        String hint = "Kéo để di chuyển  |  Lăn chuột để đổi cỡ  |  Phím mũi tên để nhích  |  Chuột phải để đặt lại vị trí";
        fontRendererObj.drawStringWithShadow(hint, (width - fontRendererObj.getStringWidth(hint)) / 2f, 24, 0xB0B0B0);
        drawBtn(bBack, 8, 8, 56, 16, "< Menu", Draw.in(mx, my, 8, 8, 56, 16));

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

        if (btn == 0 && hit(bBack, mx, my)) { mc.displayGuiScreen(new MenuScreen()); return; }

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
        if (h != selected) panelScroll = 0f;
        selected = h;
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
            panelScroll += (d > 0 ? -1 : 1) * 2f * (RH + GAP);
            panelScroll = Math.max(0f, Math.min(Math.max(0, contentH - viewH), panelScroll));
            return;
        }
        HudModule h = hudAt(mx, my, sr);
        if (h == null) h = selected;
        if (h != null) {
            resize(h, d > 0 ? 0.05 : -0.05);
            selected = h;
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
