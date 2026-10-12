package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/**
 * Hiệu ứng thuốc dạng thẻ nhỏ gọn: tiêu đề + số lượng, mỗi hiệu ứng một thẻ gồm
 * ô biểu tượng (icon đúng của hiệu ứng), tên + cấp, viên thời gian và thanh thời gian mảnh.
 * Sắp hết (< 30s vàng, < 10s đỏ + nhấp nháy), hiệu ứng xấu ửng đỏ.
 */
public class PotionModule extends HudModule {
    private static final int HEAD = 16, MIN_W = 96;
    private static final int TILE = 14;                       // ô biểu tượng
    private static final float ICON_SCALE = 0.72f;            // icon 18px thu nhỏ còn ~13px
    private static final int C_WARN = 0xFFFFC857, C_DANGER = 0xFFFF5C5C;
    private static final int LOW_TICKS = 200, MID_TICKS = 600; // 10s / 30s
    private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
    private static final String TITLE = "\u00a7lHIỆU ỨNG";

    private final BoolSetting showDuration = add(new BoolSetting("duration", "Show Duration", true));
    private final BoolSetting effectColors = add(new BoolSetting("effect_colors", "Use Effect Colors", true));
    private final BoolSetting showIcon = add(new BoolSetting("icon", "Show Icon", true));
    private final BoolSetting showBar = add(new BoolSetting("bar", "Duration Bar", true));
    private final BoolSetting warnLow = add(new BoolSetting("warn_low", "Expiry Warning", true));
    private final BoolSetting sortTime = add(new BoolSetting("sort_time", "Sort By Time Left", false));

    private static class Entry {
        final int id, dur, max, rgb, nameW, levelW, timeW;
        final Potion potion;
        final String name, level, time;
        final boolean infinite, bad;

        Entry(FontRenderer fr, int id, Potion potion, String name, String level, String time,
              int dur, int max, int rgb, boolean infinite, boolean bad) {
            this.id = id;
            this.potion = potion;
            this.name = name;
            this.level = level;
            this.time = time;
            this.dur = dur;
            this.max = max;
            this.rgb = rgb;
            this.infinite = infinite;
            this.bad = bad;
            // đo chữ một lần ở tick thay vì mỗi frame
            this.nameW = fr.getStringWidth(name);
            this.levelW = fr.getStringWidth(level);
            this.timeW = fr.getStringWidth(time);
        }
    }

