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
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Menu dạng bảng ô (dashboard):
 *   TRANG CHỦ : mỗi tính năng là một ô có biểu tượng riêng, số phím tắt 1-9 / 0, trạng thái BẬT / TẮT.
 *               Thanh nhóm ở trên lọc theo nhóm, ô chia thành từng trang (10 ô / trang).
 *   CHI TIẾT  : bấm một ô (hoặc nhấn phím số) để mở bảng tuỳ chỉnh của tính năng đó.
 * Chuột trái vào ô = hộp xác nhận bật/tắt, chuột phải = mở tuỳ chỉnh. ESC = quay lại / đóng menu.
 *
 * Toàn bộ giao diện được vẽ trong một khung ảo VW x VH rồi tự thu nhỏ cho vừa màn hình.
 */
public class MenuScreen extends GuiScreen {
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };

    // ---- khung ảo
    private static final int VW = 520, VH = 300, PAD = 14;
    private static final int BH = 20, ROW = 24, GAP = 6, BAR_W = 6;
    // ---- các vùng theo trục dọc
    private static final int CARD_Y = 92, CARD_H = 76, CARD_GAP = 8, CARDS_VIS = 6;
    private static final int HINT_Y = 206, BTN_Y = 270;
    private static final int PANE_TOP = 78, PANE_BOTTOM = 264;

    // ---- màu
    /** Mỗi tính năng một màu nhấn riêng (theo thứ tự đăng ký). */
    private static final int[] PALETTE = {
        0x35D9FF, 0x8DFFCF, 0x58B8FF, 0xA6F3FF, 0x62E6D5, 0x78A9FF, 0xB4F7FF, 0x5BD6FF, 0xA0D9E8, 0x4DA3FF
    };

    private static final int K_DONE = 0, K_EDIT = 1, K_ENABLE = 2, K_BOOL = 3, K_MODE = 4, K_COLOR = 5,
            K_SLIDER = 6, K_ACTION = 7, K_RESET = 8, K_BACK = 9, K_IMPORT = 10;

    /** Sắp xếp tính năng theo tên hiển thị, không phân biệt hoa thường. */
    private static final Comparator<Module> BY_NAME = new Comparator<Module>() {
        @Override
        public int compare(Module a, Module b) {
            boolean fa = FAVS.contains(a.id), fb = FAVS.contains(b.id);
            if (fa != fb) return fa ? -1 : 1;
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

    /** Tính năng yêu thích (id) - hiện lên đầu danh sách. Chỉ nhớ trong phiên chơi. */
    private static final java.util.Set<String> FAVS = new java.util.HashSet<String>();

    /** Nhớ tính năng và nhóm đang chọn giữa các lần mở menu. */
    private static Module lastModule;
    private static Category lastTab; // null = nhóm "Tất cả"
    private static boolean lastMods; // đang xem hàng tính năng của một nhóm

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

        /** Âm bấm do MenuScreen tự phát (chuông SAO), không dùng tiếng click vanilla. */
        @Override
        public void playPressSound(net.minecraft.client.audio.SoundHandler handler) {}

        /** Hàng tuỳ chỉnh = ô bo góc: nhãn bên trái + điều khiển bên phải; nút thường = nút bo góc. */
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
            int col = enabled ? Skin.TEXT : 0xFF808890;
            int acc = Draw.ACCENT;

            GlStateManager.pushMatrix();
            GlStateManager.translate(fixed ? 0f : (1f - st) * 22f, 0f, 0f);

            switch (kind) {
                case K_ENABLE:
                case K_BOOL: {
                    Skin.saoRow(x, y, w, h, hov);
                    String lb = kind == K_ENABLE ? "Bật tính năng" : Lang.vi(s.label);
                    fr.drawStringWithShadow(fr.trimStringToWidth(lb, w - 50), x + 9, ty, col);
                    Skin.saoSwitch(x + w - 32, y + (h - 12) / 2, sw);
                    break;
                }
                case K_MODE: {
                    Skin.saoRow(x, y, w, h, hov);
                    String val = Lang.vi(((ModeSetting) s).get());
                    int bw2 = Math.min(w / 2, fr.getStringWidth(val) + 18);
                    int bx = x + w - bw2 - 4, by = y + 3;
                    Skin.saoField(bx, by, bw2, h - 6, hov);
                    String vt = fr.trimStringToWidth(val, bw2 - 8);
                    fr.drawString(vt, bx + (bw2 - fr.getStringWidth(vt)) / 2f, ty, Skin.INK, false);
                    fr.drawStringWithShadow(fr.trimStringToWidth(Lang.vi(s.label), w - bw2 - 20), x + 9, ty, col);
                    break;
                }
                case K_COLOR: {
                    Skin.saoRow(x, y, w, h, hov);
                    int sx = x + w - 46, sy = y + 3;
                    Draw.rrect(sx, sy, sx + 40, sy + h - 6, 3, Draw.lerp(0xFF8E949B, 0xFFFFFFFF, hov));
                    Draw.rrect(sx + 1, sy + 1, sx + 39, sy + h - 7, 2, 0xFF000000 | ((ColorSetting) s).value);
                    fr.drawStringWithShadow(fr.trimStringToWidth(Lang.vi(s.label), w - 66), x + 9, ty, col);
                    break;
                }
                case K_SLIDER: {
                    Skin.saoRow(x, y, w, h, hov);
                    SliderSetting ss = (SliderSetting) s;
                    int tx = trackX(this);
                    int vx0 = tx - 44;
                    String val = ss.display();
                    Skin.saoField(vx0, y + 3, 38, h - 6, 0f);
                    String vt = fr.trimStringToWidth(val, 34);
                    fr.drawString(vt, vx0 + (38 - fr.getStringWidth(vt)) / 2f, ty, Skin.INK, false);
                    fr.drawStringWithShadow(fr.trimStringToWidth(Lang.vi(s.label), Math.max(10, vx0 - x - 14)), x + 9, ty, col);
                    int ly = y + h / 2;
                    int hx = tx + Math.round((float) ss.ratio() * (TRACK_W - 8));
                    Draw.rrect(tx, ly - 2, tx + TRACK_W, ly + 2, 2, 0xFF8E949B);
                    Gui.drawRect(tx + 1, ly - 2, tx + TRACK_W - 1, ly - 1, 0x55000000);
                    Draw.rrect(tx, ly - 2, hx + 4, ly + 2, 2, acc);
                    Draw.disc(hx + 4f, ly + 0.8f, 6.6f, 0x30000000);
                    Draw.disc(hx + 4f, ly, 6f, Draw.lerp(0xFF9AA0A6, acc, hov));
                    Draw.disc(hx + 4f, ly, 4.7f, 0xFFFFFFFF, 0xFFDDE0E3);
                    break;
                }
                default: {
                    int style = kind == K_DONE ? Skin.PRIMARY : kind == K_RESET ? Skin.DANGER : Skin.NORMAL;
                    Skin.saoButton(fr, x, y, w, h, displayString, hov, style);
                    break;
                }
            }
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mouseDragged(mc, mouseX, mouseY); // thanh trượt cập nhật khi đang kéo
        }
    }

    private static final int TRACK_W = 104;

    private static int trackX(Btn b) {
        return b.xPosition + b.width - 110;
    }

    /** Công tắc dạng viên thuốc: núm trượt, nền màu nhấn khi bật; a = 0..1 để trượt mượt. */
    private static void drawSwitch(int tx, int sy, float a) {
        Draw.rrect(tx, sy, tx + 24, sy + 12, 6, Draw.lerp(Skin.STROKE, Draw.ACCENT, a));
        int kx = tx + 2 + Math.round(12 * a);
        Draw.rrect(kx, sy + 2, kx + 8, sy + 10, 4, 0xFFFFFFFF);
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
            float r = (mx - trackX(this) - 4) / (float) (TRACK_W - 8);
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
            if (super.mousePressed(mc, mouseX, mouseY) && mouseX >= trackX(this) - 6) {
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
    private boolean inMods;                      // false = hàng nhóm, true = hàng tính năng của nhóm
    private float rowScroll, rowTarget;          // cuộn ngang của hàng thẻ (px)
    private final Map<Object, Float> hovAnim = new HashMap<Object, Float>();
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
        tabCat = null; // mới mở: chọn nút đầu ("Tất cả"), danh sách ẩn, cụm đứng giữa
        expanded = false;
        inMods = false;
        buildShown();
        focusModule();
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
        play("menu.close");
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

    /** Phát âm thanh menu riêng (assets/lunarlite/sounds/menu/*.ogg, khai báo trong sounds.json). */
    private void play(String ev) {
        net.minecraft.util.ResourceLocation loc = new net.minecraft.util.ResourceLocation("lunarlite", ev);
        if (mc.thePlayer != null) {
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(loc,
                    (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ));
        } else {
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(loc, 1.0F));
        }
    }

    private void click() {
        play("menu.click");
    }

    private void sfxToggle(boolean on) {
        play(on ? "menu.on" : "menu.off");
    }

    private boolean soundOpened;
    private int lastHoverSfx = -1;

    // ------------------------------------------------------------------ khung ảo <-> màn hình

    /** Độ phóng to tối đa của menu (1.0 = cỡ gốc). Chỉnh số này để menu to / nhỏ hơn. */
    private static final float MENU_SCALE = 0.95f;

    private float ui() {
        return Math.min(MENU_SCALE, Math.min((width - 8f) / VW, (height - 8f) / VH));
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
    }

    private int tabCount() {
        return 1 + tabCats.size();
    }

    private int tabIndex() {
        return tabCat == null ? 0 : tabCats.indexOf(tabCat) + 1;
    }

    private String tabLabel(int i) {
        return i == 0 ? "Tất cả" : tabCats.get(i - 1).label;
    }

    private String tabIcon(int i) {
        return i == 0 ? "cat_all" : Icons.cat(tabCats.get(i - 1));
    }

    private void setTab(int i) {
        Category c = i <= 0 || i > tabCats.size() ? null : tabCats.get(i - 1);
        tabCat = c;
        lastTab = c;
        rowScroll = rowTarget = 0f;
        listScroll = listTarget = 0f;
        animStart = System.currentTimeMillis();
        buildShown();
    }

    private void cycleTab(int d) {
        int n = tabCount();
        setTab((tabIndex() + d + n) % n);
    }

    // ----------------------------------------------------------- hàng thẻ ngang (1 dòng)

    private int cardW() {
        return (paneW() - (CARDS_VIS - 1) * CARD_GAP) / CARDS_VIS;
    }

    private int itemCount() {
        return inMods ? shown.size() : tabCount();
    }

    private int itemsW() {
        int n = itemCount();
        return n <= 0 ? 0 : n * cardW() + (n - 1) * CARD_GAP;
    }

    private float maxRowScroll() {
        return Math.max(0, itemsW() - paneW());
    }

    /** Ít thẻ thì căn giữa, nhiều thẻ thì bắt đầu từ lề trái và cuộn ngang. */
    private int rowX0() {
        int tw = itemsW();
        return tw <= paneW() ? PAD + (paneW() - tw) / 2 : PAD;
    }

    private int itemX(int i) {
        return rowX0() + i * (cardW() + CARD_GAP) - Math.round(rowScroll);
    }

    private void clampRow() {
        float max = maxRowScroll();
        rowTarget = Math.max(0f, Math.min(max, rowTarget));
        rowScroll = Math.max(0f, Math.min(max, rowScroll));
    }

    private void scrollRow(int cards) {
        rowTarget += cards * (cardW() + CARD_GAP);
        clampRow();
    }

    /** Cuộn hàng sao cho tính năng đang chọn nằm giữa. */
    private void focusModule() {
        listScroll = listTarget = 0f;
        int i = shown.indexOf(module);
        if (i >= 0) listTarget = listScroll = i * LSTEP - (listH() - LRH) / 2f;
        clampList();
    }

    private int firstVisible() {
        return Math.max(0, Math.round(rowScroll / (cardW() + CARD_GAP)));
    }

    /** Thẻ ở vị trí chuột (0..itemCount-1), hoặc -1. */
    private int itemAt(int mx, int my) {
        if (my < CARD_Y || my >= CARD_Y + CARD_H || mx < PAD || mx >= VW - PAD) return -1;
        for (int i = 0; i < itemCount(); i++) {
            if (Draw.in(mx, my, itemX(i), CARD_Y, cardW(), CARD_H)) return i;
        }
        return -1;
    }

    private void enterCat(int i) {
        setTab(i);
        inMods = true;
        lastMods = true;
        animStart = System.currentTimeMillis();
    }

    private void leaveCat() {
        inMods = false;
        lastMods = false;
        rowScroll = rowTarget = 0f;
        animStart = System.currentTimeMillis();
    }

    private int[] catCounts(Category c) {
        int on = 0, tot = 0;
        for (Module m : LunarLite.MODULES) {
            if (c != null && m.category != c) continue;
            tot++;
            if (m.enabled) on++;
        }
        return new int[] {on, tot};
    }

    private void openModule(Module m) {
        if (m == null) return;
        play("menu.select");
        module = m;
        lastModule = m;
        detail = true;
        setScroll = setTarget = 0f;
        animStart = System.currentTimeMillis();
        rebuild();
    }

    private void goBack() {
        play("menu.back");
        detail = false;
        active = null;
        dragBar = false;
        animStart = System.currentTimeMillis();
        rebuild();
        focusModule();
    }

    private void moveModule(int d) {
        int i = shown.indexOf(module);
        if (i < 0) return;
        int j = i + d;
        if (j < 0 || j >= shown.size()) return;
        openModule(shown.get(j));
    }

    // ----------------------------------------------------------- hộp xác nhận bật / tắt (kiểu hộp "Invite" của SAO)

    private static final int DW = 214, DH = 122, DX = (VW - DW) / 2, DY = (VH - DH) / 2, DR = 13;
    private Module confirmMod;
    private long confirmAt;
    private float okHov, noHov;

    private void askToggle(Module m) {
        if (m == null) return;
        play("menu.select");
        confirmMod = m;
        confirmAt = System.currentTimeMillis();
        okHov = noHov = 0f;
    }

    private void closeConfirm(boolean ok) {
        Module m = confirmMod;
        confirmMod = null;
        if (ok && m != null) toggleModule(m);
        else play("menu.back");
    }

    private boolean onOk(int mx, int my) {
        int cx = DX + DW / 2 - 36, cy = DY + DH - 19;
        return (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= (DR + 3) * (DR + 3);
    }

    private boolean onNo(int mx, int my) {
        int cx = DX + DW / 2 + 36, cy = DY + DH - 19;
        return (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= (DR + 3) * (DR + 3);
    }

    private void confirmClick(int mx, int my) {
        if (onOk(mx, my)) closeConfirm(true);
        else if (onNo(mx, my) || !Draw.in(mx, my, DX, DY, DW, DH)) closeConfirm(false);
    }

    private void drawConfirm(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        float k = AnimationsModule.menu() ? 0.3f : 1f;
        okHov += ((onOk(mx, my) ? 1f : 0f) - okHov) * k;
        noHov += ((onNo(mx, my) ? 1f : 0f) - noHov) * k;
        float pop = AnimationsModule.menu()
                ? Math.max(0f, AnimationsModule.easeOutBack((System.currentTimeMillis() - confirmAt) * AnimationsModule.speed() / 220f)) : 1f;
        Gui.drawRect(-VW, -VH, 2 * VW, 2 * VH, Math.round(0x70 * Math.min(1f, pop)) << 24);
        GlStateManager.pushMatrix();
        GlStateManager.translate(VW / 2f, VH / 2f, 0f);
        GlStateManager.scale(pop, pop, 1f);
        GlStateManager.translate(-VW / 2f, -VH / 2f, 0f);

        boolean on = confirmMod.enabled;
        Gui.drawRect(DX - 1, DY - 1, DX + DW + 1, DY + DH + 2, 0x66000000);
        // thanh tiêu đề sáng
        vgrad(DX, DY, DX + DW, DY + 22, 0xFFF6F7F8, 0xFFDEE0E2);
        Gui.drawRect(DX, DY + 22, DX + DW, DY + 23, 0xFFB9BCBF);
        String title = on ? "Tắt tính năng" : "Bật tính năng";
        fr.drawString(title, DX + (DW - fr.getStringWidth(title)) / 2f, DY + 7, 0xFF3A3F45, false);
        // thân xám
        Gui.drawRect(DX, DY + 23, DX + DW, DY + DH - 38, 0xF28E9296);
        Gui.drawRect(DX, DY + 23, DX + DW, DY + 24, 0x22000000);
        String l1 = on ? "Bạn có muốn tắt" : "Bạn có muốn bật";
        String l2 = fit(Lang.vi(confirmMod.name), DW - 24) + "?";
        fr.drawStringWithShadow(l1, DX + (DW - fr.getStringWidth(l1)) / 2f, DY + 40, 0xFFFFFFFF);
        fr.drawStringWithShadow(l2, DX + (DW - fr.getStringWidth(l2)) / 2f, DY + 54, 0xFFFFE2A8);
        // chân hộp + hai nút tròn
        vgrad(DX, DY + DH - 38, DX + DW, DY + DH, 0xFFF1F2F3, 0xFFDEE0E2);
        Gui.drawRect(DX, DY + DH - 38, DX + DW, DY + DH - 37, 0xFFFFFFFF);
        Draw.fadeLine(DX + 12, DY + DH - 37, DW - 24, Draw.ACCENT & 0xFFFFFF, 0x80);
        int cy = DY + DH - 19;
        int okx = DX + DW / 2 - 36, nox = DX + DW / 2 + 36;
        // OK: xanh dương, vòng trắng
        Draw.disc(okx, cy + 1.5f, DR + 2f, 0x30000000, 0x00000000);
        if (okHov > 0.02f) Draw.disc(okx, cy, DR + 6f, Math.round(okHov * 80) << 24 | 0x2F7CF0, 0x002F7CF0);
        Draw.disc(okx, cy, DR + 1.5f, 0xFFFFFFFF);
        Draw.disc(okx, cy, DR, Draw.lerp(0xFF63A4FF, 0xFF8FC0FF, okHov), Draw.lerp(0xFF1F63D8, 0xFF2F7CF0, okHov));
        Draw.circleRing(okx, cy, 4.2f, 6.2f, 0xFFFFFFFF);
        // Huỷ: đỏ hồng, dấu X trắng
        Draw.disc(nox, cy + 1.5f, DR + 2f, 0x30000000, 0x00000000);
        if (noHov > 0.02f) Draw.disc(nox, cy, DR + 6f, Math.round(noHov * 80) << 24 | 0xE8506E, 0x00E8506E);
        Draw.disc(nox, cy, DR + 1.5f, 0xFFFFFFFF);
        Draw.disc(nox, cy, DR, Draw.lerp(0xFFFF8CA3, 0xFFFFA9BB, noHov), Draw.lerp(0xFFD93A5E, 0xFFE8506E, noHov));
        Draw.line(nox - 4.5f, cy - 4.5f, nox + 4.5f, cy + 4.5f, 2.4f, 0xFFFFFFFF);
        Draw.line(nox + 4.5f, cy - 4.5f, nox - 4.5f, cy + 4.5f, 2.4f, 0xFFFFFFFF);
        Skin.brackets(DX - 3, DY - 3, DW + 6, DH + 6, 8, (0xB0 << 24) | (Draw.ACCENT & 0xFFFFFF));
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Màu riêng cho biểu tượng của từng tính năng (xanh lá, đỏ, cam, xanh dương, tím, ...). */
    private static final int[] ICON_COLORS = {
        0xFF3FB86F, 0xFFE8505B, 0xFFF0A30A, 0xFF3F8EF0, 0xFF9B59D6, 0xFF1FB5A8, 0xFFE0609E, 0xFFD9A400
    };

    private static int iconColor(Module m, boolean on) {
        int i = LunarLite.MODULES.indexOf(m);
        if (i < 0) i = 0;
        int c = ICON_COLORS[i % ICON_COLORS.length];
        return on ? c : Draw.lerp(c, 0xFF9AA0A6, 0.35f);
    }

    private void toggleModule(Module m) {
        m.toggle();
        sfxToggle(m.enabled);
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

        int inner = paneW() - 30;
        oneCol = true;
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
        if (!detail) return; // trang chủ: 3 nút gom vào nút tròn cuối cột
        int n = detail ? 4 : 3;
        int w = detail ? (paneW() - GAP * (n - 1)) / n : 112;
        int x = detail ? PAD : (VW - (w * n + GAP * (n - 1))) / 2;
        if (detail) {
            Btn back = make(K_BACK, w, "« Quay lại");
            back.fixed = true;
            place(back, x, BTN_Y);
            x += w + GAP;
        }
        Btn imp = make(K_IMPORT, w, "Nhập / Xuất");
        imp.fixed = true;
        place(imp, x, BTN_Y);
        x += w + GAP;
        Btn edit = make(K_EDIT, w, "Sửa vị trí HUD");
        edit.fixed = true;
        place(edit, x, BTN_Y);
        x += w + GAP;
        Btn done = make(K_DONE, w, "Xong");
        done.fixed = true;
        place(done, x, BTN_Y);

        if (detail) {
            int px = paneX(), inner = paneW() - 30;
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
        if (!soundOpened) {
            soundOpened = true;
            play("menu.open");
        }
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
        if (detail && module != null && module.consumeUiDirty()) rebuild();
        curMx = vx(mx);
        curMy = vy(my);
        float op = openProgress();
        // nền blur đen: thế giới phía sau bị làm mờ rồi phủ một lớp đen trong suốt

        drawBackdrop(op);

        float k = AnimationsModule.menu() ? 0.35f : 1f;
        setScroll += (setTarget - setScroll) * k;
        if (Math.abs(setTarget - setScroll) < 0.4f) setScroll = setTarget;

        GlStateManager.pushMatrix();
        float kk = (0.95f + 0.05f * op) * ui();
        GlStateManager.translate(width / 2f, height / 2f + (1f - op) * 10f, 0f);
        GlStateManager.scale(kk, kk, 1f);
        GlStateManager.translate(-VW / 2f, -VH / 2f, 0f);

        drawPanel(confirmMod != null ? -9999 : curMx, confirmMod != null ? -9999 : curMy);
        if (confirmMod != null) drawConfirm(curMx, curMy);

        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Nền kiểu Aincrad: tối viền, hạt sáng bay chậm lên trên, tia sáng ngang quét ra khi mở menu. */
    private void drawBackdrop(float op) {
        long t = System.currentTimeMillis();
        // tối dần ở mép trên và mép dưới màn hình cho chiều sâu, giữa vẫn sáng
        int va = Math.round(0x50 * Math.max(0f, Math.min(1f, op)));
        vgrad(0, 0, width, height / 3, va << 24, 0);
        vgrad(0, height * 2 / 3, width, height, 0, (va + 0x10 > 255 ? 255 : va + 0x10) << 24);
        int acc = Draw.ACCENT & 0xFFFFFF;
        for (int i = 0; i < 40; i++) {
            long h = (i + 1) * 2654435761L;
            float fx = ((h >>> 4) & 1023) / 1023f;
            float ph = ((h >>> 14) & 1023) / 1023f;
            float per = 16000f + ((h >>> 24) & 7) * 2600f;
            float life = (t / per + ph) % 1f;
            float px = fx * width + (float) Math.sin(t / 2100.0 + i) * 7f;
            float py = height * (1.04f - life * 1.08f);
            int a = Math.round((float) Math.sin(Math.PI * life) * 0x78 * op);
            if (a < 6) continue;
            int sz = (i % 4 == 0) ? 2 : 1;
            int col = (a << 24) | (i % 5 == 0 ? acc : 0xFFFFFF);
            Gui.drawRect(Math.round(px), Math.round(py), Math.round(px) + sz, Math.round(py) + sz, col);
        }
        if (op < 0.98f) {
            float e = closing ? op : 1f - op;
            int bw = Math.round(width * (closing ? 0.5f + 0.4f * op : 0.9f * op + 0.05f));
            int cy = height / 2;
            int a = Math.round(0xE0 * (closing ? 1f - op : e)) & 0xFF;
            Draw.fadeLine(width / 2 - bw / 2, cy - 1, bw, 0xFFFFFF, a / 3);
            Draw.fadeLine(width / 2 - bw / 2, cy, bw, 0xFFFFFF, a);
            Draw.fadeLine(width / 2 - bw / 2, cy + 1, bw, acc, a / 3);
        }
    }

    private void drawPanel(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        int acc = Draw.ACCENT;

        // trang chủ: không có khung, chỉ tiêu đề + một hàng thẻ trên nền blur
        if (!detail || module == null) {
            drawHome(mx, my);
            return;
        }

        long tn = System.currentTimeMillis();
        // cửa sổ SAO phủ toàn bộ trang chi tiết
        Skin.saoWindow(4, 2, VW - 8, VH - 4, 40, tn);
        {
            boolean onM = module.enabled;
            Skin.saoOrb(PAD + 16, 21, 15f, SaoIcons.forModule(module), onM ? 0xFF2E6B4A : 0xFF39404C);
            String nm = Lang.vi(module.name);
            int avail = VW - 2 * PAD - 40 - 80;
            float sc = fr.getStringWidth(nm) * 1.5f > avail ? 1.15f : 1.5f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(PAD + 38, 6f, 0f);
            GlStateManager.scale(sc, sc, 1f);
            fr.drawStringWithShadow(nm, 0, 0, Skin.TEXT);
            GlStateManager.popMatrix();
            fr.drawStringWithShadow(module.category.label, PAD + 38, 25, Draw.ACCENT_LIGHT);

            int bw = 54, bx = VW - PAD - bw;
            Skin.saoPill(fr, bx, 11, bw, 20, onM, tn);
            for (int i = 0; i < descLines.length; i++) {
                fr.drawStringWithShadow(descLines[i], PAD, 50 + i * 10, Skin.SOFT);
            }
        }
        Gui.drawRect(PAD, 71, VW - PAD, 72, 0x30FFFFFF);
        { // vệt sáng chạy dọc đường kẻ dưới phần mô tả
            int sw = 70, span = VW - 2 * PAD - sw;
            int sx = PAD + Math.round((tn % 2600L) / 2600f * span);
            Draw.fadeLine(sx, 71, sw, Draw.ACCENT & 0xFFFFFF, 0xA0);
        }

        drawDetail(mx, my);
    }

    // ----------------------------------------------------------- trang chủ

    /** Thẻ kiểu SAO: xám than, viền bạc mảnh + vệt sáng mép trên; rê chuột / chọn thì viền cam và hiện dấu ngoặc ở góc. */
    private static void glass(int x, int y, int w, int h, float hv, int border) {
        Draw.rrect(x, y, x + w, y + h, 6, Draw.lerp(0xE60F1A27, 0xF01D3548, hv));
        Draw.ring(x, y, x + w, y + h, border != 0 ? border : Draw.lerp(0xB0B8EFFF, Draw.ACCENT, hv));
        Gui.drawRect(x + 6, y + 1, x + w - 6, y + 2, Draw.lerp(0x22FFFFFF, 0x44FFFFFF, hv));
        if (hv > 0.02f || border != 0) {
            float a = border != 0 ? Math.max(hv, 0.85f) : hv;
            Skin.brackets(x - 2, y - 2, w + 4, h + 4, 6, Draw.lerp(Draw.ACCENT & 0xFFFFFF, Draw.ACCENT, a));
        }
    }

    // ---- bố cục kiểu menu SAO: cột nút tròn (nhóm) bên trái, bảng danh sách tính năng bên phải
    private static final int CX = 232, CR = 14;
    private static final int LPX = 268, LPW = 238, LPY = 14, LPH = 226, LRH = 26, LSTEP = 30;
    private static final int CPX = 16, CPY = 14, CPW = 160, CPH = 226; // thẻ nhân vật bên trái
    private float listYCur = -1f;
    /** false = mới mở: chỉ thẻ nhân vật + nút tròn, đứng giữa; true = đã chọn nhóm, cụm dịch sang trái nhường chỗ cho danh sách. */
    private boolean expanded;
    private float shift; // 0 = đứng giữa, 1 = đã dịch hết sang trái
    private static final int GROUP_DX = 126;

    private int gdx() {
        return Math.round(GROUP_DX * (1f - shift));
    }
    private static final int SAO_ORANGE = 0xFFF0A30A;
    private float listScroll, listTarget;

    /** Số nút tròn: các nhóm + 1 nút cuối gom "Nhập/Xuất", "Sửa vị trí HUD", "Xong". */
    private int circleCount() {
        return tabCount() + 1;
    }

    private int circleStep() {
        int n = circleCount();
        return n <= 1 ? 42 : Math.min(42, (LPH + 24 - 2 * (CR + 3)) / (n - 1)); // đủ chỗ để các nút không dính nhau
    }

    private int circleY(int i) {
        int total = (circleCount() - 1) * circleStep();
        return LPY + (LPH - total) / 2 + i * circleStep();
    }

    private boolean actionsOpen;

    /** Nền hàng danh sách: chuyển màu dọc (sáng ở trên, đậm dần xuống dưới) thay vì một màu phẳng. */
    private void rowBg(int x, int y, int w, int h, float hv) {
        int top = Draw.lerp(0xDEF4F4F5, 0xF2FFFFFF, hv);
        int bot = Draw.lerp(0xD6E4E6E8, 0xEEF2F3F4, hv);
        drawGradientRect(x, y, x + w, y + h, top, bot);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Khung gradient dọc dùng chung (thẻ nhân vật, nền). */
    private void vgrad(int x1, int y1, int x2, int y2, int c1, int c2) {
        drawGradientRect(x1, y1, x2, y2, c1, c2);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Số tính năng của nút tròn i (0 = tất cả). */
    private int countIn(int i) {
        if (i <= 0) return LunarLite.MODULES.size();
        if (i > tabCats.size()) return 0;
        Category c = tabCats.get(i - 1);
        int n = 0;
        for (Module m : LunarLite.MODULES) if (m.category == c) n++;
        return n;
    }
    /** Hiệu ứng bấm nút tròn: co lại rồi bật ra + gợn sáng lan ra. */
    private int pressCircle = -1;
    private long pressAt;
    /** Thanh HP trên thẻ nhân vật chạy mượt từ 0 lên giá trị thật khi mở menu. */
    private float hpAnim;

    /** Nút tròn đang được chọn; -1 = chưa chọn gì (mới mở menu, danh sách còn ẩn). */
    private int selCircle() {
        if (!expanded) return -1;
        return actionsOpen ? tabCount() : tabIndex();
    }

    private int selCircleRaw() {
        return actionsOpen ? tabCount() : tabIndex();
    }

    private int listCount() {
        return actionsOpen ? 3 : shown.size();
    }

    private int circleAt(int mx, int my) {
        for (int i = 0; i < circleCount(); i++) {
            int dx = mx - gdx() - CX, dy = my - circleY(i);
            if (dx * dx + dy * dy <= (CR + 3) * (CR + 3)) return i;
        }
        return -1;
    }

    private int listContentH() {
        return Math.max(0, listCount() * LSTEP - (LSTEP - LRH));
    }

    private int listH() {
        return Math.min(LPH, listContentH());
    }

    /** Bảng danh sách nằm giữa theo nút tròn đang chọn (như menu SAO), không tràn khỏi vùng. */
    private int listYTarget() {
        int h = listH();
        return Math.max(LPY, Math.min(LPY + LPH - h, circleY(selCircleRaw()) - h / 2));
    }

    private int listY() {
        return Math.round(listYCur < 0f ? listYTarget() : listYCur);
    }

    private int listMax() {
        return Math.max(0, listContentH() - listH());
    }

    private void clampList() {
        int max = listMax();
        listTarget = Math.max(0f, Math.min(max, listTarget));
        listScroll = Math.max(0f, Math.min(max, listScroll));
    }

    private int rowAt(int mx, int my) {
        if (!Draw.in(mx, my, LPX, listY(), LPW, listH())) return -1;
        int rel = my - listY() + Math.round(listScroll);
        int i = rel / LSTEP;
        if (rel % LSTEP >= LRH || i < 0 || i >= listCount()) return -1;
        return i;
    }

    private float hover(Object key, boolean over, float k) {
        Float cur = hovAnim.get(key);
        float hv = cur == null ? 0f : cur.floatValue();
        hv += ((over ? 1f : 0f) - hv) * k;
        if (Math.abs((over ? 1f : 0f) - hv) < 0.01f) hv = over ? 1f : 0f;
        hovAnim.put(key, Float.valueOf(hv));
        return hv;
    }

    /** Item Minecraft 16px tại (x, y). */
    private void drawItemSmall(ItemStack s, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(s, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawHome(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        int en = 0, total = LunarLite.MODULES.size();
        for (Module m : LunarLite.MODULES) if (m.enabled) en++;

        float k = AnimationsModule.menu() ? 0.3f : 1f;
        long now = System.currentTimeMillis();
        int yt = listYTarget();
        listYCur = listYCur < 0f ? yt : listYCur + (yt - listYCur) * k;
        if (Math.abs(yt - listYCur) < 0.4f) listYCur = yt;
        listScroll += (listTarget - listScroll) * k;
        if (Math.abs(listTarget - listScroll) < 0.4f) listScroll = listTarget;
        clampList();
        float sk = AnimationsModule.menu() ? 0.22f : 1f;
        float shT = expanded ? 1f : 0f;
        shift += (shT - shift) * sk;
        if (Math.abs(shT - shift) < 0.005f) shift = shT;

        int hc = circleAt(mx, my);
        int hr = hc >= 0 || !expanded ? -1 : rowAt(mx, my);
        Module hoverMod = hr >= 0 && !actionsOpen ? shown.get(hr) : null;
        int hkey = hc >= 0 ? hc : hr >= 0 ? 1000 + hr : -1;
        if (hkey != lastHoverSfx) {
            lastHoverSfx = hkey;
            if (hkey >= 0 && !closing) play("menu.hover");
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(gdx(), 0f, 0f);
        // ---- thẻ nhân vật bên trái (tên, thanh HP = số tính năng bật, 4 ô giáp); mô hình vẽ ở cuối hàm
        float pa = AnimationsModule.menu()
                ? AnimationsModule.ease((now - openStart) * AnimationsModule.speed() / 340f) : 1f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-(1f - pa) * 36f, 0f, 0f);
        Gui.drawRect(CPX - 1, CPY - 1, CPX + CPW + 1, CPY + CPH + 2, 0x55000000);
        vgrad(CPX, CPY, CPX + CPW, CPY + CPH, 0xFFFCFCFD, 0xFFEBECEE);
        vgrad(CPX, CPY, CPX + CPW, CPY + 22, 0xFFF3F4F5, 0xFFE0E2E4);
        Gui.drawRect(CPX, CPY + 22, CPX + CPW, CPY + 23, 0xFFD2D4D6);
        Draw.fadeLine(CPX + 10, CPY + 23, CPW - 20, Draw.ACCENT & 0xFFFFFF, 0x90); // vạch màu nhấn dưới tiêu đề
        Gui.drawRect(CPX, CPY + 24, CPX + CPW, CPY + 40, 0x0AFFFFFF);              // ánh sáng nhẹ đầu thẻ
        String pname = mc.thePlayer != null ? mc.thePlayer.getName() : "Zhwii";
        fr.drawString(pname, CPX + (CPW - fr.getStringWidth(pname)) / 2f, CPY + 4, 0xFF3A3F45, false);
        float hpT = total > 0 ? en / (float) total : 0f;
        hpAnim += (hpT - hpAnim) * (AnimationsModule.menu() ? 0.07f : 1f);
        if (Math.abs(hpT - hpAnim) < 0.002f) hpAnim = hpT;
        Draw.hbar(CPX + 14, CPY + 15, CPW - 28, 3, hpAnim, 0xFF3FBF7F, 0xFFCFD2D5);
        // dấu ngoặc ở góc thẻ nhân vật, nhấp nháy nhẹ như cửa sổ hologram
        int brA = 120 + Math.round(70f * (float) Math.sin(now / 520.0));
        Skin.brackets(CPX - 3, CPY - 3, CPW + 6, CPH + 8, 7, (brA << 24) | (Draw.ACCENT & 0xFFFFFF));
        int sy0 = CPY + CPH - 36;
        Gui.drawRect(CPX, sy0, CPX + CPW, CPY + CPH, 0xFFC9CBCD);
        int sx0 = CPX + (CPW - 118) / 2;
        for (int a2 = 0; a2 < 4; a2++) {
            int sx = sx0 + a2 * 32, sy = sy0 + 7;
            Gui.drawRect(sx, sy, sx + 22, sy + 22, 0xFFEDEEEF);
            Gui.drawRect(sx + 1, sy + 1, sx + 21, sy + 21, 0xFF7C848B);
            Gui.drawRect(sx + 1, sy + 1, sx + 21, sy + 3, 0x55000000);   // bóng lõm phía trên ô
            Gui.drawRect(sx + 1, sy + 1, sx + 3, sy + 21, 0x33000000);
            Gui.drawRect(sx + 1, sy + 19, sx + 21, sy + 21, 0x22FFFFFF); // ánh sáng mép dưới
            ItemStack ar = mc.thePlayer != null ? mc.thePlayer.inventory.armorItemInSlot(3 - a2) : null;
            if (ar != null) drawItemSmall(ar, sx + 3, sy + 3);
        }
        GlStateManager.popMatrix();

        // mũi tên trắng từ thẻ nhân vật sang cột nút tròn
        int midY = (circleY(0) + circleY(circleCount() - 1)) / 2;
        int wax = CPX + CPW + 8 + Math.round((float) Math.sin(now / 380.0) * 2f);
        for (int c = 0; c < 14; c++) Gui.drawRect(wax + c, midY - (13 - c), wax + c + 1, midY + (13 - c) + 1, 0xFFE6E8EA);

        GlStateManager.popMatrix();

        // dòng mô tả / gợi ý phía dưới
        String foot;
        int fcol;
        if (!actionsOpen && hoverMod != null && hoverMod.description != null && hoverMod.description.length() > 0) {
            foot = Lang.vi(hoverMod.description);
            fcol = Skin.SOFT;
        } else {
            foot = "";
            fcol = Skin.DIM;
        }
        drawCentered(fr.trimStringToWidth(foot, VW - 2 * PAD), VW / 2f, 248, fcol);
        // ---- cột nút tròn
        GlStateManager.pushMatrix();
        GlStateManager.translate(gdx(), 0f, 0f);
        int n = circleCount();
        float lineP = AnimationsModule.menu()
                ? AnimationsModule.ease((now - openStart) * AnimationsModule.speed() / (260f + n * 90f)) : 1f;
        int selTab = selCircle();
        for (int i = 0; i < n; i++) {
            boolean act = i == tabCount();
            Object key = act ? (Object) "ACT" : i == 0 ? (Object) "ALL" : (Object) tabCats.get(i - 1);
            float hv = hover(key, i == hc, k);
            boolean sel = i == selTab; // selTab = -1 khi chưa chọn gì -> không nút nào vàng
            float sv = hover("sel" + i, sel, k);
            int cy = circleY(i);
            float pop = 1f;
            if (AnimationsModule.menu()) {
                pop = AnimationsModule.easeOutBack((now - openStart) * AnimationsModule.speed() / 300f - i * 0.12f);
                if (pop <= 0.01f) continue;
            }
            // co lại rồi bật ra khi bấm
            float press = 1f;
            float pp = (now - pressAt) / 260f;
            if (i == pressCircle && pp >= 0f && pp < 1f) press = 1f - 0.16f * (float) Math.sin(Math.PI * pp);
            float scl = pop * press * (1f + 0.05f * hv + 0.08f * sv);
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 280.0);

            GlStateManager.pushMatrix();
            GlStateManager.translate(CX, cy, 0f);
            GlStateManager.scale(scl, scl, 1f);
            GlStateManager.translate(-CX, -cy, 0f);

            // gợn sáng lan ra khi bấm
            if (i == pressCircle && pp >= 0f && pp < 1.7f) {
                float rp = Math.min(1f, pp / 1.7f);
                float rr = CR + 3 + rp * 16f;
                int ra = Math.round((1f - rp) * (1f - rp) * 200f);
                if (ra > 4) Draw.circleRing(CX, cy, rr - 1.4f, rr, (ra << 24) | 0xFFD27A);
            }
            // quầng sáng toả mềm quanh nút (cam đập nhẹ khi chọn, xanh mờ khi rê chuột)
            if (sv > 0.02f) {
                int ga = Math.round(sv * (60f + pulse * 40f));
                if (ga > 3) Draw.disc(CX, cy, CR + 11f + pulse * 2f, (ga << 24) | 0xF0A30A, 0x00F0A30A);
            } else if (hv > 0.02f) {
                int ga = Math.round(hv * 70f);
                if (ga > 3) Draw.disc(CX, cy, CR + 9f, (ga << 24) | 0xB8EFFF, 0x00B8EFFF);
            }

            Draw.disc(CX + 0.5f, cy + 3f, CR + 5f, 0x40000000, 0x00000000); // bóng đổ mềm dưới nút
            // viền ngoài: bạc ánh xanh -> trắng khi rê -> cam khi chọn, tròn mịn
            int rim = Draw.lerp(Draw.lerp(0xCCB8EFFF, 0xFFFFFFFF, hv), 0xFFF0A30A, sv);
            Draw.disc(CX, cy, CR + 3f, Draw.lerp(rim, 0xFFFFFFFF, 0.25f), rim);
            Draw.disc(CX, cy, CR + 1f, 0xFF0B1420);
            int body = Draw.lerp(Draw.lerp(0xFFF2F4F6, 0xFFFFE2A8, hv * 0.6f), SAO_ORANGE, sv);
            // thân nút: sáng ở giữa, đậm dần ra mép như mặt kính cong
            Draw.disc(CX, cy, CR - 1f, Draw.lerp(body, 0xFFFFFFFF, 0.38f), Draw.lerp(body, 0xFF000000, 0.16f));
            Draw.circleRing(CX, cy, CR - 4.6f, CR - 3.8f, 0x1E000000);           // đường chỉ nổi bên trong
            Draw.disc(CX, cy - CR * 0.5f, CR * 0.5f, 0x60FFFFFF, 0x00FFFFFF);    // vệt phản chiếu phía trên
            // vệt sáng quét ngang qua nút đang chọn
            if (sv > 0.5f) {
                float sw = (float) ((now % 1800L) / 1800.0);
                if (sw < 0.45f) {
                    int sx = CX - CR + 2 + Math.round(sw / 0.45f * (2 * CR - 8));
                    Gui.drawRect(sx, cy - CR + 6, sx + 2, cy + CR - 6, 0x30FFFFFF);
                }
            }
            int ic = Draw.lerp(Draw.lerp(0xFFC4C8CD, 0xFF8A9096, hv), 0xFFFFFFFF, sv);
            SaoIcons.draw(act ? "gear" : SaoIcons.cat(i == 0 ? null : tabCats.get(i - 1)), CX - 8, cy - 8, ic);

            if (sv > 0.3f) { // đốm sáng nhỏ có đuôi quay quanh nút đang chọn
                double ang = now / 700.0;
                for (int tr = 0; tr < 5; tr++) {
                    double aa = ang - tr * 0.16;
                    float ox = CX + (float) Math.cos(aa) * (CR + 7f);
                    float oy = cy + (float) Math.sin(aa) * (CR + 7f);
                    int oa = Math.round((200f - tr * 38f) * sv);
                    Draw.disc(ox, oy, 1.7f - tr * 0.22f, (oa << 24) | 0xFFD27A);
                }
            }
            // tam giác chỉ sang bảng bên phải: chỉ hiện (mờ dần + trượt ra) ở nút ĐANG CHỌN
            if (sv > 0.05f) {
                int ta = Math.round(255f * Math.min(1f, sv));
                int tc = (ta << 24) | 0xF0A30A;
                int ax = CX + CR + 4 + Math.round((1f - sv) * 8f + (float) Math.sin(now / 260.0) * 1.2f * sv);
                int len = Math.max(1, Math.round(9 * sv));
                for (int c = 0; c < len; c++) Gui.drawRect(ax + c, cy - c, ax + c + 1, cy + c + 1, tc);
            }
            GlStateManager.popMatrix();

            // nhãn nổi bên cạnh khi rê chuột (chỉ lúc cụm đang đứng giữa, chưa mở danh sách)
            float tipA = hv * (1f - Math.min(1f, shift * 2.5f));
            if (tipA > 0.05f) {
                String lb = act ? "Thao tác" : Lang.vi(tabLabel(i));
                int tw = fr.getStringWidth(lb);
                int tx = CX + CR + 10 + Math.round((1f - tipA) * -8f);
                int al = Math.round(255f * tipA);
                Draw.rrect(tx - 5, cy - 9, tx + tw + 5, cy + 9, 4, (Math.round(0xE6 * tipA) << 24) | 0x0F1A27);
                Draw.ring(tx - 5, cy - 9, tx + tw + 5, cy + 9, (Math.round(0xB0 * tipA) << 24) | 0xB8EFFF);
                fr.drawStringWithShadow(lb, tx, cy - 4, (Math.max(al, 8) << 24) | 0xF4F5F7);
            }
        }

        GlStateManager.popMatrix();

        // ---- bảng danh sách tính năng
        int ly = listY(), lh = listH();
        clip(LPX - 12, ly, LPW + 22, lh);
        int sc = Math.round(listScroll);
        int fv = Math.max(0, sc / LSTEP);
        boolean showList = expanded || shift > 0.03f;
        for (int i = 0; showList && i < listCount(); i++) {
            int y = ly + i * LSTEP - sc;
            if (y + LRH < ly || y > ly + lh) continue;
            float sp = 1f;
            if (AnimationsModule.menu()) {
                float t = (System.currentTimeMillis() - animStart) * AnimationsModule.speed() / 320f
                        - Math.min(Math.max(0, i - fv), 10) * 0.08f - 0.15f;
                sp = AnimationsModule.easeOutBack(t);
            }
            if (!expanded) sp = Math.min(sp, shift);
            Object hkeyObj = actionsOpen ? (Object) ("act" + i) : (Object) shown.get(i);
            float hv = hover(hkeyObj, i == hr && sp > 0.9f, k);
            if (sp <= 0.02f) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(LPX, 0f, 0f);
            GlStateManager.scale(sp, 1f, 1f);
            GlStateManager.translate(-LPX, 0f, 0f);
            if (actionsOpen) drawActionRow(i, y, hv);
            else drawRow(shown.get(i), y, hv, shown.get(i) == module);
            GlStateManager.popMatrix();
        }
        Draw.endScissor();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        drawFixedButtons(mx, my);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // mô hình người chơi vẽ sau cùng để không bị đè (trượt vào cùng thẻ)
        if (mc.thePlayer != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-(1f - pa) * 36f + gdx(), 0f, 0f);
            int ex = CPX + CPW / 2, ey = CPY + CPH - 36 - 10;
            GlStateManager.color(1f, 1f, 1f, 1f);
            // bóng mềm dưới chân nhân vật
            Draw.rrect(ex - 24, ey - 1, ex + 24, ey + 5, 3, 0x14000000);
            Draw.rrect(ex - 18, ey, ex + 18, ey + 4, 2, 0x1E000000);
            Draw.rrect(ex - 11, ey + 1, ex + 11, ey + 3, 1, 0x2A000000);
            GlStateManager.color(1f, 1f, 1f, 1f);
            net.minecraft.client.gui.inventory.GuiInventory.drawEntityOnScreen(ex, ey, 54, ex + gdx() - mx, ey - 50 - my, mc.thePlayer);
            GlStateManager.popMatrix();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private static final String[] ACT_LABEL = {"Nhập / Xuất", "Sửa vị trí HUD", "Xong"};

    /** Hàng thao tác (nút tròn cuối cột): cùng kiểu hàng xám với danh sách tính năng. */
    private void drawActionRow(int i, int y, float hv) {
        FontRenderer fr = fontRendererObj;
        int x = LPX, w = LPW, h = LRH;
        rowBg(x, y, w, h, hv);
        Gui.drawRect(x, y, x + w, y + 1, 0x99FFFFFF);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF7C838A);
        Gui.drawRect(x, y + h, x + w, y + h + 1, 0x44000000);
        if (i == 2) Gui.drawRect(x, y, x + 3, y + h, SAO_ORANGE);
        else if (hv > 0.02f) Gui.drawRect(x, y, x + 2, y + h, Draw.mulAlpha(Draw.ACCENT, hv));
        int cx = x + 18, cy = y + h / 2;
        Draw.disc(cx, cy, 12.5f, 0xFFFFFFFF);
        {
            int cb = i == 2 ? 0xFFB9770E : 0xFF39404C;
            Draw.disc(cx, cy, 11.3f, Draw.lerp(cb, 0xFFFFFFFF, 0.22f), Draw.lerp(cb, 0xFF000000, 0.2f));
            Draw.disc(cx, cy - 5.5f, 5.5f, 0x40FFFFFF, 0x00FFFFFF);
        }
        SaoIcons.draw(i == 0 ? "arrows" : i == 1 ? "monitor" : "check", cx - 8, cy - 8, 0xFFFFFFFF);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 36, y + (h - 10) / 2f + 1f, 0f);
        GlStateManager.scale(1.2f, 1.2f, 1f);
        fr.drawString(ACT_LABEL[i], 0, 0, 0xFF22262B, false);
        GlStateManager.popMatrix();
    }

    /** Một hàng tính năng: xám phẳng như menu SAO, nút tròn có biểu tượng bên trái, viên BẬT/TẮT bên phải. */
    private void drawRow(Module m, int y, float hv, boolean sel) {
        FontRenderer fr = fontRendererObj;
        int x = LPX, w = LPW, h = LRH;
        boolean on = m.enabled;
        // nền trắng mờ như menu SAO mẫu: sáng hơn khi rê chuột, cam khi đang chọn
        if (sel) {
            vgrad(x, y, x + w, y + h, 0xF5F2A53A, 0xF5E0881C);
            for (int c = 0; c < 9; c++) Gui.drawRect(x - 9 + c, y + h / 2 - c, x - 8 + c, y + h / 2 + c + 1, 0xFFE6E8EA);
        } else {
            rowBg(x, y, w, h, hv);
            if (hv > 0.02f) Gui.drawRect(x, y, x + 2, y + h, Draw.mulAlpha(Draw.ACCENT, hv));
        }
        Gui.drawRect(x, y, x + w, y + 1, 0xCCFFFFFF);
        Gui.drawRect(x, y + h - 1, x + w, y + h, sel ? 0x55000000 : 0x55808890);
        Gui.drawRect(x, y + h, x + w, y + h + 1, 0x22000000);

        // biểu tượng: vòng tròn phẳng có màu + glyph trắng (xám khi tính năng đang tắt)
        int cx = x + 19, cy = y + h / 2;
        int base = on ? ICON_COLORS[Math.max(0, LunarLite.MODULES.indexOf(m)) % ICON_COLORS.length] : 0xFF9AA0A6;
        Draw.disc(cx + 0.5f, cy + 1.5f, 13f, 0x28000000, 0x00000000);
        Draw.disc(cx, cy, 12f, Draw.lerp(base, 0xFFFFFFFF, 0.18f), Draw.lerp(base, 0xFF000000, 0.08f));
        Draw.disc(cx, cy - 5.5f, 5.5f, 0x30FFFFFF, 0x00FFFFFF);
        SaoIcons.draw(SaoIcons.forModule(m), cx - 8, cy - 8, 0xFFFFFFFF);

        String nm = fit(Lang.vi(m.name), Math.round((w - 70) / 1.3f));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 40, y + (h - 10) / 2f + 1f, 0f);
        GlStateManager.scale(1.3f, 1.3f, 1f);
        fr.drawString(nm, 0, 0, sel ? 0xFFFFFFFF : (on ? 0xFF2B3036 : 0xFF6A7178), sel);
        GlStateManager.popMatrix();

        boolean fav = FAVS.contains(m.id);
        if (fav || hv > 0.3f) Icons.draw("heart", x + w - 18, y + (h - 8) / 2, 1, fav ? 0xFFFF5577 : 0x66000000);
    }

    /** Vật phẩm / khối Minecraft đại diện cho từng nhóm (null = dùng biểu tượng vẽ tay). */
    private ItemStack stackFor(Category c, int i) {
        if (c == null) return i == 0 ? new ItemStack(Blocks.obsidian) : null;
        switch (c) {
            case PVP: return new ItemStack(Items.iron_sword);
            case VISUAL: return new ItemStack(Items.chainmail_chestplate);
            case EFFECT: return new ItemStack(Items.potionitem, 1, 8194);
            case UTILITY: return new ItemStack(Items.enchanted_book);
            default: return null; // HUD: vẽ khối "target" riêng (1.8.9 không có item này)
        }
    }

    /** Vật phẩm Minecraft đại diện cho từng tính năng (null = dùng biểu tượng vẽ tay). */
    private static ItemStack stackFor(Module m) {
        String id = m.id;
        if ("keystrokes".equals(id)) return new ItemStack(Blocks.stone_button);
        if ("cps".equals(id)) return new ItemStack(Blocks.lever);
        if ("bowtrajectory".equals(id)) return new ItemStack(Items.bow);
        if ("targetinfo".equals(id)) return new ItemStack(Items.skull, 1, 3);
        if ("combo".equals(id)) return new ItemStack(Items.fire_charge);
        if ("killboard".equals(id)) return new ItemStack(Items.diamond_sword);
        if ("aimassist".equals(id)) return new ItemStack(Items.compass);
        if ("enemy_detector".equals(id)) return new ItemStack(Items.spider_eye);
        if ("armor".equals(id)) return new ItemStack(Items.iron_chestplate);
        if ("hide_armor".equals(id)) return new ItemStack(Items.leather_chestplate);
        if ("potions".equals(id)) return new ItemStack(Items.potionitem, 1, 8194);
        if ("itempickup".equals(id)) return new ItemStack(Blocks.hopper);
        if ("ping".equals(id)) return new ItemStack(Items.redstone);
        if ("serverip".equals(id)) return new ItemStack(Items.name_tag);
        if ("scoreboard".equals(id)) return new ItemStack(Items.sign);
        if ("togglesprint".equals(id)) return new ItemStack(Items.iron_boots);
        if ("zoom".equals(id)) return new ItemStack(Items.ender_eye);
        if ("fullbright".equals(id)) return new ItemStack(Blocks.glowstone);
        if ("blockoutline".equals(id)) return new ItemStack(Blocks.glass);
        if ("farhorizons".equals(id)) return new ItemStack(Items.map);
        if ("itemphysics".equals(id)) return new ItemStack(Items.apple);
        if ("animations".equals(id)) return new ItemStack(Items.clock);
        if ("hotbar_animation".equals(id)) return new ItemStack(Blocks.crafting_table);
        if ("chatbar".equals(id)) return new ItemStack(Items.writable_book);
        if ("guitheme".equals(id)) return new ItemStack(Items.dye, 1, 5);
        if ("velocity".equals(id)) return new ItemStack(Items.slime_ball);
        if ("bridge".equals(id)) return new ItemStack(Blocks.cobblestone);
        return null;
    }

    /** Khối "target" (bia bắn) vẽ bằng các vòng vuông đồng tâm, 32x32 tại (x, y). */
    private static void drawTargetIcon(int x, int y) {
        int[] inset = {0, 2, 6, 10, 13};
        int[] col = {0xFF7A4A44, 0xFFE9D6CE, 0xFFD2584A, 0xFFE9D6CE, 0xFFD2584A};
        for (int i = 0; i < inset.length; i++) {
            Gui.drawRect(x + inset[i], y + inset[i], x + 32 - inset[i], y + 32 - inset[i], col[i]);
        }
    }

    /** Vẽ item Minecraft phóng to 2x (32px) tại (x, y). */
    private void drawItemIcon(ItemStack s, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(2f, 2f, 1f);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(s, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Một thẻ kính: biểu tượng lớn ở giữa, tên + gạch chân phía dưới, chấm trạng thái ở góc phải trên. */
    private void drawItem(int i, int x, int y, int w, int h, float hv) {
        FontRenderer fr = fontRendererObj;
        int acc = Draw.ACCENT;
        Module m = inMods ? shown.get(i) : null;
        Category c = inMods || i == 0 ? null : tabCats.get(i - 1);

        String label, icon;
        boolean on;
        int iconCol;
        if (m != null) {
            label = Lang.vi(m.name);
            icon = m.id;
            on = m.enabled;
            iconCol = on ? Draw.lerp(colorOf(m), 0xFF000000, 0.25f) : 0xFF6B727B;
        } else {
            label = tabLabel(i);
            icon = tabIcon(i);
            int[] cc = catCounts(c);
            on = cc[0] > 0;
            iconCol = Draw.lerp(0xFF4A5059, Draw.ACCENT, hv);
        }

        boolean sel = m != null && m == module;
        int border = sel ? (acc & 0xFFFFFF) | 0xDD000000 : 0;
        glass(x, y, w, h, hv, border);

        // biểu tượng lớn trong nút tròn trắng (như menu SAO)
        int ix = x + (w - 32) / 2, iy = y + 13;
        int cs = 38, cxx = x + (w - cs) / 2, cyy = y + 10;
        int ring = sel ? acc : Draw.lerp(on && m != null ? Skin.GREEN : 0xFFAEB4BC, acc, hv);
        Draw.rrect(cxx, cyy, cxx + cs, cyy + cs, cs / 2, ring);
        Draw.rrect(cxx + 2, cyy + 2, cxx + cs - 2, cyy + cs - 2, cs / 2 - 2, Draw.lerp(0xFFEEF0F2, 0xFFFFFFFF, hv));
        Gui.drawRect(cxx + 10, cyy + 4, cxx + cs - 10, cyy + 5, 0x55FFFFFF);
        ItemStack is = m == null ? stackFor(c, i) : stackFor(m);
        if (is != null) {
            drawItemIcon(is, ix, iy);
        } else if (m == null && c == Category.HUD) {
            drawTargetIcon(ix, iy);
        } else {
            Icons.draw(icon, ix, iy, 4, iconCol);
        }

        // chấm trạng thái (góc phải trên)
        int dot = on ? (m != null ? Skin.GREEN : acc) : 0x33FFFFFF;
        Draw.rrect(x + w - 10, y + 6, x + w - 5, y + 11, 2, dot);

        // tim yêu thích (góc trái trên của thẻ tính năng)
        if (m != null) {
            boolean fav = FAVS.contains(m.id);
            if (fav || hv > 0.3f) Icons.draw("heart", x + 5, y + 5, 1, fav ? 0xFFFF5577 : 0x66FFFFFF);
        }

        // tên + gạch chân
        String nm = fit(label, w - 10);
        float cx = x + w / 2f;
        fr.drawStringWithShadow(nm, cx - fr.getStringWidth(nm) / 2f, y + h - 19, 0xFFFFFFFF);
        int uw = m != null && on ? 24 : 12;
        int uy = y + h - 8;
        int ucol = m != null ? (on ? Skin.GREEN : 0x66FFFFFF) : Draw.lerp(0x77FFFFFF, acc, hv);
        Gui.drawRect(Math.round(cx - uw / 2f), uy, Math.round(cx + uw / 2f), uy + 1, ucol);
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
                Draw.fadeLine(px + 10, ry + BH / 2, pw - 24, 0xFFFFFF, 0x40);
                continue;
            }
            int hy = ry + (BH - 8) / 2;
            Skin.diamond(px + 13, hy + 4, 4, Skin.ORANGE);
            String head = r.header.toUpperCase();
            fr.drawStringWithShadow(head, px + 22, hy, Draw.ACCENT_LIGHT);
            Gui.drawRect(px + 22 + fr.getStringWidth(head) + 6, hy + 4, px + pw - 14, hy + 5, 0x33FFFFFF);
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
        pill(x + 1, y + 2, x + 6, y + viewH - 2, 0x33FFFFFF); // rãnh
        int thumb = dragBar ? Skin.ORANGE : Draw.lerp(0xFF8E949B, Skin.ORANGE, barAnim);
        pill(tx1, by + 2, tx1 + tw, Math.min(y + viewH - 2, by + bh - 2), thumb);
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
        if (confirmMod != null) {
            if (btn == 0) confirmClick(mx, my);
            return;
        }

        if (!detail) {
            int ci = circleAt(mx, my);
            if (ci >= 0) {
                pressCircle = ci;
                pressAt = System.currentTimeMillis();
                boolean isAct = ci == tabCount();
                if (expanded && ci == selCircle()) { // bấm lại nút đang chọn: thu danh sách, cụm về giữa
                    click();
                    expanded = false;
                } else {
                    play("menu.select");
                    if (isAct) {
                        actionsOpen = true;
                        listScroll = listTarget = 0f;
                        animStart = System.currentTimeMillis();
                    } else {
                        boolean was = actionsOpen;
                        actionsOpen = false;
                        if (ci != tabIndex()) setTab(ci);
                        else if (was) animStart = System.currentTimeMillis();
                    }
                    if (!expanded) {
                        expanded = true;
                        animStart = System.currentTimeMillis();
                    }
                }
                return;
            }
            int ri = expanded ? rowAt(mx, my) : -1;
            if (ri >= 0 && actionsOpen) {
                click();
                if (btn != 0) return;
                if (ri == 0) mc.displayGuiScreen(new ProfileScreen());
                else if (ri == 1) mc.displayGuiScreen(new HudEditorScreen());
                else requestClose();
                return;
            }
            if (ri >= 0) {
                Module hit = shown.get(ri);
                module = hit;
                lastModule = hit;
                if (btn == 0 && mx >= LPX + LPW - 22) { // tim yêu thích
                    click();
                    if (!FAVS.remove(hit.id)) FAVS.add(hit.id);
                    buildShown();
                    return;
                }
                if (btn == 1) openModule(hit);
                else askToggle(hit);
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
        if (b.kind == K_MODE || b.kind == K_COLOR || b.kind == K_ACTION || b.kind == K_RESET
                || b.kind == K_EDIT || b.kind == K_IMPORT) click();
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
            case K_IMPORT:
                mc.displayGuiScreen(new ProfileScreen());
                break;
            case K_ENABLE:
                askToggle(b.m);
                break;
            case K_BOOL: {
                long before = visSig(b.m);
                ((BoolSetting) b.s).toggle();
                sfxToggle(((BoolSetting) b.s).value);
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
        } else if (expanded) {
            listTarget += (d > 0 ? -2 : 2) * LSTEP;
            clampList();
        }
    }

    // ----------------------------------------------------------- bàn phím

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (confirmMod != null) {
            if (key == Keyboard.KEY_ESCAPE) closeConfirm(false);
            else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) closeConfirm(true);
            return;
        }
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
            int idx = Math.round(listScroll / LSTEP) + slot;
            if (expanded && !actionsOpen && idx < shown.size()) openModule(shown.get(idx));
        } else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_TAB) {
            if (!expanded) {
                expanded = true;
                animStart = System.currentTimeMillis();
            }
            actionsOpen = false;
            cycleTab(key == Keyboard.KEY_UP ? -1 : 1);
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
