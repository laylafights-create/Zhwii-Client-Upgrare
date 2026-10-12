package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/** Zoom inspired by Zoomify: smooth transitions, hold/toggle modes, scroll control and reduced sensitivity. */
public class ZoomModule extends Module {
    private final SliderSetting zoomFov = add(new SliderSetting("zoom_fov", "Zoom FOV", 20, 5, 60, 1, ""));
    private final ModeSetting activation = add(new ModeSetting("activation", "Activation", 0, "Hold", "Toggle"));
    private final BoolSetting smoothCam = add(new BoolSetting("smooth_camera", "Cinematic Camera", true));
    private final BoolSetting animate = add(new BoolSetting("animate", "Smooth Transition", true));
    private final SliderSetting transitionSpeed = add(new SliderSetting("transition_speed", "Transition Speed", 0.25, 0.05, 1.0, 0.05, "x"));
    private final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll To Adjust", true));
    private final SliderSetting scrollStep = add(new SliderSetting("scroll_step", "Scroll Step", 3, 1, 10, 1, " FOV"));
    private final BoolSetting reduceSensitivity = add(new BoolSetting("reduce_sensitivity", "Reduce Mouse Sensitivity", true));
    private final SliderSetting sensitivityScale = add(new SliderSetting("sensitivity_scale", "Zoom Sensitivity", 0.25, 0.05, 1.0, 0.05, "x"));

    private boolean zooming;
    private boolean toggleZoom;
    private boolean prevSmooth;
    private boolean prevKeyDown;
    private float prevFov;
    private float cur;
    private float target;
    private float prevSensitivity;
    private boolean sensitivitySaved;

    public ZoomModule() {
        super("zoom", "Zoom", "Thu phóng mượt kiểu Zoomify, chỉnh bằng con lăn chuột và độ nhạy.", Category.VISUAL, true);
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
        float step = (float) scrollStep.getFloat();
        target = Math.max(5f, Math.min(prevFov, target - Integer.signum(dwheel) * step));
        return true;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean want = wantZoom();

        if (want && !zooming) {
            zooming = true;
            prevFov = mc.gameSettings.fovSetting;
            prevSmooth = mc.gameSettings.smoothCamera;
            cur = prevFov;
            target = zoomFov.getFloat();
            if (smoothCam.value) mc.gameSettings.smoothCamera = true;
            if (reduceSensitivity.value) {
                prevSensitivity = mc.gameSettings.mouseSensitivity;
                sensitivitySaved = true;
            }
        }

        if (!zooming) return;

        if (want) {
            if (smoothCam.value) mc.gameSettings.smoothCamera = true;
            if (reduceSensitivity.value && sensitivitySaved) {
                mc.gameSettings.mouseSensitivity = Math.max(0.01f, Math.min(1.0f, prevSensitivity * sensitivityScale.getFloat()));
            }
        } else {
            mc.gameSettings.smoothCamera = prevSmooth;
            if (sensitivitySaved) {
                mc.gameSettings.mouseSensitivity = prevSensitivity;
                sensitivitySaved = false;
            }
        }

        float destination = want ? target : prevFov;
        if (animate.value) {
            float speed = (float) transitionSpeed.getFloat();
            cur += (destination - cur) * speed;
            if (Math.abs(destination - cur) < 0.15f) cur = destination;
        } else {
            cur = destination;
        }
        mc.gameSettings.fovSetting = cur;

        if (!want && Math.abs(cur - prevFov) < 0.01f) {
            mc.gameSettings.fovSetting = prevFov;
            mc.gameSettings.smoothCamera = prevSmooth;
            if (sensitivitySaved) {
                mc.gameSettings.mouseSensitivity = prevSensitivity;
                sensitivitySaved = false;
            }
            zooming = false;
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
                zooming = false;
            }
        }
    }
}
