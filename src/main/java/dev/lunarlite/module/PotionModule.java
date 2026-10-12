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
 * Hiệu ứng thuốc dạng thẻ giống "Phát hiện kẻ địch": thanh tiêu đề + đếm số hiệu ứng,
 * mỗi hiệu ứng một thẻ riêng (icon, tên + cấp, viên thuốc thời gian, thanh thời gian còn lại).
 * Sắp hết (< 30s vàng, < 10s đỏ + nhấp nháy), hiệu ứng xấu có nền ửng đỏ.
 */
public class PotionModule extends HudModule {
    private static final int HEAD = 20, MIN_W = 104;
    private static final int C_SAFE = 0xFF5CE08A, C_WARN = 0xFFFFC857, C_DANGER = 0xFFFF5C5C;
    private static final int LOW_TICKS = 200, MID_TICKS = 600; // 10s / 30s
    private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    private final BoolSetting showDuration = add(new BoolSetting("duration", "Show Duration", true));
    private final BoolSetting effectColors = add(new BoolSetting("effect_colors", "Use Effect Colors", true));
    private final BoolSetting showIcon = add(new BoolSetting("icon", "Show Icon", true));
    private final BoolSetting showBar = add(new BoolSetting("bar", "Duration Bar", true));
    private final BoolSetting warnLow = add(new BoolSetting("warn_low", "Expiry Warning", true));
    private final BoolSetting sortTime = add(new BoolSetting("sort_time", "Sort By Time Left", false));

    private static class Entry {
        final int id, dur, max, rgb;
        final Potion potion;
        final String name, level, time;
        final boolean infinite, bad;

        Entry(int id, Potion potion, String name, String level, String time, int dur, int max, int rgb,
              boolean infinite, boolean bad) {
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
        }
    }

    private List<Entry> current = new ArrayList<Entry>();
    private List<Entry> sample;
    private final Map<Integer, Integer> maxDur = new HashMap<Integer, Integer>();    // thời lượng lớn nhất đã thấy (để tính %)
    private final Map<Integer, Float> appear = new HashMap<Integer, Float>();        // tiến độ hiện ra của từng thẻ
    private final Map<Integer, Float> barAnim = new HashMap<Integer, Float>();       // thanh thời gian chạy mượt

    public PotionModule() {
        super("potions", "Potion Status", "Hiệu ứng thuốc đang có, thời gian còn lại và cảnh báo sắp hết.",
                Category.PVP, true, 400, 60);
    }

    // ------------------------------------------------------------------ dữ liệu

    private static String fmt(int ticks) {
        int s = Math.max(0, ticks) / 20;
        int m = s / 60;
        return m + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static String roman(int amp) {
        return amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.thePlayer == null) {
            current = new ArrayList<Entry>();
            maxDur.clear();
            return;
        }
        List<Entry> list = new ArrayList<Entry>();
        Set<Integer> seen = new HashSet<Integer>();
        for (Object o : mc.thePlayer.getActivePotionEffects()) {
            PotionEffect pe = (PotionEffect) o;
            int id = pe.getPotionID();
            Potion p = id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
            int dur = pe.getDuration();
            Integer m = maxDur.get(id);
            if (m == null || dur > m) m = dur; // uống lại / gia hạn => thanh đầy lại
            maxDur.put(id, m);
            seen.add(id);
            list.add(new Entry(id, p, I18n.format(pe.getEffectName()), roman(pe.getAmplifier() + 1),
                    Potion.getDurationString(pe), dur, m, p == null ? 0xFFFFFF : p.getLiquidColor(),
                    pe.getIsPotionDurationMax(), p != null && p.isBadEffect()));
        }
        maxDur.keySet().retainAll(seen);
        if (sortTime.value) {
            Collections.sort(list, new Comparator<Entry>() {
                @Override
                public int compare(Entry a, Entry b) {
                    int da = a.infinite ? Integer.MAX_VALUE : a.dur;
                    int db = b.infinite ? Integer.MAX_VALUE : b.dur;
                    return da < db ? -1 : (da == db ? 0 : 1);
                }
            });
        }
        current = list;
    }

    private static Entry fake(int id, Potion p, int amp, int dur, int max) {
        return new Entry(-1 - id, p, I18n.format(p.getName()), roman(amp), fmt(dur), dur, max,
                p.getLiquidColor(), false, p.isBadEffect());
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
        return showBar.value ? 24 : showIcon.value ? 22 : 14;
    }

