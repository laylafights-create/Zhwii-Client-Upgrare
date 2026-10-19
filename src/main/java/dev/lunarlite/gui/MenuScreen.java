package dev.lunarlite.gui;

import dev.lunarlite.Lang;
import dev.lunarlite.LunarLite;
import dev.lunarlite.module.AnimationsModule;
import dev.lunarlite.module.Category;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Menu dạng bảng ô (dashboard):
 *   TRANG CHỦ : mỗi tính năng là một ô có biểu tượng riêng, số phím tắt 1-9 / 0, trạng thái BẬT / TẮT.
 *               Thanh nhóm ở trên lọc theo nhóm, ô chia thành từng trang (10 ô / trang).
 *   CHI TIẾT  : bấm một ô (hoặc nhấn phím số) để mở bảng tuỳ chỉnh của tính năng đó.
 * Chuột trái vào ô = mở tuỳ chỉnh, chuột phải = bật/tắt nhanh. ESC = quay lại / đóng menu.
 *
 * Toàn bộ giao diện được vẽ trong một khung ảo VW x VH rồi tự thu nhỏ cho vừa màn hình.
 */
public class MenuScreen extends GuiScreen {
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };

    // ---- khung ảo
    private static final int VW = 460, VH = 300, PAD = 14;
    private static final int BH = 20, ROW = 24, GAP = 6, BAR_W = 6;
    // ---- các vùng theo trục dọc
    private static final int TAB_Y = 76, TAB_H = 18, TAB_GAP = 4;
    private static final int GRID_Y = 102, CARD_H = 62, CARD_GAP = 6, COLS = 5, GRID_ROWS = 2;
    private static final int PER_PAGE = COLS * GRID_ROWS;
    private static final int PAGE_Y = 238, HINT_Y = 254, BTN_Y = 270;
    private static final int PANE_TOP = 78, PANE_BOTTOM = 264;

    // ---- màu
    private static final int PANEL_BG = 0xFF101B33;
    private static final int PANEL_EDGE = 0xFF2A3F6E;
    private static final int DIVIDER = 0xFF24365F;
    private static final int STRIP_BG = 0xFF0C1630;
    private static final int CARD_BG = 0xFF15244A;
    private static final int CARD_BG_HOT = 0xFF1F3568;
    private static final int TEXT_DIM = 0xFF8FA0C8;
    private static final int TEXT_SOFT = 0xFFC3CEEA;
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFFF6B7A;
    private static final int OFF = 0xFF6C7A99;
    /** Mỗi tính năng một màu nhấn riêng (theo thứ tự đăng ký). */
    private static final int[] PALETTE = {
        0x4ADE80, 0xFFB347, 0x35D0BA, 0x5B9BFF, 0xFF6B7A, 0xB070FF, 0xFF8A3D, 0xFF5FA2, 0xA0AEC0, 0x4DA3FF
    };

    private static final int K_DONE = 0, K_EDIT = 1, K_ENABLE = 2, K_BOOL = 3, K_MODE = 4, K_COLOR = 5,
            K_SLIDER = 6, K_ACTION = 7, K_RESET = 8, K_BACK = 9;

    /** Sắp xếp tính năng theo tên hiển thị, không phân biệt hoa thường. */
    private static final Comparator<Module> BY_NAME = new Comparator<Module>() {
        @Override
        public int compare(Module a, Module b) {
            return Lang.vi(a.name).compareToIgnoreCase(Lang.vi(b.name));
        }
    };

    /** Thứ tự loại tuỳ chỉnh trong một mục: công tắc -> chế độ -> màu -> thanh trượt. */
    private static int rank(Setting st) {
        if (st instanceof BoolSetting) return 0;
        if (st instanceof ModeSetting) return 1;
        if (st instanceof ColorSetting) return 2;
        return 3; // SliderSetting
    }

    /** Nhớ tính năng và nhóm đang chọn giữa các lần mở menu. */
    private static Module lastModule;
    private static Category lastTab; // null = nhóm "Tất cả"

    // ------------------------------------------------------------------ nút

    private static class Btn extends GuiButton {
        final int kind;
        Module m;
        Setting s;
        Module.Action a;
        float hov;
        /** Tiến độ công tắc 0..1 (-1 = chưa khởi tạo). */
        float sw = -1f;
        /** Nút cố định (Xong, Quay lại, Sửa HUD): không cuộn, không trượt vào. */
        boolean fixed;
        /** Vị trí y trong vùng nội dung (nút cuộn được); -1 nếu là nút cố định. */
        int baseY = -1;

        Btn(int kind, int w, String text) {
            super(0, 0, 0, w, BH, text);
            this.kind = kind;
        }

        /**
         * Vẽ phẳng: nền mờ, rê chuột sáng dần + vạch màu nhấn, công tắc BẬT/TẮT ở bên phải,
         * thanh trượt tô màu nhấn. Nút trượt vào lần lượt khi mở tính năng.
         */
        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            FontRenderer fr = mc.fontRendererObj;
            float st = fixed ? 1f : stagger(id);
            boolean over = st > 0.7f && mouseX >= xPosition && mouseY >= yPosition
                    && mouseX < xPosition + width && mouseY < yPosition + height;
            float k = AnimationsModule.menu() ? 0.3f : 1f;
            hov += ((over ? 1f : 0f) - hov) * k;

            float swT = kind == K_ENABLE ? (m != null && m.enabled ? 1f : 0f)
                    : kind == K_BOOL ? (((BoolSetting) s).value ? 1f : 0f) : 0f;
            sw = sw < 0f ? swT : sw + (swT - sw) * k;
            if (Math.abs(sw - swT) < 0.01f) sw = swT;

            int x = xPosition, y = yPosition, w = width, h = height;
            int ty = y + (h - 8) / 2;
            int col = enabled ? 0xFFFFFF : 0x808080;

            GlStateManager.pushMatrix();
            GlStateManager.translate(fixed ? 0f : (1f - st) * 22f, 0f, 0f);

            // nền
            Gui.drawRect(x, y, x + w, y + h, Draw.lerp(0x22FFFFFF, 0x3CFFFFFF, hov));
            Gui.drawRect(x, y + h - 1, x + w, y + h, 0x18000000);
            if (kind == K_SLIDER) {
                int fw = (int) Math.round(((SliderSetting) s).ratio() * w);
                Gui.drawRect(x, y, x + fw, y + h - 1, Draw.withAlpha(Draw.ACCENT, 0x55));
                Gui.drawRect(Math.max(x, x + fw - 2), y, x + Math.max(2, fw), y + h - 1, Draw.ACCENT);
            }
            if (hov > 0.02f && enabled) {
                Gui.drawRect(x, y, x + Math.round(2 * hov), y + h, Draw.ACCENT);
            }

            // chữ
            switch (kind) {
                case K_ENABLE:
                    fr.drawStringWithShadow(fr.trimStringToWidth("Bật tính năng", w - 40), x + 7, ty, col);
                    drawSwitch(x + w - 26, ty, sw);
                    break;
                case K_BOOL:
                    fr.drawStringWithShadow(fr.trimStringToWidth(Lang.vi(s.label), w - 40), x + 7, ty, col);
                    drawSwitch(x + w - 26, ty, sw);
                    break;
                case K_MODE:
                case K_COLOR:
                case K_SLIDER: {
                    String right = kind == K_MODE ? Lang.vi(((ModeSetting) s).get())
                            : kind == K_COLOR ? colorName(((ColorSetting) s).value)
                            : ((SliderSetting) s).display();
                    int rr = x + w - 7 - (kind == K_COLOR ? 18 : 0);
                    int rw = fr.getStringWidth(right);
                    String left = fr.trimStringToWidth(Lang.vi(s.label), Math.max(10, w - 14 - rw - 10 - (kind == K_COLOR ? 18 : 0)));
                    fr.drawStringWithShadow(left, x + 7, ty, col);
                    fr.drawStringWithShadow(right, rr - rw, ty, Draw.ACCENT_LIGHT);
                    if (kind == K_COLOR) {
                        int sx = x + w - 17, sy = y + (h - 10) / 2;
                        Gui.drawRect(sx - 1, sy - 1, sx + 11, sy + 11, 0xFF000000);
                        Gui.drawRect(sx, sy, sx + 10, sy + 10, 0xFF000000 | ((ColorSetting) s).value);
                    }
                    break;
                }
                default: {
                    String t = fr.trimStringToWidth(displayString, w - 8);
                    fr.drawStringWithShadow(t, x + (w - fr.getStringWidth(t)) / 2f, ty, col);
                    break;
                }
            }
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mouseDragged(mc, mouseX, mouseY); // thanh trượt cập nhật khi đang kéo
        }
    }

    /** Công tắc nhỏ BẬT/TẮT; a = 0..1 để trượt mượt. */
    private static void drawSwitch(int tx, int ty, float a) {
        Gui.drawRect(tx - 1, ty - 1, tx + 19, ty + 9, 0xFF000000);
        Gui.drawRect(tx, ty, tx + 18, ty + 8, Draw.lerp(0xFF7A2A2A, 0xFF2E9B2E, a));
        int kx = tx + 1 + Math.round(10 * a);
        Gui.drawRect(kx, ty + 1, kx + 6, ty + 7, 0xFFEEEEEE);
    }

    /** Thanh trượt phẳng (vẽ trong Btn.drawButton), kéo bằng chuột trái. */
    private static class SliderBtn extends Btn {
        private boolean dragging;

        SliderBtn(int w, SliderSetting s) {
            super(K_SLIDER, w, "");
            this.s = s;
            refresh(this);
        }

        private void setFromMouse(int mx) {
            float r = (mx - (xPosition + 4)) / (float) (width - 8);
            ((SliderSetting) s).setRatio(Math.max(0f, Math.min(1f, r)));
            refresh(this);
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            if (dragging) setFromMouse(mouseX);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                setFromMouse(mouseX);
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
        }
    }

    private static class Cell {
        Btn main;
    }

    /** Một hàng trong bảng tuỳ chỉnh: tiêu đề mục, một nút rộng, hoặc tối đa hai ô. */
    private static class Row {
        String header;
        Btn full;
        Cell a, b;
        int y;
    }

    // ----------------------------------------------------------- nhãn / văn bản

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

    private static String label(Btn b) {
        switch (b.kind) {
            case K_ENABLE: return "Bật tính năng: " + onoff(b.m.enabled);
            case K_BOOL: return Lang.vi(b.s.label) + ": " + onoff(((BoolSetting) b.s).value);
            case K_MODE: return Lang.vi(b.s.label) + ": " + Lang.vi(((ModeSetting) b.s).get());
            case K_COLOR: return Lang.vi(b.s.label) + ": " + colorName(((ColorSetting) b.s).value);
            case K_SLIDER: return Lang.vi(b.s.label) + ": " + ((SliderSetting) b.s).display();
            default: return b.displayString;
        }
    }

    private static void refresh(Btn b) {
        String t = label(b);
        int room = b.width - (b.kind == K_COLOR ? 28 : 8);
        b.displayString = Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(t, room);
    }

    /** Màu nhấn riêng của một tính năng (ổn định theo thứ tự đăng ký). */
    private static int colorOf(Module m) {
        int i = LunarLite.MODULES.indexOf(m);
        if (i < 0) i = 0;
        return 0xFF000000 | PALETTE[i % PALETTE.length];
    }

    // -------------------------------------------------------------------- trạng thái

    private Module module;                       // tính năng đang chọn / đang mở
    private boolean detail;                      // false = trang chủ (bảng ô), true = bảng tuỳ chỉnh
    private Category tabCat;                     // nhóm đang lọc (null = tất cả)
    private final List<Category> tabCats = new ArrayList<Category>(); // các nhóm có tính năng
    private final List<Module> shown = new ArrayList<Module>();       // tính năng theo nhóm đang lọc
    private int page;
    private float tabPos = -1f;                  // vị trí gạch chân nhóm (đơn vị: ô), trượt mượt
    private final Map<Module, Float> hovAnim = new HashMap<Module, Float>();
    private float setScroll, setTarget;
    private int contentH, bw;
    private String[] descLines = new String[0];
    private final List<Row> rows = new ArrayList<Row>();
    private Row pend;
    private boolean oneCol;
    private Btn active; // nút đang giữ chuột (để thanh trượt nhận mouseReleased)
    private int curMx, curMy;   // vị trí chuột trong khung ảo của khung hình hiện tại
    private float barAnim;      // độ "phồng" thanh cuộn khi rê / kéo
    private boolean dragBar;
    private int dragOff;

    // ---- animation
    static long animStart;   // lúc bắt đầu mở / đổi trang / đổi tính năng (để ô và nút trượt vào)
    private final long openStart;
    private long closeStart;
    private boolean closing;

    public MenuScreen() {
        openStart = animStart = System.currentTimeMillis();
        module = lastModule;
        if (module == null || !LunarLite.MODULES.contains(module)) {
            module = LunarLite.MODULES.isEmpty() ? null : LunarLite.MODULES.get(0);
        }
        tabCat = lastTab;
        if (module != null && tabCat != null && module.category != tabCat) tabCat = null;
        buildShown();
        int i = shown.indexOf(module);
        if (i >= 0) page = i / PER_PAGE;
    }

    static float stagger(int id) {
        if (!AnimationsModule.menu()) return 1f;
        float t = (System.currentTimeMillis() - animStart) * AnimationsModule.speed() / 240f - Math.min(id, 10) * 0.06f;
        return AnimationsModule.ease(t);
    }

    /** 0 = đã đóng hẳn, 1 = mở hoàn toàn. */
    private float openProgress() {
        if (!AnimationsModule.menu()) return closing ? 0f : 1f;
        float sp = AnimationsModule.speed();
        long n = System.currentTimeMillis();
        if (closing) return 1f - AnimationsModule.ease((n - closeStart) * sp / 150f);
        return AnimationsModule.ease((n - openStart) * sp / 220f);
    }

    private void requestClose() {
        if (closing) return;
        if (!AnimationsModule.menu()) {
            mc.displayGuiScreen(null);
            return;
        }
        closing = true;
        closeStart = System.currentTimeMillis();
    }

    @Override
    public void updateScreen() {
        if (closing && openProgress() <= 0.001f) mc.displayGuiScreen(null);
    }

    private Btn make(int kind, int w, String text) {
        return new Btn(kind, w, text);
    }

    private void click() {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
    }

    // ------------------------------------------------------------------ khung ảo <-> màn hình

    private float ui() {
        return Math.min(1f, Math.min((width - 8f) / VW, (height - 8f) / VH));
    }

    private int vx(int mx) {
        return Math.round((mx - width / 2f) / ui() + VW / 2f);
    }

    private int vy(int my) {
        return Math.round((my - height / 2f) / ui() + VH / 2f);
    }

    /** Cắt vùng vẽ theo toạ độ của khung ảo. */
    private void clip(int x, int y, int w, int h) {
        float u = ui();
        int rx = Math.round((x - VW / 2f) * u + width / 2f);
        int ry = Math.round((y - VH / 2f) * u + height / 2f);
        Draw.scissor(mc, rx, ry, Math.round(w * u), Math.round(h * u));
    }

    private int paneX() {
        return PAD;
    }

    private int paneW() {
        return VW - 2 * PAD;
    }

    private int setViewH() {
        return PANE_BOTTOM - PANE_TOP;
    }

    // ----------------------------------------------------------- nhóm + danh sách

    private void buildShown() {
        shown.clear();
        tabCats.clear();
        for (Category c : Category.values()) {
            List<Module> inCat = new ArrayList<Module>();
            for (Module m : LunarLite.MODULES) {
                if (m.category == c) inCat.add(m);
            }
            if (inCat.isEmpty()) continue;
            tabCats.add(c);
            if (tabCat != null && c != tabCat) continue;
            Collections.sort(inCat, BY_NAME); // trong mỗi nhóm xếp A -> Z cho dễ tìm
            shown.addAll(inCat);
        }
        if (shown.isEmpty() && tabCat != null) { // nhóm không còn tính năng nào -> về "Tất cả"
            tabCat = lastTab = null;
            buildShown();
            return;
        }
        page = Math.max(0, Math.min(pages() - 1, page));
    }

    private int pages() {
        return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void changePage(int d) {
        int np = Math.max(0, Math.min(pages() - 1, page + d));
        if (np == page) return;
        page = np;
        animStart = System.currentTimeMillis();
    }

    private int tabCount() {
        return 1 + tabCats.size();
    }

    private int tabIndex() {
        return tabCat == null ? 0 : tabCats.indexOf(tabCat) + 1;
    }

    private int tabW() {
        return (paneW() - TAB_GAP * (tabCount() - 1)) / tabCount();
    }

    private int tabX(int i) {
        return PAD + i * (tabW() + TAB_GAP);
    }

    private String tabLabel(int i) {
        return i == 0 ? "Tất cả" : tabCats.get(i - 1).label;
    }

    private String tabIcon(int i) {
        return i == 0 ? "cat_all" : Icons.cat(tabCats.get(i - 1));
    }

    private int tabAt(int mx, int my) {
        if (my < TAB_Y || my >= TAB_Y + TAB_H) return -1;
        for (int i = 0; i < tabCount(); i++) {
            if (mx >= tabX(i) && mx < tabX(i) + tabW()) return i;
        }
        return -1;
    }

    private void setTab(int i) {
        Category c = i <= 0 || i > tabCats.size() ? null : tabCats.get(i - 1);
        if (c == tabCat) return;
        tabCat = c;
        lastTab = c;
        page = 0;
        animStart = System.currentTimeMillis();
        buildShown();
    }

    private void cycleTab(int d) {
        int n = tabCount();
        setTab((tabIndex() + d + n) % n);
    }

    // ----------------------------------------------------------- ô tính năng

    private int cardW() {
        return (paneW() - (COLS - 1) * CARD_GAP) / COLS;
    }

    private int gridX() {
        return PAD + (paneW() - (cardW() * COLS + (COLS - 1) * CARD_GAP)) / 2;
    }

    private int cardX(int slot) {
        return gridX() + (slot % COLS) * (cardW() + CARD_GAP);
    }

    private int cardY(int slot) {
        return GRID_Y + (slot / COLS) * (CARD_H + CARD_GAP);
    }

    /** Ô ở vị trí chuột trên trang hiện tại (0..PER_PAGE-1), hoặc -1. */
    private int slotAt(int mx, int my) {
        for (int s = 0; s < PER_PAGE; s++) {
            if (page * PER_PAGE + s >= shown.size()) break;
            if (Draw.in(mx, my, cardX(s), cardY(s), cardW(), CARD_H)) return s;
        }
        return -1;
    }

    private Module moduleAtSlot(int slot) {
        int gi = page * PER_PAGE + slot;
        return slot >= 0 && gi < shown.size() ? shown.get(gi) : null;
    }

    private void openModule(Module m) {
        if (m == null) return;
        module = m;
        lastModule = m;
        detail = true;
        setScroll = setTarget = 0f;
        animStart = System.currentTimeMillis();
        rebuild();
    }

    private void goBack() {
        detail = false;
        active = null;
        dragBar = false;
        int i = shown.indexOf(module);
        if (i >= 0) page = i / PER_PAGE;
        animStart = System.currentTimeMillis();
        rebuild();
    }

    private void moveModule(int d) {
        int i = shown.indexOf(module);
        if (i < 0) return;
        int j = i + d;
        if (j < 0 || j >= shown.size()) return;
        openModule(shown.get(j));
    }

    private void toggleModule(Module m) {
        m.toggle();
        for (Object o : buttonList) {
            if (o instanceof Btn && ((Btn) o).kind == K_ENABLE) refresh((Btn) o);
        }
    }

    // ----------------------------------------------------------- bảng tuỳ chỉnh (chi tiết)

    private void addHeader(String text) {
        pend = null;
        Row r = new Row();
        r.header = text;
        rows.add(r);
    }

    private void addFull(Btn b) {
        pend = null;
        Row r = new Row();
        r.full = b;
        rows.add(r);
    }

    private void single(Btn b) {
        Cell c = new Cell();
        c.main = b;
        if (oneCol || pend == null) {
            Row r = new Row();
            r.a = c;
            rows.add(r);
            pend = oneCol ? null : r;
        } else {
            pend.b = c;
            pend = null;
        }
    }

    private void buildRows() {
        rows.clear();
        pend = null;
        descLines = new String[0];
        contentH = 0;
        Module m = module;
        if (m == null) return;

        int inner = paneW() - 20;
        oneCol = inner < 2 * 120 + GAP;
        bw = oneCol ? inner : (inner - GAP) / 2;

        if (m.description != null && m.description.length() > 0) {
            List<?> ls = fontRendererObj.listFormattedStringToWidth(Lang.vi(m.description), paneW() - 100);
            int n = Math.min(2, ls.size());
            descLines = new String[n];
            for (int i = 0; i < n; i++) descLines[i] = (String) ls.get(i);
        }

        Btn en = make(K_ENABLE, inner, "");
        en.m = m;
        refresh(en);
        addFull(en);

        List<Setting> vis = new ArrayList<Setting>();
        for (Setting st : m.settings) if (st.isVisible()) vis.add(st);

        // thứ tự mục: mục riêng của tính năng -> Appearance -> Rainbow
        List<String> found = new ArrayList<String>();
        for (Setting st : vis) if (!found.contains(st.section)) found.add(st.section);
        List<String> sections = new ArrayList<String>();
        for (String sec : found) if (!"Appearance".equals(sec) && !"Rainbow".equals(sec)) sections.add(sec);
        if (found.contains("Appearance")) sections.add("Appearance");
        if (found.contains("Rainbow")) sections.add("Rainbow");

        List<Module.Action> acts = m.actions();
        boolean heads = sections.size() > 1 || (sections.size() == 1 && !acts.isEmpty());
        for (String sec : sections) {
            if (heads) addHeader(Lang.vi(sec));
            List<Setting> inSec = new ArrayList<Setting>();
            for (Setting st : vis) {
                if (sec.equals(st.section)) inSec.add(st);
            }
            // sort ổn định: giữ nguyên thứ tự gốc trong cùng một loại
            Collections.sort(inSec, new Comparator<Setting>() {
                @Override
                public int compare(Setting x, Setting y) {
                    return rank(x) - rank(y);
                }
            });
            int lastRank = -1;
            for (Setting st : inSec) {
                if (rank(st) != lastRank) pend = null; // sang loại mới thì xuống hàng, không ghép lẻ với loại khác
                lastRank = rank(st);
                Btn b;
                if (st instanceof SliderSetting) b = new SliderBtn(bw, (SliderSetting) st);
                else if (st instanceof BoolSetting) b = make(K_BOOL, bw, "");
                else if (st instanceof ModeSetting) b = make(K_MODE, bw, "");
                else if (st instanceof ColorSetting) b = make(K_COLOR, bw, "");
                else continue;
                b.m = m;
                b.s = st;
                refresh(b);
                single(b);
            }
        }
        if (!acts.isEmpty()) {
            if (heads) addHeader("Thao tác");
            for (Module.Action a : acts) {
                Btn b = make(K_ACTION, bw, a.label);
                b.m = m;
                b.a = a;
                single(b);
            }
        }
        addHeader("");
        Btn reset = make(K_RESET, Math.min(200, inner), "Đặt lại tuỳ chỉnh");
        reset.m = m;
        addFull(reset);

        int y = 4;
        for (Row r : rows) {
            r.y = y;
            y += ROW;
        }
        contentH = y + 4;
    }

    private void place(Btn b, int x, int y) {
        b.xPosition = x;
        b.yPosition = y;
        b.id = buttonList.size();
        buttonList.add(b);
    }

    /** Xếp nút cố định (hàng dưới cùng) và các nút trong bảng tuỳ chỉnh. */
    private void layout() {
        buttonList.clear();
        int n = detail ? 3 : 2;
        int w = (paneW() - GAP * (n - 1)) / n;
        int x = PAD;
        if (detail) {
            Btn back = make(K_BACK, w, "« Quay lại");
            back.fixed = true;
            place(back, x, BTN_Y);
            x += w + GAP;
        }
        Btn edit = make(K_EDIT, w, "Sửa vị trí HUD…");
        edit.fixed = true;
        place(edit, x, BTN_Y);
        x += w + GAP;
        Btn done = make(K_DONE, w, "Xong");
        done.fixed = true;
        place(done, x, BTN_Y);

        if (detail) {
            int px = paneX(), inner = paneW() - 20;
            for (Row row : rows) {
                if (row.header != null) continue;
                if (row.full != null) {
                    row.full.baseY = row.y;
                    place(row.full, px + 8 + (inner - row.full.width) / 2, row.y);
                    continue;
                }
                row.a.main.baseY = row.y;
                place(row.a.main, px + 8, row.y);
                if (row.b != null) {
                    row.b.main.baseY = row.y;
                    place(row.b.main, px + 8 + bw + GAP, row.y);
                }
            }
        }
        clampScroll();
    }

    private void rebuild() {
        buildShown();
        if (detail) buildRows();
        else {
            rows.clear();
            descLines = new String[0];
            contentH = 0;
        }
        layout();
    }

    private void clampScroll() {
        float max = Math.max(0, contentH - setViewH());
        setTarget = Math.max(0f, Math.min(max, setTarget));
        setScroll = Math.max(0f, Math.min(max, setScroll));
    }

    // ---------------------------------------------------------------- GuiScreen

    // ---- nền blur (dùng post-shader "blur" của game, chỉ làm mờ thế giới phía sau, không làm mờ menu)
    private static final ResourceLocation BLUR_SHADER = new ResourceLocation("lunarlite", "shaders/post/menu_blur.json");
    private boolean blurOn;

    private void startBlur() {
        if (blurOn || mc == null || mc.theWorld == null || !OpenGlHelper.shadersSupported) return;
        // đang có shader khác (vd. spectate creeper) thì không đè lên
        if (mc.entityRenderer.isShaderActive()) return;
        try {
            mc.entityRenderer.loadShader(BLUR_SHADER);
            blurOn = mc.entityRenderer.isShaderActive();
        } catch (Throwable t) {
            blurOn = false;
        }
    }

    private void stopBlur() {
        if (!blurOn) return;
        blurOn = false;
        try {
            if (mc != null && mc.entityRenderer != null) mc.entityRenderer.stopUseShader();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void initGui() {
        startBlur();
        rebuild();
    }

    private String fit(String s, int maxW) {
        if (fontRendererObj.getStringWidth(s) <= maxW) return s;
        int dots = fontRendererObj.getStringWidth("..");
        return fontRendererObj.trimStringToWidth(s, Math.max(0, maxW - dots)) + "..";
    }

    private void drawScaled(String s, float x, float y, float sc, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(sc, sc, 1f);
        fontRendererObj.drawStringWithShadow(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private void drawCentered(String s, float cx, float y, int color) {
        fontRendererObj.drawStringWithShadow(s, cx - fontRendererObj.getStringWidth(s) / 2f, y, color);
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ThemeModule.apply();
        curMx = vx(mx);
        curMy = vy(my);
        float op = openProgress();
        // nền blur: lớp phủ tối nhẹ hơn vì thế giới phía sau đã bị làm mờ
        if (blurOn) drawGradientRect(0, 0, width, height, Draw.mulAlpha(0x50101010, op), Draw.mulAlpha(0x66101010, op));
        else drawGradientRect(0, 0, width, height, Draw.mulAlpha(0xC0101010, op), Draw.mulAlpha(0xD0101010, op));

        float k = AnimationsModule.menu() ? 0.35f : 1f;
        setScroll += (setTarget - setScroll) * k;
        if (Math.abs(setTarget - setScroll) < 0.4f) setScroll = setTarget;

        GlStateManager.pushMatrix();
        float kk = (0.95f + 0.05f * op) * ui();
        GlStateManager.translate(width / 2f, height / 2f + (1f - op) * 10f, 0f);
        GlStateManager.scale(kk, kk, 1f);
        GlStateManager.translate(-VW / 2f, -VH / 2f, 0f);

        drawPanel(curMx, curMy);

        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawPanel(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        int en = 0;
        for (Module m : LunarLite.MODULES) if (m.enabled) en++;
        int total = LunarLite.MODULES.size();

        // khung + bóng
        Gui.drawRect(3, 3, VW + 3, VH + 3, 0x55000000);
        Gui.drawRect(0, 0, VW, VH, PANEL_BG);
        Draw.outline(0, 0, VW, VH, PANEL_EDGE);
        Gui.drawRect(0, 0, VW, 2, Draw.ACCENT);

        // ---------------- tiêu đề
        if (!detail || module == null) {
            // logo + tên client
            Gui.drawRect(PAD, 12, PAD + 28, 40, 0xFF1A2A52);
            Draw.outline(PAD, 12, PAD + 28, 40, Draw.ACCENT_DIM);
            Icons.draw("logo", PAD + 2, 14, 3, Draw.ACCENT_LIGHT);
            drawScaled("Zhwii Client", PAD + 36, 12, 2f, Draw.ACCENT_LIGHT);
            fr.drawStringWithShadow("Client HUD / PvP · Minecraft 1.8.9", PAD + 36, 31, TEXT_DIM);
            // huy hiệu số tính năng đang bật
            int bx = VW - PAD - 72;
            Gui.drawRect(bx, 12, bx + 72, 42, 0xFF0B1226);
            Draw.outline(bx, 12, bx + 72, 42, Draw.ACCENT);
            drawCentered("ĐANG BẬT", bx + 36, 16, Draw.ACCENT_LIGHT);
            String cnt = en + "/" + total;
            drawScaled(cnt, bx + 36 - fr.getStringWidth(cnt) * 1.5f / 2f, 27, 1.5f, 0xFFFFFFFF);
            // dòng trạng thái + thanh tiến độ
            String grp = "Nhóm: " + (tabCat == null ? "Tất cả" : tabCat.label);
            fr.drawStringWithShadow(grp, PAD, 47, 0xFFFFFFFF);
            String right = en + "/" + total + " đang bật · " + shown.size() + " tính năng hiển thị";
            fr.drawStringWithShadow(right, VW - PAD - fr.getStringWidth(right), 47, TEXT_DIM);
            Draw.hbar(PAD, 58, VW - 2 * PAD, 5, total == 0 ? 0f : en / (float) total,
                    Draw.withAlpha(Draw.ACCENT, 0xFF), 0xFF0A1226);
        } else {
            int col = colorOf(module);
            Gui.drawRect(PAD, 12, PAD + 28, 40, 0xFF1A2A52);
            Draw.outline(PAD, 12, PAD + 28, 40, col);
            Icons.draw(module.id, PAD + 2, 14, 3, col);
            String nm = Lang.vi(module.name);
            int avail = VW - 2 * PAD - 36 - 84;
            float sc = fr.getStringWidth(nm) * 2f > avail ? 1.4f : 2f;
            drawScaled(nm, PAD + 36, 12, sc, 0xFFFFFFFF);
            fr.drawStringWithShadow(module.category.label, PAD + 36, 31, TEXT_DIM);
            int bx = VW - PAD - 72;
            int sc2 = module.enabled ? GREEN : RED;
            Gui.drawRect(bx, 12, bx + 72, 42, 0xFF0B1226);
            Draw.outline(bx, 12, bx + 72, 42, sc2);
            String st = module.enabled ? "BẬT" : "TẮT";
            drawScaled(st, bx + 36 - fr.getStringWidth(st) * 1.5f / 2f, 21, 1.5f, sc2);
            for (int i = 0; i < descLines.length; i++) {
                fr.drawStringWithShadow(descLines[i], PAD, 46 + i * 10, TEXT_SOFT);
            }
        }
        Gui.drawRect(PAD, 68, VW - PAD, 69, DIVIDER);

        if (detail) drawDetail(mx, my);
        else drawHome(mx, my);
    }

    // ----------------------------------------------------------- trang chủ

    private void drawHome(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        drawTabs(mx, my);

        float k = AnimationsModule.menu() ? 0.3f : 1f;
        Module hover = null;
        int cw = cardW();
        for (int s = 0; s < PER_PAGE; s++) {
            Module m = moduleAtSlot(s);
            if (m == null) break;
            float st = stagger(s);
            int x = cardX(s), y = cardY(s);
            boolean over = st > 0.7f && Draw.in(mx, my, x, y, cw, CARD_H);
            if (over) hover = m;
            Float cur = hovAnim.get(m);
            float hv = cur == null ? 0f : cur.floatValue();
            hv += ((over ? 1f : 0f) - hv) * k;
            if (Math.abs((over ? 1f : 0f) - hv) < 0.01f) hv = over ? 1f : 0f;
            hovAnim.put(m, Float.valueOf(hv));

            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, (1f - st) * 8f, 0f);
            drawCard(m, s, x, y, cw, CARD_H, hv, m == module);
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1f, 1f, 1f, 1f);

        // điều khiển trang
        int pcx = VW / 2;
        int pg = pages();
        String ps = (page + 1) + "/" + pg;
        drawCentered(ps, pcx, PAGE_Y + 2, 0xFFFFFFFF);
        boolean canPrev = page > 0, canNext = page < pg - 1;
        boolean hp = canPrev && Draw.in(mx, my, pcx - 44, PAGE_Y - 2, 22, 16);
        boolean hn = canNext && Draw.in(mx, my, pcx + 22, PAGE_Y - 2, 22, 16);
        drawCentered("<", pcx - 33, PAGE_Y + 2, canPrev ? (hp ? Draw.ACCENT_LIGHT : 0xFFFFFFFF) : 0xFF3A4766);
        drawCentered(">", pcx + 33, PAGE_Y + 2, canNext ? (hn ? Draw.ACCENT_LIGHT : 0xFFFFFFFF) : 0xFF3A4766);

        // dòng gợi ý (đổi thành mô tả khi rê chuột vào ô)
        String foot;
        int fcol;
        if (hover != null && hover.description != null && hover.description.length() > 0) {
            foot = Lang.vi(hover.description);
            fcol = TEXT_SOFT;
        } else {
            foot = "Phím 1-9, 0 chọn · ←/→ đổi trang · ↑/↓ đổi nhóm · Chuột phải bật/tắt · ESC đóng";
            fcol = TEXT_DIM;
        }
        foot = fr.trimStringToWidth(foot, VW - 2 * PAD);
        drawCentered(foot, VW / 2f, HINT_Y, fcol);

        drawFixedButtons(mx, my);
    }

    private void drawTabs(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        int n = tabCount(), tw = tabW(), sel = tabIndex();
        if (tabPos < 0f) tabPos = sel;
        tabPos += (sel - tabPos) * (AnimationsModule.menu() ? 0.3f : 1f);
        if (Math.abs(sel - tabPos) < 0.01f) tabPos = sel;

        for (int i = 0; i < n; i++) {
            int x = tabX(i);
            boolean over = Draw.in(mx, my, x, TAB_Y, tw, TAB_H);
            Gui.drawRect(x, TAB_Y, x + tw, TAB_Y + TAB_H, i == sel ? 0xFF1B3157 : over ? 0xFF14244A : STRIP_BG);
            Draw.outline(x, TAB_Y, x + tw, TAB_Y + TAB_H, i == sel ? Draw.ACCENT_DIM : 0xFF1B2B52);
            String label = fit(tabLabel(i), tw - 8 - 12);
            int lw = fr.getStringWidth(label);
            int total = 8 + 4 + lw;
            int sx = x + (tw - total) / 2;
            int col = i == sel ? 0xFFFFFFFF : over ? 0xFFE0E6F5 : TEXT_DIM;
            Icons.draw(tabIcon(i), sx, TAB_Y + (TAB_H - 8) / 2, 1, i == sel ? Draw.ACCENT_LIGHT : col);
            fr.drawStringWithShadow(label, sx + 12, TAB_Y + (TAB_H - 8) / 2, col);
        }
        int ix = PAD + Math.round(tabPos * (tw + TAB_GAP));
        Gui.drawRect(ix, TAB_Y + TAB_H - 2, ix + tw, TAB_Y + TAB_H, Draw.ACCENT);
    }

    private void drawCard(Module m, int slot, int x, int y, int w, int h, float hv, boolean sel) {
        FontRenderer fr = fontRendererObj;
        int col = colorOf(m);
        boolean on = m.enabled;

        Gui.drawRect(x, y, x + w, y + h, Draw.lerp(CARD_BG, CARD_BG_HOT, hv));
        int edge = Draw.lerp(on ? Draw.lerp(PANEL_EDGE, col, 0.45f) : PANEL_EDGE, col, hv);
        if (sel) edge = Draw.lerp(Draw.ACCENT, col, hv);
        Draw.outline(x, y, x + w, y + h, edge);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + 3, on ? col : Draw.withAlpha(col, 0x55));

        // số phím tắt
        String key = slot == 9 ? "0" : String.valueOf(slot + 1);
        fr.drawStringWithShadow(key, x + 5, y + 7, TEXT_DIM);

        // biểu tượng riêng
        int icol = on ? col : Draw.lerp(col, CARD_BG, 0.62f);
        Icons.draw(m.id, x + (w - 24) / 2, y + 9, 3, icol);

        // tên + trạng thái
        String nm = fit(Lang.vi(m.name), w - 8);
        drawCentered(nm, x + w / 2f, y + 38, on ? 0xFFFFFFFF : 0xFFB8C4E0);
        String stt = on ? "Bật" : "Tắt";
        int sw = fr.getStringWidth(stt);
        int sx = x + (w - (6 + sw)) / 2;
        int dc = on ? GREEN : OFF;
        Gui.drawRect(sx, y + 50, sx + 4, y + 54, dc);
        fr.drawStringWithShadow(stt, sx + 6, y + 48, on ? GREEN : OFF);
    }

    private void drawFixedButtons(int mx, int my) {
        for (Object o : buttonList) {
            Btn b = (Btn) o;
            if (b.baseY < 0) b.drawButton(mc, mx, my);
        }
    }

    // ----------------------------------------------------------- chi tiết

    private void drawDetail(int mx, int my) {
        int px = paneX(), pw = paneW();
        int top = PANE_TOP, bottom = PANE_BOTTOM;
        FontRenderer fr = fontRendererObj;

        Gui.drawRect(px, top, px + pw, bottom, 0xFF0D1730);
        Draw.outline(px, top, px + pw, bottom, 0xFF1B2B52);

        int sc = Math.round(setScroll);
        for (Object o : buttonList) {
            Btn b = (Btn) o;
            if (b.baseY < 0) continue;
            b.yPosition = top + b.baseY - sc;
            b.visible = b.yPosition + BH > top && b.yPosition < bottom;
        }
        clip(px, top, pw, setViewH());
        for (Row r : rows) {
            if (r.header == null) continue;
            int ry = top + r.y - sc;
            if (ry + BH < top || ry > bottom) continue;
            if (r.header.length() == 0) {
                Gui.drawRect(px + 10, ry + BH / 2, px + pw - 14, ry + BH / 2 + 1, 0x22FFFFFF);
                continue;
            }
            int hy = ry + (BH - 8) / 2;
            int tw = fr.getStringWidth(r.header);
            int hcx = px + (pw - 4) / 2;
            fr.drawStringWithShadow(r.header, hcx - tw / 2f, hy, Draw.ACCENT_LIGHT);
            int ly = ry + BH / 2;
            Gui.drawRect(px + 10, ly, hcx - tw / 2 - 6, ly + 1, Draw.ACCENT_DIM);
            Gui.drawRect(hcx + tw / 2 + 6, ly, px + pw - 14, ly + 1, Draw.ACCENT_DIM);
        }
        for (Object o : buttonList) {
            Btn b = (Btn) o;
            if (b.baseY >= 0) b.drawButton(mc, mx, my);
        }
        Draw.endScissor();
        if (contentH > setViewH()) scrollbar(top, setViewH(), contentH, setScroll);

        drawFixedButtons(mx, my);
    }

    // ----------------------------------------------------------- thanh cuộn

    private int barX() {
        return paneX() + paneW() - BAR_W - 1;
    }

    private int thumbH(int viewH, int totalH) {
        return Math.max(20, viewH * viewH / Math.max(1, totalH));
    }

    private int thumbY(int y, int viewH, int totalH, float scroll) {
        return y + Math.round((viewH - thumbH(viewH, totalH)) * (scroll / Math.max(1, totalH - viewH)));
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

    /** Thanh cuộn kiểu Sodium: rãnh mảnh tối, tay nắm trắng bo tròn; rê chuột thì phồng và sáng hơn. */
    private void scrollbar(int y, int viewH, int totalH, float scroll) {
        int x = barX();
        int bh = thumbH(viewH, totalH);
        int by = thumbY(y, viewH, totalH, scroll);
        boolean hover = curMx >= x - 3 && curMx < x + BAR_W + 2 && curMy >= y && curMy < y + viewH;
        float target = dragBar || hover ? 1f : 0f;
        barAnim += (target - barAnim) * (AnimationsModule.menu() ? 0.3f : 1f);
        if (Math.abs(target - barAnim) < 0.02f) barAnim = target;

        int tw = 3 + Math.round(2 * barAnim);          // 3px -> 5px
        int tx1 = x + 1 + Math.round((5 - tw) / 2f);
        pill(x + 1, y + 2, x + 6, y + viewH - 2, 0x55000000); // rãnh
        int alpha = dragBar ? 0xFF : 0x99 + Math.round(0x33 * barAnim);
        pill(tx1, by + 2, tx1 + tw, Math.min(y + viewH - 2, by + bh - 2), (alpha << 24) | 0xFFFFFF);
    }

    /** Bấm chuột trái vào thanh cuộn: bắt đầu kéo (bấm ngoài tay nắm thì nhảy tới đó). */
    private boolean startBarDrag(int mx, int my) {
        int viewH = setViewH();
        if (contentH <= viewH) return false;
        int x = barX();
        if (mx < x - 3 || mx >= x + BAR_W + 2 || my < PANE_TOP || my >= PANE_TOP + viewH) return false;
        int by = thumbY(PANE_TOP, viewH, contentH, setScroll), bh = thumbH(viewH, contentH);
        dragBar = true;
        dragOff = (my >= by && my < by + bh) ? my - by : bh / 2;
        dragTo(my);
        return true;
    }

    private void dragTo(int my) {
        int viewH = setViewH();
        int range = Math.max(1, viewH - thumbH(viewH, contentH));
        float v = (my - dragOff - PANE_TOP) / (float) range * (contentH - viewH);
        setTarget = setScroll = v;
        clampScroll();
    }

    // ----------------------------------------------------------- chuột

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (dragBar && btn == 0) {
            dragTo(vy(my));
            return;
        }
        super.mouseClickMove(mx, my, btn, time);
    }

    @Override
    protected void actionPerformed(GuiButton g) throws IOException {
        if (g instanceof Btn) handle((Btn) g, false);
    }

    private boolean inPane(int mx, int my) {
        return mx >= paneX() && mx < paneX() + paneW() && my >= PANE_TOP && my < PANE_BOTTOM;
    }

    @Override
    protected void mouseClicked(int rawX, int rawY, int btn) throws IOException {
        if (closing) return;
        if (btn != 0 && btn != 1) return;
        int mx = vx(rawX), my = vy(rawY);

        if (!detail) {
            if (btn == 0) {
                int t = tabAt(mx, my);
                if (t >= 0) {
                    click();
                    setTab(t);
                    return;
                }
                int pcx = VW / 2;
                if (Draw.in(mx, my, pcx - 44, PAGE_Y - 2, 22, 16)) {
                    if (page > 0) click();
                    changePage(-1);
                    return;
                }
                if (Draw.in(mx, my, pcx + 22, PAGE_Y - 2, 22, 16)) {
                    if (page < pages() - 1) click();
                    changePage(1);
                    return;
                }
            }
            Module hit = moduleAtSlot(slotAt(mx, my));
            if (hit != null) {
                click();
                module = hit;
                lastModule = hit;
                if (btn == 1) toggleModule(hit);
                else openModule(hit);
                return;
            }
        } else if (btn == 0 && startBarDrag(mx, my)) {
            return;
        }

        for (Object o : buttonList) {
            Btn b = (Btn) o;
            if (b.baseY >= 0 && !inPane(mx, my)) continue; // phần nút bị cuộn khuất thì không bấm được
            if (btn == 1) {
                if (b.kind == K_SLIDER || !b.visible || !b.enabled) continue;
                if (mx >= b.xPosition && my >= b.yPosition && mx < b.xPosition + b.width && my < b.yPosition + BH) {
                    b.playPressSound(mc.getSoundHandler());
                    handle(b, true);
                    return;
                }
            } else if (b.mousePressed(mc, mx, my)) {
                active = b;
                b.playPressSound(mc.getSoundHandler());
                actionPerformed(b);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int rawX, int rawY, int state) {
        if (state == 0) dragBar = false;
        if (active != null && state == 0) {
            active.mouseReleased(vx(rawX), vy(rawY));
            active = null;
        }
        super.mouseReleased(rawX, rawY, state);
    }

    /** Dấu vết các tuỳ chỉnh đang hiện, để biết có cần dựng lại bảng không. */
    private static long visSig(Module m) {
        long sig = 0;
        int i = 0;
        for (Setting st : m.settings) {
            if (st.isVisible() && i < 63) sig |= 1L << i;
            i++;
        }
        return sig;
    }

    private void handle(Btn b, boolean right) {
        switch (b.kind) {
            case K_DONE:
                requestClose();
                break;
            case K_BACK:
                goBack();
                break;
            case K_EDIT:
                mc.displayGuiScreen(new HudEditorScreen());
                break;
            case K_ENABLE:
                b.m.toggle();
                refresh(b);
                break;
            case K_BOOL: {
                long before = visSig(b.m);
                ((BoolSetting) b.s).toggle();
                if (visSig(b.m) != before) rebuild(); // có tuỳ chỉnh con được ẩn / hiện
                break;
            }
            case K_MODE:
                if (right) ((ModeSetting) b.s).prev();
                else ((ModeSetting) b.s).next();
                refresh(b);
                break;
            case K_COLOR: {
                ColorSetting col = (ColorSetting) b.s;
                int n = ColorSetting.PRESETS.length, i = presetIndex(col.value);
                int next = !right ? (i + 1) % n : (i < 0 ? n - 1 : (i + n - 1) % n);
                col.value = ColorSetting.PRESETS[next];
                refresh(b);
                break;
            }
            case K_ACTION:
                b.a.run.run();
                rebuild();
                break;
            case K_RESET:
                b.m.resetSettings();
                if (b.m instanceof HudModule) ((HudModule) b.m).resetPosition();
                rebuild();
                break;
            default:
                break; // K_SLIDER do chính nó xử lý
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        if (detail) {
            setTarget += (d > 0 ? -1 : 1) * 2f * ROW;
            clampScroll();
        } else {
            changePage(d > 0 ? -1 : 1);
        }
    }

    // ----------------------------------------------------------- bàn phím

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            if (detail) goBack();
            else requestClose();
            return;
        }
        if (LunarLite.keyMenu != null && key == LunarLite.keyMenu.getKeyCode()) {
            requestClose();
            return;
        }
        if (detail) {
            if (key == Keyboard.KEY_LEFT) moveModule(-1);
            else if (key == Keyboard.KEY_RIGHT) moveModule(1);
            else if (key == Keyboard.KEY_UP) {
                setTarget -= ROW;
                clampScroll();
            } else if (key == Keyboard.KEY_DOWN) {
                setTarget += ROW;
                clampScroll();
            }
            return;
        }
        if (c >= '0' && c <= '9') {
            int slot = c == '0' ? 9 : c - '1';
            Module m = moduleAtSlot(slot);
            if (m != null) {
                click();
                openModule(m);
            }
        } else if (key == Keyboard.KEY_LEFT) {
            changePage(-1);
        } else if (key == Keyboard.KEY_RIGHT) {
            changePage(1);
        } else if (key == Keyboard.KEY_UP) {
            cycleTab(-1);
        } else if (key == Keyboard.KEY_DOWN || key == Keyboard.KEY_TAB) {
            cycleTab(1);
        }
    }

    @Override
    public void onGuiClosed() {
        stopBlur();
        lastModule = module;
        LunarLite.saveConfig();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
