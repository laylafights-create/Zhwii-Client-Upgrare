package dev.lunarlite.module;

/**
 * Hàm hỗ trợ animation cho Music Player: easing, lò xo (spring), trộn màu, marquee.
 * Thuần Java, không phụ thuộc Minecraft nên dễ kiểm thử.
 */
final class MusicAnim {
    private MusicAnim() {}

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Tiến dần về đích theo hàm mũ, không phụ thuộc FPS (speed càng lớn càng nhanh). */
    static float approach(float cur, float target, float speed, float dt) {
        if (dt <= 0f) return cur;
        float r = cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(target - r) < 0.0015f ? target : r;
    }

    static float easeOutCubic(float t) {
        t = clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static float easeInOutCubic(float t) {
        t = clamp(t, 0f, 1f);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** Vọt nhẹ qua đích rồi quay lại. */
    static float easeOutBack(float t) {
        t = clamp(t, 0f, 1f);
        final float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Nhân độ trong suốt của màu ARGB với f (0..1). */
    static int alpha(int argb, float f) {
        int a = Math.round((argb >>> 24) * clamp(f, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Vanilla FontRenderer coi alpha < 4 là đục hoàn toàn, nên phải bỏ qua không vẽ. */
    static boolean drawable(int argb) { return (argb >>> 24) >= 4; }

    /** Trộn tuyến tính hai màu ARGB. */
    static int mix(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int ar = (a >>> 24) & 255, rr = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >>> 24) & 255, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return (Math.round(ar + (br - ar) * t) << 24) | (Math.round(rr + (r2 - rr) * t) << 16)
                | (Math.round(ag + (g2 - ag) * t) << 8) | Math.round(ab + (b2 - ab) * t);
    }

    /**
     * Độ lệch (px, dương) của chữ dài chạy ngang: giữ yên, chạy tới cuối, giữ, rồi trượt êm về đầu.
     * @param t        thời gian từ lúc bắt đầu (giây)
     * @param overflow phần chữ vượt quá khung (px)
     */
    static float marquee(float t, float overflow) {
        if (overflow <= 0f) return 0f;
        final float hold = 1.3f, hold2 = 1.0f, back = 0.55f;
        float run = Math.max(0.8f, overflow / 26f);
        float period = hold + run + hold2 + back;
        float u = t % period;
        if (u < hold) return 0f;
        u -= hold;
        if (u < run) return overflow * (u / run);
        u -= run;
        if (u < hold2) return overflow;
        u -= hold2;
        return overflow * (1f - easeInOutCubic(u / back));
    }

    /** Chỉ số cuối cùng có thời điểm <= v trong mảng tăng dần (-1 nếu không có). */
    static int lastAtOrBefore(long[] times, int n, long v) {
        int lo = 0, hi = n - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= v) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    /** Lò xo giảm chấn: x là độ lệch khỏi trạng thái nghỉ (0). */
    static final class Spring {
        float x, v;
        final float k, c;
        Spring(float k, float c) { this.k = k; this.c = c; }

        void update(float dt) {
            if (dt <= 0f) return;
            // chia nhỏ bước để ổn định khi FPS thấp
            int n = (int) Math.ceil(dt / 0.008f);
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                v += (-k * x - c * v) * h;
                x += v * h;
            }
            if (Math.abs(x) < 0.0005f && Math.abs(v) < 0.005f) { x = 0f; v = 0f; }
        }
    }
}
