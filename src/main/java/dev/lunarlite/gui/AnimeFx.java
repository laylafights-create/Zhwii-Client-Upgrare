package dev.lunarlite.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Hiệu ứng kiểu anime cho menu: cánh hoa anh đào rơi, sao lấp lánh 4 cánh, tia sáng chéo,
 * vạch tốc độ khi mở menu, vệt sáng quét qua thẻ khi rê chuột, nổ sao + sóng tròn khi bấm.
 * Chỉ dùng Tessellator thuần (không cần texture), vẽ trong toạ độ màn hình / khung ảo đều được.
 */
public final class AnimeFx {
    private AnimeFx() {}

    private static final int PETAL_IN = 0xFFFFE9F1;
    private static final int PETAL_OUT = 0xFFFF9EC4;

    // ---------------------------------------------------------------- GL helpers

    private static void begin(boolean additive) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        if (additive) GlStateManager.blendFunc(770, 1);
        else GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    }

    private static void end() {
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void v(WorldRenderer wr, float x, float y, int c) {
        wr.pos(x, y, 0.0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    private static int al(int rgb, float a) {
        int i = Math.round(255f * Math.max(0f, Math.min(1f, a)));
        return (i << 24) | (rgb & 0xFFFFFF);
    }

    private static float hash(int i, int salt) {
        long h = (i * 73856093L) ^ (salt * 19349663L) ^ 0x9E3779B9L;
        h *= 2654435761L;
        return ((h >>> 8) & 0xFFFF) / 65535f;
    }

    // ---------------------------------------------------------------- nền

    /** Toàn bộ lớp nền: tia sáng, hoa rơi, sao lấp lánh. {@code op} = độ mở (0..1). */
    public static void backdrop(int w, int h, float op, int accentRgb) {
        long t = System.currentTimeMillis();
        rays(w, h, op, t);
        petals(w, h, op, t);
        sparkles(w, h, op, t, accentRgb);
    }

    /** Vài dải sáng chéo từ góc trên phải, nhấp nhô chậm (kiểu nắng xuyên qua tán hoa). */
    private static void rays(int w, int h, float op, long t) {
        begin(true);
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 4; i++) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(t / 2600.0 + i * 1.7);
            float x = w * (0.35f + 0.17f * i);
            float bw = w * (0.05f + 0.025f * (i % 2));
            float slant = h * 0.55f;
            int top = al(i % 2 == 0 ? 0xFFE0EC : 0xDFF6FF, 0.13f * pulse * op);
            int bot = al(0xFFFFFF, 0f);
            v(wr, x, 0, top);
            v(wr, x + bw, 0, top);
            v(wr, x + bw - slant, h, bot);
            v(wr, x - slant, h, bot);
        }
        ts.draw();
        end();
    }

    /** Cánh hoa anh đào: xoay, lật (flutter), đung đưa, rơi chéo từ phải trên xuống trái dưới. */
    private static void petals(int w, int h, float op, long t) {
        begin(false);
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        final int n = 26;
        for (int i = 0; i < n; i++) {
            int layer = i % 3; // 0 = xa (nhỏ, chậm, mờ) ... 2 = gần (to, nhanh)
            float per = (11000f + hash(i, 1) * 9000f) / (0.7f + 0.45f * layer);
            float life = (t / per + hash(i, 2)) % 1f;
            float fx = hash(i, 3);
            float gust = (float) Math.sin(t / 3200.0 + life * 5.0) * (6f + 8f * layer); // gió thổi từng đợt
            float px = fx * (w + w * 0.35f) - life * w * 0.35f + (float) Math.sin(t / 1300.0 + i * 2.1) * 16f + gust;
            float py = -12f + life * (h + 24f);
            float fade = Math.min(1f, Math.min(life * 7f, (1f - life) * 7f));
            float a = fade * (0.55f + 0.2f * layer) * op;
            if (a < 0.03f) continue;
            float size = (3.2f + hash(i, 4) * 3.6f) * (0.7f + 0.4f * layer);
            float rot = (float) (t / (700.0 + hash(i, 5) * 700.0) + i);
            float flut = 0.35f + 0.65f * Math.abs((float) Math.cos(t / (900.0 + hash(i, 6) * 600.0) + i));
            float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
            final int seg = 9;
            wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
            v(wr, px, py, al(PETAL_IN, a));
            for (int k = 0; k <= seg; k++) {
                double ang = Math.PI * 2.0 * k / seg;
                float lx = (float) Math.cos(ang) * size;
                float ly = (float) Math.sin(ang) * size * 0.62f * flut;
                // khía nhỏ ở đầu cánh hoa
                if (Math.cos(ang) > 0.85) lx -= size * 0.28f;
                float rx = lx * cr - ly * sr;
                float ry = lx * sr + ly * cr;
                v(wr, px + rx, py + ry, al(PETAL_OUT, a));
            }
            ts.draw();
        }
        end();
    }

    /** Sao 4 cánh nhấp nháy rải khắp màn hình. */
    private static void sparkles(int w, int h, float op, long t, int accentRgb) {
        for (int i = 0; i < 16; i++) {
            float per = 2200f + hash(i, 11) * 2600f;
            float ph = (t / per + hash(i, 12)) % 1f;
            float tw = (float) Math.sin(Math.PI * ph);
            tw *= tw;
            float a = tw * op;
            if (a < 0.05f) continue;
            // mỗi lần nhấp nháy xuất hiện ở một chỗ khác
            int cycle = (int) (t / per + hash(i, 12));
            float x = hash(i * 31 + cycle, 13) * w;
            float y = hash(i * 17 + cycle, 14) * h;
            float r = 3f + hash(i, 15) * 5f;
            star(x, y, r * (0.5f + 0.5f * tw), i % 3 == 0 ? accentRgb : 0xFFFFFF, a);
        }
    }

    // ---------------------------------------------------------------- hình cơ bản

    /** Sao bốn cánh (hai hình thoi mảnh) kèm quầng sáng nhỏ ở tâm. */
    public static void star(float cx, float cy, float r, int rgb, float a) {
        if (r <= 0.3f || a <= 0.01f) return;
        begin(true);
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        int c = al(rgb, a);
        int core = al(0xFFFFFF, a);
        float th = r * 0.16f;
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        v(wr, cx, cy, core);
        v(wr, cx, cy - r, c & 0x00FFFFFF);
        v(wr, cx + th, cy, c);
        v(wr, cx, cy + r, c & 0x00FFFFFF);
        v(wr, cx - th, cy, c);
        v(wr, cx, cy - r, c & 0x00FFFFFF);
        ts.draw();
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        v(wr, cx, cy, core);
        v(wr, cx - r, cy, c & 0x00FFFFFF);
        v(wr, cx, cy - th, c);
        v(wr, cx + r, cy, c & 0x00FFFFFF);
        v(wr, cx, cy + th, c);
        v(wr, cx - r, cy, c & 0x00FFFFFF);
        ts.draw();
        end();
        Draw.disc(cx, cy, r * 0.55f, al(rgb, a * 0.45f), al(rgb, 0f));
    }

    // ---------------------------------------------------------------- mở menu

    /** Vạch tốc độ toả ra từ giữa màn hình trong ~0.45s đầu khi mở menu. */
    public static void speedLines(int w, int h, long sinceOpenMs) {
        float p = sinceOpenMs / 450f;
        if (p <= 0f || p >= 1f) return;
        float cx = w / 2f, cy = h / 2f;
        float diag = (float) Math.sqrt(cx * cx + cy * cy);
        begin(true);
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        wr.begin(4, DefaultVertexFormats.POSITION_COLOR);
        float e = 1f - (1f - p) * (1f - p);
        for (int i = 0; i < 44; i++) {
            float ang = (float) (Math.PI * 2.0 * i / 44.0) + (hash(i, 21) - 0.5f) * 0.12f;
            float len = diag * (0.10f + 0.22f * hash(i, 22));
            float r0 = diag * (0.18f + 0.62f * e) * (0.75f + 0.5f * hash(i, 23));
            float r1 = r0 + len * (1f - p * 0.5f);
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            float wd = 0.9f + hash(i, 24) * 1.1f;
            float a = (1f - p) * 0.55f;
            float ox = -sn * wd, oy = cs * wd;
            v(wr, cx + cs * r0 + ox, cy + sn * r0 + oy, al(0xFFFFFF, 0f));
            v(wr, cx + cs * r0 - ox, cy + sn * r0 - oy, al(0xFFFFFF, 0f));
            v(wr, cx + cs * r1, cy + sn * r1, al(i % 3 == 0 ? 0xFFC4DE : 0xFFFFFF, a));
        }
        ts.draw();
        end();
    }

    // ---------------------------------------------------------------- rê chuột

    /**
     * Vệt sáng chéo quét qua thẻ một lần khi rê chuột vào ({@code hv} = độ rê 0..1, chạy từ 0 lên 1
     * nên không cần lưu trạng thái). Vệt sáng bị kẹp trong khung chữ nhật của thẻ.
     */
    public static void shine(int x, int y, int w, int h, float hv) {
        if (hv <= 0.03f || hv >= 0.97f || w < 12 || h < 8) return;
        float a = (float) Math.sin(Math.PI * hv) * 0.32f;
        float cxm = x + (hv * 1.5f - 0.25f) * w;
        float slant = h * 0.55f;
        float bw = w * 0.14f;
        float xt = cxm + slant / 2f, xb = cxm - slant / 2f;
        float lo = x + 1, hi = x + w - 1;
        begin(true);
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        int clear = al(0xFFFFFF, 0f), white = al(0xFFF1F7, a);
        // nửa trái: trong suốt -> trắng
        v(wr, clamp(xt - bw, lo, hi), y + 1, clear);
        v(wr, clamp(xt, lo, hi), y + 1, white);
        v(wr, clamp(xb, lo, hi), y + h - 1, white);
        v(wr, clamp(xb - bw, lo, hi), y + h - 1, clear);
        // nửa phải: trắng -> trong suốt
        v(wr, clamp(xt, lo, hi), y + 1, white);
        v(wr, clamp(xt + bw, lo, hi), y + 1, clear);
        v(wr, clamp(xb + bw, lo, hi), y + h - 1, clear);
        v(wr, clamp(xb, lo, hi), y + h - 1, white);
        ts.draw();
        end();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------------------------------------------------------- bấm chuột

    private static final List<float[]> BURSTS = new ArrayList<float[]>(); // {x, y, startMs, rgb}

    /** Tạo vụ nổ sao + sóng tròn tại (x, y) theo toạ độ màn hình. */
    public static void burst(float x, float y, int rgb) {
        if (BURSTS.size() > 12) BURSTS.remove(0);
        BURSTS.add(new float[] {x, y, (float) (System.currentTimeMillis() & 0x3FFFFFFL), rgb & 0xFFFFFF});
    }

    /** Vẽ các vụ nổ đang chạy (gọi ở toạ độ màn hình, sau khi đã pop ma trận menu). */
    public static void drawBursts() {
        if (BURSTS.isEmpty()) return;
        long now = System.currentTimeMillis() & 0x3FFFFFFL;
        for (Iterator<float[]> it = BURSTS.iterator(); it.hasNext(); ) {
            float[] b = it.next();
            float age = (now - (long) b[2]) / 650f;
            if (age < 0f || age >= 1f) {
                it.remove();
                continue;
            }
            float e = 1f - (1f - age) * (1f - age) * (1f - age);
            int rgb = (int) b[3];
            float fade = 1f - age;
            Draw.circleRing(b[0], b[1], e * 20f, e * 20f + 1.6f * fade + 0.4f, al(0xFFFFFF, fade * 0.8f));
            Draw.circleRing(b[0], b[1], e * 12f, e * 12f + 1.2f * fade, al(rgb, fade * 0.7f));
            int seed = (int) b[2];
            for (int i = 0; i < 8; i++) {
                float ang = (float) (Math.PI * 2.0 * i / 8.0) + hash(seed, i) * 0.5f;
                float dist = 5f + e * (22f + 10f * hash(seed, i + 8));
                float r = (2.6f + 2.4f * hash(seed, i + 16)) * fade;
                star(b[0] + (float) Math.cos(ang) * dist, b[1] + (float) Math.sin(ang) * dist,
                        r, i % 2 == 0 ? 0xFFFFFF : 0xFFB7D0, fade);
            }
        }
    }
}
