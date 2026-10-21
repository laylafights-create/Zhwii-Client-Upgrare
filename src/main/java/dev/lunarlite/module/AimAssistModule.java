package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

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

    private long lastNano, lastAim;
    // Trạng thái làm mượt (SmoothDamp): vận tốc góc hiện tại (độ / giây) và mục tiêu đang bám.
    private float yawVel, pitchVel;
    private EntityPlayer current;

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
        if (target == null) { yawVel = 0F; pitchVel = 0F; return; }
        if (now - lastAim > 150000000L) { yawVel = 0F; pitchVel = 0F; } // vừa bật lại -> không giật
        lastAim = now;

        // Vị trí nội suy theo partialTicks cho khớp hình ảnh trên màn hình.
        double ex = lerp(mc.thePlayer.lastTickPosX, mc.thePlayer.posX, pt);
        double ey = lerp(mc.thePlayer.lastTickPosY, mc.thePlayer.posY, pt) + mc.thePlayer.getEyeHeight();
        double ez = lerp(mc.thePlayer.lastTickPosZ, mc.thePlayer.posZ, pt);
        double tx = lerp(target.lastTickPosX, target.posX, pt);
        double ty = lerp(target.lastTickPosY, target.posY, pt);
        double tz = lerp(target.lastTickPosZ, target.posZ, pt);

        // Bù chuyển động: ngắm trước một chút theo vận tốc của địch (đỡ bị tụt lại khi địch chạy ngang).
        tx += (target.posX - target.lastTickPosX) * 0.8D;
        tz += (target.posZ - target.lastTickPosZ) * 0.8D;

        double h = target.height;
        double headY = ty + h * 0.9D;
        double chestY = ty + h * 0.6D;
        double feetY = ty + 0.25D;

        // Điểm ngắm mặc định: điểm gần mắt nhất trong khoảng thân địch -> không phải ngước/cúi liên tục khi cùng độ cao.
        double aimY = Math.max(ty + h * 0.35D, Math.min(headY, ey));

        // Thân trên (đầu + ngực) bị che mà chân vẫn thấy -> aim xuống chân
        boolean upperVisible = canSee(mc, ex, ey, ez, tx, headY, tz)
                || canSee(mc, ex, ey, ez, tx, chestY, tz);
        if (!upperVisible && canSee(mc, ex, ey, ez, tx, feetY, tz)) {
            aimY = feetY;
        }

        double dx = tx - ex, dy = aimY - ey, dz = tz - ez;

        float wantYaw = yawTo(dx, dz);
        float wantPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);

        float curYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * pt;
        float curPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * pt;
        float yawDiff = MathHelper.wrapAngleTo180_float(wantYaw - curYaw);
        float pitchDiff = wantPitch - curPitch;

        // SmoothDamp (lò xo tắt dần tới hạn): tăng tốc êm, giảm tốc êm, không rung, độc lập FPS.
        float smoothTime = 0.04F + (1.0F - smoothing.getFloat()) * 0.40F;
        float maxSpeed = maxStep.getFloat() * 20.0F; // độ / giây
        float dyaw = damp(yawDiff, true, smoothTime, maxSpeed, dt);
        float dpitch = damp(pitchDiff, false, smoothTime, maxSpeed, dt);
        if (Math.abs(dyaw) < 0.002F && Math.abs(dpitch) < 0.002F) return;

        // Cộng cùng một lượng vào cả giá trị trước đó để camera không bị giật.
        mc.thePlayer.rotationYaw += dyaw;
        mc.thePlayer.prevRotationYaw += dyaw;
        float newPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + dpitch, -90.0F, 90.0F);
        mc.thePlayer.prevRotationPitch += newPitch - mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationPitch = newPitch;
    }

    /** Một bước SmoothDamp cho một trục; trả về lượng xoay (độ) trong khung hình này. */
    private float damp(float diff, boolean isYaw, float smoothTime, float maxSpeed, float dt) {
        float omega = 2.0F / Math.max(0.0001F, smoothTime);
        float x = omega * dt;
        float exp = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        float vel = isYaw ? yawVel : pitchVel;
        float maxChange = maxSpeed * smoothTime;
        float change = Math.max(-maxChange, Math.min(maxChange, -diff)); // target - current = diff
        float temp = (vel + omega * change) * dt;
        vel = (vel - omega * temp) * exp;
        // Unity SmoothDamp với current = 0, target = diff  =>  bước = output - 0
        float step = -change + (change + temp) * exp;
        // Không vượt quá mục tiêu (chống overshoot)
        if ((diff > 0F && step > diff) || (diff < 0F && step < diff)) { step = diff; vel = 0F; }
        if (isYaw) yawVel = vel; else pitchVel = vel;
        return step;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    /** true nếu đường thẳng từ mắt tới điểm đó không bị khối nào chắn. */
    private static boolean canSee(Minecraft mc, double ex, double ey, double ez,
                                  double x, double y, double z) {
        return mc.theWorld.rayTraceBlocks(new Vec3(ex, ey, ez), new Vec3(x, y, z),
                false, true, false) == null;
    }

    private EntityPlayer findTarget(Minecraft mc) {
        EntityPlayer best = null;
        double bestDist = range.get();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.getHealth() <= 0.0F || p.deathTime > 0) continue;
            if (ignoreInvisible.value && p.isInvisible()) continue;

            // Kiểm tra rẻ trước (khoảng cách, FOV), kiểm tra đắt sau (tab list, team).
            // Bám mục tiêu cũ: được ưu tiên 1.5 khối -> không nhảy qua lại giữa 2 người ở gần nhau.
            double dist = mc.thePlayer.getDistanceToEntity(p) - (p == current ? 1.5D : 0.0D);
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
        current = best;
        return best;
    }

    private static float yawTo(double dx, double dz) {
        return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
    }

    private static float limit(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }
}
