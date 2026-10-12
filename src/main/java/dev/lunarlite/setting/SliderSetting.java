package dev.lunarlite.setting;

import java.util.Locale;
import net.minecraftforge.common.config.Configuration;

public class SliderSetting extends Setting {
    public final double min, max, step;
    public double def;
    public double value;
    public final String suffix;

    public SliderSetting(String key, String label, double def, double min, double max, double step, String suffix) {
        super(key, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix == null ? "" : suffix;
        this.def = def;
        set(def);
    }

    public SliderSetting withDefault(double v) {
        this.def = v;
        set(v);
        return this;
    }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        v = Math.round((v - min) / step) * step + min;
        v = Math.round(v * 10000.0) / 10000.0;
        value = Math.max(min, Math.min(max, v));
    }

    public double get() { return value; }
    public float getFloat() { return (float) value; }
    public int getInt() { return (int) Math.round(value); }

    public double ratio() { return (value - min) / (max - min); }

    public void setRatio(double r) {
        r = Math.max(0.0, Math.min(1.0, r));
        set(min + r * (max - min));
    }

    public String display() {
        int decimals = step >= 1.0 ? 0 : (step >= 0.1 ? 1 : 2);
        String s = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (decimals == 2 && s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s + suffix;
    }

    @Override
    public void load(Configuration c, String cat) {
        set(c.getFloat(key, cat, (float) def, (float) min, (float) max, label));
    }

    @Override
    public void save(Configuration c, String cat) {
        c.get(cat, key, def).set(value);
    }

    @Override
    public void reset() { set(def); }
}
