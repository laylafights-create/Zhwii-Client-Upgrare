package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Các hàm vẽ dùng chung: bo góc, viền, scissor, màu. */
public final class Draw {
    private Draw() {}

    // ---- Bảng màu chung của giao diện (menu + HUD editor): nền tối trung tính + một màu nhấn.
    // Màu nhấn do người chơi chọn trong module "Màu giao diện" (ThemeModule.apply() gọi setAccent mỗi lần vẽ).
    /** Màu nhấn chính: vạch chọn, vạch rê chuột, gạch chân, thanh cuộn. */
    public static int ACCENT = 0xFF35D9FF;
    /** Màu nhấn sáng hơn: chữ tiêu đề mục trên nền tối, thanh cuộn khi đang kéo. */
    public static int ACCENT_LIGHT = 0xFFB8F4FF;
    /** Đường kẻ mảnh / nền nút phụ khi rê chuột. */
    public static int ACCENT_DIM = 0x5535D9FF;
    /** Phần đã điền của thanh trượt. */
    public static int ACCENT_FILL = 0x6635D9FF;

    /** Đặt màu nhấn (RGB); các biến thể sáng / mờ được tính tự động từ màu này. */
    public static void setAccent(int rgb) {
        rgb &= 0xFFFFFF;
        ACCENT = 0xFF000000 | rgb;
        ACCENT_LIGHT = lerp(ACCENT, 0xFFFFFFFF, 0.45f);
        ACCENT_DIM = (0x55 << 24) | rgb;
        ACCENT_FILL = (0x66 << 24) | rgb;
    }

    public static int withAlpha(int rgb, int a) {
        return ((a & 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Nhân alpha của màu ARGB với {@code f} (0..1). */
    public static int mulAlpha(int argb, float f) {
        int a0 = argb >>> 24;
        if (a0 == 0) return argb;
        int a = Math.round(a0 * Math.max(0f, Math.min(1f, f)));
        if (a < 5) a = 5;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Hình chữ nhật bo góc (bán kính nhỏ, vẽ bằng các dải ngang). */
    public static void rrect(int x1, int y1, int x2, int y2, int r, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            Gui.drawRect(x1, y1, x2, y2, color);
            return;
        }
        Gui.drawRect(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            Gui.drawRect(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            Gui.drawRect(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** Viền 1px quanh hình chữ nhật (không chồng góc nên alpha đều). */
    public static void outline(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y1 + 1, color);
        Gui.drawRect(x1, y2 - 1, x2, y2, color);
        Gui.drawRect(x1, y1 + 1, x1 + 1, y2 - 1, color);
        Gui.drawRect(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** Viền 1px bo góc, khớp với {@link #rrect} bán kính 3 (bỏ 4 điểm ảnh ở góc). */
    public static void ring(int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 6 || y2 - y1 < 6) {
            outline(x1, y1, x2, y2, color);
            return;
        }
        Gui.drawRect(x1 + 1, y1, x2 - 1, y1 + 1, color);
        Gui.drawRect(x1 + 1, y2 - 1, x2 - 1, y2, color);
        Gui.drawRect(x1, y1 + 1, x1 + 1, y2 - 1, color);
        Gui.drawRect(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** Đường kẻ ngang 1px mờ dần ở hai đầu ({@code maxA}: độ đậm ở giữa, 0..255). */
    public static void fadeLine(int x, int y, int w, int rgb, int maxA) {
        final int seg = 4;
        for (int i = 0; i < w; i += seg) {
            float t = (i + seg / 2f) / w;
            int a = Math.round(maxA * (float) Math.sin(Math.PI * t));
            if (a > 3) Gui.drawRect(x + i, y, Math.min(x + w, x + i + seg), y + 1, withAlpha(rgb, a));
        }
    }

    /** Thanh tiến độ bo tròn: nền {@code track}, phần đầy {@code fill} (ARGB), kèm vệt sáng mỏng ở mép trên. */
    public static void hbar(int x, int y, int w, int h, float frac, int fill, int track) {
        if (w <= 0 || h <= 0) return;
        int r = h >= 4 ? 2 : 1;
        rrect(x, y, x + w, y + h, r, track);
        int f = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (f <= 0) return;
        rrect(x, y, x + f, y + h, r, fill);
        if (f > 2 && h >= 3) Gui.drawRect(x + 1, y, x + f - 1, y + 1, 0x55FFFFFF);
    }

    public static void scissor(Minecraft mc, int x, int y, int w, int h) {
        scissor(mc, x, y, w, h, 1f);
    }

    /** {@code s}: hệ số phóng giao diện (menu tự scale thêm ngoài GUI scale của game). */
    public static void scissor(Minecraft mc, int x, int y, int w, int h, float s) {
        float f = new ScaledResolution(mc).getScaleFactor() * s;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.round(x * f), mc.displayHeight - Math.round((y + h) * f),
                Math.max(0, Math.round(w * f)), Math.max(0, Math.round(h * f)));
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ---- hình tròn mịn (vẽ bằng tam giác, không bị răng cưa như rrect)

    private static void vtx(WorldRenderer wr, float x, float y, int c) {
        wr.pos(x, y, 0.0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    /** Hình tròn đầy, màu chuyển từ tâm (cIn) ra mép (cOut); mép được làm mờ 1px nên không răng cưa. */
    public static void disc(float cx, float cy, float r, int cIn, int cOut) {
        if (r <= 0f) return;
        int seg = Math.max(32, Math.round(r * 6f));
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        vtx(wr, cx, cy, cIn);
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2.0 * i / seg;
            vtx(wr, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, cOut);
        }
        t.draw();
        if ((cOut >>> 24) > 8) { // viền mờ ra ngoài 0.9px
            wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= seg; i++) {
                double a = Math.PI * 2.0 * i / seg;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                vtx(wr, cx + cs * r, cy + sn * r, cOut);
                vtx(wr, cx + cs * (r + 0.9f), cy + sn * (r + 0.9f), cOut & 0xFFFFFF);
            }
            t.draw();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static void disc(float cx, float cy, float r, int c) {
        disc(cx, cy, r, c, c);
    }

    /** Vòng tròn mảnh giữa hai bán kính r1 < r2, hai mép mờ nhẹ. */
    public static void circleRing(float cx, float cy, float r1, float r2, int c) {
        if (r2 <= r1) return;
        int seg = Math.max(32, Math.round(r2 * 6f));
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2.0 * i / seg;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            vtx(wr, cx + cs * r1, cy + sn * r1, c);
            vtx(wr, cx + cs * r2, cy + sn * r2, c);
        }
        t.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
