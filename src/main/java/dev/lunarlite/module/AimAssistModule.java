package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

/** Tự động xoay camera (có làm mượt) về phía người chơi gần nhất. Không có HUD. */
public class AimAssistModule extends Module {
    private final SliderSetting range = add(new SliderSetting("aim_range", "Max Range", 6, 2, 12, 0.5, " khối"));
    private final SliderSetting fov = add(new SliderSetting("aim_fov", "Aim FOV", 120, 10, 360, 5, "°"));
    private final SliderSetting smoothing = add(new SliderSetting("aim_smooth", "Aim Smoothing", 0.25, 0.05, 1.0, 0.05, "x"));
    private final SliderSetting maxStep = add(new SliderSetting("aim_max_step", "Max Turn Speed", 12, 1, 60, 1, "°"));
    private final BoolSetting onlyClicking = add(new BoolSetting("aim_only_click", "Only While Attacking", false));
    private final BoolSetting ignoreTeam = add(new BoolSetting("aim_ignore_team", "Ignore Teammates", true));
    private final BoolSetting ignoreInvisible = add(new BoolSetting("aim_ignore_invisible", "Ignore Invisible", true));

    public AimAssistModule() {
        super("aimassist", "Aim Assist", "Tự động aim vào kẻ địch gần nhất (bỏ qua đồng đội).", Category.PVP, false);
    }

    private long lastNano;

    /** Gọi mỗi khung hình (RenderTickEvent) để xoay mượt, không phụ thuộc FPS. */
    public void onRender(float pt) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016F : Math.min(0.1F, (now - lastNano) / 1.0E9F);
        lastNano = now;

        if (!enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return;
        if (onlyClicking.value && !mc.gameSettings.keyBindAttack.isKeyDown()) return;
        // Hypixel: ở sảnh chỉ có NPC / người chờ, và người xem (spectator) không cần aim
        if (mc.thePlayer.isSpectator() || HypixelUtil.inLobby()) return;

        EntityPlayer target = findTarget(mc);
        if (target == null) return;

        // Vị trí nội suy theo partialTicks cho khớp hình ảnh trên màn hình.
        double ex = lerp(mc.thePlayer.lastTickPosX, mc.thePlayer.posX, pt);
        double ey = lerp(mc.thePlayer.lastTickPosY, mc.thePlayer.posY, pt) + mc.thePlayer.getEyeHeight();
        double ez = lerp(mc.thePlayer.lastTickPosZ, mc.thePlayer.posZ, pt);
        double dx = lerp(target.lastTickPosX, target.posX, pt) - ex;
        double dy = lerp(target.lastTickPosY, target.posY, pt) + target.height * 0.85D - ey;
        double dz = lerp(target.lastTickPosZ, target.posZ, pt) - ez;

        float wantYaw = yawTo(dx, dz);
        float wantPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);

        float curYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * pt;
        float curPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * pt;
        float yawDiff = MathHelper.wrapAngleTo180_float(wantYaw - curYaw);
        float pitchDiff = wantPitch - curPitch;

        // Hệ số mượt độc lập FPS + giới hạn tốc độ xoay theo thời gian.
        float k = 1.0F - (float) Math.pow(1.0D - smoothing.getFloat(), dt * 20.0F);
        float limit = maxStep.getFloat() * dt * 20.0F;
        float dyaw = limit(ease(yawDiff) * k, limit);
        float dpitch = limit(ease(pitchDiff) * k, limit);
        if (Math.abs(dyaw) < 0.01F && Math.abs(dpitch) < 0.01F) return;

        // Cộng cùng một lượng vào cả giá trị trước đó để camera không bị giật.
        mc.thePlayer.rotationYaw += dyaw;
        mc.thePlayer.prevRotationYaw += dyaw;
        float newPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + dpitch, -90.0F, 90.0F);
        mc.thePlayer.prevRotationPitch += newPitch - mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationPitch = newPitch;
    }

    /** Gần đúng hướng thì giảm tốc dần (ease-out), tránh rung quanh mục tiêu. */
    private static float ease(float diff) {
        float a = Math.abs(diff);
        float f = a >= 8.0F ? 1.0F : 0.35F + 0.65F * (a / 8.0F);
        return diff * f;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private EntityPlayer findTarget(Minecraft mc) {
        EntityPlayer best = null;
        double bestDist = range.get();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.getHealth() <= 0.0F || p.deathTime > 0) continue;
            if (ignoreInvisible.value && p.isInvisible()) continue;

            // Kiểm tra rẻ trước (khoảng cách, FOV), kiểm tra đắt sau (tab list, team).
            double dist = mc.thePlayer.getDistanceToEntity(p);
            if (dist > bestDist) continue;

            float yaw = yawTo(p.posX - mc.thePlayer.posX, p.posZ - mc.thePlayer.posZ);
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw));
            if (diff > fov.getFloat() / 2.0F) continue;

            if (p.isSpectator()) continue;
            // NPC / bot Hypixel (không có trong tab list, UUID không phải v4)
            if (!HypixelUtil.isRealPlayer(p)) continue;
            // Đồng đội: TeamUtil (giáp da nhuộm -> ký hiệu team ngắn -> scoreboard team)
            if (ignoreTeam.value && TeamUtil.isTeammate(mc.thePlayer, p)) continue;

            bestDist = dist;
            best = p;
        }
        return best;
    }

    private static float yawTo(double dx, double dz) {
        return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
    }

    private static float limit(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }
}
