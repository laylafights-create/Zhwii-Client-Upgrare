package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Animation cho thanh hotbar:
 *  - Ô chọn trượt mượt bằng lò xo, giãn ra theo vận tốc và phát sáng nhẹ.
 *  - Item đang cầm PHÓNG TO ngay tại giữa ô của nó (không nổi lên), có nảy nhẹ khi tới nơi.
 *  - Item ở hai bên ô đang chọn phồng nhẹ, item vừa bỏ chọn co lại có nảy.
 *  - Đổi ô thì item mới lắc nhẹ theo hướng chuyển; item đang cầm "thở" rất nhẹ.
 *  - Nhặt thêm / đổi item thì "pop".
 *
 * Trong kho đồ (mọi GuiContainer: túi đồ, rương, shop...):
 *  - Khung chọn trượt mượt từ ô này sang ô khác, giãn theo vận tốc, phát sáng nhẹ.
 *  - Item đang trỏ chuột phóng to có nảy, lắc nhẹ theo hướng di chuyển.
 *  - Item đổi chỗ / nhặt thêm thì "pop".
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
        void grad(int l, int t, int r, int b, int c1, int c2) { drawGradientRect(l, t, r, b, c1, c2); }
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

    // Kho đồ
    private final BoolSetting invAnim = add(new BoolSetting("inv_anim", "Animation trong kho đồ", true));
    private final BoolSetting invSlide = add(new BoolSetting("inv_slide", "Khung chọn trượt mượt (kho)", true));
    private final BoolSetting invZoom = add(new BoolSetting("inv_zoom", "Phóng to item đang trỏ (kho)", true));
    private final SliderSetting invZoomSize = add(new SliderSetting("inv_zoom_size", "Độ phóng to (kho)", 25, 10, 60, 5, "%"));
    private final BoolSetting invLift = add(new BoolSetting("inv_lift", "Item nổi lên + bóng đổ (kho)", true));
    private final BoolSetting invShine = add(new BoolSetting("inv_shine", "Vệt sáng quét + gợn sóng khi trỏ vào (kho)", true));
    private final BoolSetting invPop = add(new BoolSetting("inv_pop", "Pop khi item đổi chỗ (kho)", true));

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
        Arrays.fill(iScl, 1f);
        Arrays.fill(iWobT, -1f);
        Arrays.fill(iWobDir, 1f);
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

    // ====================================================================================
    //  KHO ĐỒ (GuiContainer)
    // ====================================================================================

    private static final int MAX_SLOTS = 256;

    private final float[] iScl = new float[MAX_SLOTS];
    private final float[] iVel = new float[MAX_SLOTS];
    private final float[] iWobT = new float[MAX_SLOTS];
    private final float[] iWobDir = new float[MAX_SLOTS];
    private final Object[] iLastItem = new Object[MAX_SLOTS];
    private final int[] iLastSize = new int[MAX_SLOTS];

    private GuiContainer iGui;
    private long iLastNanos = 0L;
    private float iTime = 0f;
    private float iOpenTime = 0f;

    // Khung chọn: vị trí tuyệt đối (pixel màn hình đã scale), vận tốc, độ hiện
    private float hx, hy, hvx, hvy, hAlpha;
    private int hSlot = -1;
    private float hShine = 1f;   // tiến trình vệt sáng / gợn sóng khi vừa trỏ vào ô mới (0..1, 1 = xong)

    private Field fLeft, fTop, fSlot;
    private boolean reflectReady = false, reflectFailed = false;

    /** Gọi từ GuiScreenEvent.DrawScreenEvent.Post: có nên vẽ animation kho không. */
    public boolean shouldRenderContainer(net.minecraft.client.gui.GuiScreen screen) {
        return enabled && invAnim.value && !reflectFailed && screen instanceof GuiContainer;
    }

    private void initReflection() {
        if (reflectReady) return;
        try {
            fLeft = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i");
            fTop = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r");
            fSlot = ReflectionHelper.findField(GuiContainer.class, "theSlot", "field_147006_u");
            reflectReady = true;
        } catch (Throwable t) {
            reflectFailed = true;
        }
    }

    private void resetInventoryState(GuiContainer g) {
        iGui = g;
        Arrays.fill(iScl, 1f);
        Arrays.fill(iVel, 0f);
        Arrays.fill(iWobT, -1f);
        Arrays.fill(iWobDir, 1f);
        Arrays.fill(iLastItem, null);
        Arrays.fill(iLastSize, 0);
        iOpenTime = 0f;
        hSlot = -1;
        hShine = 1f;
        hAlpha = 0f;
        hvx = hvy = 0f;
        iLastNanos = 0L;
    }

    /** Gọi sau khi vanilla vẽ xong GuiContainer (kể cả tooltip). */
    public void renderContainer(GuiContainer g, int mouseX, int mouseY, float partial) {
        initReflection();
        if (reflectFailed) return;
        Minecraft mc = Minecraft.getMinecraft();

        if (g != iGui) resetInventoryState(g);

        long now = System.nanoTime();
        float dt = iLastNanos == 0L ? 0.016f : Math.min(0.05f, (now - iLastNanos) / 1_000_000_000f);
        iLastNanos = now;
        iTime += dt;
        iOpenTime += dt;

        int gl, gt;
        Slot hov;
        try {
            gl = fLeft.getInt(g);
            gt = fTop.getInt(g);
            hov = (Slot) fSlot.get(g);
        } catch (Throwable t) {
            reflectFailed = true;
            return;
        }

        List<Slot> slots = g.inventorySlots.inventorySlots;
        int n = Math.min(slots.size(), MAX_SLOTS);
        int hovIdx = (hov != null && hov.slotNumber >= 0 && hov.slotNumber < n && slots.get(hov.slotNumber) == hov)
            ? hov.slotNumber : -1;

        updateHighlight(slots, hovIdx, gl, gt, dt);
        updateInvSlots(slots, n, hovIdx, dt);

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1f, 1f, 1f, 1f);
        drawHighlight();

        // Item phóng to / lắc vẽ đè lên item vanilla
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem ri = mc.getRenderItem();
        float oldZ = ri.zLevel;
        ri.zLevel = 200f;
        for (int i = 0; i < n; i++) {
            float sc = iScl[i];
            float ang = wobbleAngle(i);
            if (sc > 1.002f || Math.abs(ang) > 0.1f) {
                drawInvItem(mc, ri, slots.get(i), i, gl, gt, sc, ang);
            }
        }
        ri.zLevel = oldZ;
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();

        // Trả trạng thái GL như cuối drawScreen của vanilla
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Khung chọn trượt bằng lò xo giữa các ô; mờ dần khi chuột rời khỏi mọi ô. */
    private void updateHighlight(List<Slot> slots, int hovIdx, int gl, int gt, float dt) {
        if (hovIdx != hSlot) {
            if (hovIdx >= 0) {
                hShine = 0f;
                Slot s = slots.get(hovIdx);
                float tx = gl + s.xDisplayPosition, ty = gt + s.yDisplayPosition;
                float dir = tx >= hx ? 1f : -1f;
                if (hAlpha < 0.05f || !invSlide.value) {   // lần đầu trỏ vào: nhảy thẳng tới ô, không trượt từ xa
                    hx = tx;
                    hy = ty;
                    hvx = hvy = 0f;
                }
                if (invZoom.value) {
                    iVel[hovIdx] += 2.5f;
                    if (hSlot >= 0) iVel[hSlot] -= 1.2f;
                    if (s.getHasStack()) {
                        iWobDir[hovIdx] = dir;
                        iWobT[hovIdx] = 0f;
                    }
                }
            }
            hSlot = hovIdx;
        }

        if (hSlot >= 0) {
            Slot s = slots.get(hSlot);
            float tx = gl + s.xDisplayPosition, ty = gt + s.yDisplayPosition;
            if (invSlide.value) {
                float k = slideSpeed.getInt() * 4f;
                float c = 2f * (float) Math.sqrt(k) * 0.8f;
                hvx += (k * (tx - hx) - c * hvx) * dt;
                hvy += (k * (ty - hy) - c * hvy) * dt;
                hx += hvx * dt;
                hy += hvy * dt;
                if (Math.abs(tx - hx) < 0.01f && Math.abs(hvx) < 0.1f) { hx = tx; hvx = 0f; }
                if (Math.abs(ty - hy) < 0.01f && Math.abs(hvy) < 0.1f) { hy = ty; hvy = 0f; }
            } else {
                hx = tx;
                hy = ty;
                hvx = hvy = 0f;
            }
        }

        hShine = Math.min(1f, hShine + dt * 3.2f);
        float target = hSlot >= 0 ? 1f : 0f;
        hAlpha += (target - hAlpha) * Math.min(1f, dt * 18f);
        if (Math.abs(target - hAlpha) < 0.003f) hAlpha = target;
    }

    private static int lerpRgb(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 255) * (1f - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1f - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1f - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void outline(int l, int t, int r, int b, int col) {
        Gui.drawRect(l, t, r, t + 1, col);
        Gui.drawRect(l, b - 1, r, b, col);
        Gui.drawRect(l, t + 1, l + 1, b - 1, col);
        Gui.drawRect(r - 1, t + 1, r, b - 1, col);
    }

    private void drawHighlight() {
        if (hAlpha < 0.01f) return;
        float ex = invSlide.value && stretch.value ? Math.min(6f, Math.abs(hvx) * 0.012f) : 0f;
        float ey = invSlide.value && stretch.value ? Math.min(6f, Math.abs(hvy) * 0.012f) : 0f;
        int l = Math.round(hx - 1f - ex * 0.5f), r = Math.round(hx + 17f + ex * 0.5f);
        int t = Math.round(hy - 1f - ey * 0.5f), b = Math.round(hy + 17f + ey * 0.5f);

        // Màu chủ đạo đổi mượt giữa xanh băng và tím nhạt
        float m = 0.5f + 0.5f * (float) Math.sin(iTime * 2.4f);
        int rgb = lerpRgb(0x8FDCFF, 0xC3A6FF, m);
        int bright = lerpRgb(rgb, 0xFFFFFF, 0.6f);

        // Bóng đổ mềm dưới ô -> item có cảm giác "nổi"
        if (invLift.value) {
            int sa = (int) (60f * hAlpha);
            Gui.drawRect(l + 3, b, r - 3, b + 1, (sa << 24));
            Gui.drawRect(l + 5, b + 1, r - 5, b + 2, ((sa / 2) << 24));
        }

        // Quầng sáng mờ dần ra ngoài
        if (glow.value) {
            for (int k = 1; k <= 3; k++) {
                int a = (int) ((30f / k) * hAlpha);
                outline(l - k, t - k, r + k, b + k, (a << 24) | rgb);
            }
        }

        // Gợn sóng lan ra khi vừa trỏ vào ô mới
        if (invShine.value && hShine < 1f) {
            int e = Math.round(hShine * 6f);
            int a = (int) ((1f - hShine) * 140f * hAlpha);
            outline(l - e, t - e, r + e, b + e, (a << 24) | bright);
        }

        // Nền gradient: nhạt ở trên, sáng dần xuống dưới, nhịp thở nhẹ
        if (glow.value) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(iTime * 3.2f);
            int a1 = (int) ((16f + 8f * pulse) * hAlpha);
            int a2 = (int) ((52f + 22f * pulse) * hAlpha);
            gui.grad(l + 1, t + 1, r - 1, b - 1, (a1 << 24) | rgb, (a2 << 24) | rgb);
        }

        // Viền bo góc (cắt góc 1px) + điểm sáng ở góc
        int ab = (int) (225f * hAlpha);
        int col = (ab << 24) | bright;
        Gui.drawRect(l + 1, t, r - 1, t + 1, col);
        Gui.drawRect(l + 1, b - 1, r - 1, b, col);
        Gui.drawRect(l, t + 1, l + 1, b - 1, col);
        Gui.drawRect(r - 1, t + 1, r, b - 1, col);
        int ca = (int) (110f * hAlpha);
        int cc = (ca << 24) | 0x00FFFFFF;
        Gui.drawRect(l, t, l + 1, t + 1, cc);
        Gui.drawRect(r - 1, t, r, t + 1, cc);
        Gui.drawRect(l, b - 1, l + 1, b, cc);
        Gui.drawRect(r - 1, b - 1, r, b, cc);

        // Vệt sáng chéo quét qua item khi vừa trỏ vào
        if (invShine.value && hShine < 1f) {
            int ix = Math.round(hx), iy = Math.round(hy);
            int sa = (int) (100f * (float) Math.sin(Math.PI * hShine) * hAlpha);
            if (sa > 0) {
                float c = -16f + 46f * hShine;
                for (int row = 0; row < 16; row++) {
                    float x = c + (16 - row) * 0.8f;
                    int x0 = Math.max(0, (int) (x - 2f)), x1 = Math.min(16, (int) (x + 2f));
                    if (x1 > x0) Gui.drawRect(ix + x0, iy + row, ix + x1, iy + row + 1, (sa << 24) | 0x00FFFFFF);
                }
            }
        }
    }

    /** Lò xo phóng to / pop / lắc cho từng ô trong kho. */
    private void updateInvSlots(List<Slot> slots, int n, int hovIdx, float dt) {
        float zs = invZoom.value ? invZoomSize.getInt() / 100f : 0f;
        float h = dt * 0.5f;
        boolean canPop = invPop.value && iOpenTime > 0.4f;   // bỏ qua giai đoạn server đang đổ item vào lúc mở GUI
        for (int i = 0; i < n; i++) {
            ItemStack s = slots.get(i).getStack();
            Object id = s == null ? null : s.getItem();
            int size = s == null ? 0 : s.stackSize;

            if (canPop && s != null && (id != iLastItem[i] || size > iLastSize[i])) {
                iVel[i] += id != iLastItem[i] ? 5f : 3.5f;
            }
            iLastItem[i] = id;
            iLastSize[i] = size;

            float t = i == hovIdx ? 1f + zs : 1f;
            float v = iVel[i];
            float x = iScl[i];
            for (int k = 0; k < 2; k++) {
                v += (SPRING_K * (t - x) - SPRING_C * v) * h;
                x += v * h;
            }
            if (x < 0.5f) x = 0.5f; else if (x > 2.5f) x = 2.5f;
            if (Math.abs(t - x) < 0.0005f && Math.abs(v) < 0.005f) { x = t; v = 0f; }
            iScl[i] = x;
            iVel[i] = v;

            if (iWobT[i] >= 0f) {
                iWobT[i] += dt;
                if (iWobT[i] > 0.7f) iWobT[i] = -1f;
            }
        }
    }

    private float wobbleAngle(int i) {
        if (iWobT[i] < 0f) return 0f;
        float t = iWobT[i];
        return 14f * iWobDir[i] * (float) Math.exp(-7f * t) * (float) Math.sin(24f * t);
    }

    private void drawInvItem(Minecraft mc, RenderItem ri, Slot slot, int i, int gl, int gt, float sc, float ang) {
        ItemStack s = slot.getStack();
        if (s == null) return;
        int x = gl + slot.xDisplayPosition, y = gt + slot.yDisplayPosition;
        float cx = x + 8f, cy = y + 8f;
        // Item nhấc lên theo độ phóng to (nảy theo lò xo); luôn nhỏ hơn phần phình ra nên không lộ item vanilla bên dưới
        float lift = invLift.value ? Math.max(-1.5f, Math.min(3.5f, (sc - 1f) * 6f)) : 0f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy - lift, 0f);
        if (ang != 0f) GlStateManager.rotate(ang, 0f, 0f, 1f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.translate(-cx, -cy, 0f);
        ri.renderItemAndEffectIntoGUI(s, x, y);
        GlStateManager.popMatrix();

        // Số lượng / thanh độ bền vẽ lại KHÔNG scale để luôn nằm đúng chỗ, đè lên item phóng to
        ri.renderItemOverlays(mc.fontRendererObj, s, x, y);
    }
}
