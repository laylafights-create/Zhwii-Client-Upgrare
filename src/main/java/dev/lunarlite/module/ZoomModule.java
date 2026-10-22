package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Zoom inspired by Zoomify: smooth transitions, hold/toggle modes, scroll control and reduced sensitivity.
 * Animation: FOV chạy theo từng khung hình (không giật theo tick), lò xo có nảy nhẹ,
 * vignette tối dần ở rìa màn hình theo mức zoom và chỉ số phóng đại hiện khi cuộn chuột.
 */
public class ZoomModule extends Module {
    private final SliderSetting zoomFov = add(new SliderSetting("zoom_fov", "Zoom FOV", 20, 5, 60, 1, ""));
    private final ModeSetting activation = add(new ModeSetting("activation", "Activation", 0, "Hold", "Toggle"));
    private final BoolSetting smoothCam = add(new BoolSetting("smooth_camera", "Cinematic Camera", true));
    private final BoolSetting animate = add(new BoolSetting("animate", "Smooth Transition", true));
    private final SliderSetting transitionSpeed = add(new SliderSetting("transition_speed", "Transition Speed", 0.25, 0.05, 1.0, 0.05, "x"));
    private final BoolSetting spring = add(new BoolSetting("spring", "Spring Animation", true));
    private final SliderSetting bounce = add(new SliderSetting("bounce", "Bounce", 0.25, 0.0, 0.6, 0.05, ""));
    private final BoolSetting vignette = add(new BoolSetting("vignette", "Zoom Vignette", true));
    private final SliderSetting vignetteStrength = add(new SliderSetting("vignette_strength", "Vignette Strength", 0.5, 0.1, 1.0, 0.05, "x"));
    private final BoolSetting indicator = add(new BoolSetting("indicator", "Zoom Level Indicator", true));
    private final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll To Adjust", true));
    private final ModeSetting scrollMode = add(new ModeSetting("scroll_mode", "Scroll Mode", 0, "Linear", "Percent"));
    private final SliderSetting scrollStep = add(new SliderSetting("scroll_step", "Scroll Step (Linear)", 3, 1, 10, 1, " FOV"));
    private final SliderSetting scrollPercent = add(new SliderSetting("scroll_percent", "Scroll Step (Percent)", 12, 2, 40, 1, "%"));
    private final BoolSetting zoomSound = add(new BoolSetting("zoom_sound", "Spyglass Sound", true));
    private final BoolSetting scrollInvert = add(new BoolSetting("scroll_invert", "Invert Scroll Direction", false));
    private final BoolSetting scrollAccel = add(new BoolSetting("scroll_accel", "Scroll Acceleration", true));
    private final SliderSetting scrollAccelMax = add(new SliderSetting("scroll_accel_max", "Max Acceleration", 3, 1.5, 6, 0.5, "x"));
    private final SliderSetting maxZoom = add(new SliderSetting("max_zoom", "Max Zoom", 100, 2, 100, 1, "x"));
    private final SliderSetting scrollMaxFov = add(new SliderSetting("scroll_max_fov", "Max FOV (0 = Normal FOV)", 0, 0, 110, 5, ""));
    private final BoolSetting rememberLevel = add(new BoolSetting("remember_level", "Remember Zoom Level", false));
    private final BoolSetting reduceSensitivity = add(new BoolSetting("reduce_sensitivity", "Reduce Mouse Sensitivity", true));
    private final SliderSetting sensitivityScale = add(new SliderSetting("sensitivity_scale", "Zoom Sensitivity", 0.25, 0.05, 1.0, 0.05, "x"));

    private boolean zooming;
    private boolean toggleZoom;
    private boolean prevSmooth;
    private boolean prevKeyDown;
    private float prevFov;
    private float cur;
    private float vel;
    private float dest;
    private float target;
    private float prevSensitivity;
    private boolean sensitivitySaved;

    private long lastNanos;
    private long lastScrollNanos;
    private int scrollCombo;
    private float savedTarget;
    private float indicatorT;
    private boolean registered;
    private boolean wasReleasing;

    private static final ResourceLocation SND_USE = new ResourceLocation("lunarlite", "zoom.use");
    private static final ResourceLocation SND_STOP = new ResourceLocation("lunarlite", "zoom.stop");

