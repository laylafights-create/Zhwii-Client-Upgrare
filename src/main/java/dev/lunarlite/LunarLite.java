package dev.lunarlite;

import dev.lunarlite.gui.HudEditorScreen;
import dev.lunarlite.gui.MenuScreen;
import dev.lunarlite.module.*;
import dev.lunarlite.module.HideArmorModule;
import dev.lunarlite.module.Module;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
    public static KeyBinding keyMenu, keyZoom, keyAim, keyShake, keyBridge, keyVelocity;

    // Rung block khi đào + tắt hạt vỡ block
    public static boolean blockShake = true;
    // Bỏ nền đen khi mở kho đồ / rương
    public static boolean cleanInv = true;
    private static final float SHAKE = 0.05f; // độ rung, tăng/giảm tuỳ ý
    private final Random shakeRand = new Random();

    public static ZoomModule ZOOM;
    public static AimAssistModule AIM;
    public static VelocityModule VELOCITY;
    public static BridgeModule BRIDGE;
    public static HitEffectModule HIT_FX;
    public static BlockOutlineModule BLOCK_OUTLINE;
    public static EnemyDetectorModule ENEMY_DETECTOR;
    public static ScoreboardModule SCOREBOARD;
    public static BowTrajectoryModule BOW_TRAJECTORY;
    public static AnimationsModule ANIM;
    public static HotbarAnimationModule HOTBAR;
    public static ChatBarModule CHAT_BAR;
    public static HideArmorModule HIDE_ARMOR;
    public static LodModule LOD;

    private static Configuration config;
    private static File configFile;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        configFile = e.getSuggestedConfigurationFile();
        config = new Configuration(configFile);
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        ZOOM = new ZoomModule();
        AIM = new AimAssistModule();
        VELOCITY = new VelocityModule();
        BRIDGE = new BridgeModule();
        HIT_FX = new HitEffectModule();
        BLOCK_OUTLINE = new BlockOutlineModule();
        ENEMY_DETECTOR = new EnemyDetectorModule();
        SCOREBOARD = new ScoreboardModule();
        BOW_TRAJECTORY = new BowTrajectoryModule();
        ANIM = new AnimationsModule();
        HOTBAR = new HotbarAnimationModule();
        CHAT_BAR = new ChatBarModule();
        HIDE_ARMOR = new HideArmorModule();
        LOD = new LodModule();

        // PvP / HUD
        MODULES.add(new FpsModule());
        MODULES.add(new CpsModule());
        MODULES.add(new KeystrokesModule());
        MODULES.add(ENEMY_DETECTOR);
        MODULES.add(BOW_TRAJECTORY);
        MODULES.add(new TargetInfoModule());
        MODULES.add(new ComboModule());
        MODULES.add(HIT_FX);
        MODULES.add(new KillBoardModule());
        MODULES.add(AIM);
        MODULES.add(VELOCITY);
        MODULES.add(BRIDGE);
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
        MODULES.add(HIDE_ARMOR);
        MODULES.add(BLOCK_OUTLINE);
        MODULES.add(LOD);
        MODULES.add(new ItemPhysicsModule());
        MODULES.add(ANIM);
        MODULES.add(HOTBAR);
        MODULES.add(CHAT_BAR);
        MODULES.add(new ThemeModule());

        config.load();
        for (Module m : MODULES) m.load(config);
        blockShake = config.getBoolean("blockShake", "general", true, "Rung block khi đào và tắt hạt vỡ block");
        cleanInv = config.getBoolean("cleanInv", "general", true, "Bỏ nền đen khi mở kho đồ / rương");
        dev.lunarlite.gui.SaoDeath.enabled = config.getBoolean("deathFx", "general", true, "Hiệu ứng chết kiểu Sword Art Online");
        dev.lunarlite.gui.SaoShatter.enabled = config.getBoolean("shatterFx", "general", true, "Quái / người chơi khác vỡ thành mảnh khi chết kiểu SAO");
        config.save();

        keyMenu = new KeyBinding("Mở menu Zhwii Client", Keyboard.KEY_RSHIFT, "Zhwii Client");
        keyZoom = new KeyBinding("Zoom", Keyboard.KEY_C, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyMenu);
        keyAim = new KeyBinding("Bật/tắt Aim Assist", Keyboard.KEY_R, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyZoom);
        ClientRegistry.registerKeyBinding(keyAim);
        keyShake = new KeyBinding("Bật/tắt rung block", Keyboard.KEY_NONE, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyShake);
        keyBridge = new KeyBinding("Bật/tắt Hỗ trợ bridge", Keyboard.KEY_G, "Zhwii Client");
        keyVelocity = new KeyBinding("Bật/tắt Giảm knockback", Keyboard.KEY_V, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyBridge);
        ClientRegistry.registerKeyBinding(keyVelocity);

        // Chat bar: chỉ tin người chơi + avatar/giường team + tự dịch tiếng Việt (cài đặt nằm trong menu)
        CHAT_BAR.register();

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(VELOCITY);
        FMLCommonHandler.instance().bus().register(this);
    }

    /** Thư mục config của game (nơi chứa file config chính và thư mục hồ sơ). */
    public static File configDir() {
        File dir = configFile == null ? null : configFile.getParentFile();
        return dir != null ? dir : new File("config");
    }

    /** Ghi toàn bộ cài đặt hiện tại vào {@code c} (không tự lưu file). */
    public static void fill(Configuration c) {
        for (Module m : MODULES) m.save(c);
        c.get("general", "blockShake", true).set(blockShake);
        c.get("general", "cleanInv", true).set(cleanInv);
        c.get("general", "deathFx", true).set(dev.lunarlite.gui.SaoDeath.enabled);
        c.get("general", "shatterFx", true).set(dev.lunarlite.gui.SaoShatter.enabled);
    }

    public static void saveConfig() {
        if (config == null) return;
        fill(config);
        config.save();
    }

    /**
     * Nạp cài đặt từ {@code c} (đã load). Chỉ tính năng có mặt trong {@code c} mới bị ghi đè.
     * Sau đó lưu vào config chính. Trả về số tính năng đã nạp.
     */
    public static int applyConfig(Configuration c) {
        Set<String> names = c.getCategoryNames();
        int n = 0;
        for (Module m : MODULES) {
            if (!ProfileManager.has(names, m.id)) continue;
            m.load(c);
            n++;
        }
        if (ProfileManager.has(names, "general")) {
            blockShake = c.getBoolean("blockShake", "general", true, "Rung block khi đào và tắt hạt vỡ block");
            cleanInv = c.getBoolean("cleanInv", "general", true, "Bỏ nền đen khi mở kho đồ / rương");
            dev.lunarlite.gui.SaoDeath.enabled = c.getBoolean("deathFx", "general", true, "Hiệu ứng chết kiểu Sword Art Online");
            dev.lunarlite.gui.SaoShatter.enabled = c.getBoolean("shatterFx", "general", true, "Quái / người chơi khác vỡ thành mảnh khi chết kiểu SAO");
        }
        saveConfig();
        return n;
    }

    private static boolean sfxDead;
    private static net.minecraft.client.audio.ISound lowSound;

    /** Âm SAO: tiếng chết khi máu về 0; cảnh báo máu lặp liên tục khi dưới 5 tim, tự dừng khi hồi lên từ 5 tim. */
    private static void playHealthSounds(Minecraft mc) {
        float hp = mc.thePlayer.getHealth();
        dev.lunarlite.gui.SaoDeath.update(hp <= 0f || mc.thePlayer.isDead);
        if (hp <= 0f || mc.thePlayer.isDead) {
            stopLow(mc);
            if (!sfxDead) {
                sfxDead = true;
                sfx("sao.death");
            }
            return;
        }
        sfxDead = false;
        if (hp < 10f) {
            if (lowSound == null || !mc.getSoundHandler().isSoundPlaying(lowSound)) {
                lowSound = new LoopSound(new net.minecraft.util.ResourceLocation("lunarlite", "sao.critical_health"));
                mc.getSoundHandler().playSound(lowSound);
            }
        } else {
            stopLow(mc);
        }
    }

    /** Tiếng chết SAO cho mọi sinh vật khác (động vật, mob, người chơi) khi chúng bắt đầu chết, phát tại chỗ chúng. */
    private static void playNearbyDeaths(Minecraft mc) {
        if (mc.theWorld == null) return;
        int played = 0;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof net.minecraft.entity.EntityLivingBase) || o == mc.thePlayer) continue;
            if (o instanceof net.minecraft.entity.item.EntityArmorStand) continue;
            net.minecraft.entity.EntityLivingBase e = (net.minecraft.entity.EntityLivingBase) o;
            if (e.deathTime != 1) continue;
            double d2 = e.getDistanceSqToEntity(mc.thePlayer);
            if (d2 > 64.0 * 64.0) continue;
            dev.lunarlite.gui.SaoShatter.burst(e); // vỡ thành mảnh đa giác kiểu SAO
            if (HIT_FX != null) HIT_FX.onDeath(e);  // hiệu ứng chết kiểu anime
            if (played < 3 && d2 <= 48.0 * 48.0) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                        new net.minecraft.util.ResourceLocation("lunarlite", "sao.death"),
                        (float) e.posX, (float) e.posY, (float) e.posZ));
                played++;
            }
        }
    }

    /** Âm lặp gốc của OpenAL (không delay, không suy giảm theo khoảng cách) nên nối liền mạch, không bị hở giữa các lần. */
    private static class LoopSound extends net.minecraft.client.audio.PositionedSound {
        LoopSound(net.minecraft.util.ResourceLocation loc) {
            super(loc);
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 1.0F;
            this.attenuationType = net.minecraft.client.audio.ISound.AttenuationType.NONE;
        }
    }

    private static void stopLow(Minecraft mc) {
        if (lowSound != null) {
            mc.getSoundHandler().stopSound(lowSound);
            lowSound = null;
        }
    }

    private static net.minecraft.client.audio.ISound sfx(String ev) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        net.minecraft.client.audio.ISound snd = net.minecraft.client.audio.PositionedSoundRecord.create(
                new net.minecraft.util.ResourceLocation("lunarlite", ev),
                (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ);
        mc.getSoundHandler().playSound(snd);
        return snd;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            sfxDead = false;
            lowSound = null;
            dev.lunarlite.gui.SaoDeath.update(false);
            dev.lunarlite.gui.SaoShatter.clear();
            return;
        }
        playHealthSounds(mc);
        playNearbyDeaths(mc);

        if (keyMenu.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new MenuScreen());
        }
        if (keyAim.isPressed() && mc.currentScreen == null) {
            AIM.toggle();
            saveConfig();
        }
        if (keyBridge.isPressed() && mc.currentScreen == null) {
            BRIDGE.toggle();
            saveConfig();
        }
        if (keyVelocity.isPressed() && mc.currentScreen == null) {
            VELOCITY.toggle();
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
        if (e.phase != TickEvent.Phase.START) return;
        if (AIM != null) AIM.onRender(e.renderTickTime);
        if (BRIDGE != null) BRIDGE.onRender(e.renderTickTime);
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
            if (HIT_FX != null) HIT_FX.onHit((EntityLivingBase) e.target);
        }
    }

    /** Viền block đổi màu cầu vồng: huỷ viền gốc của game và tự vẽ viền mới. */
    @SubscribeEvent
    public void onBlockHighlight(DrawBlockHighlightEvent e) {
        if (BLOCK_OUTLINE != null && BLOCK_OUTLINE.enabled) BLOCK_OUTLINE.onHighlight(e);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (cleanInv && e.gui != null) e.gui = dev.lunarlite.gui.CleanGuis.wrap(e.gui);
        AnimationsModule.guiOpened(e.gui);
    }

    @SubscribeEvent
    public void onGuiDrawPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        AnimationsModule.guiPre(e.gui);
        if (dev.lunarlite.gui.SaoDeath.active() && e.gui instanceof net.minecraft.client.gui.GuiGameOver) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post e) {
        AnimationsModule.guiPost();
        if (dev.lunarlite.gui.SaoDeath.active() && e.gui instanceof net.minecraft.client.gui.GuiGameOver) {
            net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(Minecraft.getMinecraft());
            dev.lunarlite.gui.SaoDeath.render(sr.getScaledWidth(), sr.getScaledHeight(), e.mouseX, e.mouseY, true);
        }
    }

    @SubscribeEvent
    public void onPlayerPre(RenderPlayerEvent.Pre e) {
        if (HIDE_ARMOR != null) HIDE_ARMOR.onPre(e);
        if (ENEMY_DETECTOR != null) ENEMY_DETECTOR.onPre(e);
    }

    @SubscribeEvent
    public void onPlayerPost(RenderPlayerEvent.Post e) {
        if (ENEMY_DETECTOR != null) ENEMY_DETECTOR.onPost(e);
        if (HIDE_ARMOR != null) HIDE_ARMOR.onPost(e);
    }

    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        if (LOD != null) LOD.render(e.partialTicks);
        if (BOW_TRAJECTORY != null) BOW_TRAJECTORY.render(e.partialTicks);
        if (ENEMY_DETECTOR != null) ENEMY_DETECTOR.renderMarkers(e.partialTicks);
        renderShake(e.partialTicks);
        dev.lunarlite.gui.SaoShatter.render(e.partialTicks);
        dev.lunarlite.gui.AnimeHit.render(e.partialTicks);
    }

    /** Ẩn thân xác sinh vật / người chơi khác đang chết: đã được thay bằng mảnh vỡ SAO. */
    @SubscribeEvent
    public void onLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre e) {
        if (e.entity != null && dev.lunarlite.gui.SaoShatter.hides(e.entity)) e.setCanceled(true);
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
        if (dev.lunarlite.gui.SaoDeath.active() && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver)) {
            dev.lunarlite.gui.SaoDeath.render(e.resolution.getScaledWidth(), e.resolution.getScaledHeight(), 0, 0, false);
        }
    }
}
