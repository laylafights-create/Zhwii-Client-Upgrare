package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Animation cho thanh hotbar:
 *  - Ô chọn trượt mượt bằng lò xo, giãn ra theo vận tốc và phát sáng nhẹ.
 *  - Item đang cầm PHÓNG TO ngay tại giữa ô của nó (không nổi lên), có nảy nhẹ khi tới nơi.
 *  - Item ở hai bên ô đang chọn phồng nhẹ, item vừa bỏ chọn co lại có nảy.
 *  - Đổi ô thì item mới lắc nhẹ theo hướng chuyển; item đang cầm "thở" rất nhẹ.
 *  - Nhặt thêm / đổi item thì "pop".
 *
 * Mọi chuyển động dùng chung một lò xo giảm chấn cho mỗi ô nên rất nhẹ (không cấp phát bộ nhớ mỗi khung hình).
 * Module này vẽ lại hotbar thay cho vanilla (huỷ HOTBAR của Forge rồi tự vẽ).
 */
public class HotbarAnimationModule extends Module {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    /** Lò xo của từng item: hơi thiếu giảm chấn (~0.45) để có độ nảy khi phóng to. */
    private static final float SPRING_K = 480f;
    private static final float SPRING_C = 2f * 0.45f * (float) Math.sqrt(SPRING_K);

    /** zLevel của Gui là protected nên dùng lớp con riêng để vẽ texture hotbar. */
    private static final class HotbarGui extends Gui {
        void setZ(float z) { this.zLevel = z; }
    }

    private final HotbarGui gui = new HotbarGui();

    private final BoolSetting slide = add(new BoolSetting("slide", "Ô chọn trượt mượt", true));
    private final SliderSetting slideSpeed = add(new SliderSetting("slide_speed", "Tốc độ trượt", 18, 6, 100, 2, ""));
    private final BoolSetting stretch = add(new BoolSetting("stretch", "Ô chọn giãn khi trượt", true));
    private final BoolSetting glow = add(new BoolSetting("glow", "Ô chọn phát sáng nhẹ", true));
    private final BoolSetting zoom = add(new BoolSetting("zoom", "Phóng to item đang cầm", true));
    private final SliderSetting zoomSize = add(new SliderSetting("zoom_size", "Độ phóng to", 40, 10, 80, 5, "%"));
    private final BoolSetting wave = add(new BoolSetting("wave", "Item bên cạnh phồng nhẹ", true));
    private final BoolSetting wobble = add(new BoolSetting("wobble", "Lắc nhẹ khi đổi item", true));
    private final BoolSetting breathe = add(new BoolSetting("breathe", "Item đang cầm thở nhẹ", true));
    private final BoolSetting pop = add(new BoolSetting("pop", "Pop khi nhặt/đổi item", true));

    // Trạng thái từng ô
    private final float[] scl = new float[9];     // tỉ lệ hiện tại (1 = bình thường)
    private final float[] vel = new float[9];     // vận tốc của lò xo
    private final float[] wobT = new float[9];    // thời gian lắc (< 0 = không lắc)
    private final float[] wobDir = new float[9];  // hướng lắc: +1 / -1
    private final Object[] lastItem = new Object[9];
    private final int[] lastSize = new int[9];

    private float selPos = -1f;   // vị trí ô chọn (đơn vị: ô), mượt
    private float selVel = 0f;    // vận tốc ô chọn, dùng cho độ nảy và độ giãn
    private int lastCur = -1;
    private float time = 0f;
    private boolean initialized = false;
    private long lastNanos = 0L;

    public HotbarAnimationModule() {
        super("hotbar_animation", "Hotbar Animation",
            "Animation cho thanh hotbar: trượt, phóng to item đang cầm, lắc, pop.", Category.PVP, true);
        for (int i = 0; i < 9; i++) {
            scl[i] = 1f;
            wobT[i] = -1f;
        }
    }

    /** LunarLite gọi: có nên thay hotbar vanilla không. */
    public boolean shouldRender() {
        Minecraft mc = Minecraft.getMinecraft();
        return enabled && mc.getRenderViewEntity() instanceof EntityPlayer && !mc.playerController.isSpectator();
    }

