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
 * Menu kiểu "lưới thẻ pixel" trên nền kính trắng mờ:
 *   TRANG CHỦ : thanh nhóm (tab cam) ở trên, bên dưới là lưới thẻ. Mỗi thẻ có tên (góc trái), tim yêu thích (góc phải),
 *               biểu tượng là vật phẩm / block của Minecraft, nút ENABLED / DISABLED và nút bánh răng.
 *               Chạm vào thẻ (hoặc bánh răng) = mở tuỳ chỉnh. Kéo lên / xuống để cuộn (dùng được trên điện thoại).
 *   CHI TIẾT  : nhãn bên trái, điều khiển bên phải, thanh trượt mảnh có ô giá trị, nút chế độ màu cam.
 * Toàn bộ giao diện vẽ trong khung ảo VW x VH rồi tự thu nhỏ cho vừa màn hình.
 */
public class MenuScreen extends GuiScreen {
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };

    // ---- khung ảo
    private static final int VW = 520, VH = 272, PAD = 14;
    private static final int TAB_Y = 14, TAB_H = 20;
    private static final int GRID_Y = 46, GRID_B = VH - 14;
    private static final int COLS = 4, GAP = 8, CARD_H = 88;
    private static final float MENU_SCALE = 1.0f;

    // ---- chi tiết
    private static final int COL_X = 60, COL_W = VW - 120;
    private static final int ROW_H = 26;
    private static final int TRACK_W = 140;

    // ---- màu (nền kính trắng, chữ tối, nhấn cam)
    private static final int INK = 0xFF13233A;
    private static final int INK2 = 0xFF44546A;
    private static final int INK3 = 0xFF7A8798;
    private static final int ORANGE = 0xFF2F7DF6;
    private static final int ORANGE_HI = 0xFF5A9BFF;
    private static final int ORANGE_DK = 0xFF1F5FC4;
    private static final int RED = 0xFFD9404B;
    private static final int GLASS_PANEL = 0x66FFFFFF;
    private static final int GLASS_CARD = 0xB8FFFFFF;
    private static final int GLASS_HOV = 0xDCFFFFFF;
    private static final int GLASS_EDGE = 0xF2FFFFFF;
    private static final int SHADE = 0x22000000;

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

    private static void ic(String id, ItemStack s) {
        ICONS.put(id, s);
    }

    private static void initIcons() {
        if (iconDefault != null) return;
        iconDefault = new ItemStack(Blocks.crafting_table);
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
        for (Category c : Category.values()) {
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
        return i == 0 ? "Tất cả" : tabCats.get(i - 1).label;
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

    private int tabW(int i) {
        return fontRendererObj.getStringWidth(tabLabel(i)) + 20;
    }

    private int tabX(int i) {
        int x = PAD;
        for (int k = 0; k < i; k++) x += tabW(k) + 5;
        return x;
    }

    // ---- nút góc phải trên cùng
    private int closeX() {
        return VW - PAD - 20;
    }

    private int hudX() {
        return closeX() - 6 - 54;
    }

    private int profX() {
        return hudX() - 6 - 46;
    }

    // ---- lưới thẻ
    private int cardW() {
        return (VW - 2 * PAD - (COLS - 1) * GAP) / COLS;
    }

    private int cardX(int i) {
        return PAD + (i % COLS) * (cardW() + GAP);
    }

    private int cardY(int i) {
        return GRID_Y + (i / COLS) * (CARD_H + GAP) - Math.round(gridScroll);
    }

    private int gridContentH() {
        int r = (shown.size() + COLS - 1) / COLS;
        return r == 0 ? 0 : r * (CARD_H + GAP) - GAP;
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

    /** Ô kính trắng mờ: nền trắng trong suốt, viền sáng 1px, vệt sáng mảnh ở mép trên, bóng nhẹ ở mép dưới. */
    private static void glass(int x, int y, int w, int h, int r, int fill, int edge) {
        Draw.rrect(x, y, x + w, y + h, r, fill);
        Draw.ring(x, y, x + w, y + h, edge);
        if (w > 12 && h > 8) Gui.drawRect(x + 4, y + 1, x + w - 4, y + 2, 0x55FFFFFF);
        if (w > 12) Gui.drawRect(x + 3, y + h, x + w - 3, y + h + 1, SHADE);
    }

    private void button(int x, int y, int w, int h, String label, float hov, boolean orange, int textColor) {
        if (orange) {
            Draw.rrect(x, y, x + w, y + h, 3, Draw.lerp(ORANGE, ORANGE_HI, hov));
            Draw.ring(x, y, x + w, y + h, 0xFFA9CCFF);
            Gui.drawRect(x + 3, y + 1, x + w - 3, y + 2, 0x66FFFFFF);
        } else {
            glass(x, y, w, h, 3, Draw.lerp(0x1A0B2A5A, 0x340B2A5A, hov), GLASS_EDGE);
        }
        if (label != null) {
            String t = fit(label, w - 6);
            txtC(t, x + w / 2f, y + (h - 8) / 2f, textColor);
        }
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

        // lớp trắng phủ lên thế giới đã bị làm mờ -> cảm giác kính mờ trắng
        int va = Math.round(0x46 * Math.max(0f, Math.min(1f, op)));
        Gui.drawRect(0, 0, width, height, (va << 24) | 0xFFFFFF);
        Draw.fadeLine(width / 4, height / 2, width / 2, 0xFFFFFF, Math.round(0x60 * Math.max(0f, 1f - op)));

        GlStateManager.pushMatrix();
        float pop = 0.90f + 0.10f * op;
        if (!closing && AnimationsModule.menu()) {
            pop = 0.84f + 0.16f * AnimationsModule.easeOutBack((System.currentTimeMillis() - openStart) * AnimationsModule.speed() / 380f);
        }
        float kk = pop * ui();
        GlStateManager.translate(width / 2f, height / 2f + (1f - op) * 10f, 0f);
        GlStateManager.scale(kk, kk, 1f);
        GlStateManager.translate(-VW / 2f, -VH / 2f, 0f);

        // khung kính lớn phía sau
        glass(6, 6, VW - 12, VH - 12, 6, GLASS_PANEL, GLASS_EDGE);

        if (detail && module != null) drawDetail(curMx, curMy);
        else drawHome(curMx, curMy);

        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ----------------------------------------------------------- trang chủ

    private void drawHome(int mx, int my) {
        FontRenderer fr = fontRendererObj;

        // tab nhóm (cam khi đang chọn)
        for (int i = 0; i < tabCount(); i++) {
            int x = tabX(i), w = tabW(i);
            boolean sel = i == tabIndex();
            float h = hv("tab" + i, in(mx, my, x, TAB_Y, w, TAB_H));
            button(x, TAB_Y, w, TAB_H, tabLabel(i), sel ? 1f : h, sel, sel ? 0xFFFFFFFF : INK);
        }
        // nút góc phải: hồ sơ, sửa HUD, đóng
        button(profX(), TAB_Y, 46, TAB_H, "Hồ sơ", hv("prof", in(mx, my, profX(), TAB_Y, 46, TAB_H)), false, INK);
        button(hudX(), TAB_Y, 54, TAB_H, "Sửa HUD", hv("hud", in(mx, my, hudX(), TAB_Y, 54, TAB_H)), false, INK);
        button(closeX(), TAB_Y, 20, TAB_H, "x", hv("x", in(mx, my, closeX(), TAB_Y, 20, TAB_H)), false, RED);

        // lưới thẻ
        clip(PAD - 2, GRID_Y - 2, VW - 2 * PAD + 4, GRID_B - GRID_Y + 3);
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
            Draw.rrect(VW - 11, GRID_Y, VW - 8, GRID_B, 1, 0x33000000);
            Draw.rrect(VW - 11, ty, VW - 8, ty + th, 1, ORANGE);
        }
    }

    private void drawCard(Module m, int i, int x, int y, int w, int mx, int my) {
        boolean inGrid = my >= GRID_Y && my < GRID_B;
        boolean over = inGrid && in(mx, my, x, y, w, CARD_H);
        float h = hv("card" + i, over);
        boolean on = m.enabled;

        glass(x, y, w, CARD_H, 3, Draw.lerp(GLASS_CARD, GLASS_HOV, h), over ? 0xFFFFFFFF : GLASS_EDGE);
        if (over) Draw.ring(x - 1, y - 1, x + w + 1, y + CARD_H + 1, 0x885A9BFF);

        // tên (góc trái) + tim yêu thích (góc phải)
        txt(fit(Lang.vi(m.name), w - 26), x + 7, y + 7, on ? INK : INK2);
        boolean fav = FAVS.contains(m.id);
        boolean overFav = inGrid && in(mx, my, x + w - 20, y + 3, 16, 16);
        float fh = hv("fav" + i, overFav);
        Icons.draw("heart", x + w - 16, y + 7, 1, fav ? 0xFFE0457B : Draw.lerp(0x55000000, 0xAA000000, fh));

        // biểu tượng = vật phẩm Minecraft (to, giữa thẻ)
        float ix = x + (w - 32) / 2f, iy = y + 22;
        Draw.rrect((int) ix - 4, (int) iy - 3, (int) ix + 36, (int) iy + 35, 3, 0x30FFFFFF);
        drawItem(iconOf(m), ix, iy, 2f);
        if (!on) Draw.rrect((int) ix - 4, (int) iy - 3, (int) ix + 36, (int) iy + 35, 3, 0x70FFFFFF);

        // nút ENABLED / DISABLED + bánh răng
        int by = y + CARD_H - 24;
        int tw = w - 12 - 22;
        boolean overTog = inGrid && in(mx, my, x + 6, by, tw, 18);
        boolean overGear = inGrid && in(mx, my, x + w - 24, by, 18, 18);
        button(x + 6, by, tw, 18, on ? "ENABLED" : "DISABLED", hv("tog" + i, overTog), on, on ? 0xFFFFFFFF : INK2);
        button(x + w - 24, by, 18, 18, null, hv("gear" + i, overGear), false, INK);
        Icons.draw("gear", x + w - 24 + 5, by + 5, 1, Draw.lerp(0xFF44546A, ORANGE_DK, hv("gear" + i, overGear)));
    }

    // ----------------------------------------------------------- bảng tuỳ chỉnh

    private void drawDetail(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        Module m = module;

        // thanh trên: quay lại, biểu tượng + tên, bật / tắt
        button(PAD, TAB_Y, 64, TAB_H, "< Quay lại", hv("back", in(mx, my, PAD, TAB_Y, 64, TAB_H)), false, INK);
        drawItem(iconOf(m), PAD + 74, TAB_Y + 2, 1f);
        txt(fit(Lang.vi(m.name), VW - 2 * PAD - 74 - 20 - 100), PAD + 96, TAB_Y + 6, INK);
        int ex = VW - PAD - 84;
        button(ex, TAB_Y, 84, TAB_H, m.enabled ? "ENABLED" : "DISABLED", hv("en", in(mx, my, ex, TAB_Y, 84, TAB_H)), m.enabled,
                m.enabled ? 0xFFFFFFFF : INK2);

        for (int i = 0; i < descLines.length; i++) txt(descLines[i], PAD + 2, GRID_Y + i * 10, INK2);

        int top = listTop(), vh = listViewH();
        glass(COL_X - 10, top - 4, COL_W + 20, vh + 8, 4, 0x40FFFFFF, GLASS_EDGE);

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
            Draw.rrect(COL_X + COL_W + 2, top, COL_X + COL_W + 5, top + vh, 1, 0x33000000);
            Draw.rrect(COL_X + COL_W + 2, ty, COL_X + COL_W + 5, ty + th, 1, ORANGE);
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
                txt(r.text, rx, y + 8, ORANGE_DK);
                Gui.drawRect(rx, y + r.h - 3, rx + rw, y + r.h - 2, 0x44000000);
                break;
            }
            case R_BOOL: {
                BoolSetting b = (BoolSetting) r.s;
                if (over) Draw.rrect(rx - 4, y + 1, rx + rw + 4, y + r.h - 1, 3, 0x33FFFFFF);
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
                Draw.rrect(bx - 1, by - 1, bx + 47, by + 17, 3, Draw.lerp(0xAA000000, 0xFFFFFFFF, h));
                Draw.rrect(bx, by, bx + 46, by + 16, 2, 0xFF000000 | cs.value);
                break;
            }
            case R_SLIDER: {
                SliderSetting ss = (SliderSetting) r.s;
                int tx = trackX(rx, rw);
                txt(fit(Lang.vi(r.s.label), tx - rx - 10), rx, ty, INK);
                int cy = y + r.h / 2;
                int hx = tx + Math.round((float) ss.ratio() * (TRACK_W - 8));
                Draw.rrect(tx, cy - 1, tx + TRACK_W, cy + 2, 1, 0x55000000);
                Draw.rrect(tx, cy - 1, hx + 4, cy + 2, 1, ORANGE);
                Draw.disc(hx + 4f, cy + 0.5f, 6.4f, 0x30000000);
                Draw.disc(hx + 4f, cy + 0.5f, 5.6f, Draw.lerp(0xFFFFFFFF, ORANGE_HI, h));
                Draw.disc(hx + 4f, cy + 0.5f, 3.2f, ORANGE);
                int vx0 = rx + rw - 46;
                glass(vx0, y + (r.h - 16) / 2, 46, 16, 3, 0x70FFFFFF, GLASS_EDGE);
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

    /** Công tắc dạng viên thuốc: núm trượt, nền cam khi bật. */
    private static void drawSwitch(int x, int y, float a, float hov) {
        Draw.rrect(x, y, x + 28, y + 14, 7, Draw.lerp(Draw.lerp(0x66000000, 0x88000000, hov), ORANGE, a));
        int kx = x + 2 + Math.round(14 * a);
        Draw.rrect(kx, y + 2, kx + 10, y + 12, 5, 0xFFFFFFFF);
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
        for (int i = 0; i < tabCount(); i++) {
            if (in(mx, my, tabX(i), TAB_Y, tabW(i), TAB_H)) return "tab" + i;
        }
        if (in(mx, my, profX(), TAB_Y, 46, TAB_H)) return "prof";
        if (in(mx, my, hudX(), TAB_Y, 54, TAB_H)) return "hud";
        if (in(mx, my, closeX(), TAB_Y, 20, TAB_H)) return "x";
        if (my >= GRID_Y && my < GRID_B) {
            int cw = cardW();
            for (int i = 0; i < shown.size(); i++) {
                int x = cardX(i), y = cardY(i);
                if (!in(mx, my, x, y, cw, CARD_H)) continue;
                if (in(mx, my, x + cw - 20, y + 3, 16, 16)) return "fav" + i;
                if (in(mx, my, x + 6, y + CARD_H - 24, cw - 12 - 22, 18)) return "tog" + i;
                return "card" + i; // gồm cả bánh răng: chạm vào đâu khác trong thẻ = mở tuỳ chỉnh
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
        } else if (key.startsWith("fav")) {
            int i = idx(key, 3);
            if (i >= 0 && i < shown.size()) toggleFav(shown.get(i));
        } else if (key.startsWith("tog")) {
            int i = idx(key, 3);
            if (i >= 0 && i < shown.size()) toggleModule(shown.get(i));
        } else if (key.startsWith("card")) {
            int i = idx(key, 4);
            if (i >= 0 && i < shown.size()) {
                if (right) toggleModule(shown.get(i));
                else openModule(shown.get(i));
            }
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
            gridTarget += (d > 0 ? -1 : 1) * (CARD_H + GAP) * 0.75f;
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
            gridTarget -= CARD_H + GAP;
            clampGrid();
        } else if (key == Keyboard.KEY_DOWN) {
            gridTarget += CARD_H + GAP;
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
