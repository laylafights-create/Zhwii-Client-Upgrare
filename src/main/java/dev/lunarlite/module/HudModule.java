package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.common.config.Configuration;

/** Module có hiển thị trên màn hình: kéo thả, đổi kích thước, nền/viền và rainbow riêng. */
public abstract class HudModule extends Module {
    public int x, y;
    private final int defX, defY;

    public final SliderSetting scale;
    public final ColorSetting color;
    public final BoolSetting background, rounded, border, shadow;
    public final SliderSetting bgOpacity;
    private long shownAt, lastRender;

    protected HudModule(String id, String name, String desc, Category cat, boolean def, int x, int y) {
        super(id, name, desc, cat, def);
        this.x = this.defX = x;
        this.y = this.defY = y;

        scale = add(new SliderSetting("scale", "Scale", 1.0, 0.5, 2.5, 0.05, "x"), "Appearance");
        color = add(new ColorSetting("color", "Text Color", 0xFFFFFF), "Appearance");
        background = add(new BoolSetting("background", "Background", true), "Appearance");
        bgOpacity = add(new SliderSetting("bg_opacity", "Background opacity", 50, 0, 100, 1, "%"), "Appearance", background);
        rounded = add(new BoolSetting("rounded", "Rounded Corners", true), "Appearance", background);
        border = add(new BoolSetting("border", "Border", true), "Appearance");
        shadow = add(new BoolSetting("shadow", "Text Shadow", true), "Appearance");
        new RainbowSettings(this);
    }

    public abstract int getWidth(boolean preview);
    public abstract int getHeight(boolean preview);
    /** Vẽ tại gốc (0,0): HudModule đã tự dịch chuyển và phóng to bằng GL. */
    protected abstract void draw(int x, int y, boolean preview);

    public float getScale() { return scale.getFloat(); }

    /** Kích thước thật trên màn hình (đã nhân tỉ lệ). */
    public int boxW(boolean preview) { return Math.round(getWidth(preview) * getScale()); }
    public int boxH(boolean preview) { return Math.round(getHeight(preview) * getScale()); }

    public int renderX(ScaledResolution sr, boolean preview) {
        return Math.max(0, Math.min(x, sr.getScaledWidth() - boxW(preview)));
    }

    public int renderY(ScaledResolution sr, boolean preview) {
        return Math.max(0, Math.min(y, sr.getScaledHeight() - boxH(preview)));
    }

    public void resetPosition() {
        x = defX;
        y = defY;
        scale.reset();
    }

    public void render(ScaledResolution sr, boolean preview) {
        if (getWidth(preview) <= 0 || getHeight(preview) <= 0) return;
        float s = getScale();
        ThemeModule.apply(); // màu nhấn của HUD theo "Màu giao diện"
        // animation hiện ra: phóng nhẹ + trượt lên khi HUD vừa xuất hiện (bật module, có combo mới...)
        long now = System.currentTimeMillis();
        if (now - lastRender > 120L) shownAt = now;
        lastRender = now;
        float e = (preview || !AnimationsModule.hud()) ? 1f
                : AnimationsModule.ease((now - shownAt) * AnimationsModule.speed() / 260f);
        float bw = boxW(preview), bh = boxH(preview);
        float k = 0.86f + 0.14f * e;
        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX(sr, preview) + bw / 2f, renderY(sr, preview) + bh / 2f - (1f - e) * 6f, 0f);
        GlStateManager.scale(k, k, 1f);
        GlStateManager.translate(-bw / 2f, -bh / 2f, 0f);
        GlStateManager.scale(s, s, 1f);
        draw(0, 0, preview);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
    }

    // ---------------------------------------------------------------- helpers

    protected FontRenderer fr() { return Minecraft.getMinecraft().fontRendererObj; }

    protected int bgColor() {
        return ((int) (bgOpacity.getFloat() / 100f * 255f)) << 24;
    }

    protected int textColor(float pos) { return rainbow.resolve(color.rgb(), pos); }

    /** Màu nhấn của HUD: theo rainbow nếu đang bật, ngược lại là màu giao diện. */
    protected int accentColor(float pos) {
        return rainbow.active() ? rainbow.color(pos) : Draw.ACCENT;
    }

    /** Nền + viền của cả khung. */
    protected void drawBox(int x, int y, int w, int h) {
        if (background.value) fillRect(x, y, w, h, bgColor());
        if (border.value) drawBorder(x, y, w, h);
    }

    protected void fillRect(int x, int y, int w, int h, int argb) {
        if ((argb >>> 24) == 0) return;
        if (rounded.value && background.value) Draw.rrect(x, y, x + w, y + h, 3, argb);
        else Gui.drawRect(x, y, x + w, y + h, argb);
    }

    protected void drawBorder(int x, int y, int w, int h) {
        if (!rainbow.active()) {
            int c = Draw.withAlpha(Draw.ACCENT, 0x99);
            if (rounded.value && background.value) Draw.ring(x, y, x + w, y + h, c);
            else Draw.outline(x, y, x + w, y + h, c);
            return;
        }
        for (int i = 0; i < w; i += 2) {
            int c = rainbow.color(i);
            int ww = Math.min(2, w - i);
            Gui.drawRect(x + i, y, x + i + ww, y + 1, c);
            Gui.drawRect(x + i, y + h - 1, x + i + ww, y + h, c);
        }
        for (int j = 1; j < h - 1; j += 2) {
            int c0 = rainbow.color(j);
            int c1 = rainbow.color(w + j);
            int hh = Math.min(2, h - 1 - j);
            Gui.drawRect(x, y + j, x + 1, y + j + hh, c0);
            Gui.drawRect(x + w - 1, y + j, x + w, y + j + hh, c1);
        }
    }

    /** Vẽ chữ theo màu/rainbow/shadow của module. {@code pos}: vị trí để tính dải màu. */
    protected void drawText(String text, float tx, float ty, float pos) {
        rainbow.drawString(fr(), text, tx, ty, color.rgb(), shadow.value, pos);
    }

    // ------------------------------------------------------------------ config

    @Override
    public void load(Configuration c) {
        super.load(c);
        x = c.getInt("x", id, defX, 0, 10000, "Vị trí X");
        y = c.getInt("y", id, defY, 0, 10000, "Vị trí Y");
    }

    @Override
    public void save(Configuration c) {
        super.save(c);
        c.get(id, "x", defX).set(x);
        c.get(id, "y", defY).set(y);
    }
}
