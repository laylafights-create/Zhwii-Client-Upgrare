package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockTNT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
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
    private final BoolSetting autoRefill = add(new BoolSetting("bridge_refill", "Auto Refill Blocks", true));
    private final BoolSetting onlyBlocks = add(new BoolSetting("bridge_blocks", "Only Holding Blocks", true));
    private final SliderSetting minPitch = add(new SliderSetting("bridge_min_pitch", "Min Pitch", 60, 40, 85, 1, "°"));
    private final SliderSetting smoothing = add(new SliderSetting("bridge_smooth", "Aim Smoothing", 0.5, 0.05, 1.0, 0.05, "x"));
    private final SliderSetting placeDelay = add(new SliderSetting("bridge_delay", "Place Delay", 1, 0, 4, 1, " tick"));
    private final BoolSetting climbUp = add(new BoolSetting("bridge_climb", "Jump Climb (đặt lên cao)", true));

    private boolean forcingSneak;
    private int blockSlot = -1; // ô hotbar vừa cầm block (để biết khi nào hết block)
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
        double dyTop, dyBot;  // mép trên / mép dưới của mặt bên so với mắt
        boolean climb;        // block phía sau đã có -> nhắm mặt TRÊN của nó để đặt lên cao
        double climbH;        // mắt cao hơn mặt trên block đó bao nhiêu (số âm = mắt ở trên)
    }

    /** Chỉ coi là đang bridge khi giữ phím lùi (S). */
    private boolean bridging(Minecraft mc) {
        return mc.gameSettings.keyBindBack.isKeyDown();
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
        int fx = MathHelper.floor_double(px - dir.getFrontOffsetX() * 0.3D);
        int fz = MathHelper.floor_double(pz - dir.getFrontOffsetZ() * 0.3D);
        int fy = MathHelper.floor_double(py - 0.01D);
        // Đang nhảy: block đứng nằm thấp hơn chân, nên quét xuống tối đa 3 ô để tìm.
        int maxDrop = p.onGround ? 0 : 3;
        BlockPos sup = null;
        for (int i = 0; i <= maxDrop; i++) {
            BlockPos c = new BlockPos(fx, fy - i, fz);
            if (!mc.theWorld.getBlockState(c).getBlock().getMaterial().isReplaceable()) { sup = c; break; }
        }
        if (sup == null) return null;
        BlockPos back = sup.offset(dir);
        boolean climb = false;
        if (!mc.theWorld.isAirBlock(back)) {
            // Đã có block phía sau: chỉ cho "leo" khi đang nhảy và phía trên block đó còn trống.
            boolean solid = !mc.theWorld.getBlockState(back).getBlock().getMaterial().isReplaceable();
            if (!solid || !climbUp.value || p.onGround || !mc.theWorld.isAirBlock(back.up())) return null;
            climb = true;
        }

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
        o.dyTop = (sup.getY() + 0.95D) - ey;
        o.dyBot = (sup.getY() + 0.25D) - ey;
        o.climb = climb;
        o.climbH = (back.getY() + 1.0D) - ey;
        return o;
    }

    /** Block dùng để bridge: khối đặc, không rơi, không phải rương/TNT. */
    private static boolean usable(ItemStack s) {
        if (s == null || s.stackSize <= 0 || !(s.getItem() instanceof ItemBlock)) return false;
        Block b = ((ItemBlock) s.getItem()).getBlock();
        return b.isFullCube() && !(b instanceof BlockFalling) && !(b instanceof BlockContainer) && !(b instanceof BlockTNT);
    }

    /** Tay vừa hết block (đang giữ S) thì lấy block khác ra. */
    private void tickRefill(Minecraft mc, EntityPlayerSP p) {
        if (!autoRefill.value || !enabled || mc.theWorld == null || p == null
                || mc.currentScreen != null || p.isSpectator()) return;
        ItemStack h = p.getHeldItem();
        int cur = p.inventory.currentItem;
        if (h != null && h.stackSize > 0) {
            blockSlot = h.getItem() instanceof ItemBlock ? cur : -1;
            return;
        }
        // Tay trống: chỉ refill nếu ô này vừa cầm block và người chơi đang bridge (giữ S).
        if (blockSlot != cur || !bridging(mc)) return;
        blockSlot = refill(mc, p) ? p.inventory.currentItem : -1;
    }

    /** Ưu tiên hotbar (đổi ô), nếu không có thì kéo từ túi đồ vào ô đang cầm. */
    private boolean refill(Minecraft mc, EntityPlayerSP p) {
        ItemStack[] inv = p.inventory.mainInventory;
        int best = -1;
        for (int i = 0; i < 9; i++)
            if (usable(inv[i]) && (best < 0 || inv[i].stackSize > inv[best].stackSize)) best = i;
        if (best >= 0) { p.inventory.currentItem = best; return true; }
        for (int i = 9; i < 36; i++)
            if (usable(inv[i]) && (best < 0 || inv[i].stackSize > inv[best].stackSize)) best = i;
        if (best < 0) return false;
        // mode 2 = hoán đổi ô túi đồ với ô hotbar đang cầm
        mc.playerController.windowClick(p.inventoryContainer.windowId, best, p.inventory.currentItem, 2, p);
        return true;
    }

    /** Mỗi client tick (END): ngồi ở mép + đặt block. */
    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (cooldown > 0) cooldown--;
        tickRefill(mc, p);
        if (!active(mc, p)) { releaseSneak(mc); return; }

        // 1) Ngồi ở mép: nếu bước tiếp theo sẽ rơi khỏi block thì giữ shift.
        if (edgeCrouch.value && bridging(mc) && p.onGround && !p.capabilities.isFlying) {
            boolean edge = mc.theWorld.getCollidingBoundingBoxes(p,
                    p.getEntityBoundingBox().offset(p.motionX * 3.0D, -0.5D, p.motionZ * 3.0D)).isEmpty();
            if (edge) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
                forcingSneak = true;
            } else releaseSneak(mc);
        } else releaseSneak(mc);

        // 2) Đặt block khi tâm ngắm đang trúng mặt bên (hướng bridge) của block đang đứng.
        if (!autoPlace.value || !bridging(mc) || cooldown > 0 || p.getHeldItem() == null || p.getHeldItem().stackSize <= 0) return;
        if (p.rotationPitch < minPitch.getFloat() - 5.0F) return;
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        BlockPos hit = mop.getBlockPos();
        EnumFacing side = mop.sideHit;
        if (side.getAxis().isVertical()) {
            // Mặt trên chỉ dùng để leo: đang nhảy + bật Jump Climb + đặt lên block thấp hơn chân.
            if (side != EnumFacing.UP || p.onGround || !climbUp.value) return;
        }
        if (hit.getY() >= MathHelper.floor_double(p.posY)) return; // chỉ kéo dài cầu ở mức chân
        BlockPos target = hit.offset(side);
        if (!mc.theWorld.isAirBlock(target)) return;
        // Chưa lên tới đỉnh nhảy thì block mới còn dính vào người -> chờ, không gửi gói vô ích.
        AxisAlignedBB cell = new AxisAlignedBB(target.getX(), target.getY(), target.getZ(),
                target.getX() + 1.0D, target.getY() + 1.0D, target.getZ() + 1.0D);
        if (p.getEntityBoundingBox().intersectsWith(cell)) return;
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
        if (!aim.value || !active(mc, p)) return;
        if (p.capabilities.isFlying || p.isInWater() || p.isOnLadder()) return;
        boolean air = !p.onGround;
        if (!bridging(mc)) { yawVel = 0F; pitchVel = 0F; return; } // không giữ S = không bridge
        if (p.rotationPitch < minPitch.getFloat() - 3.0F) return; // chỉ khi đã cúi đầu đúng kiểu bridge

        Pose o = pose(mc, p, pt);
        double maxBeyond = o != null && o.climb ? 1.2D : (air ? 0.7D : 0.4D); // đang nhảy thì người lùi ra xa mép hơn
        if (o == null || o.beyond < 0.02D || o.beyond > maxBeyond) { yawVel = 0F; pitchVel = 0F; return; }
        if (now - lastAim > 150000000L) { yawVel = 0F; pitchVel = 0F; }
        lastAim = now;

        float curPitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        float wantPitch;
        if (o.climb) {
            // Nhắm xuống MẶT TRÊN của block vừa đặt (phía sau mép). Tia nhìn đi về phía trước nên chỉ trúng
            // phần mặt trên nằm giữa mép và mắt: t = khoảng cách từ mép tới điểm trúng.
            double h = -o.climbH; // mắt cao hơn mặt trên (dương)
            double tNear = 0.05D;
            double tFar = Math.min(0.95D, o.beyond * 0.9D);
            float pLow = (float) (Math.atan2(h, Math.max(0.02D, o.beyond - tNear)) * 180.0D / Math.PI);
            float pHigh = (float) (Math.atan2(h, Math.max(0.02D, o.beyond - tFar)) * 180.0D / Math.PI);
            wantPitch = MathHelper.clamp_float(curPitch, pLow, pHigh);
        } else if (air) {
            // Mắt đang lên/xuống nhanh: chỉ cần tâm ngắm nằm trong mặt bên, không cần về đúng tâm.
            float pTop = (float) (Math.atan2(-o.dyTop, o.beyond) * 180.0D / Math.PI);
            float pBot = (float) (Math.atan2(-o.dyBot, o.beyond) * 180.0D / Math.PI);
            wantPitch = MathHelper.clamp_float(curPitch, pTop, pBot);
        } else {
            wantPitch = (float) (Math.atan2(-o.dy, o.beyond) * 180.0D / Math.PI);
        }
        wantPitch = MathHelper.clamp_float(wantPitch, minPitch.getFloat(), 89.0F);
        float curYaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;
        float wantYaw = o.dir.getOpposite().getHorizontalIndex() * 90.0F;
        float yawDiff = MathHelper.wrapAngleTo180_float(wantYaw - curYaw);
        if (Math.abs(yawDiff) > 25.0F) { yawDiff = 0F; yawVel = 0F; } // lệch quá thì để người chơi tự xoay

        float smoothTime = 0.04F + (1.0F - smoothing.getFloat()) * 0.40F;
        if (air) smoothTime *= 0.45F; // cú nhảy chỉ ~0.5s nên phải bám nhanh hơn
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
        blockSlot = -1;
    }
}
