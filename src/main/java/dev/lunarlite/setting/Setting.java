package dev.lunarlite.setting;

import net.minecraftforge.common.config.Configuration;

/** Một tùy chỉnh của module (hiện trong menu, lưu vào config). */
public abstract class Setting {
    public final String key;
    public final String label;
    public String section = "Options";
    public BoolSetting parent;

    protected Setting(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Ẩn khi công tắc cha đang tắt (vd: các tùy chỉnh rainbow khi Rainbow = OFF). */
    public boolean isVisible() {
        return parent == null || (parent.value && parent.isVisible());
    }

    public abstract void load(Configuration c, String cat);

    public abstract void save(Configuration c, String cat);

    public abstract void reset();
}
