package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;

/** Rải hạt (lửa, tim, nốt nhạc...) dưới chân khi di chuyển. */
public class ParticleTrailModule extends Module {
    private final ModeSetting particle = add(new ModeSetting("particle", "Particle", 2, EffectUtil.PARTICLES));
    private final SliderSetting density = add(new SliderSetting("density", "Density", 2, 1, 10, 1, ""));
    private final BoolSetting onlyMoving = add(new BoolSetting("only_moving", "Only When Moving", true));
    private final BoolSetting firstPerson = add(new BoolSetting("first_person", "Show In First Person", false));
    private final Random rng = new Random();

    public ParticleTrailModule() {
        super("particletrail", "Particle Trail", "Rải hạt hiệu ứng dưới chân khi bạn di chuyển.", Category.EFFECT, false);
    }

    @Override
    public void onTick() {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null || p.isInvisible()) return;
        if (!firstPerson.value && mc.gameSettings.thirdPersonView == 0) return;
        double dx = p.posX - p.lastTickPosX, dz = p.posZ - p.lastTickPosZ;
        if (onlyMoving.value && dx * dx + dz * dz < 0.0004) return;
        EnumParticleTypes t = EffectUtil.type(particle.get());
        int n = density.getInt();
        for (int i = 0; i < n; i++) {
            EffectUtil.spawn(t,
                    p.posX + (rng.nextDouble() - 0.5) * 0.5,
                    p.posY + 0.1 + rng.nextDouble() * 0.2,
                    p.posZ + (rng.nextDouble() - 0.5) * 0.5,
                    0, 0.02, 0);
        }
    }
}