    /** LunarLite gọi khi đã huỷ hotbar vanilla. */
    public void render(ScaledResolution sr, float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = (EntityPlayer) mc.getRenderViewEntity();

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        time += dt;

        int cur = p.inventory.currentItem;
        updateSelection(cur, dt);
        updateSlots(p, cur, dt);

        int cx = sr.getScaledWidth() / 2;
        int h = sr.getScaledHeight();

        // Nền hotbar
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(WIDGETS);
        gui.setZ(-90f);
        gui.drawTexturedModalRect(cx - 91, h - 22, 0, 0, 182, 22);

        // Ô chọn: trượt theo số lẻ, giãn ngang theo vận tốc, phát sáng nhẹ bên trong.
        float sx = stretch.value ? 1f + Math.min(0.45f, Math.abs(selVel) * 0.045f) : 1f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx - 92f + selPos * 20f + 12f, h - 23f, 0f);
        GlStateManager.scale(sx, 1f, 1f);
        GlStateManager.translate(-12f, 0f, 0f);
        gui.drawTexturedModalRect(0, 0, 0, 22, 24, 22);
        if (glow.value) {
            int a = (int) (24f + 14f * (float) Math.sin(time * 3.2f));
            Gui.drawRect(3, 3, 21, 21, (a << 24) | 0x00FFFFFF);
        }
        GlStateManager.popMatrix();
        gui.setZ(0f);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // Item: vẽ các ô thường trước, ô đang chọn vẽ sau cùng để phần phóng to nằm đè lên ô bên cạnh.
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        int iy = h - 16 - 3;
        for (int i = 0; i < 9; i++) {
            if (i != cur) drawItem(mc, p, i, cx - 90 + i * 20 + 2, iy, false, partial);
        }
        drawItem(mc, p, cur, cx - 90 + cur * 20 + 2, iy, true, partial);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    /** Cập nhật ô chọn (lò xo) và kích hoạt hiệu ứng khi đổi ô. */
    private void updateSelection(int cur, float dt) {
        if (selPos < 0f || lastCur < 0) {
            selPos = cur;
            lastCur = cur;
        }
        if (cur != lastCur) {
            int d = cur - lastCur;
            if (d > 4) d -= 9; else if (d < -4) d += 9;   // cuộn vòng 8 -> 0
            wobDir[cur] = d >= 0 ? 1f : -1f;
            if (wobble.value) wobT[cur] = 0f;
            if (zoom.value) {
                vel[cur] += 2.5f;       // cú "đấm" nhỏ khi item mới phóng to
                vel[lastCur] -= 1.2f;   // item cũ co lại hơi quá rồi bật về
            }
            lastCur = cur;
        }

        if (slide.value) {
            float k = slideSpeed.getInt() * 4f;               // độ cứng
            float c = 2f * (float) Math.sqrt(k) * 0.8f;       // giảm chấn ~0.8 (nảy nhẹ)
            selVel += (k * (cur - selPos) - c * selVel) * dt;
            selPos += selVel * dt;
            if (Math.abs(cur - selPos) < 0.001f && Math.abs(selVel) < 0.01f) { selPos = cur; selVel = 0f; }
        } else {
            selPos = cur;
            selVel = 0f;
        }
    }

    /** Cập nhật lò xo phóng to / pop / lắc cho cả 9 ô (mỗi khung hình đúng một lần). */
    private void updateSlots(EntityPlayer p, int cur, float dt) {
        float zs = zoom.value ? zoomSize.getInt() / 100f : 0f;
        float h = dt * 0.5f;   // 2 bước nhỏ cho ổn định
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            Object id = s == null ? null : s.getItem();
            int size = s == null ? 0 : s.stackSize;

            // Nhặt thêm / đổi item -> bơm xung vào lò xo (không pop ở khung hình đầu tiên).
            if (initialized && pop.value && s != null && (id != lastItem[i] || size > lastSize[i])) {
                vel[i] += id != lastItem[i] ? 5f : 3.5f;
            }
            lastItem[i] = id;
            lastSize[i] = size;

            // Mục tiêu: ô chọn phóng to, hai ô bên cạnh phồng nhẹ.
            float t = 1f;
            if (i == cur) t += zs;
            else if (wave.value && (i == cur - 1 || i == cur + 1)) t += zs * 0.12f;

            float v = vel[i];
            float x = scl[i];
            for (int n = 0; n < 2; n++) {
                v += (SPRING_K * (t - x) - SPRING_C * v) * h;
                x += v * h;
            }
            if (x < 0.5f) x = 0.5f; else if (x > 2.5f) x = 2.5f;
            if (Math.abs(t - x) < 0.0005f && Math.abs(v) < 0.005f) { x = t; v = 0f; }
            scl[i] = x;
            vel[i] = v;

            if (wobT[i] >= 0f) {
                wobT[i] += dt;
                if (wobT[i] > 0.7f) wobT[i] = -1f;
            }
        }
        initialized = true;
    }

    private void drawItem(Minecraft mc, EntityPlayer p, int i, int x, int y, boolean selected, float partial) {
        ItemStack s = p.inventory.mainInventory[i];
        if (s == null) return;

        float sc = scl[i];
        if (breathe.value && selected) sc *= 1f + 0.025f * (float) Math.sin(time * 3f);

        float ang = 0f;
        if (wobT[i] >= 0f) {
            float t = wobT[i];
            ang = 14f * wobDir[i] * (float) Math.exp(-7f * t) * (float) Math.sin(24f * t);
        }

        boolean xf = Math.abs(sc - 1f) > 0.002f || Math.abs(ang) > 0.1f;
        float cx = x + 8f, cy = y + 8f;   // phóng to quanh TÂM ô, không dịch lên trên
        if (xf) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0f);
            if (ang != 0f) GlStateManager.rotate(ang, 0f, 0f, 1f);
            GlStateManager.scale(sc, sc, 1f);
            GlStateManager.translate(-cx, -cy, 0f);
        }

        // Khi tắt "pop" thì dùng hiệu ứng co giãn vanilla (animationsToGo) khi vừa nhặt.
        float f = pop.value ? 0f : s.animationsToGo - partial;
        if (f > 0f) {
            float g = 1f + f / 5f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 8, y + 12, 0f);
            GlStateManager.scale(1f / g, (g + 1f) / 2f, 1f);
            GlStateManager.translate(-(x + 8), -(y + 12), 0f);
        }
        mc.getRenderItem().renderItemAndEffectIntoGUI(s, x, y);
        if (f > 0f) GlStateManager.popMatrix();
        mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, s, x, y);

        if (xf) GlStateManager.popMatrix();
    }
}
