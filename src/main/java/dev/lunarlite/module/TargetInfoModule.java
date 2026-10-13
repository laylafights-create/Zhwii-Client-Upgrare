package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Compact target card (avatar | name + distance / hp text / health bar) for any living entity: players and mobs. */
public class TargetInfoModule extends HudModule {
    private final BoolSetting showAvatar = add(new BoolSetting("show_avatar", "Hiện avatar", true), "Thông tin mục tiêu");
    private final BoolSetting showHealth = add(new BoolSetting("show_health", "Hiện máu", true), "Thông tin mục tiêu");
    private final BoolSetting showHeartText = add(new BoolSetting("health_text", "Hiện % máu", true), "Thông tin mục tiêu", showHealth);
    private final BoolSetting showDistance = add(new BoolSetting("show_distance", "Hiện khoảng cách", false), "Thông tin mục tiêu");
    private final BoolSetting showMaxHealth = add(new BoolSetting("show_max_health", "Hiện số máu", false), "Thông tin mục tiêu", showHealth);
    private final ModeSetting nameMode = add(new ModeSetting("name_mode", "Kiểu tên", 0, "Tên hiển thị", "Tên tài khoản", "Rút gọn", "Ẩn"), "Thông tin mục tiêu");
    private final ModeSetting healthMode = add(new ModeSetting("health_mode", "Màu thanh máu", 2, "Chuyển màu", "Xanh lá", "Đỏ", "Tùy chỉnh"), "Thông tin mục tiêu", showHealth);
    private final SliderSetting range = add(new SliderSetting("range", "Tầm xa tối đa", 8, 1, 64, 1, " block"), "Thông tin mục tiêu");
    private final SliderSetting barHeight = add(new SliderSetting("bar_height", "Độ cao thanh máu", 4, 2, 10, 1, " px"), "Thông tin mục tiêu", showHealth);
    private final SliderSetting cardWidth = add(new SliderSetting("card_width", "Độ rộng thẻ", 130, 100, 240, 2, " px"), "Thông tin mục tiêu");
    private final ColorSetting healthColor = add(new ColorSetting("health_color", "Màu máu tùy chỉnh", 0x55FF55), "Thông tin mục tiêu", showHealth);
    private final ColorSetting nameColor = add(new ColorSetting("name_color", "Màu tên", 0xFFFFFF), "Thông tin mục tiêu");

    private static final int AVATAR = 30;
    private static final int PAD = 6;
    private static final int ROW = 12;      // name row
    private static final int TEXT_ROW = 11; // hp text row
    private static final int GAP = 2;       // gap above the bar

    /** True only while the avatar model is being drawn, so its name tag can be cancelled. */
    private boolean drawingAvatar;

    /** Team "always show name tags" bypasses hideGUI, so cancel the label event itself. */
    @SubscribeEvent
    public void onNameTag(RenderLivingEvent.Specials.Pre event) {
        if (drawingAvatar) event.setCanceled(true);
    }

