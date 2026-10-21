package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Hỗ trợ bridge nhanh (bridge lùi): tự ngồi ở mép block, tự chỉnh góc nhìn xuống mặt bên của block
 * đang đứng và tự đặt block khi tâm ngắm đã trúng. Không có HUD.
 */
public class BridgeModule extends Module {
    private final BoolSetting edgeCrouch = add(new BoolSetting("bridge_crouch", "Edge Crouch", true));
    private final BoolSetting aim = add(new BoolSetting("bridge_aim", "Bridge Aim", true));
    private final BoolSetting autoPlace = add(new BoolSetting("bridge_place", "Auto Place", true));
    private final BoolSetting onlyBlocks = add(new BoolSetting("bridge_blocks", "Only Holding Blocks", true));
    private final SliderSetting minPitch = add(new SliderSetting("bridge_min_pitch", "Min Pitch", 60, 40, 85, 1, "°"));
    private final SliderSetting smoothing = add(new SliderSetting("bridge_smooth", "Aim Smoothing", 0.5, 0.05, 1.0, 0.05, "x"));
    private final SliderSetting placeDelay = add(new SliderSetting("bridge_delay", "Place Delay", 1, 0, 4, 1, " tick"));

    private boolean forcingSneak;
    private int cooldown;
    private long lastNano, lastAim;
    private float yawVel, pitchVel;

    public BridgeModule() {
        super("bridge", "Bridge Assist", "Hỗ trợ bridge nhanh: tự ngồi ở mép, chỉnh góc ngắm và đặt block.", Category.PVP, false);
    }

    /** Thông tin tư thế bridge hiện tại (null nếu không ở mép block). */
    private static final class Pose {
        BlockPos support;     // block đang đứng ở mép
        EnumFacing dir;       // hướng bridge kéo dài (phía sau lưng)
        double beyond;        // mắt nhô ra khỏi mép bao nhiêu khối
        double dy;            // độ cao mặt bên mục tiêu so với mắt (âm)
    }

    private boolean active(Minecraft mc, EntityPlayerSP p) {
        if (!enabled || mc.theWorld == null || p == null || mc.currentScreen != null || p.isSpectator()) return false;
        if (onlyBlocks.value) {
            ItemStack h = p.getHeldItem();
            if (h == null || !(h.getItem() instanceof ItemBlock)) return false;
        }
        return true;
    }

    private Pose pose(Minecraft mc, EntityPlayerSP p, float pt) {
        EnumFacing dir = p.getHorizontalFacing().getOpposite();
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        // Block đang đứng: lùi vào phía đảo 0.3 khối so với tâm để vẫn tìm được khi người nhô ra mép.
        BlockPos sup = new BlockPos(MathHelper.floor_double(px - dir.getFrontOffsetX() * 0.3D),
                MathHelper.floor_double(py - 0.01D), MathHelper.floor_double(pz - dir.getFrontOffsetZ() * 0.3D));
        if (mc.theWorld.getBlockState(sup).getBlock().getMaterial().isReplaceable()) return null;
        if (!mc.theWorld.isAirBlock(sup.offset(dir))) return null;

        double ex = px, ey = py + p.getEyeHeight(), ez = pz;
        double plane = dir.getFrontOffsetX() != 0
                ? sup.getX() + (dir.getFrontOffsetX() > 0 ? 1.0D : 0.0D)
                : sup.getZ() + (dir.getFrontOffsetZ() > 0 ? 1.0D : 0.0D);
        double beyond = dir.getFrontOffsetX() != 0 ? (ex - plane) * dir.getFrontOffsetX() : (ez - plane) * dir.getFrontOffsetZ();
        Pose o = new Pose();
        o.support = sup;
        o.dir = dir;
        o.beyond = beyond;
        o.dy = (sup.getY() + 0.75D) - ey;
        return o;
    }

    /** Mỗi client tick (END): ngồi ở mép + đặt block. */
    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (cooldown > 0) cooldown--;
        if (!active(mc, p)) { releaseSneak(mc); return; }

