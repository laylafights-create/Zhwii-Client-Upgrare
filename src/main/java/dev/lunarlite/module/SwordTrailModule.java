package dev.lunarlite.module;

import dev.lunarlite.effect.SwordTrailEffect;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;

/** Module mỏng: chỉ giữ tuỳ chỉnh trong menu, phần logic và vẽ nằm ở SwordTrailEffect. */
public class SwordTrailModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("mode", "Color Mode", 0, "Static", "Rainbow", "Gradient"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x55FFFF));
    private final ColorSetting color2 = add(new ColorSetting("color2", "Gradient End", 0xAA55FF));
    private final SliderSetting length = add(new SliderSetting("length", "Trail Length", 12, 4, 30, 1, " tick"));
    private final SliderSetting reach = add(new SliderSetting("reach", "Trail Size", 3.2, 1.5, 5.0, 0.1, ""));
    private final SliderSetting sweep = add(new SliderSetting("sweep", "Swing Angle", 130, 60, 180, 5, "°"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 80, 10, 100, 5, "%"));

    private final SwordTrailEffect effect = new SwordTrailEffect();

    public SwordTrailModule() {
        super("swordtrail", "Sword Trail", "Vệt sáng mờ dần theo đường chém khi vung kiếm.", Category.VISUAL, false);
    }

    private void sync() {
        effect.colorMode = mode.index;
        effect.color = color.rgb();
        effect.color2 = color2.rgb();
        effect.length = length.getInt();
        effect.reach = reach.get();
        effect.sweep = sweep.getFloat();
        effect.opacity = opacity.getFloat() / 100f;
    }

    @Override
    public void onToggle() {
        if (!enabled) effect.clear();
    }

    @Override
    public void onTick() {
        sync();
        effect.tick(enabled);
    }

    /** Gọi từ RenderWorldLastEvent. */
    public void render(float partialTicks) {
        if (!enabled) return;
        sync();
        effect.render(partialTicks);
    }
}
