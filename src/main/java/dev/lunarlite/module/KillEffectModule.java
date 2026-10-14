package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.util.EnumParticleTypes;

/** Hiệu ứng khi hạ gục mục tiêu bạn vừa đánh (chỉ hiện phía bạn). Có hiệu ứng kéo dài nhiều tick như Soul Spiral. */
public class KillEffectModule extends Module {
    private final ModeSetting effect = add(new ModeSetting("effect", "Effect", 0,
            "Lightning", "Soul Spiral", "Ring Wave", "Explosion", "Fireworks", "Hearts", "Flames"));
    private final SliderSetting amount = add(new SliderSetting("amount", "Amount", 30, 5, 100, 5, ""));
    private final Random rng = new Random();

    /** Hiệu ứng đang chạy qua nhiều tick. */
    private static final class Active {
        final double x, y, z;
        final float h;
        final String type;
        int tick;
        Active(double x, double y, double z, float h, String type) { this.x = x; this.y = y; this.z = z; this.h = h; this.type = type; }
    }

    private final List<Active> active = new ArrayList<Active>();
    private EntityLivingBase target;
    private long hitAt;

    public KillEffectModule() {
        super("killeffect", "Kill Effect", "Sét, xoắn linh hồn, sóng, pháo hoa... khi bạn hạ gục mục tiêu.", Category.EFFECT, false);
    }

    @Override
    public void onToggle() { active.clear(); target = null; }

    public void onAttack(Entity e) {
        if (!enabled || !(e instanceof EntityLivingBase)) return;
        target = (EntityLivingBase) e;
        hitAt = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) { active.clear(); target = null; return; }
        if (target != null) {
            if (!enabled || System.currentTimeMillis() - hitAt > 6000L) target = null;
            else if (target.isDead || target.getHealth() <= 0f) {
                start(target.posX, target.posY, target.posZ, target.height);
                target = null;
            }
        }
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (!step(a)) it.remove();
        }
    }

    private void start(double x, double y, double z, float h) {
        String m = effect.get();
        Minecraft mc = Minecraft.getMinecraft();
        if (m.equals("Lightning")) {
            mc.theWorld.addWeatherEffect(new EntityLightningBolt(mc.theWorld, x, y, z));
            return;
        }
        if (active.size() >= 6) active.remove(0);
        active.add(new Active(x, y, z, h, m));
    }

    /** Chạy một tick của hiệu ứng; trả false khi xong. */
    private boolean step(Active a) {
        int n = amount.getInt();
        a.tick++;
        String m = a.type;
        if (m.equals("Soul Spiral")) { // 2 dải xoắn ốc bay lên trên đầu mục tiêu trong 30 tick
            if (a.tick > 30) return false;
            double ang = a.tick * 0.5, yy = a.y + a.tick * (a.h + 1.2) / 30.0, r = 0.7 * (1.0 - a.tick / 45.0);
            for (int k = 0; k < 2; k++) {
                double ak = ang + k * Math.PI;
                EffectUtil.spawn(EnumParticleTypes.PORTAL, a.x + Math.cos(ak) * r, yy, a.z + Math.sin(ak) * r, 0, 0.05, 0);
                EffectUtil.spawn(EnumParticleTypes.SPELL_WITCH, a.x + Math.cos(ak) * r, yy, a.z + Math.sin(ak) * r, 0, 0, 0);
            }
            return true;
        }
        if (m.equals("Ring Wave")) { // 3 vòng hạt lan ra ở độ cao khác nhau
            if (a.tick > 9) return false;
            if (a.tick % 3 != 1) return true;
            int ring = a.tick / 3;
            int pts = Math.max(12, n);
            for (int i = 0; i < pts; i++) {
                double ang = i / (double) pts * Math.PI * 2.0;
                EffectUtil.spawn(EnumParticleTypes.CRIT_MAGIC, a.x, a.y + 0.2 + ring * a.h * 0.35, a.z,
                        Math.cos(ang) * 0.5, 0.02, Math.sin(ang) * 0.5);
            }
            return true;
        }
        // Các hiệu ứng nổ một lần
        EnumParticleTypes t;
        if (m.equals("Explosion")) t = EnumParticleTypes.EXPLOSION_LARGE;
        else if (m.equals("Fireworks")) t = EnumParticleTypes.FIREWORKS_SPARK;
        else if (m.equals("Hearts")) t = EnumParticleTypes.HEART;
        else t = EnumParticleTypes.FLAME;
        int c = m.equals("Explosion") ? Math.max(1, n / 10) : n;
        for (int i = 0; i < c; i++) {
            double vx = rng.nextGaussian(), vy = Math.abs(rng.nextGaussian()), vz = rng.nextGaussian();
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6;
            EffectUtil.spawn(t, a.x + (rng.nextDouble() - 0.5) * 0.6, a.y + rng.nextDouble() * a.h, a.z + (rng.nextDouble() - 0.5) * 0.6,
                    vx / l * 0.4, vy / l * 0.4, vz / l * 0.4);
        }
        return false;
    }
}
