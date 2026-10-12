package dev.lunarlite.setting;

import net.minecraftforge.common.config.Configuration;

public class BoolSetting extends Setting {
    public boolean value;
    public boolean def;

    public BoolSetting(String key, String label, boolean def) {
        super(key, label);
        this.def = def;
        this.value = def;
    }

    /** Đổi giá trị mặc định (dùng trong constructor của module). */
    public BoolSetting withDefault(boolean v) {
        this.def = v;
        this.value = v;
        return this;
    }

    public boolean get() { return value; }

    public void toggle() { value = !value; }

    @Override
    public void load(Configuration c, String cat) {
        value = c.getBoolean(key, cat, def, label);
    }

    @Override
    public void save(Configuration c, String cat) {
        c.get(cat, key, def).set(value);
    }

    @Override
    public void reset() { value = def; }
}
