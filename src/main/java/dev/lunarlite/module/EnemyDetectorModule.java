package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.SliderSetting;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;

/**
 * Phát hiện người chơi khác trong bán kính tuỳ chỉnh: hiện danh sách (tên, máu, khoảng cách),
 * tô màu theo độ gần và báo âm thanh khi có người mới bước vào bán kính.
 *
 * Đã gộp luôn "Enemy Outline": viền ôm sát cơ thể kẻ địch (silhouette outline) giống hiệu ứng Glowing.
 *  1. Vẽ hình dáng người chơi (thân + giáp + vật cầm + cape...) ĐÚNG 1 LẦN vào framebuffer phụ.
 *  2. "Giãn" hình dáng đó bằng cách vẽ lại chính texture ấy (mỗi lần chỉ 1 quad) dịch vài pixel theo nhiều hướng,
 *     chỉ ghi stencil. Bit 0 = thân, bit 1 = viền chính, bit 2 = hào quang mềm. Mọi thứ bị scissor giới hạn trong hộp bao người chơi.
 *  3. Tô quad chỉ ở pixel có stencil tương ứng => viền đều, bo tròn, liền mạch.
 */
public class EnemyDetectorModule extends HudModule {
    private static final int W = 140, HEAD = 20, ROW_FULL = 20, ROW_COMPACT = 12;

    private final SliderSetting radius = add(new SliderSetting("radius", "Bán kính phát hiện", 30, 5, 100, 1, " block"));
    private final SliderSetting maxRows = add(new SliderSetting("max_rows", "Số người hiển thị tối đa", 5, 1, 8, 1, ""));
    private final BoolSetting showHealth = add(new BoolSetting("show_health", "Hiện thanh máu", true));
    private final BoolSetting showFace = add(new BoolSetting("show_face", "Hiện avatar", true));
    private final BoolSetting showDir = add(new BoolSetting("show_dir", "Hiện mũi tên hướng", true));
    // ---- Tuỳ chỉnh mũi tên (hình tam giác chỉ hướng)
    private final SliderSetting rainbowSpeed = add(new SliderSetting("rainbow_speed", "Tốc độ rainbow (giây/vòng)", 4.0, 1.0, 10.0, 0.5, "s"));
    private final SliderSetting arrowSize = add(new SliderSetting("arrow_size", "Cỡ mũi tên", 1.0, 0.6, 2.0, 0.1, "x"), "Arrow", showDir);
    private final BoolSetting arrowRainbow = add(new BoolSetting("arrow_rainbow", "Rainbow mũi tên", false), "Arrow", showDir);
    private final BoolSetting arrowOutline = add(new BoolSetting("arrow_outline", "Viền mũi tên (Outline)", false), "Arrow", showDir);
    private final ColorSetting arrowOutColor = add(new ColorSetting("arrow_outline_color", "Màu viền", 0xFFFFFF), "Arrow", showDir);
    private final SliderSetting arrowOutWidth = add(new SliderSetting("arrow_outline_width", "Độ dày viền", 1.0, 0.5, 3.0, 0.25, "px"), "Arrow", showDir);
    private final BoolSetting arrowOutRainbow = add(new BoolSetting("arrow_outline_rainbow", "Rainbow viền", false), "Arrow", showDir);
    private final BoolSetting arrowGlow = add(new BoolSetting("arrow_glow", "Hào quang mũi tên", false), "Arrow", showDir);

    private final BoolSetting ignoreTeam = add(new BoolSetting("ignore_team", "Bỏ qua đồng đội (cùng team)", true));
    private final BoolSetting ignoreNpc = add(new BoolSetting("ignore_npc", "Bỏ qua NPC", true));
    private final BoolSetting alert = add(new BoolSetting("alert", "Báo âm thanh khi có người vào", true));
    private final ColorSetting alertColor = add(new ColorSetting("alert_color", "Màu cảnh báo", 0xFF5555));
    private final BoolSetting showList = add(new BoolSetting("show_list", "Hiện danh sách", true));

    // ---- Viền kẻ địch (silhouette outline), dùng chung bán kính / lọc đồng đội / NPC ở trên
    private final BoolSetting outline = add(new BoolSetting("outline", "Enemy Outline", false), "Outline");
    private final ColorSetting outColor = add(new ColorSetting("outline_color", "Color", 0xFFFFFF), "Outline", outline);
    private final SliderSetting outWidth = add(new SliderSetting("outline_width", "Line Width", 3.0, 1.0, 10.0, 0.5, "px"), "Outline", outline);
    private final SliderSetting outSmooth = add(new SliderSetting("outline_smooth", "Smoothness", 8, 8, 32, 2, ""), "Outline", outline);
    private final BoolSetting outGlow = add(new BoolSetting("outline_glow", "Glow", true), "Outline", outline);
    private final SliderSetting outMax = add(new SliderSetting("outline_max", "Max Players", 4, 1, 10, 1, ""), "Outline", outline);
    private final BoolSetting throughWalls = add(new BoolSetting("outline_walls", "Through Walls", true), "Outline", outline);
    private final BoolSetting outInvisible = add(new BoolSetting("outline_invisible", "Show Invisible", true), "Outline", outline);
    private final BoolSetting outRainbow = add(new BoolSetting("outline_rainbow", "Rainbow Outline", true), "Outline", outline);
    private final BoolSetting outDebug = add(new BoolSetting("outline_debug", "Debug (chat)", false), "Outline", outline);

    private static class Entry {
        final int id;
        final String name;
        final float dist, hp, bearing;
        final ResourceLocation skin;

        Entry(int id, String name, float dist, float hp, float bearing, ResourceLocation skin) {
            this.id = id;
            this.name = name;
            this.dist = dist;
            this.hp = hp;
            this.bearing = bearing;
            this.skin = skin;
        }
    }

