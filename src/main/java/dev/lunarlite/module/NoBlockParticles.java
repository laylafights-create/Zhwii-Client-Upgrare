package vn.noparticles;

import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = "noblockparticles", name = "NoBlockParticles", version = "1.1", clientSideOnly = true)
public class NoBlockParticles {

    private static final float SHAKE = 0.05f; // do rung, tang/giam tuy y
    private final Random rand = new Random();

    @EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && mc.effectRenderer != null
                && !(mc.effectRenderer instanceof Quiet)) {
            mc.effectRenderer = new Quiet(mc.theWorld, mc.getTextureManager());
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (!mc.playerController.getIsHittingBlock()) return;

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        BlockPos pos = mop.getBlockPos();
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (state.getBlock().getMaterial().isReplaceable()) return;

        Entity v = mc.getRenderViewEntity();
        double pt = e.partialTicks;
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
        double cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt;
        double cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;

        double dx = (rand.nextFloat() - 0.5f) * SHAKE;
        double dy = (rand.nextFloat() - 0.5f) * SHAKE;
        double dz = (rand.nextFloat() - 0.5f) * SHAKE;

        GlStateManager.pushMatrix();
        GlStateManager.translate(pos.getX() - cx + dx, pos.getY() - cy + dy, pos.getZ() - cz + dz);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.disableStandardItemLighting();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.BLOCK);
        wr.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
        mc.getBlockRendererDispatcher().renderBlock(state, pos, mc.theWorld, wr);
        wr.setTranslation(0, 0, 0);
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static class Quiet extends EffectRenderer {
        public Quiet(World w, TextureManager t) { super(w, t); }

        @Override
        public void addBlockDestroyEffects(BlockPos pos, IBlockState state) { }

        @Override
        public void addBlockHitEffects(BlockPos pos, EnumFacing side) { }

        @Override
        public void addBlockHitEffects(BlockPos pos, MovingObjectPosition target) { }
    }
}