    public TargetInfoModule() {
        super("targetinfo", "Thông tin mục tiêu", "Hiện tên, avatar và máu của mục tiêu (người chơi / mob) dưới tâm ngắm.", Category.PVP, true, 4, 270);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private EntityLivingBase target() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.objectMouseOver == null) return null;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || !(hit.entityHit instanceof EntityLivingBase)) return null;
        EntityLivingBase e = (EntityLivingBase) hit.entityHit;
        return mc.thePlayer.getDistanceToEntity(e) <= range.getFloat() ? e : null;
    }

    private String targetName(EntityLivingBase e, boolean preview) {
        if (preview || e == null) return "Skeleton";
        if (nameMode.is("Ẩn")) return "";
        String n = nameMode.is("Tên tài khoản") && e instanceof EntityPlayer
                ? ((EntityPlayer) e).getGameProfile().getName() : e.getDisplayName().getUnformattedText();
        if (nameMode.is("Rút gọn") && n.length() > 12) n = n.substring(0, 12) + "...";
        return n;
    }

    @Override public int getWidth(boolean preview) { return cardWidth.getInt(); }

    /** Height of the text/bar column only (no padding). */
    private int contentHeight() {
        int content = 0;
        if (!nameMode.is("Ẩn") || showDistance.value) content += ROW;
        if (showHealth.value) {
            if (showHeartText.value) content += TEXT_ROW;
            if (content > 0) content += GAP;
            content += barHeight.getInt();
        }
        return content;
    }

    @Override public int getHeight(boolean preview) {
        int h = contentHeight() + PAD * 2;
        return showAvatar.value ? Math.max(h, AVATAR + 8) : Math.max(h, 20);
    }

    /** Rounded horizontal bar segment; falls back to a plain rect when too thin to round. */
    private static void seg(int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        int r = Math.min((y2 - y1) / 2, (x2 - x1) / 2);
        if (r >= 1) Draw.rrect(x1, y1, x2, y2, r, color);
        else Gui.drawRect(x1, y1, x2, y2, color);
    }

    /** Health bar: dark track, lagging "damage" trail, shaded main fill with a bright end cap, quarter ticks. */
    private static void healthBar(int x, int y, int w, int h, float fill, float trail, int color) {
        seg(x - 1, y - 1, x + w + 1, y + h + 1, 0x40000000);     // soft outer frame
        seg(x, y, x + w, y + h, 0x90000000);                       // track
        int tx = x + Math.round(w * trail);
        int fx = x + Math.round(w * fill);
        if (tx > fx) seg(x, y, tx, y + h, 0x66FFFFFF);
        seg(x, y, fx, y + h, color);
        if (h >= 4 && fx - x > 3) {
            Gui.drawRect(x + 1, y + 1, fx - 1, y + 2, 0x48FFFFFF);               // top shine
            Gui.drawRect(x + 1, y + h - 2, fx - 1, y + h - 1, 0x30000000);       // bottom shade
            Gui.drawRect(fx - 1, y + 1, fx, y + h - 1, 0x60FFFFFF);              // bright end cap
        }
        if (h >= 4) {
            for (int i = 1; i < 4; i++) {
                int tickX = x + w * i / 4;
                Gui.drawRect(tickX, y + 1, tickX + 1, y + h - 1, 0x30000000);
            }
        }
    }

    /** Small rounded pill with centred text, tinted by {@code tint}. */
    private void pill(int x, int y, String text, int tint, int textColor) {
        int pw = fr().getStringWidth(text) + 8;
        Draw.rrect(x, y, x + pw, y + 10, 4, Draw.withAlpha(tint, 0x40));
        fr().drawString(text, x + 4, y + 1, textColor, false);
    }

    /** Card frame: soft coloured glow, base box, health-tinted border + inner hairline, top sheen, corner brackets. */
    private void drawFrame(int x, int y, int w, int h, int accent) {
        int c = accent & 0xFFFFFF;
        // outer glow (two soft layers outside the box)
        Draw.rrect(x - 2, y - 2, x + w + 2, y + h + 2, 7, Draw.withAlpha(c, 0x14));
        Draw.rrect(x - 1, y - 1, x + w + 1, y + h + 1, 6, Draw.withAlpha(c, 0x22));
        drawBox(x, y, w, h);
        // top sheen + bottom shade give the panel depth
        Draw.rrect(x + 2, y + 2, x + w - 2, y + h / 2, 4, 0x10FFFFFF);
        Draw.rrect(x + 2, y + h - h / 4, x + w - 2, y + h - 2, 4, 0x14000000);
        // border (health colour) + inner hairline
        Draw.ring(x, y, x + w, y + h, Draw.withAlpha(c, 0x99));
        Draw.ring(x + 1, y + 1, x + w - 1, y + h - 1, 0x16FFFFFF);
        // corner brackets
        int b = Draw.withAlpha(c, 0xE0), L = 6;
        Gui.drawRect(x + 3, y + 3, x + 3 + L, y + 4, b);
        Gui.drawRect(x + 3, y + 3, x + 4, y + 3 + L, b);
        Gui.drawRect(x + w - 3 - L, y + 3, x + w - 3, y + 4, b);
        Gui.drawRect(x + w - 4, y + 3, x + w - 3, y + 3 + L, b);
        Gui.drawRect(x + 3, y + h - 4, x + 3 + L, y + h - 3, b);
        Gui.drawRect(x + 3, y + h - 3 - L, x + 4, y + h - 3, b);
        Gui.drawRect(x + w - 3 - L, y + h - 4, x + w - 3, y + h - 3, b);
        Gui.drawRect(x + w - 4, y + h - 3 - L, x + w - 3, y + h - 3, b);
    }

    private void drawAvatar(EntityLivingBase e, int ax, int ay, boolean preview) {
        if (e == null || preview) {
            Draw.rrect(ax, ay, ax + AVATAR, ay + AVATAR, 4, 0x40FFFFFF);
            String q = "?";
            fr().drawString(q, ax + (AVATAR - fr().getStringWidth(q)) / 2, ay + (AVATAR - 8) / 2, 0xFFFFFFFF, true);
            return;
        }
        // Render only the entity model (player or mob), no name tag, no leftover GL state.
        Minecraft mc = Minecraft.getMinecraft();
        float hgt = Math.max(0.3f, e.height);
        float wid = Math.max(0.3f, e.width);
        int scale = Math.max(4, (int) Math.min(AVATAR / hgt, AVATAR / (wid * 1.3f)));
        int px = ax + AVATAR / 2;
        int py = ay + AVATAR - 1;

        // Vanilla only draws name tags while the GUI is visible, so hide it for this one draw call.
        boolean hideGui = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
        drawingAvatar = true;
        // The model is tinted by the current GL colour (the black card background made it a black silhouette).
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);   // don't let world depth hide the model
        try {
            GlStateManager.pushMatrix();
            GuiInventory.drawEntityOnScreen(px, py, scale, 0f, -e.getEyeHeight() * scale, e);
            GlStateManager.popMatrix();
        } finally {
            mc.gameSettings.hideGUI = hideGui;
            drawingAvatar = false;
        }
        // Reset state so the rest of the HUD (text, other modules) draws normally.
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private EntityLivingBase lastTarget;
    private float shownRatio = 1f;
    private float trailRatio = 1f;

    @Override protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase e = preview ? null : target();
        if (!preview && e == null) {
            lastTarget = null;
            return;
        }
        int w = getWidth(preview), h = getHeight(preview);

        float hp = preview || e == null ? 8.2f : Math.max(0f, e.getHealth());
        float maxHp = preview || e == null ? 20f : Math.max(1f, e.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, hp / maxHp));
        // thanh máu chạy mượt; vệt trắng tụt chậm phía sau khi mất máu; đổi mục tiêu thì nhảy thẳng
        if (preview || e != lastTarget) {
            shownRatio = ratio;
            trailRatio = ratio;
        } else {
            shownRatio += (ratio - shownRatio) * 0.2f;
            trailRatio = trailRatio > shownRatio ? trailRatio + (shownRatio - trailRatio) * 0.06f : shownRatio;
        }
        lastTarget = e;

        int hc;
        if (healthMode.is("Xanh lá")) hc = 0xFF5CE08A;
        else if (healthMode.is("Đỏ")) hc = 0xFFFF5C5C;
        else if (healthMode.is("Tùy chỉnh")) hc = 0xFF000000 | healthColor.rgb();
        else hc = Draw.lerp(0xFFFF5C5C, 0xFF5CE08A, shownRatio);

        drawFrame(x, y, w, h, hc);

        int avX = x + PAD, avY = y + (h - AVATAR) / 2;
        int textX = showAvatar.value ? avX + AVATAR + 8 : x + 8;
        int right = x + w - PAD;
        if (showAvatar.value) {
            // dark rounded plate + thin ring tinted by the health colour
            Draw.rrect(avX - 2, avY - 2, avX + AVATAR + 2, avY + AVATAR + 2, 5, Draw.withAlpha(hc, 0x30));   // soft glow
            Draw.rrect(avX - 1, avY - 1, avX + AVATAR + 1, avY + AVATAR + 1, 4, 0xA0000000);
            Draw.rrect(avX, avY, avX + AVATAR, avY + AVATAR / 2, 3, 0x14FFFFFF);                                // top sheen
            Draw.ring(avX - 1, avY - 1, avX + AVATAR + 1, avY + AVATAR + 1, Draw.withAlpha(hc, 0xCC));
        }

        // text column is vertically centred in the card
        int lineY = y + (h - contentHeight()) / 2;

        String ds = null;
        int pillW = 0;
        if (showDistance.value) {
            double d = preview || e == null || mc.thePlayer == null ? 3.2 : mc.thePlayer.getDistanceToEntity(e);
            ds = String.format(java.util.Locale.ROOT, "%.1fm", d);
            pillW = fr().getStringWidth(ds) + 8;
        }
        String name = targetName(e, preview);
        if (!nameMode.is("Ẩn") && name.length() > 0) {
            int maxW = Math.max(10, right - textX - (pillW > 0 ? pillW + 4 : 0));
            fr().drawString(fr().trimStringToWidth(name, maxW), textX, lineY + 1, 0xFF000000 | nameColor.rgb(), shadow.value);
        }
        if (ds != null) {
            int px = right - pillW;
            pill(px, lineY, ds, 0xFFFFFF, 0xFFD8D8D8);
        }
        if (!nameMode.is("Ẩn") || ds != null) lineY += ROW;

        if (showHealth.value) {
            if (showHeartText.value) {
                String pct = Math.round(ratio * 100f) + "%";
                pill(textX, lineY, pct, hc & 0xFFFFFF, hc);
                if (showMaxHealth.value) {
                    String hpTxt = String.format(java.util.Locale.ROOT, "%.1f/%d", hp, (int) maxHp);
                    fr().drawString(hpTxt, right - fr().getStringWidth(hpTxt), lineY + 1, 0xFFC0C0C0, false);
                }
                lineY += TEXT_ROW;
            }
            if (!nameMode.is("Ẩn") || showDistance.value || showHeartText.value) lineY += GAP;
            healthBar(textX, lineY, right - textX, barHeight.getInt(), shownRatio, trailRatio, hc);
        }
        // Avatar last: the 3D model writes depth, so nothing else may be drawn after it with a broken state.
        if (showAvatar.value) drawAvatar(e, avX, avY, preview);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