    private static final List<Entry> SAMPLE = new ArrayList<Entry>();
    static {
        SAMPLE.add(new Entry(-1, "Steve", 8.4f, 20f, 0f, null));
        SAMPLE.add(new Entry(-2, "Alex", 17.2f, 14f, 70f, null));
        SAMPLE.add(new Entry(-3, "Notch", 26.9f, 6f, -140f, null));
    }

    private List<Entry> found = new ArrayList<Entry>();
    private final Set<Integer> inside = new HashSet<Integer>();
    /** Người được viền (tính 1 lần mỗi tick, tối đa "Max Players" người gần nhất) để khỏi lọc lại mỗi khung hình. */
    private final Set<Integer> outlineIds = new HashSet<Integer>();
    private int cooldown;
    private int flash; // tick còn lại của hiệu ứng nháy khi có người mới vào
    private final Map<Integer, Float> appear = new HashMap<Integer, Float>(); // tiến độ hiện ra của từng hàng
    private final Map<Integer, Float> hpAnim = new HashMap<Integer, Float>(); // máu hiển thị (chạy mượt) của từng hàng

    public EnemyDetectorModule() {
        super("enemy_detector", "Phát hiện kẻ địch", "Hiện người chơi khác trong bán kính bạn chọn, kèm khoảng cách, máu và viền kẻ địch.",
                Category.PVP, true, 4, 20);
    }

