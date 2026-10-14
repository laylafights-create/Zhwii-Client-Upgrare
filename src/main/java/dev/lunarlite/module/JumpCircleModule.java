package dev.lunarlite.module;

import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;

/** Bụi tung lên dưới chân khi nhảy / tiếp đất. Bụi lấy theo loại block đang đứng (đất, cỏ, đá...). */
public class JumpCircleModule extends Module {
    private final ModeSetting trigger = add(new ModeSetting("trigger", "Trigger", 0, "Jump", "Land", "Both"));
    private final ModeSetting style = add(new ModeSetting("dust_style", "Dust Type", 0, "Block Dust", "Cloud"));
    private final SliderSetting amount = add(new SliderSetting("dust_amount", "Amount", 14, 4, 40, 1, ""));
    private final SliderSetting spread = add(new SliderSetting("dust_spread", "Spread", 1.0, 0.4, 2.5, 0.1, "x"));

    private final Random rng = new Random();
    private boolean wasOnGround = true;
    private double lastMotionY;

    public JumpCircleModule() {
        super("jumpcircle", "Jump Dust", "Bụi tung lên dưới chân khi nhảy / tiếp đất.", Category.EFFECT, false);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return;
        if (!enabled) { wasOnGround = p.onGround; lastMotionY = p.motionY; return; }
        boolean jump = !trigger.is("Land"), land = !trigger.is("Jump");
        if (jump && wasOnGround && !p.onGround && p.motionY > 0.0) puff(p, 1.0);
        if (land && !wasOnGround && p.onGround) {
            // rơi càng mạnh bụi càng nhiều (tối đa gấp rưỡi)
            puff(p, Math.min(1.5, 0.6 + Math.abs(lastMotionY) * 1.2));
        }
        wasOnGround = p.onGround;
        lastMotionY = p.motionY;
    }

    private void puff(EntityPlayer p, double power) {
        Minecraft mc = Minecraft.getMinecraft();
        BlockPos below = new BlockPos(p.posX, p.posY - 0.5, p.posZ);
        IBlockState st = mc.theWorld.getBlockState(below);
        boolean solid = st.getBlock().getMaterial().isSolid();
        boolean useBlock = solid && style.is("Block Dust");
        int n = Math.max(2, (int) Math.round(amount.getInt() * power));
        double s = spread.get();
        for (int i = 0; i < n; i++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double dist = 0.15 + rng.nextDouble() * 0.3;                 // xuất phát quanh chân
            double x = p.posX + Math.cos(ang) * dist, z = p.posZ + Math.sin(ang) * dist, y = p.posY + 0.05;
            double sp = (0.05 + rng.nextDouble() * 0.1) * s;              // bay toả ra ngoài, hơi bốc lên
            double vx = Math.cos(ang) * sp, vz = Math.sin(ang) * sp, vy = 0.02 + rng.nextDouble() * 0.08;
            if (useBlock) {
                mc.theWorld.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y, z, vx, vy, vz, Block.getStateId(st));
            } else {
                mc.theWorld.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, vx * 0.6, vy * 0.6, vz * 0.6);
            }
        }
    }

    /** Giữ lại để LunarLite gọi mỗi khung hình; module này dùng hạt của game nên không cần vẽ gì. */
    public void render(float pt) {}
}
