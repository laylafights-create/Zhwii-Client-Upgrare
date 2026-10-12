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
 *  - Ô chọn trượt mượt (có nảy nhẹ) thay vì nhảy cái rụp.
 *  - Item đang cầm nhấc lên + phóng to.
 *  - Item nhặt thêm / đổi item thì "pop".
 *
 * Module này vẽ lại hotbar thay cho vanilla (huỷ HOTBAR của Forge rồi tự vẽ).
 */
public class HotbarAnimationModule extends Module {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    /** zLevel của Gui là protected nên dùng lớp con riêng để vẽ texture hotbar. */
    private static final class HotbarGui extends Gui {
        void setZ(float z) { this.zLevel = z; }
    }

    private final HotbarGui gui = new HotbarGui();

    private final BoolSetting slide = add(new BoolSetting("slide", "Ô chọn trượt mượt", true));
    private final SliderSetting slideSpeed = add(new SliderSetting("slide_speed", "Tốc độ trượt", 18, 6, 100, 2, ""));
    private final BoolSetting lift = add(new BoolSetting("lift", "Nhấc item đang cầm", true));
    private final BoolSetting pop = add(new BoolSetting("pop", "Pop khi nhặt/đổi item", true));

    private final float[] liftAnim = new float[9];
    private final float[] popAnim = new float[9];
    private final Object[] lastItem = new Object[9];
    private final int[] lastSize = new int[9];

    private float selPos = -1f;   // vị trí ô chọn (đơn vị: ô), mượt
    private float selVel = 0f;    // dùng cho hiệu ứng nảy nhẹ khi tới nơi
    private long lastNanos = 0L;

    public HotbarAnimationModule() {
        super("hotbar_animation", "Hotbar Animation", "Animation cho thanh hotbar: trượt, nhấc item, pop.", Category.PVP, true);
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

        int cur = p.inventory.currentItem;
        if (selPos < 0f) selPos = cur;

        // Ô chọn: lò xo giảm chấn nhẹ -> trượt tới ô mới và hơi nảy.
        if (slide.value) {
            float k = slideSpeed.getInt() * 4f;       // độ cứng
            float c = 2f * (float) Math.sqrt(k) * 0.8f; // giảm chấn ~0.8 (nảy nhẹ)
            selVel += (k * (cur - selPos) - c * selVel) * dt;
            selPos += selVel * dt;
            if (Math.abs(cur - selPos) < 0.001f && Math.abs(selVel) < 0.01f) { selPos = cur; selVel = 0f; }
        } else {
            selPos = cur;
            selVel = 0f;
        }

        int cx = sr.getScaledWidth() / 2;
        int h = sr.getScaledHeight();

        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(WIDGETS);
        gui.setZ(-90f);
        gui.drawTexturedModalRect(cx - 91, h - 22, 0, 0, 182, 22);
        gui.drawTexturedModalRect(Math.round(cx - 91 - 1 + selPos * 20f), h - 22 - 1, 0, 22, 24, 22);
        gui.setZ(0f);

        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < 9; i++) {
            drawItem(mc, p, i, cx - 90 + i * 20 + 2, h - 16 - 3, i == cur, dt, partial);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    private void drawItem(Minecraft mc, EntityPlayer p, int i, int x, int y, boolean selected, float dt, float partial) {
        ItemStack s = p.inventory.mainInventory[i];

        // Nhấc item đang cầm.
        float target = (lift.value && selected) ? 1f : 0f;
        liftAnim[i] += (target - liftAnim[i]) * (1f - (float) Math.exp(-16f * dt));

        // Phát hiện nhặt thêm / đổi item để pop.
        Object id = s == null ? null : s.getItem();
        int size = s == null ? 0 : s.stackSize;
        if (pop.value && s != null && (id != lastItem[i] || size > lastSize[i])) popAnim[i] = 1f;
        lastItem[i] = id;
        lastSize[i] = size;
        if (popAnim[i] > 0f) popAnim[i] = Math.max(0f, popAnim[i] - dt * 4f);

        if (s == null) return;

        float l = liftAnim[i];
        float popScale = 0.25f * popAnim[i] * (float) Math.sin(popAnim[i] * Math.PI);
        float sc = 1f + 0.12f * l + popScale;
        float cx = x + 8f, cy = y + 8f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy - 3f * l, 0f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.translate(-cx, -cy, 0f);

        // Hiệu ứng "co giãn" vanilla khi vừa nhặt (animationsToGo).
        float f = s.animationsToGo - partial;
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

        GlStateManager.popMatrix();
    }
}
