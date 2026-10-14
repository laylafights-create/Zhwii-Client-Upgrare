package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;

/** Hạt bắn ra khi đánh trúng: Burst (nổ cầu), Ring (vòng hạt lan ra), Fountain (phun lên), Slash Sparks (tia chém chéo). */
public class HitEffectModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", 0, "Burst", "Ring", "Fountain", "Slash Sparks"));
    private final ModeSetting particle = add(new ModeSetting("particle", "Particle", 1, EffectUtil.PARTICLES));
    private final SliderSetting amount = add(new SliderSetting("amount", "Amount", 14, 1, 40, 1, ""));
    private final SliderSetting spread = add(new SliderSetting("spread", "Spread", 1.0, 0.2, 3.0, 0.1, "x"));
    private final Random rng = new Random();

    public HitEffectModule() {
        super("hiteffect", "Hit Effect", "Hạt bắn quanh mục tiêu khi bạn đánh trúng (nhiều kiểu).", Category.EFFECT, false);
    }

    /** Gọi khi người chơi tấn công một sinh vật. */
    public void onAttack(Entity t) {
        if (!enabled || t == null) return;
        EnumParticleTypes pt = EffectUtil.type(particle.get());
        double s = spread.get(), cx = t.posX, cy = t.posY + t.height * 0.6, cz = t.posZ;
        int n = amount.getInt();
        if (style.is("Ring")) {
            for (int i = 0; i < n; i++) {
                double a = i / (double) n * Math.PI * 2.0;
                EffectUtil.spawn(pt, cx, cy, cz, Math.cos(a) * 0.25 * s, 0.02, Math.sin(a) * 0.25 * s);
            }
        } else if (style.is("Fountain")) {
            for (int i = 0; i < n; i++) {
                EffectUtil.spawn(pt, cx + (rng.nextDouble() - 0.5) * 0.4, t.posY + t.height * 0.4, cz + (rng.nextDouble() - 0.5) * 0.4,
                        (rng.nextDouble() - 0.5) * 0.15 * s, 0.25 + rng.nextDouble() * 0.25, (rng.nextDouble() - 0.5) * 0.15 * s);
            }
        } else if (style.is("Slash Sparks")) {
            double ang = rng.nextDouble() * Math.PI, dx = Math.cos(ang), dz = Math.sin(ang);
            for (int i = 0; i < n; i++) { // hạt xếp dọc một đường chéo, bay ra hai phía như tia chém
                double u = (i / (double) (n - 1 + (n == 1 ? 1 : 0)) - 0.5) * 2.0;
                EffectUtil.spawn(pt, cx + dx * u * 0.7 * s, cy + u * 0.7 * s, cz + dz * u * 0.7 * s,
                        dx * u * 0.15, u * 0.15, dz * u * 0.15);
            }
        } else { // Burst
            for (int i = 0; i < n; i++) {
                double vx = rng.nextGaussian(), vy = rng.nextGaussian(), vz = rng.nextGaussian();
                double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6;
                EffectUtil.spawn(pt, cx, cy, cz, vx / l * 0.35 * s, vy / l * 0.35 * s, vz / l * 0.35 * s);
            }
        }
    }
}