    private static final Comparator<Entry> BY_TIME = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            int da = a.infinite ? Integer.MAX_VALUE : a.dur;
            int db = b.infinite ? Integer.MAX_VALUE : b.dur;
            return da < db ? -1 : (da == db ? 0 : 1);
        }
    };

    private List<Entry> current = new ArrayList<Entry>();
    private List<Entry> sample;
    private final Map<Integer, Integer> maxDur = new HashMap<Integer, Integer>(); // thời lượng lớn nhất đã thấy
    private final Map<Integer, Float> appear = new HashMap<Integer, Float>();     // tiến độ hiện ra
    private final Map<Integer, Float> barAnim = new HashMap<Integer, Float>();    // thanh chạy mượt
    private final Set<Integer> seen = new HashSet<Integer>();                     // tái sử dụng, tránh tạo mới
    private long lastNs;

    public PotionModule() {
        super("potions", "Potion Status", "Hiệu ứng thuốc đang có, thời gian còn lại và cảnh báo sắp hết.",
                Category.PVP, true, 400, 60);
    }

    // ------------------------------------------------------------------ dữ liệu

    private static String fmt(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static String roman(int amp) {
        return amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.thePlayer == null) {
            if (!current.isEmpty()) current = new ArrayList<Entry>();
            maxDur.clear();
            return;
        }
        FontRenderer f = mc.fontRendererObj;
        List<Entry> list = new ArrayList<Entry>();
        seen.clear();
        for (Object o : mc.thePlayer.getActivePotionEffects()) {
            PotionEffect pe = (PotionEffect) o;
            int id = pe.getPotionID();
            Potion p = id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
            int dur = pe.getDuration();
            Integer m = maxDur.get(id);
            if (m == null || dur > m) m = dur; // uống lại / gia hạn => thanh đầy lại
            maxDur.put(id, m);
            seen.add(id);
            list.add(new Entry(f, id, p, I18n.format(pe.getEffectName()), roman(pe.getAmplifier() + 1),
                    Potion.getDurationString(pe), dur, m, p == null ? 0xFFFFFF : p.getLiquidColor(),
                    pe.getIsPotionDurationMax(), p != null && p.isBadEffect()));
        }
        maxDur.keySet().retainAll(seen);
        if (sortTime.value) Collections.sort(list, BY_TIME);
        current = list;
    }

    private static Entry fake(int id, Potion p, int amp, int dur, int max) {
        return new Entry(Minecraft.getMinecraft().fontRendererObj, -1 - id, p, I18n.format(p.getName()), roman(amp),
                fmt(dur), dur, max, p.getLiquidColor(), false, p.isBadEffect());
    }

    private List<Entry> list(boolean preview) {
        if (!preview) return current;
        if (sample == null) {
            sample = new ArrayList<Entry>();
            sample.add(fake(1, Potion.moveSpeed, 2, 1800, 3600));
            sample.add(fake(2, Potion.damageBoost, 1, 900, 3600));
            sample.add(fake(3, Potion.regeneration, 2, 160, 900));
        }
        return sample;
    }

    // ------------------------------------------------------------------ kích thước

    private int rowH() {
        return (showBar.value || showIcon.value) ? 20 : 14;
    }

    @Override
    public int getWidth(boolean preview) {
        List<Entry> es = list(preview);
        if (es.isEmpty()) return 0;
        int content = 0;
        for (Entry e : es) {
            int w = e.nameW + 3 + e.levelW;
            if (showDuration.value) w += 10 + e.timeW + 6;
            if (w > content) content = w;
        }
        return Math.max(MIN_W, 3 + 4 + (showIcon.value ? TILE + 4 : 0) + content + 4 + 3);
    }

    @Override
    public int getHeight(boolean preview) {
        int n = list(preview).size();
        return n == 0 ? 0 : HEAD + n * rowH() + 3;
    }

    // ------------------------------------------------------------------ tiện ích vẽ

    private static void rr(int x, int y, int w, int h, int r, int c) {
        Draw.rrect(x, y, x + w, y + h, r, c);
    }

    private static int withAlpha(int rgb, int a) {
        return ((a & 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Làm sáng màu thuốc tối (Weakness, Wither...) để dễ nhìn trên nền tối. */
    private static int vivid(int rgb) {
        rgb &= 0xFFFFFF;
        int mx = Math.max((rgb >> 16) & 255, Math.max((rgb >> 8) & 255, rgb & 255));
        return 0xFF000000 | (Draw.lerp(0xFF000000 | rgb, 0xFFFFFFFF, mx < 150 ? 0.45f : 0.2f) & 0xFFFFFF);
    }

    private boolean isLow(Entry e) {
        return warnLow.value && !e.infinite && e.dur <= LOW_TICKS;
    }

    /** Ô biểu tượng: nền tint theo màu hiệu ứng + icon đúng của thuốc; thuốc không có icon thì dùng chữ cái đầu. */
    private void drawIcon(FontRenderer fr, Entry e, int tx, int ty, int color, boolean low, float pulse) {
        rr(tx, ty, TILE, TILE, 4, withAlpha(color, 0x30));
        if (low && pulse > 0.01f) rr(tx, ty, TILE, TILE, 4, withAlpha(C_DANGER, Math.round(pulse * 0x50)));

        Potion p = e.potion;
        if (p != null && p.hasStatusIcon()) {
            int idx = p.getStatusIconIndex();
            float off = (TILE - 18f * ICON_SCALE) / 2f;
            Minecraft.getMinecraft().getTextureManager().bindTexture(INVENTORY);
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx + off, ty + off, 0f);
            GlStateManager.scale(ICON_SCALE, ICON_SCALE, 1f);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18, 256f, 256f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } else {
            String c = e.name.isEmpty() ? "?" : e.name.substring(0, 1).toUpperCase();
            fr.drawString("\u00a7l" + c, tx + (TILE - fr.getStringWidth(c) - 1) / 2f, ty + (TILE - 8) / 2f, color, false);
        }
    }

    // ------------------------------------------------------------------ vẽ HUD

    @Override
    protected void draw(int x, int y, boolean preview) {
        FontRenderer fr = fr();
        List<Entry> es = list(preview);
        int total = es.size();
        if (total == 0) return;
        int W = getWidth(preview), h = getHeight(preview);

        // dt để animation mượt như nhau ở mọi FPS
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float kAppear = 1f - (float) Math.exp(-dt * 16f);
        float kBar = 1f - (float) Math.exp(-dt * 12f);

        boolean anyLow = false;
        for (Entry e : es) if (isLow(e)) { anyLow = true; break; }
        float pulse = anyLow ? (float) (Math.sin(System.currentTimeMillis() / 110.0) * 0.5 + 0.5) : 0f;
        int accent = accentColor(0) | 0xFF000000;
        int status = anyLow ? C_DANGER : accent;

        // ---- nền + viền bo góc
        drawBox(x, y, W, h);

        // ---- thanh tiêu đề
        rr(x + 2, y + 2, W - 4, HEAD - 4, 3, 0x40000000);
        if (pulse > 0.01f) rr(x + 2, y + 2, W - 4, HEAD - 4, 3, withAlpha(C_DANGER, Math.round(pulse * 0x44)));
        int icx = x + 9, icy = y + HEAD / 2;
        rr(icx - 4, icy - 4, 9, 9, 4, withAlpha(status, 0x38 + Math.round(pulse * 0x70)));
        rr(icx - 2, icy - 2, 5, 5, 2, status);

        int ty0 = y + (HEAD - 8) / 2;
        fr.drawString(TITLE, x + 17, ty0, status, shadow.value);
        String n = String.valueOf(total);
        int bw = Math.max(11, fr.getStringWidth(n) + 7);
        int bx = x + 17 + fr.getStringWidth(TITLE) + 5;
        rr(bx, y + 3, bw, 10, 4, status);
        fr.drawString(n, bx + (bw - fr.getStringWidth(n)) / 2f, ty0, 0xFF101010, false);

        Draw.fadeLine(x + 6, y + HEAD - 1, W - 12, status & 0xFFFFFF, 0xAA);

        // ---- danh sách
        int rh = rowH();
        int cardX = x + 3, cardW = W - 6, cardH = rh - 2;
        boolean tracking = !preview;
        if (tracking) seen.clear();

        for (int i = 0; i < total; i++) {
            Entry e = es.get(i);
            int ry = y + HEAD + 2 + i * rh;

            float ap = 1f;
            float frac = e.infinite ? 1f : (e.max <= 0 ? 0f : Math.max(0f, Math.min(1f, e.dur / (float) e.max)));
            if (tracking) {
                Float cur = appear.get(e.id);
                ap = cur == null ? 0f : cur + (1f - cur) * kAppear;
                if (ap > 0.99f) ap = 1f;
                appear.put(e.id, ap);
                Float bc = barAnim.get(e.id);
                frac = bc == null ? frac : bc + (frac - bc) * kBar;
                barAnim.put(e.id, frac);
                seen.add(e.id);
            }

            int base = effectColors.value ? vivid(e.rgb) : accent;
            boolean low = isLow(e);
            boolean mid = warnLow.value && !e.infinite && e.dur <= MID_TICKS;
            int pc = low ? C_DANGER : mid ? C_WARN : base; // màu theo độ gấp

            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.round((1f - ap) * 14f), 0f, 0f); // trượt vào từ phải

            // thẻ nền
            rr(cardX, ry, cardW, cardH, 4, e.bad ? withAlpha(C_DANGER, 0x22) : 0x14FFFFFF);
            if (low && pulse > 0.01f) rr(cardX, ry, cardW, cardH, 4, withAlpha(C_DANGER, Math.round(pulse * 0x45)));
            if (ap < 1f) rr(cardX, ry, cardW, cardH, 4, withAlpha(accent, Math.round((1f - ap) * 0x55)));

            int cx = cardX + 4;
            if (showIcon.value) {
                drawIcon(fr, e, cx, ry + (cardH - TILE) / 2, base, low, pulse);
                cx += TILE + 4;
            }
            int ty = showBar.value ? ry + 3 : ry + (cardH - 8) / 2;
            int right = cardX + cardW - 4;

            // thời gian trong viên thuốc màu
            int nameRight = right;
            if (showDuration.value) {
                int pw = e.timeW + 6;
                int px = right - pw;
                rr(px, ty - 1, pw, 10, 4, withAlpha(pc, 0x38));
                fr.drawString(e.time, px + 3, ty, pc, shadow.value);
                nameRight = px - 4;
            }

            // tên + cấp
            String nm = e.name;
            if (cx + e.nameW + 3 + e.levelW > nameRight) {
                nm = fr.trimStringToWidth(e.name, Math.max(10, nameRight - cx - e.levelW - 3));
            }
            fr.drawString(nm, cx, ty, textColor(x + ry) | 0xFF000000, shadow.value);
            fr.drawString(e.level, cx + (nm == e.name ? e.nameW : fr.getStringWidth(nm)) + 3, ty, base, shadow.value);

            // thanh thời gian mảnh
            if (showBar.value) {
                Draw.hbar(cx, ry + cardH - 5, right - cx, 2, frac, pc | 0xFF000000, 0x55000000);
            }
            GlStateManager.popMatrix();
        }
        if (tracking) {
            appear.keySet().retainAll(seen);
            barAnim.keySet().retainAll(seen);
        }
    }
}
