package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
import dev.lunarlite.gui.CleanGuis;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;

/** Công tắc chung cho animation của menu và HUD (không có HUD riêng). */
public class AnimationsModule extends Module {
    private final BoolSetting menu = add(new BoolSetting("menu", "Menu Animations", true));
    private final BoolSetting hud = add(new BoolSetting("hud", "HUD Animations", true));
    private final BoolSetting inventory = add(new BoolSetting("inventory", "Inventory Animations", true));
    private final SliderSetting speed = add(new SliderSetting("speed", "Animation Speed", 100, 50, 200, 10, "%"));

    public AnimationsModule() {
        super("animations", "Animations",
                "Animation mở/đóng menu, hover nút, chuyển trang, hiện HUD và nhấn phím.", Category.VISUAL, true);
    }

    public static boolean menu() {
        AnimationsModule a = LunarLite.ANIM;
        return a == null || (a.enabled && a.menu.value);
    }

    public static boolean hud() {
        AnimationsModule a = LunarLite.ANIM;
        return a == null || (a.enabled && a.hud.value);
    }

    // ------------------------------------------------------------ animation mở kho đồ / rương / dân làng...
    private static final float OPEN_MS = 230f;
    private static long invOpenAt;
    private static Pose current;

    /** Trạng thái biến đổi của animation mở: phóng to nhẹ (có nảy) + trượt lên. */
    public static final class Pose {
        float k = 1f, dy = 0f, cx, cy;
        boolean pushed;

        /** Đổi toạ độ chuột về hệ toạ độ gốc để hover/tooltip không bị lệch khi đang chạy animation. */
        public int mx(int x) { return pushed ? Math.round(cx + (x - cx) / k) : x; }
        public int my(int y) { return pushed ? Math.round(cy + (y - cy - dy) / k) : y; }

        public void end() {
            if (pushed) {
                GlStateManager.popMatrix();
                pushed = false;
            }
        }
    }

    /** Bắt đầu animation cho màn hình mở lúc `start`. Luôn gọi Pose.end() sau khi vẽ. */
    public static Pose begin(GuiScreen gui, long start) {
        Pose p = new Pose();
        p.cx = gui.width / 2f;
        p.cy = gui.height / 2f;
        AnimationsModule a = LunarLite.ANIM;
        if (a != null && (!a.enabled || !a.inventory.value)) return p;
        float t = (System.currentTimeMillis() - start) * speed() / OPEN_MS;
        if (t >= 1f) return p;
        float e = easeOutBack(t);
        p.k = 0.88f + 0.12f * e;
        p.dy = (1f - e) * 24f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(p.cx, p.cy + p.dy, 0f);
        GlStateManager.scale(p.k, p.k, 1f);
        GlStateManager.translate(-p.cx, -p.cy, 0f);
        p.pushed = true;
        return p;
    }

    /** Gọi khi một màn hình mới được mở. */
    public static void guiOpened(GuiScreen gui) {
        if (gui instanceof GuiContainer) invOpenAt = System.currentTimeMillis();
    }

    /** Dùng cho các container chưa có bản "sạch" riêng (vd. creative, beacon, ngựa). */
    public static void guiPre(GuiScreen gui) {
        current = null;
        if (!(gui instanceof GuiContainer) || gui instanceof CleanGuis.Clean) return;
        current = begin(gui, invOpenAt);
    }

    /** Sau khi vẽ xong: trả lại ma trận. */
    public static void guiPost() {
        if (current != null) {
            current.end();
            current = null;
        }
    }

    /** Ease-out-back: vượt nhẹ rồi về đúng vị trí, nhìn "đàn hồi" hơn. */
    public static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c1 = 1.1f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Hệ số tốc độ: 1.0 = bình thường. */
    public static float speed() {
        AnimationsModule a = LunarLite.ANIM;
        return a == null ? 1f : a.speed.getFloat() / 100f;
    }

    /** Ease-out cubic, t tự kẹp trong 0..1. */
    public static float ease(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
