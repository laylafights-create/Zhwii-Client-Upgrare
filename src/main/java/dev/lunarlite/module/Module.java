package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

/** Một tính năng bật/tắt được (giống "mod" trong Lunar Client). */
public abstract class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public boolean enabled;
    private final boolean defaultEnabled;

    public final List<Setting> settings = new ArrayList<Setting>();
    /** Bộ rainbow của module (null nếu module không có). */
    public RainbowSettings rainbow;

    protected Module(String id, String name, String description, Category category, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
        this.enabled = defaultEnabled;
    }

    public <T extends Setting> T add(T s) {
        return add(s, "Options", null);
    }

    public <T extends Setting> T add(T s, String section) {
        return add(s, section, null);
    }

    public <T extends Setting> T add(T s, String section, BoolSetting parent) {
        s.section = section;
        s.parent = parent;
        settings.add(s);
        return s;
    }

    /** Nút riêng của module hiện trong menu (vd: Add / Remove của Resource Counter). */
    public static final class Action {
        public final String label;
        public final Runnable run;
        /** Icon vật phẩm vẽ cạnh chữ (có thể null). */
        public final ItemStack icon;

        public Action(String label, Runnable run, ItemStack icon) {
            this.label = label;
            this.run = run;
            this.icon = icon;
        }
    }

    public List<Action> actions() {
        return Collections.<Action>emptyList();
    }

    /** Trả true (một lần) khi nhãn các nút riêng đã đổi và menu cần dựng lại. */
    public boolean consumeUiDirty() {
        return false;
    }

    /** Gọi mỗi client tick khi đang ở trong thế giới. */
    public void onTick() {}

    public void onToggle() {}

    public void toggle() {
        enabled = !enabled;
        onToggle();
    }

    public void resetSettings() {
        for (Setting s : settings) s.reset();
    }

    public void load(Configuration c) {
        enabled = c.getBoolean("enabled", id, defaultEnabled, "Bật " + name);
        for (Setting s : settings) s.load(c, id);
    }

    public void save(Configuration c) {
        c.get(id, "enabled", defaultEnabled).set(enabled);
        for (Setting s : settings) s.save(c, id);
    }
}
