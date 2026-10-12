package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
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

    // ------------------------------------------------------------ animation mở kho đồ / rương / bàn chế tạo...
    private static long invOpenAt;
    private static boolean invPushed;

    /** Gọi khi một màn hình mới được mở. */
    public static void guiOpened(GuiScreen gui) {
        if (gui instanceof GuiContainer) invOpenAt = System.currentTimeMillis();
    }

    /** Trước khi vẽ màn hình: phóng nhẹ + trượt lên trong ~0,18 giây đầu. */
    public static void guiPre(GuiScreen gui) {
        invPushed = false;
        AnimationsModule a = LunarLite.ANIM;
        if (a == null || !a.enabled || !a.inventory.value || !(gui instanceof GuiContainer)) return;
        float e = ease((System.currentTimeMillis() - invOpenAt) * speed() / 180f);
        if (e >= 1f) return;
        float cx = gui.width / 2f, cy = gui.height / 2f, k = 0.92f + 0.08f * e;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy + (1f - e) * 14f, 0f);
        GlStateManager.scale(k, k, 1f);
        GlStateManager.translate(-cx, -cy, 0f);
        invPushed = true;
    }

    /** Sau khi vẽ xong: trả lại ma trận. */
    public static void guiPost() {
        if (invPushed) {
            GlStateManager.popMatrix();
            invPushed = false;
        }
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
