package dev.lunarlite.setting;

import dev.lunarlite.module.Module;
import java.awt.Color;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

/**
 * Bộ tùy chỉnh rainbow dùng chung cho MỌI module: bật/tắt, kiểu (Wave/Line/Solid),
 * tốc độ, độ bão hòa, độ sáng, độ giãn của dải màu.
 */
public class RainbowSettings {
    public final BoolSetting enabled;
    public final ModeSetting mode;
    public final SliderSetting speed, saturation, brightness, spread;

    public RainbowSettings(Module owner) {
        enabled = owner.add(new BoolSetting("rainbow", "Rainbow", false), "Rainbow");
        mode = owner.add(new ModeSetting("rb_mode", "Mode", 0, "Wave", "Line", "Solid"), "Rainbow", enabled);
        speed = owner.add(new SliderSetting("rb_speed", "Speed", 1.0, 0.1, 5.0, 0.1, "x"), "Rainbow", enabled);
        saturation = owner.add(new SliderSetting("rb_sat", "Saturation", 0.85, 0.0, 1.0, 0.05, ""), "Rainbow", enabled);
        brightness = owner.add(new SliderSetting("rb_bri", "Brightness", 1.0, 0.2, 1.0, 0.05, ""), "Rainbow", enabled);
        spread = owner.add(new SliderSetting("rb_spread", "Wave Size", 1.0, 0.2, 3.0, 0.1, "x"), "Rainbow", enabled);
        owner.rainbow = this;
    }

    public boolean active() { return enabled.value; }

    public static int hsb(float h, float s, float b) {
        return 0xFF000000 | (Color.HSBtoRGB(h, s, b) & 0xFFFFFF);
    }

    /** Màu rainbow tại vị trí {@code pos} (pixel). Kiểu Solid bỏ qua vị trí. */
    public int color(float pos) {
        if (mode.is("Solid")) pos = 0f;
        double t = (System.currentTimeMillis() % 400000L) / 4000.0 * speed.value;
        double h = t - pos * 0.006 * spread.value;
        h -= Math.floor(h);
        return hsb((float) h, saturation.getFloat(), brightness.getFloat());
    }

    /** Màu cuối cùng: rainbow nếu đang bật, ngược lại là màu gốc (luôn có alpha 0xFF). */
    public int resolve(int baseRgb, float pos) {
        return active() ? color(pos) : (0xFF000000 | baseRgb);
    }

    /** Vẽ chuỗi; Wave đổi màu theo từng ký tự, Line theo từng dòng ({@code pos}), Solid một màu. */
    public void drawString(FontRenderer fr, String text, float x, float y, int baseRgb, boolean shadow, float pos) {
        if (!active()) {
            fr.drawString(text, x, y, 0xFF000000 | baseRgb, shadow);
            return;
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (plain == null) plain = text;
        if (mode.is("Wave")) {
            float cx = x;
            for (int i = 0; i < plain.length(); i++) {
                char ch = plain.charAt(i);
                fr.drawString(String.valueOf(ch), cx, y, color(pos + (cx - x)), shadow);
                cx += fr.getCharWidth(ch);
            }
        } else {
            fr.drawString(plain, x, y, color(pos), shadow);
        }
    }
}
