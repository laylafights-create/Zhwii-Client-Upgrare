package dev.lunarlite;

import dev.lunarlite.gui.HudEditorScreen;
import dev.lunarlite.gui.MenuScreen;
import dev.lunarlite.module.*;
import dev.lunarlite.module.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = LunarLite.MODID, name = LunarLite.NAME, version = LunarLite.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class LunarLite {
    public static final String MODID = "lunarlite";
    public static final String NAME = "Zhwii Client";
    public static final String VERSION = "2.0.0";

    public static final List<Module> MODULES = new ArrayList<Module>();
    public static KeyBinding keyMenu, keyZoom, keyAim, keyShake;

    // Rung block khi đào + tắt hạt vỡ block
    public static boolean blockShake = true;
    // Bỏ nền đen khi mở kho đồ / rương
    public static boolean cleanInv = true;
    private static final float INV_ANIM_MS = 150f;
    private static final float INV_ANIM_START = 0.85f;
    private static final float SHAKE = 0.05f; // độ rung, tăng/giảm tuỳ ý
    private final Random shakeRand = new Random();

    public static ZoomModule ZOOM;
    public static AimAssistModule AIM;
    public static BlockOutlineModule BLOCK_OUTLINE;
    public static EnemyDetectorModule ENEMY_DETECTOR;
    public static ScoreboardModule SCOREBOARD;
    public static BowTrajectoryModule BOW_TRAJECTORY;
    public static AnimationsModule ANIM;
    public static HotbarAnimationModule HOTBAR;
    public static ChatBarModule CHAT_BAR;

    private static Configuration config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        config = new Configuration(e.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        ZOOM = new ZoomModule();
        AIM = new AimAssistModule();
        BLOCK_OUTLINE = new BlockOutlineModule();
        ENEMY_DETECTOR = new EnemyDetectorModule();
        SCOREBOARD = new ScoreboardModule();
        BOW_TRAJECTORY = new BowTrajectoryModule();
        ANIM = new AnimationsModule();
        HOTBAR = new HotbarAnimationModule();
        CHAT_BAR = new ChatBarModule();

        // PvP / HUD
        MODULES.add(new FpsModule());
        MODULES.add(new CpsModule());
        MODULES.add(new KeystrokesModule());
        MODULES.add(ENEMY_DETECTOR);
        MODULES.add(BOW_TRAJECTORY);
        MODULES.add(new TargetInfoModule());
        MODULES.add(new ComboModule());
        MODULES.add(new KillBoardModule());
        MODULES.add(AIM);
        MODULES.add(new ArmorModule());
        MODULES.add(new PotionModule());
        MODULES.add(new ItemPickupModule());
        MODULES.add(new PingModule());
        MODULES.add(new ServerIpModule());
        MODULES.add(SCOREBOARD);
        // Utility / Visual
        MODULES.add(new ToggleSprintModule());
        MODULES.add(ZOOM);
        MODULES.add(new FullbrightModule());
        MODULES.add(BLOCK_OUTLINE);
        MODULES.add(new ItemPhysicsModule());
        MODULES.add(ANIM);
        MODULES.add(HOTBAR);
        MODULES.add(CHAT_BAR);
        MODULES.add(new ThemeModule());

        config.load();
        for (Module m : MODULES) m.load(config);
        blockShake = config.getBoolean("blockShake", "general", true, "Rung block khi đào và tắt hạt vỡ block");
        cleanInv = config.getBoolean("cleanInv", "general", true, "Bỏ nền đen khi mở kho đồ / rương");
        config.save();

        keyMenu = new KeyBinding("Mở menu Zhwii Client", Keyboard.KEY_RSHIFT, "Zhwii Client");
        keyZoom = new KeyBinding("Zoom", Keyboard.KEY_C, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyMenu);
        keyAim = new KeyBinding("Bật/tắt Aim Assist", Keyboard.KEY_R, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyZoom);
        ClientRegistry.registerKeyBinding(keyAim);
        keyShake = new KeyBinding("Bật/tắt rung block", Keyboard.KEY_NONE, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyShake);

        // Chat bar: chỉ tin người chơi + avatar/giường team + tự dịch tiếng Việt (cài đặt nằm trong menu)
        CHAT_BAR.register();

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    public static void saveConfig() {
        if (config == null) return;
        for (Module m : MODULES) m.save(config);
        config.get("general", "blockShake", true).set(blockShake);
        config.get("general", "cleanInv", true).set(cleanInv);
        config.save();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (keyMenu.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new MenuScreen());
        }
        if (keyAim.isPressed() && mc.currentScreen == null) {
            AIM.toggle();
            saveConfig();
        }
        if (keyShake.isPressed() && mc.currentScreen == null) {
            blockShake = !blockShake;
            saveConfig();
        }
        // thay EffectRenderer bằng bản có thể chặn hạt vỡ block
        if (mc.theWorld != null && mc.effectRenderer != null && !(mc.effectRenderer instanceof QuietEffects)) {
            mc.effectRenderer = new QuietEffects(mc.theWorld, mc.getTextureManager());
        }
        for (Module m : MODULES) m.onTick();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START && AIM != null) AIM.onRender(e.renderTickTime);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.buttonstate) CpsModule.click(e.button);
        if (e.dwheel != 0 && ZOOM != null && ZOOM.handleScroll(e.dwheel)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (e.entityPlayer == Minecraft.getMinecraft().thePlayer && e.target instanceof EntityLivingBase) {
            ComboModule.onAttack();
        }
    }

    /** Viền block đổi màu cầu vồng: huỷ viền gốc của game và tự vẽ viền mới. */
    @SubscribeEvent
    public void onBlockHighlight(DrawBlockHighlightEvent e) {
        if (BLOCK_OUTLINE != null && BLOCK_OUTLINE.enabled) BLOCK_OUTLINE.onHighlight(e);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (cleanInv && e.gui != null) {
            Minecraft mc = Minecraft.getMinecraft();
            if (e.gui.getClass() == GuiInventory.class && mc.thePlayer != null) {
                e.gui = new CleanInventory(mc.thePlayer);
            } else if (e.gui.getClass() == GuiChest.class && mc.thePlayer != null) {
                ContainerChest c = (ContainerChest) ((GuiChest) e.gui).inventorySlots;
                e.gui = new CleanChest(mc.thePlayer.inventory, c.getLowerChestInventory());
            }
        }
        AnimationsModule.guiOpened(e.gui);
    }

    /** Bắt đầu animation phóng to (nếu AnimationsModule không tự làm). Trả về {scale, cx, cy}; luôn phải popMatrix sau đó. */
    static float[] pushInvAnim(long start, int w, int h) {
        float s = 1f;
        if (ANIM == null || !ANIM.enabled) {
            float t = Math.min(1f, (System.currentTimeMillis() - start) / INV_ANIM_MS);
            float inv = 1f - t;
            s = INV_ANIM_START + (1f - INV_ANIM_START) * (1f - inv * inv * inv);
        }
        float cx = w / 2f, cy = h / 2f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.translate(-cx, -cy, 0);
        return new float[]{s, cx, cy};
    }

    /** Kho đồ không có nền đen. */
    public static class CleanInventory extends GuiInventory {
        private final long start = System.currentTimeMillis();
        public CleanInventory(EntityPlayer p) { super(p); }

        @Override
        public void drawDefaultBackground() { }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            float[] a = pushInvAnim(start, width, height);
            super.drawScreen((int) ((mx - a[1]) / a[0] + a[1]), (int) ((my - a[2]) / a[0] + a[2]), pt);
            GlStateManager.popMatrix();
        }
    }

    /** Rương không có nền đen. */
    public static class CleanChest extends GuiChest {
        private final long start = System.currentTimeMillis();
        public CleanChest(IInventory upper, IInventory lower) { super(upper, lower); }

        @Override
        public void drawDefaultBackground() { }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            float[] a = pushInvAnim(start, width, height);
            super.drawScreen((int) ((mx - a[1]) / a[0] + a[1]), (int) ((my - a[2]) / a[0] + a[2]), pt);
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void onGuiDrawPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        AnimationsModule.guiPre(e.gui);
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post e) {
        AnimationsModule.guiPost();
    }

    @SubscribeEvent
    public void onPlayerPre(RenderPlayerEvent.Pre e) {
        if (ENEMY_DETECTOR != null) ENEMY_DETECTOR.onPre(e);
    }

    @SubscribeEvent
    public void onPlayerPost(RenderPlayerEvent.Post e) {
        if (ENEMY_DETECTOR != null) ENEMY_DETECTOR.onPost(e);
    }

    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        if (BOW_TRAJECTORY != null) BOW_TRAJECTORY.render(e.partialTicks);
        if (ENEMY_DETECTOR != null) ENEMY_DETECTOR.renderMarkers(e.partialTicks);
        renderShake(e.partialTicks);
    }

    /** Vẽ bản sao của block đang đào, rung ngẫu nhiên quanh vị trí gốc. */
    private void renderShake(float partialTicks) {
        if (!blockShake) return;
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
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * partialTicks;
        double cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * partialTicks;
        double cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * partialTicks;

        double dx = (shakeRand.nextFloat() - 0.5f) * SHAKE;
        double dy = (shakeRand.nextFloat() - 0.5f) * SHAKE;
        double dz = (shakeRand.nextFloat() - 0.5f) * SHAKE;

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

    /** EffectRenderer chặn hạt khi đập / phá block (khi blockShake đang bật). */
    public static class QuietEffects extends EffectRenderer {
        public QuietEffects(World w, TextureManager t) { super(w, t); }

        @Override
        public void addBlockDestroyEffects(BlockPos pos, IBlockState state) {
            if (!blockShake) super.addBlockDestroyEffects(pos, state);
        }

        @Override
        public void addBlockHitEffects(BlockPos pos, EnumFacing side) {
            if (!blockShake) super.addBlockHitEffects(pos, side);
        }

        @Override
        public void addBlockHitEffects(BlockPos pos, MovingObjectPosition target) {
            if (!blockShake) super.addBlockHitEffects(pos, target);
        }
    }

    @SubscribeEvent
    public void onHotbar(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        if (HOTBAR != null && HOTBAR.shouldRender()) {
            e.setCanceled(true);
            HOTBAR.render(e.resolution, e.partialTicks);
        }
    }

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (SCOREBOARD != null && SCOREBOARD.enabled) SCOREBOARD.hideVanilla();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (SCOREBOARD != null) SCOREBOARD.restoreVanilla();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo || mc.currentScreen instanceof HudEditorScreen) return;
        for (Module m : MODULES) {
            if (m.enabled && m instanceof HudModule) ((HudModule) m).render(e.resolution, false);
        }
    }
}
