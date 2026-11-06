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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
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
 * Menu kiểu "pixel tối": nền tối trong suốt, góc cam, mỗi nhóm một màu viền.
 *   TRANG CHỦ : hàng tab (ô biểu tượng + ô nhãn) ở trên, bên dưới là lưới 3 cột. Mỗi tính năng gồm ô biểu tượng
 *               (vật phẩm Minecraft) và ô tên có chấm trạng thái. Bật = viền + chữ sáng màu nhóm, tắt = xám.
 *               Bấm ô tên = bật / tắt. Bấm ô biểu tượng hoặc chuột phải = mở tuỳ chỉnh. Chuột giữa = ghim lên đầu.
 *               Kéo lên / xuống để cuộn (dùng được trên điện thoại).
 *   CHI TIẾT  : nhãn bên trái, điều khiển bên phải, cùng bộ khung pixel.
 * Toàn bộ giao diện vẽ trong khung ảo VW x VH rồi tự thu nhỏ cho vừa màn hình.
 */
public class MenuScreen extends GuiScreen {
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };

    // ---- khung ảo
    private static final int VW = 560, VH = 272, PAD = 14;
    private static final int TAB_Y = 14, TAB_H = 26;
    private static final int GRID_Y = 54, GRID_B = VH - 24;
    private static final int COLS = 3, GAP = 10, ROW_GAP = 6, CARD_H = 32, ICON_W = 32, ICON_GAP = 4;
    private static final int PITCH = CARD_H + ROW_GAP;
    private static final float MENU_SCALE = 1.0f;

    // ---- tab
    private static final int TAB_ICON = TAB_H, TAB_GAP = 3, TAB_SP = 6;
    private static final Category[] TAB_ORDER = { Category.PVP, Category.HUD, Category.VISUAL, Category.EFFECT, Category.UTILITY };

    // ---- chi tiết
    private static final int COL_X = 60, COL_W = VW - 120;
    private static final int ROW_H = 26;
    private static final int TRACK_W = 140;

    // ---- màu (nền tối kiểu pixel, mỗi nhóm một màu viền)
    private static final int INK = 0xFFE8EDF5;
    private static final int INK2 = 0xFFA3AFC0;
    private static final int INK3 = 0xFF6B7788;
    private static final int ORANGE = 0xFFFFA21F;
    private static final int ORANGE_HI = 0xFFFFC266;
    private static final int ORANGE_DK = 0xFFE0850A;
    private static final int RED = 0xFFFF5A64;
    private static final int PANEL = 0xE2121A22;
    private static final int PANEL_EDGE = 0xFF3C4552;
    private static final int GLASS_EDGE = PANEL_EDGE;
    private static final int OFF_EDGE = 0xFF3A4452;
    private static final int OFF_EDGE_HI = 0xFF6B7A8E;
    private static final int OFF_FILL = 0xB0182029;
    private static final int OFF_FILL_HI = 0xD02A3442;
    private static final int OFF_TEXT = 0xFF8794A6;

    /** Màu viền theo nhóm: null = "Tất cả" (cam), PvP hồng, HUD xanh dương, Hình ảnh tím, Hiệu ứng ngọc, Tiện ích xanh lá. */
    private static int catRgb(Category c) {
        if (c == null) return 0xFFA21F;
        switch (c) {
            case PVP: return 0xFF4F9A;
            case HUD: return 0x3D8BFF;
            case VISUAL: return 0x9B63FF;
            case EFFECT: return 0x22D3E6;
            case UTILITY: return 0x2FD487;
            default: return 0xFFA21F;
        }
    }

    /** Nền tối pha màu nhóm (k = 0..1). */
    private static int tintFill(int col, float k) {
        return Draw.lerp(0xE0101820, 0xFF000000 | col, k);
    }

    private static final java.util.Set<String> FAVS = new HashSet<String>();
    private static Module lastModule;
    private static Category lastTab; // null = nhóm "Tất cả"
    static long animStart;

    private static final Comparator<Module> BY_NAME = new Comparator<Module>() {
        @Override
        public int compare(Module a, Module b) {
            boolean fa = FAVS.contains(a.id), fb = FAVS.contains(b.id);
            if (fa != fb) return fa ? -1 : 1;
            return Lang.vi(a.name).compareToIgnoreCase(Lang.vi(b.name));
        }
    };

    // ------------------------------------------------------------------ biểu tượng = vật liệu Minecraft

    private static final Map<String, ItemStack> ICONS = new HashMap<String, ItemStack>();
    private static ItemStack iconDefault;
    private static ItemStack iconProf, iconHudEd;
    private static final Map<Category, ItemStack> TAB_ICONS = new HashMap<Category, ItemStack>();

    private static void ic(String id, ItemStack s) {
        ICONS.put(id, s);
    }

    private static void initIcons() {
        if (iconDefault != null) return;
        iconDefault = new ItemStack(Blocks.crafting_table);
        iconProf = new ItemStack(Items.skull, 1, 3);
        iconHudEd = new ItemStack(Items.painting);
        TAB_ICONS.put(null, new ItemStack(Blocks.chest));
        TAB_ICONS.put(Category.PVP, new ItemStack(Items.diamond_sword));
        TAB_ICONS.put(Category.HUD, new ItemStack(Items.clock));
        TAB_ICONS.put(Category.VISUAL, new ItemStack(Items.emerald));
        TAB_ICONS.put(Category.EFFECT, new ItemStack(Items.blaze_powder));
        TAB_ICONS.put(Category.UTILITY, new ItemStack(Items.iron_pickaxe));
        ic("aimassist", new ItemStack(Items.ender_eye));
        ic("animations", new ItemStack(Items.painting));
        ic("armor", new ItemStack(Items.diamond_chestplate));
        ic("blockoutline", new ItemStack(Blocks.glass));
        ic("bowtrajectory", new ItemStack(Items.bow));
        ic("bridge", new ItemStack(Blocks.stonebrick));
        ic("chatbar", new ItemStack(Items.writable_book));
        ic("combo", new ItemStack(Items.diamond_sword));
        ic("cps", new ItemStack(Items.golden_sword));
        ic("enemy_detector", new ItemStack(Items.spider_eye));
        ic("farhorizons", new ItemStack(Items.map));
        ic("fps", new ItemStack(Items.repeater));
        ic("fullbright", new ItemStack(Items.glowstone_dust));
        ic("guitheme", new ItemStack(Items.dye, 1, 14));
        ic("hide_armor", new ItemStack(Items.leather_chestplate));
        ic("hotbar_animation", new ItemStack(Blocks.chest));
        ic("itemphysics", new ItemStack(Blocks.anvil));
        ic("itempickup", new ItemStack(Blocks.hopper));
        ic("keystrokes", new ItemStack(Blocks.lever));
        ic("killboard", new ItemStack(Items.bed));
        ic("music", new ItemStack(Blocks.jukebox));
        ic("ping", new ItemStack(Items.comparator));
        ic("potions", new ItemStack(Items.potionitem, 1, 8193));
        ic("scoreboard", new ItemStack(Items.name_tag));
        ic("serverip", new ItemStack(Items.compass));
        ic("targetinfo", new ItemStack(Items.skull, 1, 3));
        ic("togglesprint", new ItemStack(Items.iron_boots));
        ic("velocity", new ItemStack(Items.slime_ball));
        ic("zoom", new ItemStack(Items.glass_bottle));
    }

    private static ItemStack tabIconOf(Category c) {
        initIcons();
        ItemStack st = TAB_ICONS.get(c);
        return st == null ? iconDefault : st;
    }

    private static ItemStack iconOf(Module m) {
        initIcons();
        ItemStack s = m == null ? null : ICONS.get(m.id);
        return s == null ? iconDefault : s;
    }

    // ------------------------------------------------------------------ hàng tuỳ chỉnh

    private static final int R_HEAD = 0, R_BOOL = 1, R_MODE = 2, R_COLOR = 3, R_SLIDER = 4, R_ACTION = 5, R_RESET = 6;

    private static class SRow {
        int kind;
        String text;
        Setting s;
        Module.Action a;
        int y, h = ROW_H;
    }

    private static int rank(Setting st) {
        if (st instanceof BoolSetting) return 0;
        if (st instanceof ModeSetting) return 1;
        if (st instanceof ColorSetting) return 2;
        return 3;
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

    private static long visSig(Module m) {
        long sig = 0;
        int i = 0;
        for (Setting st : m.settings) {
            if (st.isVisible() && i < 63) sig |= 1L << i;
            i++;
        }
        return sig;
    }

    // -------------------------------------------------------------------- trạng thái

    private Module module;
    private boolean detail;
    private Category tabCat;
    private final List<Category> tabCats = new ArrayList<Category>();
    private final List<Module> shown = new ArrayList<Module>();
    private final Map<String, Float> hovAnim = new HashMap<String, Float>();
    private final List<SRow> rows = new ArrayList<SRow>();
    private String[] descLines = new String[0];
    private int contentH;
    private float gridScroll, gridTarget;
    private float setScroll, setTarget;
    private long lastFrameNs;
    private float scrollK = 1f;
    private int curMx, curMy;

    // nhấn / kéo
    private String pressKey;
    private int pressX, pressY;
    private float pressScroll;
    private boolean dragged;
    private SRow activeSlider;

    // ---- animation
    private final long openStart;
    private long closeStart;
    private boolean closing;
    private boolean soundOpened;

    public MenuScreen() {
        openStart = animStart = System.currentTimeMillis();
        module = lastModule;
        if (module == null || !LunarLite.MODULES.contains(module)) {
            module = LunarLite.MODULES.isEmpty() ? null : LunarLite.MODULES.get(0);
        }
        tabCat = lastTab;
        buildShown();
    }

    static float stagger(int id) {
        if (!AnimationsModule.menu()) return 1f;
        float t = (System.currentTimeMillis() - animStart) * AnimationsModule.speed() / 240f - Math.min(id, 12) * 0.04f;
        return AnimationsModule.ease(t);
    }

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

    // ------------------------------------------------------------------ âm thanh

    private void play(String ev) {
        ResourceLocation loc = new ResourceLocation("lunarlite", ev);
        if (mc.thePlayer != null) {
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(loc,
                    (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ));
        } else {
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(loc, 1.0F));
        }
    }

    // ------------------------------------------------------------------ khung ảo <-> màn hình

    private float ui() {
        return Math.min(MENU_SCALE, Math.min((width - 8f) / VW, (height - 8f) / VH));
    }

    private int vx(int mx) {
        return Math.round((mx - width / 2f) / ui() + VW / 2f);
    }

    private int vy(int my) {
        return Math.round((my - height / 2f) / ui() + VH / 2f);
    }

    private void clip(int x, int y, int w, int h) {
        float u = ui();
        int rx = Math.round((x - VW / 2f) * u + width / 2f);
        int ry = Math.round((y - VH / 2f) * u + height / 2f);
        Draw.scissor(mc, rx, ry, Math.round(w * u), Math.round(h * u));
    }

    // ------------------------------------------------------------------ nhóm + danh sách

    private void buildShown() {
        shown.clear();
        tabCats.clear();
        for (Category c : TAB_ORDER) {
            List<Module> inCat = new ArrayList<Module>();
            for (Module m : LunarLite.MODULES) {
                if (m.category == c) inCat.add(m);
            }
            if (inCat.isEmpty()) continue;
            tabCats.add(c);
            if (tabCat != null && c != tabCat) continue;
            Collections.sort(inCat, BY_NAME);
            shown.addAll(inCat);
        }
        if (shown.isEmpty() && tabCat != null) {
            tabCat = lastTab = null;
            buildShown();
        }
    }

    private int tabCount() {
        return 1 + tabCats.size();
    }

    private int tabIndex() {
        return tabCat == null ? 0 : tabCats.indexOf(tabCat) + 1;
    }

    private String tabLabel(int i) {
        return i == 0 ? "TẤT CẢ" : tabCats.get(i - 1).label.toUpperCase();
    }

    private void setTab(int i) {
        Category c = i <= 0 || i > tabCats.size() ? null : tabCats.get(i - 1);
        tabCat = c;
        lastTab = c;
        gridScroll = gridTarget = 0f;
        animStart = System.currentTimeMillis();
        buildShown();
    }

    private void cycleTab(int d) {
        int n = tabCount();
        setTab((tabIndex() + d + n) % n);
    }

    // ---- tab: cụm = ô biểu tượng + ô nhãn. Hết chỗ thì các tab không chọn chỉ còn ô biểu tượng.
    private boolean tabsCompact;

    private int tabFullW(int i) {
        return TAB_ICON + TAB_GAP + fontRendererObj.getStringWidth(tabLabel(i)) + 16;
    }

    private void layoutTabs() {
        int avail = profX() - 10 - PAD, total = -TAB_SP;
        for (int i = 0; i < tabCount(); i++) total += tabFullW(i) + TAB_SP;
        tabsCompact = total > avail;
    }

    private int tabW(int i) {
        return tabsCompact && i != tabIndex() ? TAB_ICON : tabFullW(i);
    }

    private int tabX(int i) {
        int x = PAD;
        for (int k = 0; k < i; k++) x += tabW(k) + TAB_SP;
        return x;
    }

    // ---- nút góc phải trên cùng (hồ sơ, sửa HUD, đóng): ba ô biểu tượng vuông
    private int closeX() {
        return VW - PAD - TAB_H;
    }

    private int hudX() {
        return closeX() - 4 - TAB_H;
    }

    private int profX() {
        return hudX() - 4 - TAB_H;
    }

    // ---- lưới thẻ
    private int cardW() {
        return (VW - 2 * PAD - (COLS - 1) * GAP) / COLS;
    }

    private int cardX(int i) {
        return PAD + (i % COLS) * (cardW() + GAP);
    }

    private int cardY(int i) {
        return GRID_Y + 2 + (i / COLS) * PITCH - Math.round(gridScroll);
    }

    private int gridContentH() {
        int r = (shown.size() + COLS - 1) / COLS;
        return r == 0 ? 0 : r * PITCH - ROW_GAP + 4;
    }

    private float gridMax() {
        return Math.max(0, gridContentH() - (GRID_B - GRID_Y));
    }

    private void clampGrid() {
        float max = gridMax();
        gridTarget = Math.max(0f, Math.min(max, gridTarget));
        gridScroll = Math.max(0f, Math.min(max, gridScroll));
    }

    // ---- chi tiết
    private int descH() {
        return descLines.length == 0 ? 0 : descLines.length * 10 + 4;
    }

    private int listTop() {
        return GRID_Y + descH() + 6;
    }

    private int listViewH() {
        return GRID_B - listTop();
    }

    private float setMax() {
        return Math.max(0, contentH - listViewH());
    }

    private void clampSet() {
        float max = setMax();
        setTarget = Math.max(0f, Math.min(max, setTarget));
        setScroll = Math.max(0f, Math.min(max, setScroll));
    }

    // ------------------------------------------------------------------ hành động

    private void openModule(Module m) {
        if (m == null) return;
        play("menu.select");
        module = m;
        lastModule = m;
        detail = true;
        setScroll = setTarget = 0f;
        animStart = System.currentTimeMillis();
        buildRows();
    }

    private void goBack() {
        play("menu.back");
        detail = false;
        activeSlider = null;
        animStart = System.currentTimeMillis();
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
        play(m.enabled ? "menu.on" : "menu.off");
    }

    private void toggleFav(Module m) {
        if (!FAVS.remove(m.id)) FAVS.add(m.id);
        play("menu.click");
        buildShown();
    }

    // ------------------------------------------------------------------ dựng hàng tuỳ chỉnh

    private void addRow(int kind, String text, Setting s, Module.Action a, int h) {
        SRow r = new SRow();
        r.kind = kind;
        r.text = text;
        r.s = s;
        r.a = a;
        r.h = h;
        rows.add(r);
    }

    private void buildRows() {
        rows.clear();
        descLines = new String[0];
        contentH = 0;
        Module m = module;
        if (m == null) return;

        int maxW = VW - 2 * PAD;
        if (m.description != null && m.description.length() > 0) {
            List<?> ls = fontRendererObj.listFormattedStringToWidth(Lang.vi(m.description), maxW);
            int n = Math.min(2, ls.size());
            descLines = new String[n];
            for (int i = 0; i < n; i++) descLines[i] = (String) ls.get(i);
            if (ls.size() > 2) descLines[1] = fit(descLines[1] + "...", maxW);
        }

        List<Setting> vis = new ArrayList<Setting>();
        for (Setting st : m.settings) if (st.isVisible()) vis.add(st);

        List<String> found = new ArrayList<String>();
        for (Setting st : vis) if (!found.contains(st.section)) found.add(st.section);
        List<String> sections = new ArrayList<String>();
        for (String sec : found) if (!"Appearance".equals(sec) && !"Rainbow".equals(sec)) sections.add(sec);
        if (found.contains("Appearance")) sections.add("Appearance");
        if (found.contains("Rainbow")) sections.add("Rainbow");

        List<Module.Action> acts = m.actions();
        boolean heads = sections.size() > 1 || (sections.size() == 1 && !acts.isEmpty());
        for (String sec : sections) {
            if (heads) addRow(R_HEAD, Lang.vi(sec), null, null, 22);
            List<Setting> inSec = new ArrayList<Setting>();
            for (Setting st : vis) {
                if (sec.equals(st.section)) inSec.add(st);
            }
            Collections.sort(inSec, new Comparator<Setting>() {
                @Override
                public int compare(Setting x, Setting y) {
                    return rank(x) - rank(y);
                }
            });
            for (Setting st : inSec) {
                if (st instanceof SliderSetting) addRow(R_SLIDER, null, st, null, ROW_H);
                else if (st instanceof BoolSetting) addRow(R_BOOL, null, st, null, ROW_H);
                else if (st instanceof ModeSetting) addRow(R_MODE, null, st, null, ROW_H);
                else if (st instanceof ColorSetting) addRow(R_COLOR, null, st, null, ROW_H);
            }
        }
        if (!acts.isEmpty()) {
            if (heads) addRow(R_HEAD, "Thao tác", null, null, 22);
            for (Module.Action a : acts) addRow(R_ACTION, a.label, null, a, 28);
        }
        addRow(R_RESET, "Đặt lại tuỳ chỉnh", null, null, 34);

        int y = 2;
        for (SRow r : rows) {
            r.y = y;
            y += r.h;
        }
        contentH = y + 4;
        clampSet();
    }

    // ------------------------------------------------------------------ nền blur (post-shader của game)

    private static final ResourceLocation BLUR_SHADER = new ResourceLocation("lunarlite", "shaders/post/menu_blur.json");
    private boolean blurOn;

    private void startBlur() {
        if (blurOn || mc == null || mc.theWorld == null || !OpenGlHelper.shadersSupported) return;
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
        if (detail) buildRows();
    }

    // ------------------------------------------------------------------ vẽ: tiện ích

    private String fit(String s, int maxW) {
        FontRenderer fr = fontRendererObj;
        if (fr.getStringWidth(s) <= maxW) return s;
        int dots = fr.getStringWidth("..");
        return fr.trimStringToWidth(s, Math.max(0, maxW - dots)) + "..";
    }

    private void txt(String s, float x, float y, int color) {
        fontRendererObj.drawString(s, x, y, color, false);
    }

    private void txtC(String s, float cx, float y, int color) {
        fontRendererObj.drawString(s, cx - fontRendererObj.getStringWidth(s) / 2f, y, color, false);
    }

    private float hv(String key, boolean over) {
        Float f = hovAnim.get(key);
        float v = f == null ? 0f : f.floatValue();
        float k = AnimationsModule.menu() ? 0.3f : 1f;
        v += ((over ? 1f : 0f) - v) * k;
        if (Math.abs((over ? 1f : 0f) - v) < 0.01f) v = over ? 1f : 0f;
        hovAnim.put(key, Float.valueOf(v));
        return v;
    }

    /** Hộp pixel: nền, viền ngoài 1px cắt góc, viền trong mờ (kiểu khung ornament), quầng sáng ngoài nếu glowA > 0. */
    private static void pbox(int x, int y, int w, int h, int fill, int edge, int glowA) {
        if (w < 4 || h < 4) return;
        if (glowA > 0) {
            int rgb = edge & 0xFFFFFF;
            Draw.outline(x - 1, y - 1, x + w + 1, y + h + 1, (glowA << 24) | rgb);
            Draw.outline(x - 2, y - 2, x + w + 2, y + h + 2, ((glowA / 2) << 24) | rgb);
        }
        Gui.drawRect(x + 1, y, x + w - 1, y + h, fill);
        Gui.drawRect(x, y + 1, x + 1, y + h - 1, fill);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, fill);
        Draw.ring(x, y, x + w, y + h, edge);
        if (w > 8 && h > 8) Draw.ring(x + 1, y + 1, x + w - 1, y + h - 1, Draw.mulAlpha(edge, 0.35f));
    }

    /** Giữ tên cũ cho phần "chi tiết": khung tối pixel. {@code r} bị bỏ qua (pixel không bo góc). */
    private static void glass(int x, int y, int w, int h, int r, int fill, int edge) {
        pbox(x, y, w, h, fill, edge, 0);
    }

    /** Ba vạch chấm dọc trang trí ở hai đầu ô tên. */
    private static void dashes(int x, int cy, int color) {
        for (int k = -1; k <= 1; k++) Gui.drawRect(x, cy + k * 4 - 1, x + 1, cy + k * 4 + 1, color);
    }

    private void button(int x, int y, int w, int h, String label, float hov, boolean orange, int textColor) {
        if (orange) {
            pbox(x, y, w, h, Draw.lerp(0xE0382400, 0xE0583800, hov), Draw.lerp(ORANGE_DK, ORANGE_HI, hov), 0x30);
        } else {
            pbox(x, y, w, h, Draw.lerp(OFF_FILL, OFF_FILL_HI, hov), Draw.lerp(OFF_EDGE, OFF_EDGE_HI, hov), 0);
        }
        if (label != null) {
            String t = fit(label, w - 6);
            txtC(t, x + w / 2f, y + (h - 8) / 2f, textColor);
        }
    }

    private void txtS(String s, float x, float y, int color) {
        fontRendererObj.drawString(s, x, y, color, true);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return Draw.in(mx, my, x, y, w, h);
    }

    /** Vẽ một vật phẩm / block Minecraft ở toạ độ (x, y), cạnh 16 * s điểm ảnh. */
    private void drawItem(ItemStack st, float x, float y, float s) {
        if (st == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(st, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ vẽ: màn hình

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ThemeModule.apply();
        if (detail && module != null && module.consumeUiDirty()) buildRows();
        curMx = vx(mx);
        curMy = vy(my);
        float op = openProgress();

        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.016f : Math.min(0.1f, (nowNs - lastFrameNs) / 1e9f);
        lastFrameNs = nowNs;
        scrollK = AnimationsModule.menu() ? 1f - (float) Math.exp(-dt * 14f) : 1f;
        gridScroll += (gridTarget - gridScroll) * scrollK;
        if (Math.abs(gridTarget - gridScroll) < 0.4f) gridScroll = gridTarget;
        setScroll += (setTarget - setScroll) * scrollK;
        if (Math.abs(setTarget - setScroll) < 0.4f) setScroll = setTarget;

        // lớp tối nhẹ phủ lên thế giới đã bị làm mờ
        int va = Math.round(0x40 * Math.max(0f, Math.min(1f, op)));
        Gui.drawRect(0, 0, width, height, va << 24);

        GlStateManager.pushMatrix();
        float pop = 0.90f + 0.10f * op;
        if (!closing && AnimationsModule.menu()) {
            pop = 0.84f + 0.16f * AnimationsModule.easeOutBack((System.currentTimeMillis() - openStart) * AnimationsModule.speed() / 380f);
        }
        float kk = pop * ui();
        GlStateManager.translate(width / 2f, height / 2f + (1f - op) * 10f, 0f);
        GlStateManager.scale(kk, kk, 1f);
        GlStateManager.translate(-VW / 2f, -VH / 2f, 0f);

        // khung tối lớn phía sau + bốn ô vuông cam ở góc
        pbox(6, 6, VW - 12, VH - 12, PANEL, PANEL_EDGE, 0);
        Gui.drawRect(3, 3, 9, 9, ORANGE);
        Gui.drawRect(VW - 9, 3, VW - 3, 9, ORANGE);
        Gui.drawRect(3, VH - 9, 9, VH - 3, ORANGE);
        Gui.drawRect(VW - 9, VH - 9, VW - 3, VH - 3, ORANGE);

        if (detail && module != null) drawDetail(curMx, curMy);
        else drawHome(curMx, curMy);

        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ----------------------------------------------------------- trang chủ

    private void drawIconButton(int x, ItemStack st, boolean cross, String key, int mx, int my) {
        float h = hv(key, in(mx, my, x, TAB_Y, TAB_H, TAB_H));
        pbox(x, TAB_Y, TAB_H, TAB_H, Draw.lerp(OFF_FILL, OFF_FILL_HI, h), Draw.lerp(OFF_EDGE, OFF_EDGE_HI, h), 0);
        if (cross) {
            int c = Draw.lerp(0xFF8794A6, RED, h), ox = x + 8, oy = TAB_Y + 8;
            for (int k = 0; k < 10; k++) {
                Gui.drawRect(ox + k, oy + k, ox + k + 2, oy + k + 2, c);
                Gui.drawRect(ox + 9 - k, oy + k, ox + 11 - k, oy + k + 2, c);
            }
        } else {
            drawItem(st, x + (TAB_H - 16) / 2f, TAB_Y + (TAB_H - 16) / 2f, 1f);
            if (h < 0.99f) Gui.drawRect(x + 1, TAB_Y + 1, x + TAB_H - 1, TAB_Y + TAB_H - 1, Draw.mulAlpha(0x66141A22, 1f - h));
        }
    }

    private void drawTab(int i, int mx, int my) {
        int x = tabX(i), w = tabW(i);
        boolean sel = i == tabIndex();
        int rgb = catRgb(i == 0 ? null : tabCats.get(i - 1));
        int col = 0xFF000000 | rgb;
        float h = hv("tab" + i, in(mx, my, x, TAB_Y, w, TAB_H));
        float k = sel ? 0.30f : 0.18f + 0.10f * h;
        Category cat = i == 0 ? null : tabCats.get(i - 1);

        pbox(x, TAB_Y, TAB_ICON, TAB_H, tintFill(col, k), Draw.lerp(col, 0xFFFFFFFF, h * 0.3f), sel ? 0x30 : 0);
        drawItem(tabIconOf(cat), x + (TAB_ICON - 16) / 2f, TAB_Y + (TAB_H - 16) / 2f, 1f);
        if (w > TAB_ICON) {
            int lx = x + TAB_ICON + TAB_GAP, lw = w - TAB_ICON - TAB_GAP;
            pbox(lx, TAB_Y, lw, TAB_H, tintFill(col, k), Draw.lerp(col, 0xFFFFFFFF, h * 0.3f), sel ? 0x30 : 0);
            int dc = Draw.lerp(col, 0xFFFFFFFF, 0.2f);
            dashes(lx + 4, TAB_Y + TAB_H / 2, dc);
            dashes(lx + lw - 5, TAB_Y + TAB_H / 2, dc);
            txtS(tabLabel(i), lx + (lw - fontRendererObj.getStringWidth(tabLabel(i))) / 2f, TAB_Y + (TAB_H - 8) / 2f,
                    Draw.lerp(col, 0xFFFFFFFF, 0.55f));
        }
        if (sel) Gui.drawRect(x, TAB_Y + TAB_H + 4, x + w, TAB_Y + TAB_H + 6, col);
    }

    private void drawHome(int mx, int my) {
        layoutTabs();

        // đường kẻ dưới hàng tab, rồi tab (gạch chân cam của tab đang chọn nằm đè lên)
        Gui.drawRect(PAD, TAB_Y + TAB_H + 5, VW - PAD, TAB_Y + TAB_H + 6, 0x2AFFFFFF);
        for (int i = 0; i < tabCount(); i++) drawTab(i, mx, my);
        drawIconButton(profX(), iconProf, false, "prof", mx, my);
        drawIconButton(hudX(), iconHudEd, false, "hud", mx, my);
        drawIconButton(closeX(), null, true, "x", mx, my);

        // lưới thẻ
        clip(PAD - 3, GRID_Y - 1, VW - 2 * PAD + 6, GRID_B - GRID_Y + 2);
        int cw = cardW();
        for (int i = 0; i < shown.size(); i++) {
            int x = cardX(i), y = cardY(i);
            if (y + CARD_H < GRID_Y - 4 || y > GRID_B + 4) continue;
            Module m = shown.get(i);
            float st = stagger(i);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, (1f - st) * 12f, 0f);
            drawCard(m, i, x, y, cw, mx, my);
            GlStateManager.popMatrix();
        }
        Draw.endScissor();

        if (shown.isEmpty()) txtC("Chưa có tính năng nào", VW / 2f, VH / 2f, INK3);

        // thanh cuộn mảnh
        float max = gridMax();
        if (max > 0) {
            int trackH = GRID_B - GRID_Y;
            int th = Math.max(20, Math.round(trackH * trackH / (float) gridContentH()));
            int ty = GRID_Y + Math.round((trackH - th) * (gridScroll / max));
            Gui.drawRect(VW - 11, GRID_Y, VW - 9, GRID_B, 0x22FFFFFF);
            Gui.drawRect(VW - 11, ty, VW - 9, ty + th, ORANGE);
        }

        // gợi ý thao tác
        txt("Bấm tên: bật / tắt  \u00B7  Bấm biểu tượng hoặc chuột phải: tuỳ chỉnh  \u00B7  Chuột giữa: ghim lên đầu",
                PAD, VH - 19, INK3);

        // nhãn khi rê chuột vào ba nút góc phải
        String tip = null;
        int tipRight = VW - PAD;
        if (in(mx, my, profX(), TAB_Y, TAB_H, TAB_H)) tip = "Hồ sơ";
        else if (in(mx, my, hudX(), TAB_Y, TAB_H, TAB_H)) tip = "Sửa HUD";
        else if (in(mx, my, closeX(), TAB_Y, TAB_H, TAB_H)) tip = "Đóng";
        if (tip != null) {
            int tw = fontRendererObj.getStringWidth(tip) + 10;
            pbox(tipRight - tw, TAB_Y + TAB_H + 9, tw, 14, 0xF0121A22, OFF_EDGE_HI, 0);
            txt(tip, tipRight - tw + 5, TAB_Y + TAB_H + 12, INK);
        }
    }

    private void drawCard(Module m, int i, int x, int y, int w, int mx, int my) {
        boolean inGrid = my >= GRID_Y && my < GRID_B;
        boolean overIco = inGrid && in(mx, my, x, y, ICON_W, CARD_H);
        int nx = x + ICON_W + ICON_GAP, nw = w - ICON_W - ICON_GAP;
        boolean overName = inGrid && in(mx, my, nx, y, nw, CARD_H);
        float hi = hv("ico" + i, overIco), hn = hv("card" + i, overName);
        boolean on = m.enabled;
        int col = 0xFF000000 | catRgb(m.category);
        int light = Draw.lerp(col, 0xFFFFFFFF, 0.55f);

        // ô biểu tượng (bấm = mở tuỳ chỉnh)
        pbox(x, y, ICON_W, CARD_H,
                on ? tintFill(col, 0.20f + 0.08f * hi) : Draw.lerp(OFF_FILL, OFF_FILL_HI, hi),
                on ? Draw.lerp(col, 0xFFFFFFFF, hi * 0.35f) : Draw.lerp(OFF_EDGE, OFF_EDGE_HI, hi),
                on ? 0x38 : 0);
        drawItem(iconOf(m), x + 6f, y + 6f, 1.25f);
        if (!on) Gui.drawRect(x + 1, y + 1, x + ICON_W - 1, y + CARD_H - 1, 0x99141A22);
        if (hi > 0.05f) Icons.draw("gear", x + ICON_W - 10, y + 3, 1, Draw.mulAlpha(0xFFFFFFFF, hi));

        // ô tên (bấm = bật / tắt)
        pbox(nx, y, nw, CARD_H,
                on ? tintFill(col, 0.22f + 0.08f * hn) : Draw.lerp(OFF_FILL, OFF_FILL_HI, hn),
                on ? Draw.lerp(col, 0xFFFFFFFF, hn * 0.35f) : Draw.lerp(OFF_EDGE, OFF_EDGE_HI, hn),
                on ? 0x38 : 0);
        int dc = on ? Draw.lerp(col, 0xFFFFFFFF, 0.2f) : 0xFF56626F;
        dashes(nx + 5, y + CARD_H / 2, dc);
        dashes(nx + nw - 6, y + CARD_H / 2, dc);

        boolean fav = FAVS.contains(m.id);
        int nameW = nw - 12 - 20 - (fav ? 10 : 0);
        txtS(fit(Lang.vi(m.name).toUpperCase(), nameW), nx + 12, y + (CARD_H - 8) / 2f, on ? light : OFF_TEXT);
        if (fav) Icons.draw("heart", nx + nw - 28, y + (CARD_H - 8) / 2, 1, on ? light : OFF_TEXT);

        // chấm trạng thái
        int sx = nx + nw - 17, sy = y + CARD_H / 2 - 2;
        if (on) {
            Gui.drawRect(sx, sy, sx + 4, sy + 4, light);
        } else {
            Gui.drawRect(sx - 1, sy - 1, sx + 5, sy + 5, 0xFF2A3340);
            Gui.drawRect(sx, sy, sx + 4, sy + 4, 0xFF10161C);
        }
    }

    // ----------------------------------------------------------- bảng tuỳ chỉnh

    private void drawDetail(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        Module m = module;

        // thanh trên: quay lại, biểu tượng + tên, bật / tắt
        Gui.drawRect(PAD, TAB_Y + TAB_H + 5, VW - PAD, TAB_Y + TAB_H + 6, 0x2AFFFFFF);
        button(PAD, TAB_Y, 64, TAB_H, "< Quay lại", hv("back", in(mx, my, PAD, TAB_Y, 64, TAB_H)), false, INK);
        drawItem(iconOf(m), PAD + 74, TAB_Y + 5, 1f);
        txtS(fit(Lang.vi(m.name).toUpperCase(), VW - 2 * PAD - 74 - 20 - 100), PAD + 96, TAB_Y + 9,
                Draw.lerp(0xFF000000 | catRgb(m.category), 0xFFFFFFFF, 0.55f));
        int ex = VW - PAD - 84;
        button(ex, TAB_Y, 84, TAB_H, m.enabled ? "ENABLED" : "DISABLED", hv("en", in(mx, my, ex, TAB_Y, 84, TAB_H)), m.enabled,
                m.enabled ? 0xFFFFFFFF : INK2);

        for (int i = 0; i < descLines.length; i++) txt(descLines[i], PAD + 2, GRID_Y + i * 10, INK2);

        int top = listTop(), vh = listViewH();
        glass(COL_X - 10, top - 4, COL_W + 20, vh + 8, 4, 0x70101820, GLASS_EDGE);

        clip(COL_X - 8, top - 2, COL_W + 16, vh + 4);
        int rx = COL_X + 8, rw = COL_W - 16;
        for (int i = 0; i < rows.size(); i++) {
            SRow r = rows.get(i);
            int y = top + r.y - Math.round(setScroll);
            if (y + r.h < top - 2 || y > top + vh + 2) continue;
            drawRow(r, i, rx, y, rw, mx, my, top, vh);
        }
        Draw.endScissor();

        float max = setMax();
        if (max > 0) {
            int th = Math.max(20, Math.round(vh * vh / (float) contentH));
            int ty = top + Math.round((vh - th) * (setScroll / max));
            Gui.drawRect(COL_X + COL_W + 2, top, COL_X + COL_W + 4, top + vh, 0x22FFFFFF);
            Gui.drawRect(COL_X + COL_W + 2, ty, COL_X + COL_W + 4, ty + th, ORANGE);
        }
    }

    private int trackX(int rx, int rw) {
        return rx + rw - 46 - 10 - TRACK_W;
    }

    private void drawRow(SRow r, int i, int rx, int y, int rw, int mx, int my, int top, int vh) {
        boolean inView = my >= top && my < top + vh;
        boolean over = inView && in(mx, my, rx - 4, y, rw + 8, r.h);
        float h = hv("row" + i, over);
        int ty = y + (r.h - 8) / 2;

        switch (r.kind) {
            case R_HEAD: {
                txt(r.text, rx, y + 8, ORANGE);
                Gui.drawRect(rx, y + r.h - 3, rx + rw, y + r.h - 2, 0x33FFFFFF);
                break;
            }
            case R_BOOL: {
                BoolSetting b = (BoolSetting) r.s;
                if (over) Gui.drawRect(rx - 4, y + 1, rx + rw + 4, y + r.h - 1, 0x14FFFFFF);
                txt(fit(Lang.vi(r.s.label), rw - 44), rx, ty, INK);
                drawSwitch(rx + rw - 30, y + (r.h - 14) / 2, b.value ? 1f : 0f, h);
                break;
            }
            case R_MODE: {
                ModeSetting ms = (ModeSetting) r.s;
                txt(fit(Lang.vi(r.s.label), rw - 130), rx, ty, INK);
                int bx = rx + rw - 120, by = y + (r.h - 18) / 2;
                button(bx, by, 120, 18, Lang.vi(ms.get()), h, true, 0xFFFFFFFF);
                break;
            }
            case R_COLOR: {
                ColorSetting cs = (ColorSetting) r.s;
                txt(fit(Lang.vi(r.s.label), rw - 130), rx, ty, INK);
                int sx = rx + rw - 120, sy = y + (r.h - 18) / 2;
                txt(colorName(cs.value), sx, ty, INK2);
                int bx = rx + rw - 46, by = y + (r.h - 16) / 2;
                pbox(bx - 1, by - 1, 48, 18, 0xFF000000 | cs.value, Draw.lerp(OFF_EDGE_HI, 0xFFFFFFFF, h), 0);
                break;
            }
            case R_SLIDER: {
                SliderSetting ss = (SliderSetting) r.s;
                int tx = trackX(rx, rw);
                txt(fit(Lang.vi(r.s.label), tx - rx - 10), rx, ty, INK);
                int cy = y + r.h / 2;
                int hx = tx + Math.round((float) ss.ratio() * (TRACK_W - 8));
                Gui.drawRect(tx, cy - 1, tx + TRACK_W, cy + 2, 0xFF2A3340);
                Gui.drawRect(tx, cy - 1, hx + 4, cy + 2, ORANGE);
                pbox(hx - 1, cy - 4, 10, 9, Draw.lerp(0xFF202A36, 0xFF3A2A10, h), Draw.lerp(ORANGE_DK, ORANGE_HI, h), 0);
                int vx0 = rx + rw - 46;
                glass(vx0, y + (r.h - 16) / 2, 46, 16, 3, 0xB0182029, GLASS_EDGE);
                txtC(fit(ss.display(), 42), vx0 + 23, ty, INK);
                break;
            }
            case R_ACTION: {
                int bw = Math.min(240, rw);
                int bx = rx + (rw - bw) / 2, by = y + 3;
                button(bx, by, bw, r.h - 6, null, h, false, INK);
                int tw = fontRendererObj.getStringWidth(r.text);
                int ix = bx + (bw - tw) / 2;
                if (r.a != null && r.a.icon != null) {
                    drawItem(r.a.icon, ix - 12, by + (r.h - 6 - 12) / 2f, 0.75f);
                    ix += 8;
                }
                txt(r.text, ix, by + (r.h - 6 - 8) / 2f, INK);
                break;
            }
            case R_RESET: {
                int bw = Math.min(180, rw);
                int bx = rx + (rw - bw) / 2, by = y + 6;
                button(bx, by, bw, 22, r.text, h, false, RED);
                break;
            }
            default:
                break;
        }
    }

    /** Công tắc pixel: núm vuông trượt, viền + nền cam khi bật. */
    private static void drawSwitch(int x, int y, float a, float hov) {
        int edge = Draw.lerp(Draw.lerp(OFF_EDGE, OFF_EDGE_HI, hov), ORANGE, a);
        pbox(x, y, 28, 14, Draw.lerp(OFF_FILL, 0xE0382400, a), edge, 0);
        int kx = x + 3 + Math.round(13 * a);
        Gui.drawRect(kx, y + 3, kx + 8, y + 11, Draw.lerp(0xFF8794A6, ORANGE_HI, a));
    }

    // ----------------------------------------------------------- vị trí các vùng bấm

    /** Trả về khoá của vùng bấm tại (mx, my) trong khung ảo, hoặc null. */
    private String hit(int mx, int my) {
        if (detail && module != null) {
            if (in(mx, my, PAD, TAB_Y, 64, TAB_H)) return "back";
            if (in(mx, my, VW - PAD - 84, TAB_Y, 84, TAB_H)) return "en";
            int top = listTop(), vh = listViewH();
            if (my < top || my >= top + vh || mx < COL_X - 8 || mx >= COL_X + COL_W + 8) return null;
            int rx = COL_X + 8, rw = COL_W - 16;
            for (int i = 0; i < rows.size(); i++) {
                SRow r = rows.get(i);
                int y = top + r.y - Math.round(setScroll);
                if (my < y || my >= y + r.h) continue;
                switch (r.kind) {
                    case R_BOOL:
                        return "row" + i;
                    case R_MODE:
                        return in(mx, my, rx + rw - 120, y + (r.h - 18) / 2, 120, 18) ? "row" + i : null;
                    case R_COLOR:
                        return in(mx, my, rx + rw - 46, y + (r.h - 16) / 2, 46, 16) ? "row" + i : null;
                    case R_SLIDER:
                        return mx >= trackX(rx, rw) - 6 && mx < trackX(rx, rw) + TRACK_W + 6 ? "row" + i : null;
                    case R_ACTION: {
                        int bw = Math.min(240, rw);
                        return in(mx, my, rx + (rw - bw) / 2, y + 3, bw, r.h - 6) ? "row" + i : null;
                    }
                    case R_RESET: {
                        int bw = Math.min(180, rw);
                        return in(mx, my, rx + (rw - bw) / 2, y + 6, bw, 22) ? "row" + i : null;
                    }
                    default:
                        return null;
                }
            }
            return null;
        }
        layoutTabs();
        for (int i = 0; i < tabCount(); i++) {
            if (in(mx, my, tabX(i), TAB_Y, tabW(i), TAB_H)) return "tab" + i;
        }
        if (in(mx, my, profX(), TAB_Y, TAB_H, TAB_H)) return "prof";
        if (in(mx, my, hudX(), TAB_Y, TAB_H, TAB_H)) return "hud";
        if (in(mx, my, closeX(), TAB_Y, TAB_H, TAB_H)) return "x";
        if (my >= GRID_Y && my < GRID_B) {
            int cw = cardW();
            for (int i = 0; i < shown.size(); i++) {
                int x = cardX(i), y = cardY(i);
                if (!in(mx, my, x, y, cw, CARD_H)) continue;
                if (mx < x + ICON_W) return "card" + i; // ô biểu tượng = mở tuỳ chỉnh
                if (mx >= x + ICON_W + ICON_GAP) return "tog" + i; // ô tên = bật / tắt
                return null;
            }
        }
        return null;
    }

    private static int idx(String key, int prefixLen) {
        try {
            return Integer.parseInt(key.substring(prefixLen));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void perform(String key, boolean right) {
        if (key == null) return;
        if (key.startsWith("tab")) {
            play("menu.click");
            setTab(idx(key, 3));
        } else if (key.equals("prof")) {
            play("menu.click");
            mc.displayGuiScreen(new ProfileScreen());
        } else if (key.equals("hud")) {
            play("menu.click");
            mc.displayGuiScreen(new HudEditorScreen());
        } else if (key.equals("x")) {
            requestClose();
        } else if (key.startsWith("tog")) {
            int i = idx(key, 3);
            if (i >= 0 && i < shown.size()) {
                if (right) openModule(shown.get(i));
                else toggleModule(shown.get(i));
            }
        } else if (key.startsWith("card")) {
            int i = idx(key, 4);
            if (i >= 0 && i < shown.size()) openModule(shown.get(i));
        } else if (key.equals("back")) {
            goBack();
        } else if (key.equals("en")) {
            if (module != null) toggleModule(module);
        } else if (key.startsWith("row")) {
            int i = idx(key, 3);
            if (i < 0 || i >= rows.size()) return;
            performRow(rows.get(i), right);
        }
    }

    private void performRow(SRow r, boolean right) {
        Module m = module;
        switch (r.kind) {
            case R_BOOL: {
                long before = visSig(m);
                BoolSetting b = (BoolSetting) r.s;
                b.toggle();
                play(b.value ? "menu.on" : "menu.off");
                if (visSig(m) != before) buildRows();
                break;
            }
            case R_MODE: {
                play("menu.click");
                if (right) ((ModeSetting) r.s).prev();
                else ((ModeSetting) r.s).next();
                break;
            }
            case R_COLOR: {
                play("menu.click");
                ColorSetting col = (ColorSetting) r.s;
                int n = ColorSetting.PRESETS.length, i = presetIndex(col.value);
                int next = !right ? (i + 1) % n : (i < 0 ? n - 1 : (i + n - 1) % n);
                col.value = ColorSetting.PRESETS[next];
                break;
            }
            case R_ACTION:
                play("menu.click");
                r.a.run.run();
                buildRows();
                break;
            case R_RESET:
                play("menu.click");
                m.resetSettings();
                if (m instanceof HudModule) ((HudModule) m).resetPosition();
                buildRows();
                break;
            default:
                break;
        }
    }

    private void setSliderFromMouse(SRow r, int mx) {
        int rx = COL_X + 8, rw = COL_W - 16;
        float v = (mx - trackX(rx, rw) - 4) / (float) (TRACK_W - 8);
        ((SliderSetting) r.s).setRatio(Math.max(0f, Math.min(1f, v)));
    }

    // ----------------------------------------------------------- chuột

    @Override
    protected void mouseClicked(int rawX, int rawY, int btn) throws IOException {
        if (closing) return;
        int mx = vx(rawX), my = vy(rawY);
        String key = hit(mx, my);
        if (btn == 1) { // chuột phải: bật / tắt thẻ, hoặc đổi chế độ lùi lại
            if (key != null) perform(key, true);
            return;
        }
        if (btn == 2) { // chuột giữa: ghim / bỏ ghim tính năng lên đầu nhóm
            if (!detail && key != null && (key.startsWith("card") || key.startsWith("tog"))) {
                int i = idx(key, key.startsWith("card") ? 4 : 3);
                if (i >= 0 && i < shown.size()) toggleFav(shown.get(i));
            }
            return;
        }
        if (btn != 0) return;
        pressKey = key;
        pressX = mx;
        pressY = my;
        pressScroll = detail ? setTarget : gridTarget;
        dragged = false;
        activeSlider = null;
        if (detail && key != null && key.startsWith("row")) {
            int i = idx(key, 3);
            if (i >= 0 && i < rows.size() && rows.get(i).kind == R_SLIDER) {
                activeSlider = rows.get(i);
                setSliderFromMouse(activeSlider, mx);
            }
        }
    }

    @Override
    protected void mouseClickMove(int rawX, int rawY, int btn, long time) {
        if (btn != 0) return;
        int mx = vx(rawX), my = vy(rawY);
        if (activeSlider != null) {
            setSliderFromMouse(activeSlider, mx);
            return;
        }
        if (!dragged && Math.abs(my - pressY) > 5) dragged = true;
        if (dragged) { // kéo để cuộn (dùng được bằng cảm ứng trên điện thoại)
            if (detail) {
                setTarget = setScroll = pressScroll - (my - pressY);
                clampSet();
            } else {
                gridTarget = gridScroll = pressScroll - (my - pressY);
                clampGrid();
            }
        }
    }

    @Override
    protected void mouseReleased(int rawX, int rawY, int state) {
        if (state != 0) return;
        int mx = vx(rawX), my = vy(rawY);
        if (activeSlider != null) {
            activeSlider = null;
            pressKey = null;
            return;
        }
        String key = pressKey;
        pressKey = null;
        if (key == null || dragged) return;
        if (key.equals(hit(mx, my))) perform(key, false);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        if (detail) {
            setTarget += (d > 0 ? -1 : 1) * 2f * ROW_H;
            clampSet();
        } else {
            gridTarget += (d > 0 ? -1 : 1) * PITCH * 0.75f;
            clampGrid();
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
                setTarget -= ROW_H;
                clampSet();
            } else if (key == Keyboard.KEY_DOWN) {
                setTarget += ROW_H;
                clampSet();
            }
            return;
        }
        if (key == Keyboard.KEY_LEFT) cycleTab(-1);
        else if (key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_TAB) cycleTab(1);
        else if (key == Keyboard.KEY_UP) {
            gridTarget -= PITCH;
            clampGrid();
        } else if (key == Keyboard.KEY_DOWN) {
            gridTarget += PITCH;
            clampGrid();
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
