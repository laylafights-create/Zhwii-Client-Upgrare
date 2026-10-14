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
 * Hiệu ứng thuốc dạng gọn: mỗi hiệu ứng một hàng gồm icon đúng của hiệu ứng, thời gian còn lại dạng
 * mm:ss và một đường kẻ mảnh phía dưới chạy hết chiều rộng khung. Không có tiêu đề hay tên hiệu ứng.
 * Sắp hết (< 30s chữ vàng, < 10s đỏ + nhấp nháy).
 */
public class PotionModule extends HudModule {
    private static final int PAD = 5, MIN_W = 50;
    private static final int ICON = 16;                       // kích thước icon vẽ ra
    private static final float ICON_SCALE = ICON / 18f;       // icon gốc 18px
    private static final int GAP = 5;                         // khoảng cách icon - chữ
    private static final int LINE_H = 2;                      // độ dày đường kẻ
    private static final int C_WARN = 0xFFFFC857, C_DANGER = 0xFFFF5C5C;
    private static final int LOW_TICKS = 200, MID_TICKS = 600; // 10s / 30s
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    private final BoolSetting showDuration = add(new BoolSetting("duration", "Show Duration", true));
    private final BoolSetting effectColors = add(new BoolSetting("effect_colors", "Use Effect Colors", true));
    private final BoolSetting showIcon = add(new BoolSetting("icon", "Show Icon", true));
    private final BoolSetting showBar = add(new BoolSetting("bar", "Duration Bar", true));
    private final BoolSetting drainBar = add(new BoolSetting("bar_drain", "Đường kẻ ngắn dần theo thời gian", false));
    private final BoolSetting warnLow = add(new BoolSetting("warn_low", "Expiry Warning", true));
    private final BoolSetting sortTime = add(new BoolSetting("sort_time", "Sort By Time Left", false));

    private static class Entry {
        final int id, dur, max, rgb, timeW;
        final Potion potion;
        final String name, time;
        final boolean infinite, bad;

        Entry(FontRenderer fr, int id, Potion potion, String name, String time,
              int dur, int max, int rgb, boolean infinite, boolean bad) {
            this.id = id;
            this.potion = potion;
            this.name = name;
            this.time = time;
            this.dur = dur;
            this.max = max;
            this.rgb = rgb;
            this.infinite = infinite;
            this.bad = bad;
            // đo chữ một lần ở tick thay vì mỗi frame
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
    private final Map<Integer, Float> barAnim = new HashMap<Integer, Float>();    // đường kẻ chạy mượt
    private final Set<Integer> seen = new HashSet<Integer>();                     // tái sử dụng, tránh tạo mới
    private long lastNs;

    public PotionModule() {
        super("potions", "Potion Status", "Hiệu ứng thuốc đang có, thời gian còn lại và cảnh báo sắp hết.",
                Category.PVP, true, 400, 60);
    }

    // ------------------------------------------------------------------ dữ liệu

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** 01:28, hoặc 1:05:09 khi từ một giờ trở lên. */
    private static String fmt(int ticks) {
        int s = Math.max(0, ticks) / 20;
        int h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? h + ":" + two(m) + ":" + two(sec) : two(m) + ":" + two(sec);
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
            if (m == null || dur > m) m = dur; // uống lại / gia hạn => đường kẻ đầy lại
            maxDur.put(id, m);
            seen.add(id);
            boolean inf = pe.getIsPotionDurationMax();
            list.add(new Entry(f, id, p, I18n.format(pe.getEffectName()), inf ? "**" : fmt(dur),
                    dur, m, p == null ? 0xFFFFFF : p.getLiquidColor(), inf, p != null && p.isBadEffect()));
        }
        maxDur.keySet().retainAll(seen);
        if (sortTime.value) Collections.sort(list, BY_TIME);
        current = list;
    }

    private static Entry fake(int id, Potion p, int dur, int max) {
        return new Entry(Minecraft.getMinecraft().fontRendererObj, -1 - id, p, I18n.format(p.getName()),
                fmt(dur), dur, max, p.getLiquidColor(), false, p.isBadEffect());
    }

    private List<Entry> list(boolean preview) {
        if (!preview) return current;
        if (sample == null) {
            sample = new ArrayList<Entry>();
            sample.add(fake(1, Potion.damageBoost, 1760, 3600));
            sample.add(fake(2, Potion.moveSpeed, 1740, 3600));
            sample.add(fake(3, Potion.regeneration, 160, 900));
        }
        return sample;
    }

