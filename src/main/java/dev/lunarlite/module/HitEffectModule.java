package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;

/** Bắn hạt quanh mục tiêu mỗi khi bạn đánh trúng. */
public class HitEffectModule extends Module {
    private final ModeSetting particle = add(new ModeSetting("particle", "Particle", 1, EffectUtil.PARTICLES));
    private final SliderSetting amount = add(new SliderSetting("amount", "Amount", 12, 1, 60, 1, ""));
    private final SliderSetting spread = add(new SliderSetting("spread", "Spread", 1.0, 0.2, 3.0, 0.1, "x"));
    private final Random rng = new Random();

    public HitEffectModule() {
        super("hiteffect", "Hit Effect", "Bắn hạt quanh mục tiêu khi bạn đánh trúng.", Category.EFFECT, true);
    }

    /** Gọi khi người chơi tấn công một sinh vật. */
    public void onAttack(Entity target) {
        if (!enabled || target == null) return;
        EnumParticleTypes t = EffectUtil.type(particle.get());
        double s = spread.get();
        for (int i = 0; i < amount.getInt(); i++) {
            EffectUtil.spawn(t,
                    target.posX + (rng.nextDouble() - 0.5) * target.width * s,
                    target.posY + rng.nextDouble() * target.height,
                    target.posZ + (rng.nextDouble() - 0.5) * target.width * s,
                    (rng.nextDouble() - 0.5) * 0.4 * s, rng.nextDouble() * 0.3, (rng.nextDouble() - 0.5) * 0.4 * s);
        }
    }
}
