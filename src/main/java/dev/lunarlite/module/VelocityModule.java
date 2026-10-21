package dev.lunarlite.module;

import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Giảm lực đẩy (knockback) khi bị đánh: nhân vận tốc lúc vừa trúng đòn với tỉ lệ đặt trước. Không có HUD. */
public class VelocityModule extends Module {
    private final SliderSetting horizontal = add(new SliderSetting("vel_h", "Horizontal", 80, 0, 100, 1, "%"));
    private final SliderSetting vertical = add(new SliderSetting("vel_v", "Vertical", 100, 0, 100, 1, "%"));

    public VelocityModule() {
        super("velocity", "Velocity", "Giảm lực đẩy (knockback) khi bị đánh theo tỉ lệ ngang / dọc.", Category.PVP, false);
    }

    /**
     * Chạy ở đầu onLivingUpdate của người chơi: lúc này gói velocity đã áp dụng và hurtTime vừa được đặt
     * (== maxHurtTime) nhưng chưa di chuyển tick nào -> nhân vận tốc đúng một lần cho mỗi đòn.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!enabled) return;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null || e.entity != p) return;
        if (p.maxHurtTime <= 0 || p.hurtTime != p.maxHurtTime) return;
        if (HypixelUtil.inLobby()) return;
        p.motionX *= horizontal.get() / 100.0D;
        p.motionZ *= horizontal.get() / 100.0D;
        p.motionY *= vertical.get() / 100.0D;
    }
}
