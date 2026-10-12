package dev.lunarlite.module;

import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Không có HUD: chỉnh gamma để nhìn rõ trong hang tối. */
public class FullbrightModule extends Module {
    private final SliderSetting gamma = add(new SliderSetting("gamma", "Brightness", 15, 2, 1500, 1, ""));
    private boolean applied;
    private float prevGamma;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Sáng tối đa, nhìn rõ trong hang.", Category.VISUAL, false);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (enabled && !applied) {
            prevGamma = mc.gameSettings.gammaSetting;
            applied = true;
        }
        if (enabled) {
            mc.gameSettings.gammaSetting = gamma.getFloat();
        } else if (applied) {
            mc.gameSettings.gammaSetting = prevGamma;
            applied = false;
        }
    }
}
