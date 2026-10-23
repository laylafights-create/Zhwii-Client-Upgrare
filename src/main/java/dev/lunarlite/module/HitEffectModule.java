package dev.lunarlite.module;

import dev.lunarlite.gui.AnimeHit;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.entity.EntityLivingBase;

/** Hiệu ứng kiểu anime khi đánh trúng kẻ địch: chớp sao, vệt chém, sóng tròn, cánh hoa anh đào. */
public class HitEffectModule extends Module {
    private final SliderSetting amount = add(new SliderSetting("hitfx_amount", "Intensity", 1, 0.5, 2, 0.25, "x"));
    private final BoolSetting petals = add(new BoolSetting("hitfx_petals", "Sakura Petals", true));
    private final BoolSetting slash = add(new BoolSetting("hitfx_slash", "Slash Lines", true));
    private final BoolSetting death = add(new BoolSetting("hitfx_death", "Death Effect", true));
    private final BoolSetting stars = add(new BoolSetting("hitfx_stars", "Sparkles", true));

    public HitEffectModule() {
        super("hitfx", "Hit Effect", "Hiệu ứng anime khi đánh trúng: chớp sao, vệt chém, cánh hoa anh đào.", Category.PVP, true);
    }

    /** Gọi khi một sinh vật khác bắt đầu chết. */
    public void onDeath(EntityLivingBase target) {
        if (!enabled || !death.get() || target == null) return;
        AnimeHit.death(target, amount.getFloat());
    }

    /** Gọi khi người chơi tấn công một sinh vật. */
    public void onHit(EntityLivingBase target) {
        if (!enabled || target == null) return;
        if (target.hurtResistantTime > 10) return; // đang trong khung miễn sát thương: đòn không gây sát thương
        AnimeHit.spawn(target, amount.getFloat(), petals.get(), slash.get(), stars.get());
    }
}
