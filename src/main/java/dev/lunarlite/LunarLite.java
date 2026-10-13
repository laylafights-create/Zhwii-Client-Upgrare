package dev.lunarlite;

import dev.lunarlite.gui.HudEditorScreen;
import dev.lunarlite.gui.MenuScreen;
import dev.lunarlite.module.*;
import dev.lunarlite.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
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
    public static KeyBinding keyMenu, keyZoom, keyAim;

    public static ZoomModule ZOOM;
    public static AimAssistModule AIM;
    public static BlockOutlineModule BLOCK_OUTLINE;
    public static EnemyDetectorModule ENEMY_DETECTOR;
    public static ScoreboardModule SCOREBOARD;
    public static BowTrajectoryModule BOW_TRAJECTORY;
    public static AnimationsModule ANIM;
    public static HotbarAnimationModule HOTBAR;

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
        MODULES.add(new ResourceCounterModule());
        MODULES.add(new ItemPickupModule());
        MODULES.add(new SpeedModule());
        MODULES.add(new PingModule());
        MODULES.add(new ServerIpModule());
        MODULES.add(SCOREBOARD);
        // Utility / Visual
        MODULES.add(new ToggleSprintModule());
        MODULES.add(ZOOM);
        MODULES.add(new FullbrightModule());
        MODULES.add(new TimeChangerModule());
        MODULES.add(BLOCK_OUTLINE);
        MODULES.add(new ItemPhysicsModule());
        MODULES.add(ANIM);
        MODULES.add(HOTBAR);
        MODULES.add(new ThemeModule());

        config.load();
        for (Module m : MODULES) m.load(config);
        config.save();

        keyMenu = new KeyBinding("Mở menu Zhwii Client", Keyboard.KEY_RSHIFT, "Zhwii Client");
        keyZoom = new KeyBinding("Zoom", Keyboard.KEY_C, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyMenu);
        keyAim = new KeyBinding("Bật/tắt Aim Assist", Keyboard.KEY_R, "Zhwii Client");
        ClientRegistry.registerKeyBinding(keyZoom);
        ClientRegistry.registerKeyBinding(keyAim);

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    public static void saveConfig() {
        if (config == null) return;
        for (Module m : MODULES) m.save(config);
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
        AnimationsModule.guiOpened(e.gui);
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
