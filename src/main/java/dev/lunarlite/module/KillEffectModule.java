package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.util.EnumParticleTypes;

/** Hiệu ứng khi hạ gục mục tiêu bạn vừa đánh (chỉ hiện phía bạn, không ảnh hưởng server). */
public class KillEffectModule extends Module {
    private final ModeSetting effect = add(new ModeSetting("effect", "Effect", 0,
            "Lightning", "Explosion", "Fireworks", "Soul Burst", "Hearts", "Flames"));
    private final SliderSetting amount = add(new SliderSetting("amount", "Amount", 30, 5, 100, 5, ""));
    private final Random rng = new Random();

    private EntityLivingBase target;
    private long hitAt;

    public KillEffectModule() {
        super("killeffect", "Kill Effect", "Sét, nổ, pháo hoa... khi bạn hạ gục mục tiêu.", Category.EFFECT, false);
    }

    public void onAttack(Entity e) {
        if (!enabled || !(e instanceof EntityLivingBase)) return;
        target = (EntityLivingBase) e;
        hitAt = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (target == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.theWorld == null || System.currentTimeMillis() - hitAt > 6000L) {
            target = null;
            return;
        }
        if (target.isDead || target.getHealth() <= 0f) {
            play(target.posX, target.posY, target.posZ, target.height);
            target = null;
        }
    }

    private void play(double x, double y, double z, float h) {
        Minecraft mc = Minecraft.getMinecraft();
        int n = amount.getInt();
        String m = effect.get();
        if (m.equals("Lightning")) {
            mc.theWorld.addWeatherEffect(new EntityLightningBolt(mc.theWorld, x, y, z));
            return;
        }
        EnumParticleTypes t;
        if (m.equals("Explosion")) t = EnumParticleTypes.EXPLOSION_LARGE;
        else if (m.equals("Fireworks")) t = EnumParticleTypes.FIREWORKS_SPARK;
        else if (m.equals("Soul Burst")) t = EnumParticleTypes.PORTAL;
        else if (m.equals("Hearts")) t = EnumParticleTypes.HEART;
        else t = EnumParticleTypes.FLAME;
        if (m.equals("Explosion")) n = Math.max(1, n / 10);
        for (int i = 0; i < n; i++) {
            EffectUtil.spawn(t, x + (rng.nextDouble() - 0.5), y + rng.nextDouble() * h, z + (rng.nextDouble() - 0.5),
                    (rng.nextDouble() - 0.5) * 0.5, rng.nextDouble() * 0.5, (rng.nextDouble() - 0.5) * 0.5);
        }
    }
}
