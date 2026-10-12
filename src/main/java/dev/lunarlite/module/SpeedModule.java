package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Tốc độ di chuyển ngang (blocks/giây), có làm mượt. */
public class SpeedModule extends TextHudModule {
    private final ModeSetting unit = add(new ModeSetting("unit", "Unit", 0, "Blocks/s", "km/h"));
    private final SliderSetting decimals = add(new SliderSetting("decimals", "Decimals", 1, 0, 3, 1, ""));
    private double smooth;

    public SpeedModule() {
        super("speed", "Speed", "Tốc độ chạy ngang của bạn.", Category.HUD, false, 4, 144);
    }

    @Override
    public void onTick() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        double dx = p.posX - p.prevPosX, dz = p.posZ - p.prevPosZ;
        double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
        smooth += (bps - smooth) * 0.25;
    }

    @Override
    protected String[] getLines(boolean preview) {
        double v = unit.is("km/h") ? smooth * 3.6 : smooth;
        return new String[] { String.format(Locale.ROOT, "Speed: %." + decimals.getInt() + "f %s", v, unit.get()) };
    }
}
