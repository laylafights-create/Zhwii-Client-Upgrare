package dev.lunarlite.setting;

import java.awt.Color;
import net.minecraftforge.common.config.Configuration;

public class ColorSetting extends Setting {
    public static final int[] PRESETS = {
        0xFFFFFF, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5577FF, 0xAA55FF, 0xFF55FF, 0x000000
    };

    public int value;
    public int def;

    public ColorSetting(String key, String label, int def) {
        super(key, label);
        this.def = def & 0xFFFFFF;
        this.value = this.def;
    }

    public ColorSetting withDefault(int v) {
        this.def = v & 0xFFFFFF;
        this.value = this.def;
        return this;
    }

    public int rgb() { return value & 0xFFFFFF; }

    public int argb() { return 0xFF000000 | value; }

    public void setHue(float h) {
        value = Color.HSBtoRGB(h, 1f, 1f) & 0xFFFFFF;
    }

    private float[] hsb() {
        return Color.RGBtoHSB((value >> 16) & 255, (value >> 8) & 255, value & 255, null);
    }

    public float hue() { return hsb()[0]; }

    /** true nếu màu đủ rực để hiện chấm đánh dấu trên thanh hue. */
    public boolean isColorful() {
        float[] h = hsb();
        return h[1] > 0.5f && h[2] > 0.5f;
    }

    @Override
    public void load(Configuration c, String cat) {
        value = c.getInt(key, cat, def, 0, 0xFFFFFF, label);
    }

    @Override
    public void save(Configuration c, String cat) {
        c.get(cat, key, def).set(value);
    }

    @Override
    public void reset() { value = def; }
}
