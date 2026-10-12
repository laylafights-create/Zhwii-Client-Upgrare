package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

public class ToggleSprintModule extends TextHudModule {
    private final BoolSetting showHud = add(new BoolSetting("show_hud", "Show Status Text", true));
    private final ModeSetting label = add(new ModeSetting("label", "Label", 0,
            "[Sprinting (Toggled)]", "[Sprinting]", "Sprint"));

    public ToggleSprintModule() {
        super("togglesprint", "Toggle Sprint", "Tự động chạy nhanh khi giữ phím tiến.", Category.UTILITY, false, 4, 224);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (!enabled || p == null || mc.currentScreen != null) return;
        if (mc.gameSettings.keyBindForward.isKeyDown()
                && !p.isSneaking() && !p.isUsingItem() && !p.isCollidedHorizontally
                && p.getFoodStats().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }

    @Override
    protected String[] getLines(boolean preview) {
        if (!showHud.value) return new String[0];
        Minecraft mc = Minecraft.getMinecraft();
        if (preview || (mc.thePlayer != null && mc.thePlayer.isSprinting())) {
            return new String[] { label.get() };
        }
        return new String[0];
    }
}