    @Override
    public int getWidth(boolean preview) {
        List<Entry> es = list(preview);
        if (es.isEmpty()) return 0;
        FontRenderer fr = fr();
        int content = 0;
        for (Entry e : es) {
            int w = fr.getStringWidth(e.name) + 3 + fr.getStringWidth(e.level);
            if (showDuration.value) w += 8 + fr.getStringWidth(e.time) + 6;
            content = Math.max(content, w);
        }
        return Math.max(MIN_W, 3 + 6 + (showIcon.value ? 22 : 0) + content + 4 + 3);
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

    private void drawIcon(Entry e, int ix, int iy, int fallback) {
        Potion p = e.potion;
        if (p != null && p.hasStatusIcon()) {
            int idx = p.getStatusIconIndex();
            Minecraft.getMinecraft().getTextureManager().bindTexture(INVENTORY);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawModalRectWithCustomSizedTexture(ix, iy, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18, 256f, 256f);
            GlStateManager.disableBlend();
        } else {
            rr(ix + 3, iy + 3, 12, 12, 4, fallback); // thuốc không có icon: chấm màu
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

        boolean anyLow = false;
        for (Entry e : es) if (isLow(e)) anyLow = true;
        float pulse = anyLow ? (float) (Math.sin(System.currentTimeMillis() / 110.0) * 0.5 + 0.5) : 0f;
        int accent = accentColor(0) | 0xFF000000;
        int status = anyLow ? C_DANGER : accent;

        // ---- nền + viền bo góc
        drawBox(x, y, W, h);

        // ---- thanh tiêu đề
        rr(x + 2, y + 2, W - 4, HEAD - 4, 3, 0x40000000);
        if (pulse > 0.01f) rr(x + 2, y + 2, W - 4, HEAD - 4, 3, withAlpha(C_DANGER, Math.round(pulse * 0x44)));
        int icx = x + 10, icy = y + HEAD / 2;
        rr(icx - 4, icy - 4, 9, 9, 4, withAlpha(status, 0x38 + Math.round(pulse * 0x70)));
        rr(icx - 2, icy - 2, 5, 5, 2, status);

        String title = "\u00a7lHIỆU ỨNG";
        int ty0 = y + (HEAD - 8) / 2;
        fr.drawString(title, x + 18, ty0, status, shadow.value);
        String n = String.valueOf(total);
        int bw = Math.max(11, fr.getStringWidth(n) + 7);
        int bx = x + 18 + fr.getStringWidth(title) + 5;
        rr(bx, y + 5, bw, 10, 4, status);
        fr.drawString(n, bx + (bw - fr.getStringWidth(n)) / 2f, ty0, 0xFF101010, false);

        Draw.fadeLine(x + 6, y + HEAD - 1, W - 12, status & 0xFFFFFF, 0xAA);

        // ---- danh sách
        int rh = rowH();
        int cardX = x + 3, cardW = W - 6, cardH = rh - 2;
        Set<Integer> alive = new HashSet<Integer>();
        for (int i = 0; i < total; i++) {
            Entry e = es.get(i);
            int ry = y + HEAD + 2 + i * rh;

            // hiện ra mượt + thanh thời gian chạy mượt
            float ap = 1f;
            float frac = e.infinite ? 1f : (e.max <= 0 ? 0f : Math.max(0f, Math.min(1f, e.dur / (float) e.max)));
            if (!preview) {
                Float cur = appear.get(e.id);
                ap = cur == null ? 0f : cur + (1f - cur) * 0.22f;
                if (ap > 0.99f) ap = 1f;
                appear.put(e.id, ap);
                Float bc = barAnim.get(e.id);
                frac = bc == null ? frac : bc + (frac - bc) * 0.2f;
                barAnim.put(e.id, frac);
                alive.add(e.id);
            }

            int base = effectColors.value ? vivid(e.rgb) : accent;
            boolean low = isLow(e);
            boolean mid = warnLow.value && !e.infinite && e.dur <= MID_TICKS;
            int pc = low ? C_DANGER : mid ? C_WARN : base; // màu theo độ gấp

            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.round((1f - ap) * 16f), 0f, 0f); // trượt vào từ phải

            // thẻ nền (hiệu ứng xấu ửng đỏ, sắp hết thì nhấp nháy)
            rr(cardX, ry, cardW, cardH, 3, e.bad ? withAlpha(C_DANGER, 0x22) : 0x14FFFFFF);
            if (low && pulse > 0.01f) rr(cardX, ry, cardW, cardH, 3, withAlpha(C_DANGER, Math.round(pulse * 0x45)));
            if (ap < 1f) rr(cardX, ry, cardW, cardH, 3, withAlpha(accent, Math.round((1f - ap) * 0x55)));
            rr(cardX + 1, ry + 3, 2, Math.max(2, cardH - 6), 1, pc);

            int cx = cardX + 6;
            if (showIcon.value) {
                drawIcon(e, cx, ry + (cardH - 18) / 2, base);
                cx += 22;
            }
            int ty = showBar.value ? ry + 5 : ry + (cardH - 8) / 2;
            int right = cardX + cardW - 4;

            // thời gian trong viên thuốc màu
            int nameRight = right;
            if (showDuration.value) {
                int pw = fr.getStringWidth(e.time) + 6;
                int px = right - pw;
                rr(px, ty - 1, pw, 10, 3, withAlpha(pc, 0x38));
                fr.drawString(e.time, px + 3, ty, pc, shadow.value);
                nameRight = px - 4;
            }

            // tên + cấp
            int lvW = fr.getStringWidth(e.level);
            String nm = fr.trimStringToWidth(e.name, Math.max(10, nameRight - cx - lvW - 3));
            fr.drawString(nm, cx, ty, textColor(x + ry) | 0xFF000000, shadow.value);
            fr.drawString(e.level, cx + fr.getStringWidth(nm) + 3, ty, base, shadow.value);

            // thanh thời gian còn lại
            if (showBar.value) {
                Draw.hbar(cx, ry + 15, right - cx, 3, frac, pc | 0xFF000000, 0x66000000);
            }
            GlStateManager.popMatrix();
        }
        if (!preview) {
            appear.keySet().retainAll(alive);
            barAnim.keySet().retainAll(alive);
        }
    }
}
