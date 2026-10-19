package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;

/**
 * Tự động nhảy khi đang đánh để ăn chí mạng (critical).
 * Chỉ nhảy khi ĐANG ĐÁNH (giữ / vừa bấm chuột trái) VÀ tâm ngắm đang trỏ vào một mục tiêu trong tầm;
 * không đánh hoặc không có mục tiêu thì không nhảy. Không có HUD.
 * Bật / tắt bằng ô trong menu hoặc phím tắt của tính năng này.
 */
public class AimAssistModule extends Module {
    private final SliderSetting range = add(new SliderSetting("aim_crit_range", "Tầm kích hoạt", 3.0, 2.0, 6.0, 0.1, " khối"));
    private final SliderSetting delay = add(new SliderSetting("aim_crit_delay", "Độ trễ giữa các lần nhảy", 1, 0, 10, 1, " tick"));
    private final BoolSetting onlyPlayers = add(new BoolSetting("aim_crit_only_players", "Chỉ người chơi", true));
    private final BoolSetting ignoreTeam = add(new BoolSetting("aim_ignore_team", "Bỏ qua đồng đội", true));

    /** Số tick client đã chạy; dùng làm đồng hồ cho cooldown, không phụ thuộc FPS. */
    private int tick;
    /** Còn coi là "đang đánh" tới hết tick này (bắt cú click rất nhanh, nhả chuột trong cùng một tick). */
    private int armedUntil = -100;
    private int lastJump = -100;

    public AimAssistModule() {
        super("aimassist", "Aim Assist", "Tự động nhảy khi đang đánh vào mục tiêu để ăn chí mạng. Không đánh thì không nhảy.", Category.PVP, false);
    }

    /**
     * Gọi mỗi khung hình (RenderTickEvent): ghi nhận cú bấm chuột trái ngay cả khi nhả ra trước khi hết tick,
     * để click nhanh vẫn được tính là đang đánh.
     */
    public void onRender(float pt) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.currentScreen == null && mc.gameSettings.keyBindAttack.isKeyDown()) {
            armedUntil = tick + 3;
        }
    }

    @Override
    public void onToggle() {
        armedUntil = -100;
    }

    @Override
    public void onTick() {
        tick++;
        if (!enabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        // Các điều kiện rẻ nhất kiểm tra trước; đa số tick sẽ thoát ngay ở đây.
        if (p == null || mc.theWorld == null || mc.currentScreen != null) return;
        if (!p.onGround) return;
        if (!(mc.gameSettings.keyBindAttack.isKeyDown() || tick <= armedUntil)) return;
        if (tick - lastJump <= delay.getInt()) return;
        // Người chơi tự bấm nhảy thì để game tự xử lý, không chen vào.
        if (mc.gameSettings.keyBindJump.isKeyDown()) return;

        // Những trạng thái không thể ăn chí mạng (hoặc nhảy vô nghĩa).
        if (p.isSpectator() || p.capabilities.isFlying || p.isInWater() || p.isInLava()
                || p.isOnLadder() || p.isRiding() || p.isPotionActive(Potion.blindness)) return;
        if (HypixelUtil.inLobby()) return;

        // Phải đang trỏ vào một sinh vật còn sống trong tầm.
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        Entity t = mop.entityHit;
        if (!(t instanceof EntityLivingBase) || t.isDead || t instanceof EntityArmorStand) return;
        if (((EntityLivingBase) t).getHealth() <= 0.0F) return;
        if (p.getDistanceToEntity(t) > range.getFloat()) return;
        if (t instanceof EntityPlayer) {
            EntityPlayer tp = (EntityPlayer) t;
            if (tp.isSpectator()) return;
            if (ignoreTeam.value && TeamUtil.isTeammate(p, tp)) return;
        } else if (onlyPlayers.value) {
            return;
        }

        // Trần quá thấp thì nhảy chỉ đập đầu, không có đà rơi để ăn crit.
        if (!mc.theWorld.getCollidingBoundingBoxes(p, p.getEntityBoundingBox().offset(0.0D, 1.0D, 0.0D)).isEmpty()) return;

        p.jump();
        lastJump = tick;
    }
}
