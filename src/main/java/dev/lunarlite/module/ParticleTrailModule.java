package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;

/** Hạt quanh người: Trail (rải dưới chân), Spiral (xoắn ốc quanh thân), Halo (vòng sáng trên đầu), Aura (hào quang bay lên). */
public class ParticleTrailModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", 1, "Trail", "Spiral", "Halo", "Aura"));
    private final ModeSetting particle = add(new ModeSetting("particle", "Particle", 2, EffectUtil.PARTICLES));
    private final SliderSetting density = add(new SliderSetting("density", "Density", 2, 1, 10, 1, ""));
    private final BoolSetting onlyMoving = add(new BoolSetting("only_moving", "Only When Moving", false));
    private final BoolSetting firstPerson = add(new BoolSetting("first_person", "Show In First Person", false));
    private final Random rng = new Random();
    private int tick;

    public ParticleTrailModule() {
        super("particletrail", "Particle Trail", "Hạt hiệu ứng quanh người: vệt chân, xoắn ốc, vòng sáng trên đầu, hào quang.", Category.EFFECT, false);
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
        tick++;
        EnumParticleTypes t = EffectUtil.type(particle.get());
        int n = density.getInt();

        if (style.is("Spiral")) { // 2 hạt xoay quanh thân, đi lên rồi lặp lại
            double h = (tick % 30) / 30.0 * (p.height + 0.2);
            for (int k = 0; k < n; k++) {
                double a = tick * 0.35 + k * Math.PI * 2.0 / n;
                EffectUtil.spawn(t, p.posX + Math.cos(a) * 0.7, p.posY + h, p.posZ + Math.sin(a) * 0.7, 0, 0, 0);
            }
        } else if (style.is("Halo")) { // vòng tròn quay trên đầu
            for (int k = 0; k < n + 2; k++) {
                double a = tick * 0.2 + k * Math.PI * 2.0 / (n + 2);
                EffectUtil.spawn(t, p.posX + Math.cos(a) * 0.4, p.posY + p.height + 0.25, p.posZ + Math.sin(a) * 0.4, 0, 0, 0);
            }
        } else if (style.is("Aura")) { // hạt tỏa quanh người bay chậm lên
            for (int k = 0; k < n; k++) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                EffectUtil.spawn(t, p.posX + Math.cos(a) * 0.5, p.posY + rng.nextDouble() * p.height, p.posZ + Math.sin(a) * 0.5,
                        0, 0.04 + rng.nextDouble() * 0.04, 0);
            }
        } else { // Trail
            for (int k = 0; k < n; k++) {
                EffectUtil.spawn(t, p.posX + (rng.nextDouble() - 0.5) * 0.5, p.posY + 0.1 + rng.nextDouble() * 0.2,
                        p.posZ + (rng.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
            }
        }
    }
}
