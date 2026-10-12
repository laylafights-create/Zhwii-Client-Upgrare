package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Khóa thời gian của thế giới phía client (không ảnh hưởng server). */
public class TimeChangerModule extends Module {
    private final ModeSetting preset = add(new ModeSetting("preset", "Preset", 1, "Custom", "Day", "Sunset", "Night", "Midnight"));
    private final SliderSetting custom = add(new SliderSetting("custom", "Custom Time", 6000, 0, 24000, 250, ""));

    public TimeChangerModule() {
        super("timechanger", "Time Changer", "Đặt thời gian trong ngày (chỉ phía client).", Category.VISUAL, false);
    }

    private long ticks() {
        switch (preset.index) {
            case 1: return 1000L;
            case 2: return 12500L;
            case 3: return 14500L;
            case 4: return 18000L;
            default: return custom.getInt();
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (enabled && mc.theWorld != null) mc.theWorld.setWorldTime(ticks());
    }
}