    // ------------------------------------------------------------------ kích thước

    /** Chiều cao phần nội dung của một hàng (icon hoặc chữ). */
    private int contentH() {
        return showIcon.value ? ICON : 10;
    }

    private int rowH() {
        return contentH() + (showBar.value ? 1 + LINE_H : 0) + 2;
    }

    @Override
    public int getWidth(boolean preview) {
        List<Entry> es = list(preview);
        if (es.isEmpty()) return 0;
        int timeW = 0;
        if (showDuration.value) {
            timeW = fr().getStringWidth("00:00"); // chữ số cùng bề rộng nên khung không giật khi đổi giờ
            for (Entry e : es) if (e.timeW > timeW) timeW = e.timeW;
        }
        int inner = (showIcon.value ? ICON + (showDuration.value ? GAP : 0) : 0) + timeW;
        return Math.max(MIN_W, PAD * 2 + inner);
    }

    @Override
    public int getHeight(boolean preview) {
        int n = list(preview).size();
        return n == 0 ? 0 : PAD * 2 + n * rowH() - 2;
    }

    // ------------------------------------------------------------------ tiện ích vẽ

    /** Làm sáng màu thuốc tối (Weakness, Wither...) để dễ nhìn trên nền tối. */
    private static int vivid(int rgb) {
        rgb &= 0xFFFFFF;
        int mx = Math.max((rgb >> 16) & 255, Math.max((rgb >> 8) & 255, rgb & 255));
        return 0xFF000000 | (Draw.lerp(0xFF000000 | rgb, 0xFFFFFFFF, mx < 150 ? 0.45f : 0.2f) & 0xFFFFFF);
    }

    private boolean isLow(Entry e) {
        return warnLow.value && !e.infinite && e.dur <= LOW_TICKS;
    }

    /** Icon đúng của hiệu ứng, vẽ trực tiếp không có ô nền; thuốc không có icon thì dùng chữ cái đầu. */
    private void drawIcon(FontRenderer fr, Entry e, int tx, int ty, int color) {
        Potion p = e.potion;
        if (p != null && p.hasStatusIcon()) {
            int idx = p.getStatusIconIndex();
            Minecraft.getMinecraft().getTextureManager().bindTexture(INVENTORY);
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx, ty, 0f);
            GlStateManager.scale(ICON_SCALE, ICON_SCALE, 1f);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18, 256f, 256f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        } else {
            String c = e.name.isEmpty() ? "?" : e.name.substring(0, 1).toUpperCase();
            fr.drawString("\u00a7l" + c, tx + (ICON - fr.getStringWidth(c) - 1) / 2f, ty + (ICON - 8) / 2f, color, false);
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

        // ---- nền + viền bo góc
        drawBox(x, y, W, h);

        // ---- danh sách
        int rh = rowH(), ch = contentH();
        boolean tracking = !preview;
        if (tracking) seen.clear();

        for (int i = 0; i < total; i++) {
            Entry e = es.get(i);
            int ry = y + PAD + i * rh;

            float ap = 1f;
            float frac = (!drainBar.value || e.infinite) ? 1f
                    : (e.max <= 0 ? 0f : Math.max(0f, Math.min(1f, e.dur / (float) e.max)));
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

            int cx = x + PAD;
            if (showIcon.value) {
                drawIcon(fr, e, cx, ry, base);
                cx += ICON + GAP;
            }

            // thời gian: trắng bình thường, vàng khi dưới 30s, đỏ nhấp nháy khi dưới 10s
            if (showDuration.value) {
                int tc = low ? Draw.lerp(C_DANGER, 0xFFFFFFFF, pulse * 0.6f)
                        : mid ? C_WARN : (textColor(x + ry) | 0xFF000000);
                fr.drawString(e.time, cx, ry + (ch - 8) / 2, tc, shadow.value);
            }

            // đường kẻ mảnh chạy hết chiều rộng khung
            if (showBar.value) {
                Draw.hbar(x + PAD, ry + ch + 1, W - PAD * 2, LINE_H, frac, pc | 0xFF000000, 0x55000000);
            }
            GlStateManager.popMatrix();
        }
        if (tracking) {
            appear.keySet().retainAll(seen);
            barAnim.keySet().retainAll(seen);
        }
    }
}
