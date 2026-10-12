package dev.lunarlite.setting;

import net.minecraftforge.common.config.Configuration;

public class ModeSetting extends Setting {
    public final String[] modes;
    public final int defIndex;
    public int index;

    public ModeSetting(String key, String label, int defIndex, String... modes) {
        super(key, label);
        this.modes = modes;
        this.defIndex = defIndex;
        this.index = defIndex;
    }

    public String get() { return modes[index]; }

    public boolean is(String m) { return modes[index].equals(m); }

    public void next() { index = (index + 1) % modes.length; }

    public void prev() { index = (index + modes.length - 1) % modes.length; }

    @Override
    public void load(Configuration c, String cat) {
        String s = c.getString(key, cat, modes[defIndex], label);
        index = defIndex;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(s)) { index = i; break; }
        }
    }

    @Override
    public void save(Configuration c, String cat) {
        c.get(cat, key, modes[defIndex]).set(modes[index]);
    }

    @Override
    public void reset() { index = defIndex; }
}