    private void playSound(ResourceLocation loc) {
        if (!zoomSound.value) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        // create(loc, pitch) chỉ phát với volume 0.25 -> dùng create(loc, x, y, z) (volume 1.0, pitch 1.0) tại vị trí người chơi.
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(loc,
                (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ));
    }

    public ZoomModule() {
        super("zoom", "Zoom", "Thu phóng mượt kiểu Zoomify, chỉnh bằng con lăn chuột và độ nhạy.", Category.VISUAL, true);
    }

    /** Đăng ký sự kiện render một lần (nếu LunarLite đã tự đăng ký module này thì bỏ dòng gọi trong onTick). */
    private void ensureRegistered() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    private boolean wantZoom() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || LunarLite.keyZoom == null || mc.currentScreen != null || mc.thePlayer == null) return false;
        boolean down = LunarLite.keyZoom.isKeyDown();
        if (activation.is("Toggle")) {
            if (LunarLite.keyZoom.isPressed()) toggleZoom = !toggleZoom;
            prevKeyDown = down;
            return toggleZoom;
        }
        prevKeyDown = down;
        return down;
    }

    /** @return true when the zoom module consumes this mouse-wheel event. */
    public boolean handleScroll(int dwheel) {
        if (!scroll.value || !zooming || !wantZoom() || dwheel == 0) return false;

        // Gia tốc: cuộn liên tục thì mỗi nấc nhảy xa dần
        float mult = 1f;
        long now = System.nanoTime();
        if (scrollAccel.value) {
            float gap = (now - lastScrollNanos) / 1_000_000_000f;
            scrollCombo = gap < 0.18f ? Math.min(scrollCombo + 1, 10) : 0;
            mult = 1f + ((float) scrollAccelMax.getFloat() - 1f) * (scrollCombo / 10f);
        } else {
            scrollCombo = 0;
        }
        lastScrollNanos = now;

        int dir = Integer.signum(dwheel) * (scrollInvert.value ? -1 : 1);   // dir > 0 = zoom vào
        if (scrollMode.is("Percent")) {
            float factor = 1f + (float) scrollPercent.getFloat() / 100f * mult;
            target = dir > 0 ? target / factor : target * factor;
        } else {
            float st = (float) scrollStep.getFloat() * mult;
            if (dir > 0) st = Math.min(st, target * 0.5f);   // FOV nhỏ thì bước nhỏ lại, tránh nhảy vọt
            target -= dir * st;
        }

        // Max Zoom (x) -> FOV nhỏ nhất: tan(lo/2) = tan(prevFov/2) / maxZoom
        float lo = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(prevFov * 0.5)) / maxZoom.getFloat()));
        lo = Math.max(0.05f, lo);
        float hi = scrollMaxFov.getFloat() <= 0 ? prevFov : (float) scrollMaxFov.getFloat();
        if (hi < lo + 0.05f) hi = lo + 0.05f;
        target = Math.max(lo, Math.min(hi, target));
        savedTarget = target;
        indicatorT = 1.4f;
        return true;
    }

    @Override
    public void onTick() {
        ensureRegistered();
        Minecraft mc = Minecraft.getMinecraft();
        boolean want = wantZoom();

        if (want && !zooming) {
            zooming = true;
            prevFov = mc.gameSettings.fovSetting;
            prevSmooth = mc.gameSettings.smoothCamera;
            cur = prevFov;
            vel = 0f;
            lastNanos = 0L;
            target = rememberLevel.value && savedTarget > 0f ? Math.min(savedTarget, prevFov) : zoomFov.getFloat();
            scrollCombo = 0;
            indicatorT = 1.4f;
            playSound(SND_USE);
            if (smoothCam.value) mc.gameSettings.smoothCamera = true;
            if (reduceSensitivity.value) {
                prevSensitivity = mc.gameSettings.mouseSensitivity;
                sensitivitySaved = true;
            }
        }

        if (!zooming) return;

        if (want) {
            wasReleasing = false;
            if (smoothCam.value) mc.gameSettings.smoothCamera = true;
            if (reduceSensitivity.value && sensitivitySaved) {
                mc.gameSettings.mouseSensitivity = Math.max(0.01f, Math.min(1.0f, prevSensitivity * sensitivityScale.getFloat()));
            }
        } else {
            if (!wasReleasing) { playSound(SND_STOP); wasReleasing = true; }
            mc.gameSettings.smoothCamera = prevSmooth;
            if (sensitivitySaved) {
                mc.gameSettings.mouseSensitivity = prevSensitivity;
                sensitivitySaved = false;
            }
        }

        dest = want ? target : prevFov;

        // FOV được cập nhật mỗi khung hình ở onRenderTick; ở đây chỉ kiểm tra đã trả về xong chưa.
        if (!want && Math.abs(cur - prevFov) < 0.05f && Math.abs(vel) < 0.5f) finish(mc);
    }

    private void finish(Minecraft mc) {
        mc.gameSettings.fovSetting = prevFov;
        mc.gameSettings.smoothCamera = prevSmooth;
        if (sensitivitySaved) {
            mc.gameSettings.mouseSensitivity = prevSensitivity;
            sensitivitySaved = false;
        }
        vel = 0f;
        zooming = false;
    }

    /** Mỗi khung hình: chạy lò xo / làm mượt FOV theo thời gian thực nên không phụ thuộc tick hay FPS. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !zooming) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (indicatorT > 0f) indicatorT -= dt;

        float speed = (float) transitionSpeed.getFloat();
        if (!animate.value) {
            cur = dest;
            vel = 0f;
        } else if (spring.value) {
            float w = 8f + speed * 40f;                        // tần số góc: tốc độ phản hồi
            float z = 1f - (float) bounce.getFloat();          // hệ số giảm chấn: 1 = không nảy
            float h = dt * 0.5f;
            for (int i = 0; i < 2; i++) {
                vel += (w * w * (dest - cur) - 2f * z * w * vel) * h;
                cur += vel * h;
            }
            if (Math.abs(dest - cur) < 0.02f && Math.abs(vel) < 0.3f) { cur = dest; vel = 0f; }
        } else {
            float f = 1f - (float) Math.pow(1f - Math.min(speed, 0.99f), dt / 0.05f);   // tương đương lerp mỗi tick
            cur += (dest - cur) * f;
            vel = 0f;
            if (Math.abs(dest - cur) < 0.15f) cur = dest;
        }
        if (cur < 0.05f) cur = 0.05f; else if (cur > 110f) cur = 110f;
        Minecraft.getMinecraft().gameSettings.fovSetting = cur;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !zooming || !enabled) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = e.resolution;
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();

        float full = Math.max(1f, prevFov - zoomFov.getFloat());
        float p = Math.max(0f, Math.min(1f, (prevFov - cur) / full));

        if (vignette.value && p > 0.01f) drawVignette(w, h, p);

        if (indicator.value && indicatorT > 0f) {
            float mag = (float) (Math.tan(Math.toRadians(prevFov * 0.5)) / Math.tan(Math.toRadians(cur * 0.5)));
            String s = String.format(mag >= 10f ? "%.0fx" : "%.1fx", mag);
            int ia = (int) (255f * Math.min(1f, indicatorT / 0.4f));
            if (ia >= 4) {
                FontRenderer fr = mc.fontRendererObj;
                fr.drawStringWithShadow(s, w / 2f - fr.getStringWidth(s) / 2f, h / 2f + 14f, (ia << 24) | 0x00FFFFFF);
            }
        }
    }

    /** Vignette: các vòng mỏng tối dần về rìa màn hình, đậm theo mức zoom (không tạo texture / không cấp phát). */
    private void drawVignette(int w, int h, float p) {
        final int n = 14;
        float step = Math.max(w, h) * 0.014f;
        float base = 0.6f * (float) vignetteStrength.getFloat() * p;
        for (int i = 0; i < n; i++) {
            float f = 1f - i / (float) n;
            int a = (int) (255f * base * f * f);
            if (a <= 0) continue;
            int col = a << 24;
            int o0 = Math.round(i * step), o1 = Math.round((i + 1) * step);
            Gui.drawRect(o0, o0, w - o0, o1, col);              // trên
            Gui.drawRect(o0, h - o1, w - o0, h - o0, col);      // dưới
            Gui.drawRect(o0, o1, o1, h - o1, col);              // trái
            Gui.drawRect(w - o1, o1, w - o0, h - o1, col);      // phải
        }
    }

    @Override
    public void onToggle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled) {
            toggleZoom = false;
            if (zooming) {
                mc.gameSettings.fovSetting = prevFov;
                mc.gameSettings.smoothCamera = prevSmooth;
                if (sensitivitySaved) mc.gameSettings.mouseSensitivity = prevSensitivity;
                sensitivitySaved = false;
                vel = 0f;
                zooming = false;
            }
        }
    }
}