        // 1) Ngồi ở mép: nếu bước tiếp theo sẽ rơi khỏi block thì giữ shift.
        if (edgeCrouch.value && p.onGround && !p.capabilities.isFlying) {
            boolean edge = mc.theWorld.getCollidingBoundingBoxes(p,
                    p.getEntityBoundingBox().offset(p.motionX * 3.0D, -0.5D, p.motionZ * 3.0D)).isEmpty();
            if (edge) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                forcingSneak = true;
            } else releaseSneak(mc);
        } else releaseSneak(mc);

        // 2) Đặt block khi tâm ngắm đang trúng mặt bên (hướng bridge) của block đang đứng.
        if (!autoPlace.value || cooldown > 0 || p.getHeldItem() == null || p.getHeldItem().stackSize <= 0) return;
        if (p.rotationPitch < minPitch.getFloat() - 5.0F) return;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos hit = mop.getBlockPos();
        if (mop.sideHit.getAxis().isVertical() || hit.getY() >= MathHelper.floor_double(p.posY)) return; // chỉ kéo dài cầu ở mức chân
        if (!mc.theWorld.isAirBlock(hit.offset(mop.sideHit))) return;
        if (mc.playerController.onPlayerRightClick(p, mc.theWorld, p.getHeldItem(), hit, mop.sideHit, mop.hitVec)) {
            p.swingItem();
            cooldown = placeDelay.getInt();
        }
    }

    /** Mỗi khung hình: chỉnh pitch (và yaw về trục gần nhất) cho tâm ngắm trúng mặt bên của block. */
    public void onRender(float pt) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016F : Math.min(0.1F, (now - lastNano) / 1.0E9F);
        lastNano = now;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (!aim.value || !active(mc, p) || !p.onGround) return;
        if (p.rotationPitch < minPitch.getFloat() - 10.0F) return; // chỉ khi đang nhìn xuống kiểu bridge

        Pose o = pose(mc, p, pt);
        if (o == null || o.beyond < 0.02D || o.beyond > 0.4D) { yawVel = 0F; pitchVel = 0F; return; }
        if (now - lastAim > 150000000L) { yawVel = 0F; pitchVel = 0F; }
        lastAim = now;

        float wantPitch = MathHelper.clamp_float((float) (Math.atan2(-o.dy, o.beyond) * 180.0D / Math.PI), minPitch.getFloat(), 89.0F);
        float curPitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        float curYaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;
        float wantYaw = o.dir.getOpposite().getHorizontalIndex() * 90.0F;
        float yawDiff = MathHelper.wrapAngleTo180_float(wantYaw - curYaw);
        if (Math.abs(yawDiff) > 25.0F) { yawDiff = 0F; yawVel = 0F; } // lệch quá thì để người chơi tự xoay

        float smoothTime = 0.04F + (1.0F - smoothing.getFloat()) * 0.40F;
        float dyaw = damp(yawDiff, true, smoothTime, 600.0F, dt);
        float dpitch = damp(wantPitch - curPitch, false, smoothTime, 600.0F, dt);
        if (Math.abs(dyaw) < 0.002F && Math.abs(dpitch) < 0.002F) return;

        p.rotationYaw += dyaw;
        p.prevRotationYaw += dyaw;
        float newPitch = MathHelper.clamp_float(p.rotationPitch + dpitch, -90.0F, 90.0F);
        p.prevRotationPitch += newPitch - p.rotationPitch;
        p.rotationPitch = newPitch;
    }

    /** Một bước SmoothDamp cho một trục (cùng thuật toán với Aim Assist). */
    private float damp(float diff, boolean isYaw, float smoothTime, float maxSpeed, float dt) {
        float omega = 2.0F / Math.max(0.0001F, smoothTime);
        float x = omega * dt;
        float exp = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        float vel = isYaw ? yawVel : pitchVel;
        float maxChange = maxSpeed * smoothTime;
        float change = Math.max(-maxChange, Math.min(maxChange, -diff));
        float temp = (vel + omega * change) * dt;
        vel = (vel - omega * temp) * exp;
        float step = -change + (change + temp) * exp;
        if ((diff > 0F && step > diff) || (diff < 0F && step < diff)) { step = diff; vel = 0F; }
        if (isYaw) yawVel = vel; else pitchVel = vel;
        return step;
    }

    private void releaseSneak(Minecraft mc) {
        if (!forcingSneak) return;
        forcingSneak = false;
        KeyBinding kb = mc.gameSettings.keyBindSneak;
        int c = kb.getKeyCode();
        boolean physical = c < 0 ? Mouse.isButtonDown(c + 100) : Keyboard.isKeyDown(c);
        KeyBinding.setKeyBindState(c, physical);
    }

    @Override
    public void onToggle() {
        if (!enabled) releaseSneak(Minecraft.getMinecraft());
        yawVel = 0F;
        pitchVel = 0F;
    }
}
