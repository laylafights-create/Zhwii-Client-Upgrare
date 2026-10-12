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
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Menu 2 cột:
 *   Bên TRÁI : danh sách tên tính năng (chia theo nhóm, cuộn được, có ô BẬT/TẮT nhanh).
 *   Bên PHẢI : bảng tuỳ chỉnh của tính năng đang chọn (bật/tắt, các tuỳ chỉnh, thao tác, đặt lại).
 * Bấm tên tính năng = chọn; bấm công tắc nhỏ (hoặc chuột phải lên tên) = bật/tắt nhanh.
 * Thanh tab trên cùng lọc theo nhóm (Tất cả / HUD / PVP / ...); phím ← / → đổi tab.
 * Con lăn chuột cuộn cột nào đang trỏ vào; phím ↑ / ↓ đổi tính năng. "Xong" / ESC để đóng.
 * Chuột phải lên nút giá trị = lùi giá trị.
 */
public class MenuScreen extends GuiScreen {
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };
    private static final int BH = 20, ROW = 24, GAP = 6, WIDE = 200, LROW = 20, LHEAD = 18;
    private static final int K_DONE = 0, K_EDIT = 1, K_ENABLE = 2, K_BOOL = 3, K_MODE = 4, K_COLOR = 5,
            K_SLIDER = 6, K_ACTION = 7, K_RESET = 8;

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

    /** Nhớ tính năng và tab đang chọn giữa các lần mở menu. */
    private static Module lastModule;
    private static Category lastTab; // null = tab "Tất cả"

    private static final int TAB_Y = 26, TAB_H = 18, TAB_GAP = 3;

    // ------------------------------------------------------------------ nút

    private static class Btn extends GuiButton {
        final int kind;
        Module m;
        Setting s;
        Module.Action a;
        float hov;
        /** Tiến độ công tắc 0..1 (-1 = chưa khởi tạo). */
        float sw = -1f;
        /** Nút cố định (Xong, Sửa HUD): không cuộn, không trượt vào. */
        boolean fixed;
        /** Vị trí y trong vùng nội dung bên phải (nút cuộn được); -1 nếu là nút cố định. */
        int baseY = -1;

        Btn(int kind, int w, String text) {
            super(0, 0, 0, w, BH, text);
            this.kind = kind;
        }

        /**
         * Vẽ phẳng, cùng phong cách với danh sách bên trái: nền mờ, rê chuột sáng dần + vạch màu nhấn,
         * công tắc BẬT/TẮT ở bên phải, thanh trượt tô màu nhấn. Nút trượt vào lần lượt khi chọn tính năng.
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

    /** Công tắc nhỏ BẬT/TẮT (dùng chung cho danh sách bên trái và bảng bên phải); a = 0..1 để trượt mượt. */
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

    // -------------------------------------------------------------------- trạng thái

    private Module module;                       // tính năng đang chọn
    private Category tabCat;                     // tab đang chọn (null = tất cả)
    private final List<Category> tabCats = new ArrayList<Category>(); // các nhóm có tính năng
    private float tabPos = -1f;                  // vị trí gạch chân tab (đơn vị: ô tab), trượt mượt
    private final List<Object> listItems = new ArrayList<Object>(); // String (tiêu đề nhóm) hoặc Module
    private int[] listY = new int[0];
    private int listH;
    private float listScroll, listTarget;
    private float setScroll, setTarget;
    private int contentH, headH, bw;
    private String[] descLines = new String[0];
    private final List<Row> rows = new ArrayList<Row>();
    private Row pend;
    private boolean oneCol;
    private Btn active; // nút đang giữ chuột (để thanh trượt nhận mouseReleased)
    private final Map<Module, Float> swAnim = new HashMap<Module, Float>(); // tiến độ công tắc ở danh sách trái
    private int curMx, curMy;          // vị trí chuột khung hình hiện tại (cho thanh cuộn)
    private final float[] barAnim = new float[3]; // độ "phồng" thanh cuộn khi rê/kéo: [1] trái, [2] phải

    // ---- animation
    static long animStart;   // lúc bắt đầu mở / đổi tính năng (để các nút trượt vào)
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

    // ------------------------------------------------------------------ hình học

    private int hubW() {
        return Math.min(width - 12, 480);
    }

    private int hubX() {
        return width / 2 - hubW() / 2;
    }

    private int sideW() {
        return Math.max(96, Math.min(150, hubW() * 3 / 10));
    }

    private int paneX() {
        return hubX() + sideW() + 6;
    }

    private int paneW() {
        return hubW() - sideW() - 6;
    }

    private int top() {
        return TAB_Y + TAB_H + 6;
    }

    private int doneY() {
        return height - 28;
    }

    private int bottom() {
        return doneY() - 16;
    }

    private int listBottom() {
        return bottom() - 26;
    }

    private int listViewH() {
        return listBottom() - top();
    }

    private int setViewH() {
        return bottom() - top();
    }

    private boolean inList(int mx, int my) {
        return mx >= hubX() && mx < hubX() + sideW() && my >= top() && my < listBottom();
    }

    private boolean inPane(int mx, int my) {
        return mx >= paneX() && mx < paneX() + paneW() && my >= top() && my < bottom();
    }

    // ----------------------------------------------------------- thanh tab trên cùng

    private int tabCount() {
        return 1 + tabCats.size();
    }

    private int tabIndex() {
        return tabCat == null ? 0 : tabCats.indexOf(tabCat) + 1;
    }

    private int tabW() {
        return (hubW() - TAB_GAP * (tabCount() - 1)) / tabCount();
    }

    private int tabX(int i) {
        return hubX() + i * (tabW() + TAB_GAP);
    }

    private String tabLabel(int i) {
        return i == 0 ? "Tất cả" : tabCats.get(i - 1).label;
    }

    /** Tab ở vị trí chuột, hoặc -1. */
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
        buildList();
        if (indexOf(module) < 0) { // tính năng đang chọn không thuộc tab mới -> chọn cái đầu tiên của tab
            for (Object o : listItems) {
                if (o instanceof Module) {
                    module = (Module) o;
                    lastModule = module;
                    setScroll = setTarget = 0f;
                    animStart = System.currentTimeMillis();
                    break;
                }
            }
        }
        rebuild();
        listTarget = 0f;
        ensureVisible(module);
    }

    private void cycleTab(int d) {
        int n = tabCount();
        setTab((tabIndex() + d + n) % n);
    }

    private void drawTabs(int mx, int my, float op) {
        int n = tabCount(), tw = tabW(), sel = tabIndex();
        if (tabPos < 0f) tabPos = sel;
        tabPos += (sel - tabPos) * (AnimationsModule.menu() ? 0.3f : 1f);
        if (Math.abs(sel - tabPos) < 0.01f) tabPos = sel;

        for (int i = 0; i < n; i++) {
            int x = tabX(i);
            boolean over = mx >= x && mx < x + tw && my >= TAB_Y && my < TAB_Y + TAB_H;
            Gui.drawRect(x, TAB_Y, x + tw, TAB_Y + TAB_H, Draw.mulAlpha(0x66000000, op));
            Gui.drawRect(x, TAB_Y, x + tw, TAB_Y + 1, 0x33FFFFFF);
            if (i == sel) Gui.drawRect(x, TAB_Y, x + tw, TAB_Y + TAB_H, 0x30FFFFFF);
            else if (over) Gui.drawRect(x, TAB_Y, x + tw, TAB_Y + TAB_H, 0x22FFFFFF);
            String t = fontRendererObj.trimStringToWidth(tabLabel(i), tw - 6);
            fontRendererObj.drawStringWithShadow(t, x + (tw - fontRendererObj.getStringWidth(t)) / 2f,
                    TAB_Y + (TAB_H - 8) / 2f, i == sel ? 0xFFFFFF : over ? 0xE0E0E0 : 0xA8A8A8);
        }
        int ix = hubX() + Math.round(tabPos * (tw + TAB_GAP));
        Gui.drawRect(ix, TAB_Y + TAB_H - 2, ix + tw, TAB_Y + TAB_H, Draw.ACCENT);
    }

    // ----------------------------------------------------------- danh sách bên trái

    private void buildList() {
        listItems.clear();
        tabCats.clear();
        for (Category c : Category.values()) {
            List<Module> inCat = new ArrayList<Module>();
            for (Module m : LunarLite.MODULES) {
                if (m.category == c) inCat.add(m);
            }
            if (inCat.isEmpty()) continue;
            tabCats.add(c);
            if (tabCat != null && c != tabCat) continue;
            // Trong mỗi nhóm xếp theo tên A -> Z cho dễ tìm.
            Collections.sort(inCat, BY_NAME);
            if (tabCat == null) listItems.add(c.label); // tab "Tất cả" mới cần tiêu đề nhóm
            listItems.addAll(inCat);
        }
        if (listItems.isEmpty() && tabCat != null) { // nhóm không còn tính năng nào -> về "Tất cả"
            tabCat = lastTab = null;
            buildList();
            return;
        }
        listY = new int[listItems.size()];
        int y = 0;
        for (int i = 0; i < listItems.size(); i++) {
            listY[i] = y;
            y += listItems.get(i) instanceof Module ? LROW : LHEAD;
        }
        listH = y + 6;
        clampScroll();
    }

    private int indexOf(Module m) {
        return listItems.indexOf(m);
    }

    private void ensureVisible(Module m) {
        int i = indexOf(m);
        if (i < 0) return;
        int y = listY[i] + 3;
        if (y < listTarget + (i > 0 && listItems.get(i - 1) instanceof String ? LHEAD : 0)) {
            listTarget = y - (i > 0 && listItems.get(i - 1) instanceof String ? LHEAD : 0);
        } else if (y + LROW > listTarget + listViewH()) {
            listTarget = y + LROW - listViewH() + 3;
        }
        clampScroll();
    }

    private void select(Module m) {
        if (m == null || m == module) return;
        module = m;
        lastModule = m;
        setScroll = setTarget = 0f;
        animStart = System.currentTimeMillis();
        rebuild();
    }

    private void moveSelection(int d) {
        int i = indexOf(module);
        if (i < 0) return;
        for (int j = i + d; j >= 0 && j < listItems.size(); j += d) {
            if (listItems.get(j) instanceof Module) {
                select((Module) listItems.get(j));
                ensureVisible(module);
                return;
            }
        }
    }

    private void clampScroll() {
        listTarget = Math.max(0f, Math.min(Math.max(0, listH - listViewH()), listTarget));
        listScroll = Math.max(0f, Math.min(Math.max(0, listH - listViewH()), listScroll));
        setTarget = Math.max(0f, Math.min(Math.max(0, contentH - setViewH()), setTarget));
        setScroll = Math.max(0f, Math.min(Math.max(0, contentH - setViewH()), setScroll));
    }

    // ----------------------------------------------------------- bảng tuỳ chỉnh bên phải

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
        headH = 0;
        Module m = module;
        if (m == null) return;

        int inner = paneW() - 20;
        oneCol = inner < 2 * 120 + GAP;
        bw = oneCol ? inner : (inner - GAP) / 2;

        if (m.description != null && m.description.length() > 0) {
            List<?> ls = fontRendererObj.listFormattedStringToWidth(Lang.vi(m.description), inner);
            descLines = new String[ls.size()];
            for (int i = 0; i < descLines.length; i++) descLines[i] = (String) ls.get(i);
        }
        headH = 8 + 14 + descLines.length * 10 + 8;

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
        Btn reset = make(K_RESET, Math.min(WIDE, inner), "Đặt lại tuỳ chỉnh");
        reset.m = m;
        addFull(reset);

        int y = headH;
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

    /** Xếp nút cố định và các nút trong bảng tuỳ chỉnh (y thật được cập nhật mỗi khung hình theo cuộn). */
    private void layout() {
        buttonList.clear();
        Btn edit = make(K_EDIT, sideW() - 8, "Sửa vị trí HUD…");
        edit.fixed = true;
        refresh(edit);
        place(edit, hubX() + 4, bottom() - 22);
        Btn done = make(K_DONE, WIDE, "Xong");
        done.fixed = true;
        place(done, width / 2 - WIDE / 2, doneY());

        int px = paneX(), pw = paneW(), inner = pw - 20;
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
        clampScroll();
    }

    private void rebuild() {
        buildList();
        buildRows();
        layout();
    }

    // ---------------------------------------------------------------- GuiScreen

    @Override
    public void initGui() {
        rebuild();
        ensureVisible(module);
        listScroll = listTarget;
    }

    private String title() {
        return "Zhwii Client";
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ThemeModule.apply();
        curMx = mx;
        curMy = my;
        float op = openProgress();
        drawGradientRect(0, 0, width, height, Draw.mulAlpha(0xC0101010, op), Draw.mulAlpha(0xD0101010, op));
        int cx = width / 2;
        int top = top(), bottom = bottom();
        int hx = hubX(), sw = sideW(), px = paneX(), pw = paneW();

        // cuộn mượt
        float k = AnimationsModule.menu() ? 0.35f : 1f;
        listScroll += (listTarget - listScroll) * k;
        setScroll += (setTarget - setScroll) * k;
        if (Math.abs(listTarget - listScroll) < 0.4f) listScroll = listTarget;
        if (Math.abs(setTarget - setScroll) < 0.4f) setScroll = setTarget;

        GlStateManager.pushMatrix();
        float kk = 0.95f + 0.05f * op;
        GlStateManager.translate(cx, height / 2f + (1f - op) * 10f, 0f);
        GlStateManager.scale(kk, kk, 1f);
        GlStateManager.translate(-cx, -height / 2f, 0f);

        // tiêu đề + gợi ý
        drawCenteredString(fontRendererObj, title(), cx, 10, 0xFFFFFF);
        int uw = Math.round((fontRendererObj.getStringWidth(title()) / 2f + 8) * op);
        Gui.drawRect(cx - uw, 19, cx + uw, 20, Draw.ACCENT);
        drawTabs(mx, my, op);

        // nền hai khung
        for (int p = 0; p < 2; p++) {
            int x = p == 0 ? hx : px, w = p == 0 ? sw : pw;
            Gui.drawRect(x, top, x + w, bottom, Draw.mulAlpha(0x66000000, op));
            Gui.drawRect(x, top, x + w, top + 1, 0x33FFFFFF);
            Gui.drawRect(x, bottom - 1, x + w, bottom, 0x33FFFFFF);
        }

        // ---------- danh sách bên trái
        Module hover = null;
        Draw.scissor(mc, hx, top, sw, listViewH());
        int ls = Math.round(listScroll);
        for (int i = 0; i < listItems.size(); i++) {
            int y = top + 3 + listY[i] - ls;
            Object it = listItems.get(i);
            if (it instanceof String) {
                if (y + LHEAD < top || y > listBottom()) continue;
                String h = (String) it;
                int ty = y + 6;
                fontRendererObj.drawStringWithShadow(h, hx + 8, ty, Draw.ACCENT_LIGHT);
                int lx = hx + 8 + fontRendererObj.getStringWidth(h) + 5;
                Gui.drawRect(lx, ty + 4, hx + sw - 8, ty + 5, Draw.ACCENT_DIM);
                continue;
            }
            if (y + LROW < top || y > listBottom()) continue;
            Module m = (Module) it;
            int x1 = hx + 3, x2 = hx + sw - 9, y2 = y + LROW - 2;
            boolean over = inList(mx, my) && mx >= x1 && mx < x2 && my >= y && my < y2;
            boolean sel = m == module;
            if (over) hover = m;
            if (sel) {
                Gui.drawRect(x1, y, x2, y2, 0x40FFFFFF);
                Gui.drawRect(x1, y, x1 + 2, y2, Draw.ACCENT);
            } else if (over) {
                Gui.drawRect(x1, y, x2, y2, 0x22FFFFFF);
            }
            // công tắc nhỏ bật / tắt nhanh
            int tx = x2 - 22, ty2 = y + (LROW - 2 - 8) / 2;
            float swT = m.enabled ? 1f : 0f;
            Float swCur = swAnim.get(m);
            float swA = swCur == null ? swT : swCur + (swT - swCur) * k;
            if (Math.abs(swA - swT) < 0.01f) swA = swT;
            swAnim.put(m, swA);
            drawSwitch(tx, ty2, swA);
            String nm = fontRendererObj.trimStringToWidth(Lang.vi(m.name), sw - 6 - 26 - 12);
            fontRendererObj.drawStringWithShadow(nm, x1 + 6, y + (LROW - 2 - 8) / 2, m.enabled || sel ? 0xFFFFFF : 0xA8A8A8);
        }
        Draw.endScissor();
        if (listH > listViewH()) scrollbar(true, top, listViewH(), listH, listScroll);
        Gui.drawRect(hx + 4, listBottom(), hx + sw - 4, listBottom() + 1, 0x33FFFFFF);

        // ---------- bảng tuỳ chỉnh bên phải
        int sc = Math.round(setScroll);
        for (Object o : buttonList) {
            Btn b = (Btn) o;
            if (b.baseY < 0) continue;
            b.yPosition = top + b.baseY - sc;
            b.visible = b.yPosition + BH > top && b.yPosition < bottom;
        }
        Draw.scissor(mc, px, top, pw, setViewH());
        if (module != null) {
            int y0 = top - sc;
            GlStateManager.pushMatrix();
            GlStateManager.translate(px + 8, y0 + 8, 0f);
            GlStateManager.scale(1.4f, 1.4f, 1f);
            fontRendererObj.drawStringWithShadow(Lang.vi(module.name), 0, 0, 0xFFFFFF);
            GlStateManager.popMatrix();
            String cat = module.category.label;
            fontRendererObj.drawStringWithShadow(cat, px + pw - 12 - fontRendererObj.getStringWidth(cat), y0 + 10, 0x909090);
            for (int i = 0; i < descLines.length; i++) {
                fontRendererObj.drawString(descLines[i], px + 8, y0 + 8 + 14 + i * 10, 0xC0C0C0);
            }
            for (Row r : rows) {
                if (r.header == null) continue;
                int ry = top + r.y - sc;
                if (ry + BH < top || ry > bottom) continue;
                if (r.header.length() == 0) {
                    Gui.drawRect(px + 10, ry + BH / 2, px + pw - 14, ry + BH / 2 + 1, 0x22FFFFFF);
                    continue;
                }
                int hy = ry + (BH - 8) / 2;
                int tw = fontRendererObj.getStringWidth(r.header);
                int hcx = px + (pw - 4) / 2;
                fontRendererObj.drawStringWithShadow(r.header, hcx - tw / 2f, hy, Draw.ACCENT_LIGHT);
                int ly = ry + BH / 2;
                Gui.drawRect(px + 10, ly, hcx - tw / 2 - 6, ly + 1, Draw.ACCENT_DIM);
                Gui.drawRect(hcx + tw / 2 + 6, ly, px + pw - 14, ly + 1, Draw.ACCENT_DIM);
            }
            for (Object o : buttonList) {
                Btn b = (Btn) o;
                if (b.baseY >= 0) b.drawButton(mc, mx, my);
            }
        }
        Draw.endScissor();
        if (contentH > setViewH()) scrollbar(false, top, setViewH(), contentH, setScroll);

        // nút cố định (Sửa HUD, Xong)
        for (Object o : buttonList) {
            Btn b = (Btn) o;
            if (b.baseY < 0) b.drawButton(mc, mx, my);
        }

        // dòng mô tả / gợi ý ngay trên nút Xong
        String foot = hover != null && hover.description != null ? Lang.vi(hover.description) : null;
        if (foot != null) {
            foot = fontRendererObj.trimStringToWidth(foot, width - 20);
            drawCenteredString(fontRendererObj, foot, cx, doneY() - 13, 0xE0E0E0);
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static final int BAR_W = 6;
    private int dragBar;   // 0 = không kéo, 1 = danh sách trái, 2 = bảng phải
    private int dragOff;   // khoảng cách từ chuột tới đỉnh thanh trượt

    private int barX(boolean list) {
        return list ? hubX() + sideW() - BAR_W - 1 : paneX() + paneW() - BAR_W - 1;
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

    /**
     * Thanh cuộn kiểu Sodium: rãnh mảnh tối, tay nắm trắng bo tròn;
     * rê chuột thì phồng lên và sáng hơn, kéo thì trắng hẳn.
     */
    private void scrollbar(boolean list, int y, int viewH, int totalH, float scroll) {
        int idx = list ? 1 : 2;
        int x = barX(list);
        int bh = thumbH(viewH, totalH);
        int by = thumbY(y, viewH, totalH, scroll);
        boolean on = dragBar == idx;
        boolean hover = curMx >= x - 3 && curMx < x + BAR_W + 2 && curMy >= y && curMy < y + viewH;
        float target = on || hover ? 1f : 0f;
        barAnim[idx] += (target - barAnim[idx]) * (AnimationsModule.menu() ? 0.3f : 1f);
        if (Math.abs(target - barAnim[idx]) < 0.02f) barAnim[idx] = target;

        int tw = 3 + Math.round(2 * barAnim[idx]);          // 3px -> 5px
        int tx1 = x + 1 + Math.round((5 - tw) / 2f);
        pill(x + 1, y + 2, x + 6, y + viewH - 2, 0x55000000); // rãnh
        int alpha = on ? 0xFF : 0x99 + Math.round(0x33 * barAnim[idx]);
        pill(tx1, by + 2, tx1 + tw, Math.min(y + viewH - 2, by + bh - 2), (alpha << 24) | 0xFFFFFF);
    }

    /** Bấm chuột trái vào thanh cuộn: bắt đầu kéo (bấm ngoài thanh thì nhảy tới đó). */
    private boolean startBarDrag(int mx, int my) {
        for (int k = 1; k <= 2; k++) {
            boolean list = k == 1;
            int viewH = list ? listViewH() : setViewH();
            int totalH = list ? listH : contentH;
            if (totalH <= viewH) continue;
            int x = barX(list);
            if (mx < x - 3 || mx >= x + BAR_W + 2 || my < top() || my >= top() + viewH) continue;
            float cur = list ? listScroll : setScroll;
            int by = thumbY(top(), viewH, totalH, cur), bh = thumbH(viewH, totalH);
            dragBar = k;
            dragOff = (my >= by && my < by + bh) ? my - by : bh / 2;
            dragTo(my);
            return true;
        }
        return false;
    }

    private void dragTo(int my) {
        boolean list = dragBar == 1;
        int viewH = list ? listViewH() : setViewH();
        int totalH = list ? listH : contentH;
        int range = Math.max(1, viewH - thumbH(viewH, totalH));
        float v = (my - dragOff - top()) / (float) range * (totalH - viewH);
        if (list) listTarget = listScroll = v;
        else setTarget = setScroll = v;
        clampScroll();
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (dragBar != 0 && btn == 0) {
            dragTo(my);
            return;
        }
        super.mouseClickMove(mx, my, btn, time);
    }

    @Override
    protected void actionPerformed(GuiButton g) throws IOException {
        if (g instanceof Btn) handle((Btn) g, false);
    }

    /** Module ở vị trí chuột trong danh sách (hoặc null); out[0] = true nếu trúng công tắc nhỏ. */
    private Module listHit(int mx, int my, boolean[] onSwitch) {
        if (!inList(mx, my)) return null;
        int ls = Math.round(listScroll);
        for (int i = 0; i < listItems.size(); i++) {
            if (!(listItems.get(i) instanceof Module)) continue;
            int y = top() + 3 + listY[i] - ls;
            int x1 = hubX() + 3, x2 = hubX() + sideW() - 9;
            if (mx >= x1 && mx < x2 && my >= y && my < y + LROW - 2) {
                if (onSwitch != null) onSwitch[0] = mx >= x2 - 24;
                return (Module) listItems.get(i);
            }
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        if (closing) return;
        if (btn != 0 && btn != 1) return;
        if (btn == 0) {
            int t = tabAt(mx, my);
            if (t >= 0) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                        new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                setTab(t);
                return;
            }
        }
        if (btn == 0 && startBarDrag(mx, my)) return;

        boolean[] sw = new boolean[1];
        Module hit = listHit(mx, my, sw);
        if (hit != null) {
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                    new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
            if (btn == 1 || sw[0]) {
                hit.toggle();
                if (hit == module) {
                    for (Object o : buttonList) {
                        if (o instanceof Btn && ((Btn) o).kind == K_ENABLE) refresh((Btn) o);
                    }
                }
            } else {
                select(hit);
            }
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
    protected void mouseReleased(int mx, int my, int state) {
        if (state == 0) dragBar = 0;
        if (active != null && state == 0) {
            active.mouseReleased(mx, my);
            active = null;
        }
        super.mouseReleased(mx, my, state);
    }

    /** Dấu vết các tuỳ chỉnh đang hiện, để biết có cần dựng lại bảng phải không. */
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
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        float step = (d > 0 ? -1 : 1) * 2f * LROW;
        if (mx < paneX() - 3) listTarget += step;
        else setTarget += step;
        clampScroll();
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || (LunarLite.keyMenu != null && key == LunarLite.keyMenu.getKeyCode())) {
            requestClose();
        } else if (key == Keyboard.KEY_UP) {
            moveSelection(-1);
        } else if (key == Keyboard.KEY_DOWN) {
            moveSelection(1);
        } else if (key == Keyboard.KEY_LEFT) {
            cycleTab(-1);
        } else if (key == Keyboard.KEY_RIGHT) {
            cycleTab(1);
        }
    }

    @Override
    public void onGuiClosed() {
        lastModule = module;
        LunarLite.saveConfig();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