    // ------------------------------------------------------------------ quét

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.theWorld == null || mc.thePlayer == null) {
            found = new ArrayList<Entry>();
            inside.clear();
            outlineIds.clear();
            miss.clear();
            lobbyTicks = 0;
            releaseSilhouette();
            flash = 0;
            return;
        }
        if (cooldown > 0) cooldown--;
        if (flash > 0) flash--;

        float r = radius.getInt();
        List<Entry> list = new ArrayList<Entry>();
        Set<Integer> now = new HashSet<Integer>();
        boolean newcomer = false;

        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.getHealth() <= 0f || p.isSpectator()) continue;
            float d = mc.thePlayer.getDistanceToEntity(p);
            if (d > r) continue;
            if (ignoreNpc.value && !HypixelUtil.isRealPlayer(p)) continue;
            if (ignoreTeam.value && TeamUtil.isTeammate(mc.thePlayer, p)) continue;

            float bearing = (float) (Math.atan2(-(p.posX - mc.thePlayer.posX), p.posZ - mc.thePlayer.posZ) * 180.0 / Math.PI);
            ResourceLocation skin = p instanceof AbstractClientPlayer ? ((AbstractClientPlayer) p).getLocationSkin() : null;
            list.add(new Entry(p.getEntityId(), p.getName(), d, p.getHealth() + p.getAbsorptionAmount(), bearing, skin));
            now.add(p.getEntityId());
            if (!inside.contains(p.getEntityId())) newcomer = true;
        }
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Float.compare(a.dist, b.dist);
            }
        });
        found = list;
        inside.clear();
        inside.addAll(now);

        if (newcomer) flash = 24;
        if (alert.value && newcomer && cooldown == 0) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.5f));
            cooldown = 20; // tối đa 1 lần / giây
        }

        // ---- danh sách người được viền (xem updateOutlineTargets)
        if (outline.value) {
            updateOutlineTargets(mc);
        } else {
            outlineIds.clear();
            miss.clear();
            lobbyTicks = 0;
            releaseSilhouette();
        }
    }

    // ----------------------------------------------------------------- hiển thị

    private int rowH() {
        return showHealth.value || showFace.value ? ROW_FULL : ROW_COMPACT;
    }

    private int rows(boolean preview) {
        int n = preview ? SAMPLE.size() : found.size();
        return Math.min(n, maxRows.getInt());
    }

    @Override public int getWidth(boolean preview) { return preview || showList.value ? W : 0; }

    @Override
    public int getHeight(boolean preview) {
        int n = rows(preview);
        return HEAD + (n > 0 ? n * rowH() + 3 : 0);
    }

    // ------------------------------------------------------------ bảng màu & tiện ích vẽ

    private static final int C_SAFE = 0xFF5CE08A, C_WARN = 0xFFFFC857, C_DANGER = 0xFFFF5C5C;

    private static int withAlpha(int rgb, int a) {
        return ((a & 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Càng gần càng đỏ. */
    private int distColor(float d) {
        float ratio = d / Math.max(1f, radius.getInt());
        if (ratio < 0.34f) return C_DANGER;
        if (ratio < 0.67f) return C_WARN;
        return C_SAFE;
    }

    private static int hpColor(float hp) {
        float r = hp / 20f;
        return r > 0.7f ? C_SAFE : r > 0.35f ? C_WARN : C_DANGER;
    }

    /** Hình chữ nhật bo góc (bo kiểu bậc thang theo từng dòng quét, nhẹ và sắc nét ở GUI scale nhỏ). */
    private static void rrect(int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
            return;
        }
        Gui.drawRect(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            Gui.drawRect(x + inset, y + i, x + w - inset, y + i + 1, c);
            Gui.drawRect(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    /** Viền 1px bo góc (bán kính ~3). */
    private static void ring(int x, int y, int w, int h, int c) {
        Gui.drawRect(x + 2, y, x + w - 2, y + 1, c);
        Gui.drawRect(x + 2, y + h - 1, x + w - 2, y + h, c);
        Gui.drawRect(x, y + 2, x + 1, y + h - 2, c);
        Gui.drawRect(x + w - 1, y + 2, x + w, y + h - 2, c);
        Gui.drawRect(x + 1, y + 1, x + 2, y + 2, c);
        Gui.drawRect(x + w - 2, y + 1, x + w - 1, y + 2, c);
        Gui.drawRect(x + 1, y + h - 2, x + 2, y + h - 1, c);
        Gui.drawRect(x + w - 2, y + h - 2, x + w - 1, y + h - 1, c);
    }

    /** Đường kẻ mờ dần ở hai đầu. */
    private static void fadeLine(int x, int y, int w, int rgb, int maxA) {
        final int seg = 4;
        for (int i = 0; i < w; i += seg) {
            float t = (i + seg / 2f) / w;
            int a = Math.round(maxA * (float) Math.sin(Math.PI * t));
            if (a > 3) Gui.drawRect(x + i, y, Math.min(x + w, x + i + seg), y + 1, withAlpha(rgb, a));
        }
    }

    /** Mặt người chơi (kèm lớp mũ) trong khung bo góc. */
    private void drawFace(ResourceLocation skin, int fx, int fy, int size, int frame) {
        rrect(fx - 1, fy - 1, size + 2, size + 2, 2, frame);
        if (skin == null) {
            Gui.drawRect(fx, fy, fx + size, fy + size, 0xFF8A8A8A);
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Gui.drawScaledCustomSizeModalRect(fx, fy, 8f, 8f, 8, 8, size, size, 64f, 64f);
        Gui.drawScaledCustomSizeModalRect(fx, fy, 40f, 8f, 8, 8, size, size, 64f, 64f);
        GlStateManager.disableBlend();
    }

    /** Màu cầu vồng tự tính theo thời gian, độc lập với HudModule nên bật là chạy ngay. offset tính bằng độ (0-360). */
    private int rainbowRgb(float offset) {
        float period = Math.max(500f, rainbowSpeed.getFloat() * 1000f);
        float hue = (((System.currentTimeMillis() % (long) period) / period) + offset / 360f) % 1f;
        if (hue < 0f) hue += 1f;
        return java.awt.Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF;
    }

    /**
     * Mũi tên chỉ hướng dáng gốc (đầu nhọn + khía đuôi), tô phẳng; tuỳ chọn viền / glow / rainbow. deg = 0 là phía trước mặt, dương là sang phải.
     */
    private void drawArrow(float cx, float cy, float deg, int argb, float rowOffset) {
        int fillRgb = argb & 0xFFFFFF;
        if (arrowRainbow.value) fillRgb = rainbowRgb(rowOffset);
        int lineRgb = arrowOutRainbow.value ? rainbowRgb(rowOffset + 90f) : (arrowOutColor.rgb() & 0xFFFFFF);

        final float s = arrowSize.getFloat();
        // đỉnh: mũi, cánh phải, khía đuôi, cánh trái
        final float[] vx = {0f, 2.8f * s, 0f, -2.8f * s};
        final float[] vy = {-3.6f * s, 2.6f * s, 0.8f * s, 2.6f * s};

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean wasSmooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0f);
        GlStateManager.rotate(deg, 0f, 0f, 1f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        // hào quang mềm phía sau
        if (arrowGlow.value) {
            float gr = ((fillRgb >> 16) & 255) / 255f, gg = ((fillRgb >> 8) & 255) / 255f, gb = (fillRgb & 255) / 255f;
            for (int i = 3; i >= 1; i--) {
                GlStateManager.color(gr, gg, gb, 0.07f);
                GL11.glLineWidth(1f + i * 1.2f);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (int k = 0; k < 4; k++) GL11.glVertex2f(vx[k], vy[k]);
                GL11.glEnd();
            }
        }

        // tô phẳng một màu như bản gốc
        GlStateManager.color(((fillRgb >> 16) & 255) / 255f, ((fillRgb >> 8) & 255) / 255f, (fillRgb & 255) / 255f, 1f);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(vx[0], vy[0]); GL11.glVertex2f(vx[1], vy[1]); GL11.glVertex2f(vx[2], vy[2]);
        GL11.glVertex2f(vx[0], vy[0]); GL11.glVertex2f(vx[2], vy[2]); GL11.glVertex2f(vx[3], vy[3]);
        GL11.glEnd();

        // viền
        if (arrowOutline.value) {
            GlStateManager.color(((lineRgb >> 16) & 255) / 255f, ((lineRgb >> 8) & 255) / 255f, (lineRgb & 255) / 255f, 1f);
            GL11.glLineWidth(Math.max(0.5f, arrowOutWidth.getFloat()));
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int k = 0; k < 4; k++) GL11.glVertex2f(vx[k], vy[k]);
            GL11.glEnd();
        }

        GL11.glLineWidth(1f);
        if (!wasSmooth) GL11.glDisable(GL11.GL_LINE_SMOOTH);
        if (cull) GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ vẽ HUD

    @Override
    protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = fr();
        List<Entry> es = preview ? SAMPLE : found;
        int total = es.size();
        int h = getHeight(preview);
        boolean danger = total > 0;
        int status = danger ? distColor(es.get(0).dist) : C_SAFE;          // theo kẻ địch gần nhất
        int alertRgb = alertColor.rgb() & 0xFFFFFF;
        float pulse = preview || flash <= 0 ? 0f
                : (float) (Math.sin(flash * 0.8) * 0.5 + 0.5) * Math.min(1f, flash / 8f);

        // ---- nền + viền bo góc
        if (background.value) rrect(x, y, W, h, 3, bgColor());
        if (border.value) ring(x, y, W, h, withAlpha(status, 0x88));

        // ---- thanh tiêu đề (dạng viên thuốc bên trong thẻ)
        rrect(x + 2, y + 2, W - 4, HEAD - 4, 3, 0x40000000);
        if (pulse > 0.01f) rrect(x + 2, y + 2, W - 4, HEAD - 4, 3, withAlpha(alertRgb, Math.round(pulse * 0x66)));

        int dot = danger ? withAlpha(alertRgb, 0xFF) : C_SAFE;
        int icx = x + 10, icy = y + HEAD / 2;
        rrect(icx - 4, icy - 4, 9, 9, 4, withAlpha(dot, 0x38 + Math.round(pulse * 0x70)));
        rrect(icx - 2, icy - 2, 5, 5, 2, dot);

        String title = danger ? "KẺ ĐỊCH" : "AN TOÀN";
        int ty0 = y + (HEAD - 8) / 2;
        fr.drawString("\u00a7l" + title, x + 18, ty0, dot, shadow.value);
        if (danger) {
            String n = String.valueOf(total);
            int bw = Math.max(11, fr.getStringWidth(n) + 7);
            int bx = x + 18 + fr.getStringWidth("\u00a7l" + title) + 5;
            rrect(bx, y + 5, bw, 10, 4, withAlpha(alertRgb, 0xFF));
            fr.drawString(n, bx + (bw - fr.getStringWidth(n)) / 2f, ty0, 0xFF101010, false);
        }
        String rs = radius.getInt() + "m";
        int rw = fr.getStringWidth(rs) + 7;
        rrect(x + W - 4 - rw, y + 5, rw, 10, 4, 0x30FFFFFF);
        fr.drawString(rs, x + W - 4 - rw + 3.5f, ty0, 0xFFB8B8B8, false);

        // ---- danh sách
        if (!danger) return;
        fadeLine(x + 6, y + HEAD - 1, W - 12, status & 0xFFFFFF, 0xAA);

        int rh = rowH();
        int shown = Math.min(total, maxRows.getInt());
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < shown; i++) {
            Entry e = es.get(i);
            int ry = y + HEAD + 2 + i * rh;
            int cardX = x + 3, cardW = W - 6, cardH = rh - 2;

            // tiến độ hiện ra + máu chạy mượt
            float ap = 1f, hpShow = e.hp;
            if (!preview) {
                Float cur = appear.get(e.id);
                ap = cur == null ? 0f : cur + (1f - cur) * 0.22f;
                if (ap > 0.99f) ap = 1f;
                appear.put(e.id, ap);
                Float hc = hpAnim.get(e.id);
                hpShow = hc == null ? e.hp : hc + (e.hp - hc) * 0.2f;
                hpAnim.put(e.id, hpShow);
                seen.add(e.id);
            }

            int dc = distColor(e.dist);
            boolean nearest = i == 0;

            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.round((1f - ap) * 16f), 0f, 0f); // trượt vào từ phải

            // thẻ nền của từng người
            int cardBg = nearest ? withAlpha(dc, 0x26) : 0x14FFFFFF;
            rrect(cardX, ry, cardW, cardH, 3, cardBg);
            if (ap < 1f) rrect(cardX, ry, cardW, cardH, 3, withAlpha(alertRgb, Math.round((1f - ap) * 0x55)));
            rrect(cardX + 1, ry + 3, 2, Math.max(2, cardH - 6), 1, dc);

            int cx = cardX + 6;
            if (showFace.value) {
                int fs = rh - 8;
                drawFace(e.skin, cx + 1, ry + (cardH - fs) / 2, fs, nearest ? withAlpha(dc, 0xFF) : 0xFF000000);
                cx += fs + 7;
            }

            boolean bar = showHealth.value;
            int ty = bar ? ry + 3 : ry + (cardH - 8) / 2;

            // khoảng cách trong viên thuốc màu
            int rx = cardX + cardW - 4;
            String ds = (Math.round(e.dist * 10f) / 10f) + "m";
            int pw = fr.getStringWidth(ds) + 6;
            int px = rx - pw;
            rrect(px, ty - 1, pw, 10, 3, withAlpha(dc, 0x38));
            fr.drawString(ds, px + 3, ty, dc, shadow.value);

            int nameRight = px - 4;
            if (showDir.value) {
                float rel = preview || mc.thePlayer == null ? e.bearing : e.bearing - mc.thePlayer.rotationYaw;
                int acx = px - 8, acy = ty + 2;
                rrect(acx - 5, acy - 5, 10, 10, 5, 0x40000000);
                drawArrow(acx, acy, rel, dc, i * 45f);
                nameRight = acx - 7;
            }
            String nm = fr.trimStringToWidth(e.name, Math.max(10, nameRight - cx));
            fr.drawString(nm, cx, ty, textColor(x + ry), shadow.value);

            if (bar) {
                int bx0 = cx, bx1 = cardX + cardW - 4, by = ry + 12, bw = bx1 - bx0;
                rrect(bx0, by, bw, 3, 1, 0x66000000);
                int fill = Math.round(bw * Math.max(0f, Math.min(1f, hpShow / 20f)));
                if (fill > 0) {
                    int hc = hpColor(hpShow);
                    rrect(bx0, by, fill, 3, 1, hc);
                    if (fill > 2) Gui.drawRect(bx0 + 1, by, bx0 + fill - 1, by + 1, 0x55FFFFFF); // ánh sáng mép trên
                }
            }
            GlStateManager.popMatrix();
        }
        if (!preview) {
            appear.keySet().retainAll(seen);
            hpAnim.keySet().retainAll(seen);
        }
    }

    // ======================================================================= chọn mục tiêu viền
    //
    // Chống "lúc hiện lúc không": danh sách mục tiêu KHÔNG được đổi chỉ vì một tick dao động.
    //  - Đang được viền thì được lố bán kính thêm STICKY_RANGE và được ưu tiên giữ chỗ (STICKY_BONUS).
    //  - Tạm thời trượt điều kiện (NPC / đồng đội / tab list nháy...) vẫn được giữ GRACE_TICKS tick.
    //  - Chỉ coi là "ở sảnh" khi inLobby() đúng liên tục LOBBY_TICKS tick (scoreboard Hypixel hay nháy).

    private static final int GRACE_TICKS = 10;
    private static final int LOBBY_TICKS = 40;
    private static final double STICKY_RANGE = 3.0;
    private static final float STICKY_BONUS = 4f;

    private static final class Cand {
        final EntityPlayer p;
        final float key;

        Cand(EntityPlayer p, float key) {
            this.p = p;
            this.key = key;
        }
    }

    private static final Comparator<Cand> CAND_ORDER = new Comparator<Cand>() {
        @Override
        public int compare(Cand a, Cand b) {
            return Float.compare(a.key, b.key);
        }
    };

    /** id -> số tick liên tiếp đã trượt điều kiện (chỉ có với người đang được viền). */
    private final Map<Integer, Integer> miss = new HashMap<Integer, Integer>();
    private int lobbyTicks;

    // ---- số liệu chẩn đoán (bật "Debug" để in ra chat mỗi giây)
    private int dbgTick, dbgPre, dbgArmed, dbgDrawn, dbgOff, dbgErr;
    private String dbgLastErr = "";

    private boolean passes(Minecraft mc, EntityPlayer p, float dist, boolean sticky) {
        if (p.getHealth() <= 0f) return false;
        if (p.isInvisible() && !outInvisible.value) return false;
        if (dist > radius.value + (sticky ? STICKY_RANGE : 0.0)) return false;
        if (ignoreNpc.value && !HypixelUtil.isRealPlayer(p)) return false; // Bỏ NPC / bot (không có trong tab list)
        if (ignoreTeam.value && TeamUtil.isTeammate(mc.thePlayer, p)) return false; // Chỉ kẻ địch
        return true;
    }

    private void updateOutlineTargets(Minecraft mc) {
        if (HypixelUtil.inLobby()) {
            if (lobbyTicks < 10000) lobbyTicks++;
        } else {
            lobbyTicks = 0;
        }
        if (lobbyTicks > LOBBY_TICKS) {
            outlineIds.clear();
            miss.clear();
            return;
        }

        EntityPlayer me = mc.thePlayer;
        List<Cand> cand = new ArrayList<Cand>();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == me || p.isDead || p.isSpectator()) continue;
            int id = p.getEntityId();
            boolean was = outlineIds.contains(id);
            float d = me.getDistanceToEntity(p);
            if (passes(mc, p, d, was)) {
                miss.remove(id);
            } else if (was && p.getHealth() > 0f) {
                Integer m = miss.get(id);
                int n = m == null ? 1 : m + 1;
                if (n > GRACE_TICKS) {
                    miss.remove(id);
                    continue;
                }
                miss.put(id, n); // giữ thêm vài tick, tránh nháy
            } else {
                continue;
            }
            cand.add(new Cand(p, was ? d - STICKY_BONUS : d));
        }
        Collections.sort(cand, CAND_ORDER);
        outlineIds.clear();
        int max = Math.min(cand.size(), outMax.getInt());
        for (int i = 0; i < max; i++) outlineIds.add(cand.get(i).p.getEntityId());
        miss.keySet().retainAll(outlineIds);

        if (outDebug.value) {
            if (++dbgTick >= 20) {
                dbgTick = 0;
                printDebug(mc);
            }
        } else {
            dbgTick = 0;
        }
    }

    private void printDebug(Minecraft mc) {
        String fbInfo = silFb == null ? "chưa tạo" : silFb.framebufferWidth + "x" + silFb.framebufferHeight;
        String s = "\u00a7b[Outline]\u00a7r mục tiêu=" + outlineIds.size() + " sảnh=" + lobbyTicks
                + " | onPre=" + dbgPre + " armed=" + dbgArmed + " vẽ=" + dbgDrawn + " ngoàiMH=" + dbgOff + " lỗi=" + dbgErr
                + " | stencil=" + (stencilOk ? "OK" : "TẮT") + " fb=" + fbInfo
                + (dbgLastErr.length() > 0 ? " | lỗi cuối: " + dbgLastErr : "");
        mc.thePlayer.addChatMessage(new ChatComponentText(s));
        dbgPre = 0; dbgArmed = 0; dbgDrawn = 0; dbgOff = 0; dbgErr = 0;
    }

    // ======================================================================= viền kẻ địch
    //
    // Bản cũ vẽ lại CẢ model + giáp + vật cầm + cape khoảng 2.5 x "Smoothness" lần cho mỗi người.
    // Bản này vẽ hình dáng ĐÚNG 1 LẦN vào framebuffer phụ, rồi "giãn" bằng cách vẽ lại texture đó
    // (mỗi lần chỉ 1 quad 4 đỉnh) và giới hạn bằng scissor.

    private static Field layersField;
    static {
        try {
            layersField = ReflectionHelper.findField(RendererLivingEntity.class, "layerRenderers", "field_177097_h");
        } catch (Throwable t) {
            layersField = null;
        }
    }

    private final Map<RenderPlayer, Boolean> installed = new IdentityHashMap<RenderPlayer, Boolean>();
    private final FloatBuffer mvBuf = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer pjBuf = BufferUtils.createFloatBuffer(16);
    private final float[] eye = new float[4], clip = new float[4];
    /** Vùng pixel (x0,y0,x1,y1): tight = hộp bao người chơi, comp = tight + lề (vùng tô), sil = tight + 2 lề (vùng vẽ hình dáng). */
    private final int[] tight = new int[4], comp = new int[4], sil = new int[4];
    private float quadZ;
    private boolean quadZValid;
    // Các bit stencil: 0 = hình dáng thật, 1 = viền chính, 2 = hào quang mềm
    private static final int BIT_BODY = 1, BIT_OUTLINE = 2, BIT_GLOW = 4;
    private boolean stencilOk;
    /** Thất bại (stencil / FBO phụ) KHÔNG còn bị khoá vĩnh viễn: thử lại sau một lúc. */
    private long stencilRetryAt, silRetryAt;
    private int stencilFails;
    private int ownedRb = -1;
    private boolean armed;
    private EntityPlayer target;
    /** Framebuffer phụ chứa hình dáng (alpha) của người chơi đang được viền. Không có depth. */
    private Framebuffer silFb;

    /** Gắn layer vẽ viền vào renderer người chơi (một lần cho mỗi renderer). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void install(RenderPlayer r) {
        if (layersField == null || installed.containsKey(r)) return;
        installed.put(r, Boolean.TRUE);
        try {
            ((List) layersField.get(r)).add(new OutlineLayer(r));
        } catch (Throwable t) {
            layersField = null;
        }
    }

    private void stencilFailed() {
        stencilFails = Math.min(stencilFails + 1, 5);
        stencilRetryAt = System.currentTimeMillis() + (1000L << stencilFails); // 2s, 4s ... tối đa 32s
        stencilOk = false;
    }

    /**
     * Main framebuffer của MC mặc định không có stencil: đổi depth buffer sang depth+stencil.
     * Nếu thất bại thì trả lại depth buffer cũ (không làm hỏng khung hình) và thử lại sau.
     */
    private void setupStencil() {
        if (stencilRetryAt != 0L && System.currentTimeMillis() < stencilRetryAt) { stencilOk = false; return; }
        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null || !OpenGlHelper.isFramebufferEnabled()) { stencilOk = false; return; }
        if (fb.depthBuffer > -1) {
            // Chỉ gắn khi framebuffer chính đang được bind (tránh gắn nhầm khi có shader / FBO khác)
            if (GL11.glGetInteger(36006) != fb.framebufferObject) { stencilOk = false; return; }
            int oldDepth = fb.depthBuffer;
            int id = -1;
            try {
                id = EXTFramebufferObject.glGenRenderbuffersEXT();
                EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, id);
                EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, 34041,
                        fb.framebufferTextureWidth, fb.framebufferTextureHeight);
                EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, id);
                EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, id);
                if (EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)
                        != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
                    throw new IllegalStateException("framebuffer incomplete");
                }
            } catch (Throwable t) {
                try {
                    EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                            EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, 0);
                    EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                            EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, oldDepth);
                    if (id != -1) EXTFramebufferObject.glDeleteRenderbuffersEXT(id);
                } catch (Throwable ignored) {
                }
                stencilFailed();
                return;
            }
            try {
                // Framebuffer vừa được tạo lại (đổi kích thước cửa sổ): bỏ stencil buffer cũ đã bị tách
                if (ownedRb != -1) EXTFramebufferObject.glDeleteRenderbuffersEXT(ownedRb);
                EXTFramebufferObject.glDeleteRenderbuffersEXT(oldDepth);
            } catch (Throwable ignored) {
            }
            ownedRb = id;
            fb.depthBuffer = -1;
            stencilFails = 0;
        }
        stencilOk = true;
    }

    /** Tạo / tạo lại framebuffer phụ khi đổi kích thước cửa sổ. Luôn trả lại FB chính cho GL. */
    private boolean ensureSilhouette(Framebuffer main, int w, int h) {
        if (silFb != null && silFb.framebufferWidth == w && silFb.framebufferHeight == h) return true;
        if (System.currentTimeMillis() < silRetryAt) return false;
        releaseSilhouette();
        try {
            silFb = new Framebuffer(w, h, false); // không cần depth: hình dáng chỉ cần alpha
        } catch (Throwable t) {
            silFb = null;
            silRetryAt = System.currentTimeMillis() + 3000L; // không tạo được FBO phụ => thử lại sau
            dbgLastErr = "FBO phụ: " + t.getMessage();
        }
        main.bindFramebuffer(true);
        return silFb != null;
    }

    /** Giải phóng framebuffer phụ (khi tắt module / tắt viền) để khỏi chiếm VRAM. */
    private void releaseSilhouette() {
        if (silFb == null) return;
        try {
            silFb.deleteFramebuffer();
        } catch (Throwable ignored) {
        }
        silFb = null;
    }

    /** LunarLite gọi mỗi khung hình ở RenderWorldLast. Hiện chưa vẽ gì. */
    public void renderMarkers(float partialTicks) {
    }

    public void onPre(RenderPlayerEvent.Pre e) {
        armed = false;
        target = null;
        if (!enabled || !outline.value) return;
        install(e.renderer);
        if (layersField == null || !outlineIds.contains(e.entityPlayer.getEntityId())) return;
        dbgPre++;
        setupStencil();
        if (!stencilOk) return;
        armed = true;
        target = e.entityPlayer;
        dbgArmed++;
    }

    public void onPost(RenderPlayerEvent.Post e) {
        armed = false;
        target = null;
    }

    private void endStencil() {
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        armed = false;
        target = null;
    }

    // ---------------------------------------------------------------- hình học màn hình

    private static void mul(FloatBuffer m, float x, float y, float z, float w, float[] out) {
        for (int r = 0; r < 4; r++) out[r] = m.get(r) * x + m.get(4 + r) * y + m.get(8 + r) * z + m.get(12 + r) * w;
    }

    private static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static void expand(int[] dst, int[] src, int m, int fw, int fh) {
        dst[0] = clampI(src[0] - m, 0, fw);
        dst[1] = clampI(src[1] - m, 0, fh);
        dst[2] = clampI(src[2] + m, 0, fw);
        dst[3] = clampI(src[3] + m, 0, fh);
    }

    /**
     * Chiếu hộp bao quanh người chơi (trong hệ toạ độ model hiện tại) ra màn hình để biết chỉ cần xử lý vùng nào.
     * Trả về false nếu người chơi nằm ngoài màn hình (bỏ qua hoàn toàn).
     */
    private boolean computeRect(int margin, int fw, int fh) {
        mvBuf.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mvBuf);
        pjBuf.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, pjBuf);
        float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f, minZ = 1e9f;
        boolean clipped = false;
        for (int i = 0; i < 8; i++) {
            // Hộp rộng rãi (đơn vị block, trước khi nhân scale model): đủ cho tay, kiếm, cape...
            mul(mvBuf, (i & 1) == 0 ? -1.0f : 1.0f, (i & 2) == 0 ? -0.75f : 1.75f, (i & 4) == 0 ? -1.0f : 1.0f, 1f, eye);
            mul(pjBuf, eye[0], eye[1], eye[2], eye[3], clip);
            if (clip[3] < 0.05f) { clipped = true; break; } // quá gần / sau camera
            float nx = clip[0] / clip[3], ny = clip[1] / clip[3], nz = clip[2] / clip[3];
            if (nx < minX) minX = nx;
            if (nx > maxX) maxX = nx;
            if (ny < minY) minY = ny;
            if (ny > maxY) maxY = ny;
            if (nz < minZ) minZ = nz;
        }
        if (clipped) {
            minX = -1f; minY = -1f; maxX = 1f; maxY = 1f;
            quadZValid = false;
        } else {
            quadZValid = true;
            quadZ = Math.max(-0.999f, Math.min(0.999f, minZ)); // điểm gần camera nhất của hộp
        }
        int x0 = clampI((int) Math.floor((minX * 0.5f + 0.5f) * fw), 0, fw);
        int x1 = clampI((int) Math.ceil((maxX * 0.5f + 0.5f) * fw), 0, fw);
        int y0 = clampI((int) Math.floor((minY * 0.5f + 0.5f) * fh), 0, fh);
        int y1 = clampI((int) Math.ceil((maxY * 0.5f + 0.5f) * fh), 0, fh);
        if (x1 <= x0 || y1 <= y0) return false;
        tight[0] = x0; tight[1] = y0; tight[2] = x1; tight[3] = y1;
        expand(comp, tight, margin, fw, fh);
        expand(sil, tight, margin * 2, fw, fh);
        return true;
    }

    /** 1 quad: vẽ vùng "tight" của texture hình dáng, lệch (dx,dy) pixel. Chỉ cần gọi giữa glBegin/glEnd. */
    private void shiftedQuad(float dx, float dy, float z, int fw, int fh) {
        int[] t = tight;
        float u0 = t[0] / (float) fw, v0 = t[1] / (float) fh, u1 = t[2] / (float) fw, v1 = t[3] / (float) fh;
        float x0 = (t[0] + dx) / fw * 2f - 1f, x1 = (t[2] + dx) / fw * 2f - 1f;
        float y0 = (t[1] + dy) / fh * 2f - 1f, y1 = (t[3] + dy) / fh * 2f - 1f;
        GL11.glTexCoord2f(u0, v0); GL11.glVertex3f(x0, y0, z);
        GL11.glTexCoord2f(u1, v0); GL11.glVertex3f(x1, y0, z);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex3f(x1, y1, z);
        GL11.glTexCoord2f(u0, v1); GL11.glVertex3f(x0, y1, z);
    }

    /** {@code count} quad dịch đều quanh vòng tròn bán kính {@code radius} pixel. */
    private void ring(float radius, int count, float phase, float z, int fw, int fh) {
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2.0) * (i + phase) / count;
            shiftedQuad((float) (Math.cos(a) * radius), (float) (Math.sin(a) * radius), z, fw, fh);
        }
    }

    private void fillQuad(float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-1f, -1f, 0f);
        GL11.glVertex3f(1f, -1f, 0f);
        GL11.glVertex3f(1f, 1f, 0f);
        GL11.glVertex3f(-1f, 1f, 0f);
        GL11.glEnd();
    }

    /** Tham số của lần vẽ hiện tại, gom lại để truyền cho các hàm phụ (dùng lại, không cấp phát mỗi khung hình). */
    private static final class Frame {
        EntityPlayer p;
        float limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale;
        ModelBase model;
        ResourceLocation skin;
    }

    private final class OutlineLayer implements LayerRenderer<EntityPlayer> {
        private final RenderPlayer renderer;
        private final Frame frame = new Frame();
        @SuppressWarnings("rawtypes")
        private final List<LayerRenderer> others = new ArrayList<LayerRenderer>();
        private int othersSrcSize = -1;

        OutlineLayer(RenderPlayer renderer) { this.renderer = renderer; }

        /** Các layer khác của renderer (trừ chính mình); chỉ dựng lại khi danh sách layer đổi kích thước. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void refreshOthers() {
            try {
                List src = (List) layersField.get(renderer);
                if (src.size() == othersSrcSize) return;
                others.clear();
                for (Object o : src) {
                    if (o != this) others.add((LayerRenderer) o);
                }
                othersSrcSize = src.size();
            } catch (Throwable ignored) {
            }
        }

        /** Vẽ toàn bộ hình dáng người chơi (model chính + giáp + vật cầm + cape...) - chỉ gọi 1 lần / người / khung hình. */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void renderShape(Frame f) {
            if (f.skin != null) Minecraft.getMinecraft().getTextureManager().bindTexture(f.skin);
            f.model.render(f.p, f.limbSwing, f.limbSwingAmount, f.ageInTicks, f.netHeadYaw, f.headPitch, f.scale);
            for (int i = 0; i < others.size(); i++) {
                try {
                    others.get(i).doRenderLayer(f.p, f.limbSwing, f.limbSwingAmount, f.partialTicks, f.ageInTicks,
                            f.netHeadYaw, f.headPitch, f.scale);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public void doRenderLayer(EntityPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
                                  float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            if (!armed || p != target) return;
            Minecraft mc = Minecraft.getMinecraft();
            Framebuffer main = mc.getFramebuffer();
            if (main == null) return;
            int fw = main.framebufferWidth, fh = main.framebufferHeight;

            float w = Math.max(1f, outWidth.getFloat());
            int margin = (int) Math.ceil(w * 1.8f) + 3;
            // Người chơi nằm ngoài màn hình => khỏi làm gì cả
            if (!computeRect(margin, fw, fh)) { dbgOff++; return; }
            if (!ensureSilhouette(main, fw, fh)) return;

            int rgb = outRainbow.value ? rainbowRgb((p.getEntityId() % 10) * 36f) : outColor.argb();
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            boolean depthTest = !throughWalls.value && quadZValid; // "Through Walls" tắt: bị tường che thì ẩn viền
            float z = depthTest ? quadZ : 0f;
            int smooth = Math.max(6, outSmooth.getInt());
            // Giữ khoảng cách giữa các mẫu <= ~2.5px để viền dày không bị răng cưa / hở
            int nMain = Math.min(96, Math.max(smooth, (int) Math.ceil(Math.PI * 2.0 * w / 2.5)));
            int nGlow = Math.min(96, Math.max(smooth, (int) Math.ceil(Math.PI * 2.0 * w * 1.8 / 3.0)));

            boolean wasFog = GL11.glIsEnabled(GL11.GL_FOG);
            boolean wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            boolean wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean silBound = false, matPushed = false;

            Frame f = frame;
            f.p = p; f.limbSwing = limbSwing; f.limbSwingAmount = limbSwingAmount; f.partialTicks = partialTicks;
            f.ageInTicks = ageInTicks; f.netHeadYaw = netHeadYaw; f.headPitch = headPitch; f.scale = scale;
            f.model = renderer.getMainModel();
            f.skin = p instanceof AbstractClientPlayer ? ((AbstractClientPlayer) p).getLocationSkin() : null;
            refreshOthers();

            try {
                // 1) Vẽ hình dáng người chơi đúng 1 lần vào framebuffer phụ (chỉ trong vùng "sil")
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(sil[0], sil[1], sil[2] - sil[0], sil[3] - sil[1]);
                silFb.bindFramebuffer(true);
                silBound = true;
                GL11.glColorMask(true, true, true, true);
                // Dùng GlStateManager để bộ nhớ đệm màu xoá của MC luôn khớp với GL (tránh nền thế giới bị đổi màu)
                GlStateManager.clearColor(0f, 0f, 0f, 0f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GlStateManager.disableBlend();
                GlStateManager.enableAlpha();
                GlStateManager.color(1f, 1f, 1f, 1f);
                renderShape(f); // gồm cả model chính nên người tàng hình vẫn có hình dáng
                main.bindFramebuffer(true);
                silBound = false;

                // 2) Dựng stencil từ texture hình dáng (chỉ trong vùng "comp")
                GL11.glScissor(comp[0], comp[1], comp[2] - comp[0], comp[3] - comp[1]);
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilMask(0xFF);
                GL11.glClearStencil(0);
                GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                GL11.glColorMask(false, false, false, false);
                GlStateManager.depthMask(false);

                // Hệ toạ độ NDC trực tiếp: quad vẽ đúng vị trí pixel trên màn hình
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GL11.glLoadIdentity();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                GL11.glLoadIdentity();
                matPushed = true;

                GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.disableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.enableTexture2D();
                silFb.bindFramebufferTexture();
                GlStateManager.disableLighting();
                GlStateManager.disableFog();
                GlStateManager.disableCull();
                GlStateManager.disableBlend();
                GlStateManager.enableAlpha();
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.disableDepth();

                // 2a) Bit 0 = thân (không cần depth: thân luôn bị loại khỏi viền)
                GL11.glStencilFunc(GL11.GL_ALWAYS, BIT_BODY, 0xFF);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                GL11.glBegin(GL11.GL_QUADS);
                shiftedQuad(0f, 0f, 0f, fw, fh);
                GL11.glEnd();

                if (depthTest) GlStateManager.enableDepth();

                // 2b) Viền chính: vòng ngoài + vòng trong (lấp khe ở chỗ mảnh), 1 lượt vẽ duy nhất
                GL11.glStencilMask(BIT_OUTLINE);
                GL11.glStencilFunc(GL11.GL_EQUAL, BIT_OUTLINE, BIT_BODY);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                GL11.glBegin(GL11.GL_QUADS);
                ring(w, nMain, 0f, z, fw, fh);
                if (w > 2f) ring(w * 0.5f, nMain / 2, 0.5f, z, fw, fh);
                GL11.glEnd();

                // 2c) Hào quang mềm bên ngoài viền
                if (outGlow.value) {
                    GL11.glStencilMask(BIT_GLOW);
                    GL11.glStencilFunc(GL11.GL_EQUAL, BIT_GLOW, BIT_BODY | BIT_OUTLINE);
                    GL11.glBegin(GL11.GL_QUADS);
                    ring(w * 1.8f, nGlow, 0.25f, z, fw, fh);
                    GL11.glEnd();
                }

                // 3) Tô màu: quad toàn màn hình (bị scissor cắt còn vùng "comp"), chỉ ở pixel có stencil viền / glow
                GL11.glColorMask(true, true, true, true);
                GL11.glStencilMask(0x00);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                GlStateManager.disableDepth();
                GlStateManager.disableTexture2D();
                GlStateManager.disableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

                GL11.glStencilFunc(GL11.GL_EQUAL, BIT_OUTLINE, BIT_BODY | BIT_OUTLINE);
                fillQuad(r, g, b, 1f);
                if (outGlow.value) {
                    GL11.glStencilFunc(GL11.GL_EQUAL, BIT_GLOW, BIT_BODY | BIT_OUTLINE | BIT_GLOW);
                    fillQuad(r, g, b, 0.35f);
                }
                dbgDrawn++;
            } catch (Throwable t) {
                // Không để lỗi của viền làm hỏng việc vẽ người chơi của MC
                dbgErr++;
                dbgLastErr = t.getClass().getSimpleName() + ": " + t.getMessage();
            } finally {
                // Khôi phục trạng thái GL
                if (silBound) main.bindFramebuffer(true);
                if (matPushed) {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glColorMask(true, true, true, true);
                endStencil();
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                GlStateManager.disableBlend();
                if (wasAlpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
                if (wasFog) GlStateManager.enableFog();
                if (wasCull) GlStateManager.enableCull();
                GlStateManager.enableLighting();
                GlStateManager.enableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.enableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.color(1f, 1f, 1f, 1f);
                f.p = null; f.model = null; f.skin = null;
            }
        }

        @Override
        public boolean shouldCombineTextures() { return false; }
    }
}
