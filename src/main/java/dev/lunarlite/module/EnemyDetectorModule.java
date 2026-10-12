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
import net.minecraft.util.ResourceLocation;

/**
 * Phát hiện người chơi khác trong bán kính tuỳ chỉnh: hiện danh sách (tên, máu, khoảng cách),
 * tô màu theo độ gần và báo âm thanh khi có người mới bước vào bán kính.
 *
 * Đã gộp luôn "Enemy Outline": viền ôm sát cơ thể kẻ địch (silhouette outline) giống hiệu ứng Glowing.
 *  1. Stencil bit 0 = hình dáng thật của người chơi (thân + giáp + vật cầm + cape...).
 *  2. "Giãn" hình dáng đó ra ngoài bằng cách vẽ lại người chơi nhiều lần, mỗi lần dịch vài pixel
 *     theo một hướng trên màn hình (chỉ ghi stencil). Bit 1 = viền chính, bit 2 = hào quang mềm.
 *  3. Tô quad toàn màn hình, chỉ ở pixel có stencil tương ứng => viền đều, bo tròn, liền mạch.
 */
public class EnemyDetectorModule extends HudModule {
    private static final int W = 140, HEAD = 20, ROW_FULL = 20, ROW_COMPACT = 12;

    private final SliderSetting radius = add(new SliderSetting("radius", "Bán kính phát hiện", 30, 5, 100, 1, " block"));
    private final SliderSetting maxRows = add(new SliderSetting("max_rows", "Số người hiển thị tối đa", 5, 1, 8, 1, ""));
    private final BoolSetting showHealth = add(new BoolSetting("show_health", "Hiện thanh máu", true));
    private final BoolSetting showFace = add(new BoolSetting("show_face", "Hiện avatar", true));
    private final BoolSetting showDir = add(new BoolSetting("show_dir", "Hiện mũi tên hướng", true));
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
                Category.PVP, true, 4, 70);
    }

    // ------------------------------------------------------------------ quét

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!enabled || mc.theWorld == null || mc.thePlayer == null) {
            found = new ArrayList<Entry>();
            inside.clear();
            outlineIds.clear();
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

        // ---- danh sách người được viền: chỉ ở ngoài sảnh Hypixel, lấy tối đa N người gần nhất
        outlineIds.clear();
        if (outline.value && !HypixelUtil.inLobby()) {
            List<EntityPlayer> cand = new ArrayList<EntityPlayer>();
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                if (isOutlineTarget(p)) cand.add(p);
            }
            final EntityPlayer me = mc.thePlayer;
            Collections.sort(cand, new Comparator<EntityPlayer>() {
                @Override
                public int compare(EntityPlayer a, EntityPlayer b) {
                    return Float.compare(me.getDistanceToEntity(a), me.getDistanceToEntity(b));
                }
            });
            int max = Math.min(cand.size(), outMax.getInt());
            for (int i = 0; i < max; i++) outlineIds.add(cand.get(i).getEntityId());
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

    /** Mũi tên nhỏ chỉ hướng kẻ địch; deg = 0 là phía trước mặt, dương là sang phải. */
    private void drawArrow(float cx, float cy, float deg, int argb) {
        float a = ((argb >> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f,
                g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0f);
        GlStateManager.rotate(deg, 0f, 0f, 1f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(0f, -3.6f);
        GL11.glVertex2f(2.8f, 2.6f);
        GL11.glVertex2f(0f, 0.8f);
        GL11.glVertex2f(0f, -3.6f);
        GL11.glVertex2f(0f, 0.8f);
        GL11.glVertex2f(-2.8f, 2.6f);
        GL11.glEnd();
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
                int acx = px - 8, acy = ty + 4;
                rrect(acx - 5, acy - 5, 10, 10, 5, 0x40000000);
                drawArrow(acx, acy, rel, dc);
                nameRight = acx - 7;
            }
            String nm = fr.trimStringToWidth(e.name, Math.max(10, nameRight - cx));
            fr.drawString(nm, cx, ty, textColor(x + ry), shadow.value);

            if (bar) {
                String hs = String.valueOf(Math.round(e.hp));
                float hsw = fr.getStringWidth(hs) * 0.7f;
                int bx0 = cx, bx1 = (int) (cardX + cardW - 4 - hsw - 4), by = ry + 12, bw = bx1 - bx0;
                rrect(bx0, by, bw, 3, 1, 0x66000000);
                int fill = Math.round(bw * Math.max(0f, Math.min(1f, hpShow / 20f)));
                if (fill > 0) {
                    int hc = hpColor(hpShow);
                    rrect(bx0, by, fill, 3, 1, hc);
                    if (fill > 2) Gui.drawRect(bx0 + 1, by, bx0 + fill - 1, by + 1, 0x55FFFFFF); // ánh sáng mép trên
                }
                GlStateManager.pushMatrix();
                GlStateManager.translate(cardX + cardW - 4 - hsw, ry + 10.5f, 0f);
                GlStateManager.scale(0.7f, 0.7f, 1f);
                fr.drawString(hs, 0, 0, hpColor(e.hp), false);
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
        }
        if (!preview) {
            appear.keySet().retainAll(seen);
            hpAnim.keySet().retainAll(seen);
        }
    }

    // ======================================================================= viền kẻ địch

    private static Field layersField;
    static {
        try {
            layersField = ReflectionHelper.findField(RendererLivingEntity.class, "layerRenderers", "field_177097_h");
        } catch (Throwable t) {
            layersField = null;
        }
    }

    private final Map<RenderPlayer, Boolean> installed = new IdentityHashMap<RenderPlayer, Boolean>();
    private final FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
    // Các bit stencil: 0 = hình dáng thật, 1 = viền chính, 2 = hào quang mềm
    private static final int BIT_BODY = 1, BIT_OUTLINE = 2, BIT_GLOW = 4;
    private boolean stencilOk;
    private boolean stencilBroken;
    private int ownedRb = -1;
    private boolean armed;
    private EntityPlayer target;

    private boolean isOutlineTarget(EntityPlayer p) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || p == mc.thePlayer || p.isDead || p.getHealth() <= 0f || p.isSpectator()) return false;
        if (p.isInvisible() && !outInvisible.value) return false;
        if (mc.thePlayer.getDistanceSqToEntity(p) > radius.value * radius.value) return false;
        // Bỏ NPC / bot (không có trong tab list)
        if (ignoreNpc.value && !HypixelUtil.isRealPlayer(p)) return false;
        // Chỉ kẻ địch: bỏ đồng đội cùng team
        if (!ignoreTeam.value) return true;
        return !TeamUtil.isTeammate(mc.thePlayer, p);
    }

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

    /**
     * Main framebuffer của MC mặc định không có stencil: đổi depth buffer sang depth+stencil.
     * Nếu thất bại thì trả lại depth buffer cũ (không làm hỏng khung hình) và tắt viền.
     */
    private void setupStencil() {
        if (stencilBroken) { stencilOk = false; return; }
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
                stencilBroken = true;
                stencilOk = false;
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
        }
        stencilOk = true;
    }

    public void onPre(RenderPlayerEvent.Pre e) {
        armed = false;
        if (!enabled || !outline.value) return;
        install(e.renderer);
        if (layersField == null || !outlineIds.contains(e.entityPlayer.getEntityId())) return;
        setupStencil();
        if (!stencilOk) return;
        // Đánh dấu stencil bit 0 ở mọi pixel người chơi (thân, giáp, vật cầm... kể cả bị tường che)
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilFunc(GL11.GL_ALWAYS, BIT_BODY, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_REPLACE, GL11.GL_REPLACE);
        armed = true;
        target = e.entityPlayer;
    }

    public void onPost(RenderPlayerEvent.Post e) {
        if (armed) endStencil();
    }

    private void endStencil() {
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        armed = false;
        target = null;
    }

    /** Tham số của lần vẽ hiện tại, gom lại để truyền cho các hàm phụ. */
    private static final class Frame {
        EntityPlayer p;
        float limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale;
        ModelBase model;
        ResourceLocation skin;
        @SuppressWarnings("rawtypes")
        List<LayerRenderer> others;
    }

    private final class OutlineLayer implements LayerRenderer<EntityPlayer> {
        private final RenderPlayer renderer;

        OutlineLayer(RenderPlayer renderer) { this.renderer = renderer; }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private List<LayerRenderer> otherLayers() {
            List<LayerRenderer> out = new ArrayList<LayerRenderer>();
            try {
                for (Object o : (List) layersField.get(renderer)) {
                    if (o != this) out.add((LayerRenderer) o);
                }
            } catch (Throwable ignored) {
            }
            return out;
        }

        /** Vẽ toàn bộ hình dáng người chơi (model chính + giáp + vật cầm + cape...). */
        @SuppressWarnings({"rawtypes", "unchecked"})
        private void renderShape(Frame f) {
            if (f.skin != null) Minecraft.getMinecraft().getTextureManager().bindTexture(f.skin);
            f.model.render(f.p, f.limbSwing, f.limbSwingAmount, f.ageInTicks, f.netHeadYaw, f.headPitch, f.scale);
            for (LayerRenderer l : f.others) {
                try {
                    l.doRenderLayer(f.p, f.limbSwing, f.limbSwingAmount, f.partialTicks, f.ageInTicks,
                            f.netHeadYaw, f.headPitch, f.scale);
                } catch (Throwable ignored) {
                }
                // Layer (vd: ánh phép thuật của giáp) có thể bật lại ghi depth => khóa lại
                GlStateManager.depthMask(false);
            }
            GL11.glColorMask(false, false, false, false);
        }

        /** Vẽ lại hình dáng {@code count} lần, dịch {@code radius} pixel theo các hướng đều nhau trên màn hình. */
        private void dilate(Frame f, float radius, int count, float phase) {
            Minecraft mc = Minecraft.getMinecraft();
            for (int i = 0; i < count; i++) {
                double a = (Math.PI * 2.0) * (i + phase) / count;
                float tx = (float) (Math.cos(a) * radius) * 2f / mc.displayWidth;
                float ty = (float) (Math.sin(a) * radius) * 2f / mc.displayHeight;
                // Dịch trong không gian NDC: P' = T * P (T nhân với w nên độ dịch luôn đúng số pixel dù xa/gần)
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glLoadIdentity();
                GL11.glTranslatef(tx, ty, 0f);
                projBuf.rewind();
                GL11.glMultMatrix(projBuf);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                renderShape(f);
            }
        }

        private void fillScreen(float r, float g, float b, float a) {
            GlStateManager.color(r, g, b, a);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(-1f, -1f, 0f);
            GL11.glVertex3f(1f, -1f, 0f);
            GL11.glVertex3f(1f, 1f, 0f);
            GL11.glVertex3f(-1f, 1f, 0f);
            GL11.glEnd();
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        @Override
        public void doRenderLayer(EntityPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
                                  float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            if (!armed || p != target) return;
            int rgb = outRainbow.value && rainbow != null ? rainbow.resolve(outColor.rgb(), (p.getEntityId() % 10) * 60f) : outColor.argb();
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            boolean walls = throughWalls.value;
            boolean wasFog = GL11.glIsEnabled(GL11.GL_FOG);
            boolean wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            boolean projPushed = false;

            Frame f = new Frame();
            f.p = p; f.limbSwing = limbSwing; f.limbSwingAmount = limbSwingAmount; f.partialTicks = partialTicks;
            f.ageInTicks = ageInTicks; f.netHeadYaw = netHeadYaw; f.headPitch = headPitch; f.scale = scale;
            f.model = renderer.getMainModel();
            f.skin = p instanceof AbstractClientPlayer ? ((AbstractClientPlayer) p).getLocationSkin() : null;
            f.others = otherLayers();

            try {
                // 1) Chắc chắn bit 0 phủ cả model chính (kể cả người tàng hình): chỉ ghi stencil
                GL11.glColorMask(false, false, false, false);
                GlStateManager.depthMask(false);
                GL11.glStencilMask(0xFF);
                GL11.glStencilFunc(GL11.GL_ALWAYS, BIT_BODY, 0xFF);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_REPLACE, GL11.GL_REPLACE);
                f.model.render(p, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

                // 2) Giãn hình dáng ra ngoài (chỉ ghi stencil, bỏ qua pixel thuộc cơ thể)
                float w = Math.max(1f, outWidth.getFloat());
                int n = Math.max(6, outSmooth.getInt());
                projBuf.clear();
                GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projBuf);
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.enableAlpha();
                if (walls) GlStateManager.disableDepth();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                projPushed = true;

                // 2a) Viền chính: vòng ngoài + vòng trong (lấp khe ở chỗ mảnh)
                GL11.glStencilMask(BIT_OUTLINE);
                GL11.glStencilFunc(GL11.GL_EQUAL, BIT_OUTLINE, BIT_BODY);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                dilate(f, w, n, 0f);
                if (w > 2f) dilate(f, w * 0.5f, n / 2, 0.5f);

                // 2b) Hào quang mềm bên ngoài viền
                if (outGlow.value) {
                    GL11.glStencilMask(BIT_GLOW);
                    GL11.glStencilFunc(GL11.GL_EQUAL, BIT_GLOW, BIT_BODY | BIT_OUTLINE);
                    dilate(f, w * 1.8f, n, 0.25f);
                }

                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                projPushed = false;

                // 3) Tô màu: quad toàn màn hình, chỉ ở pixel có stencil viền / glow
                GL11.glColorMask(true, true, true, true);
                GL11.glStencilMask(0x00);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.disableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.disableTexture2D();
                GlStateManager.disableLighting();
                GlStateManager.disableFog();
                GlStateManager.disableAlpha();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

                GL11.glStencilFunc(GL11.GL_EQUAL, BIT_OUTLINE, BIT_BODY | BIT_OUTLINE);
                fillScreen(r, g, b, 1f);
                if (outGlow.value) {
                    GL11.glStencilFunc(GL11.GL_EQUAL, BIT_GLOW, BIT_BODY | BIT_OUTLINE | BIT_GLOW);
                    fillScreen(r, g, b, 0.35f);
                }
            } finally {
                // Khôi phục trạng thái GL
                if (projPushed) {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                }
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glColorMask(true, true, true, true);
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                GlStateManager.disableBlend();
                if (wasAlpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
                if (wasFog) GlStateManager.enableFog();
                GlStateManager.enableLighting();
                GlStateManager.enableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.enableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.color(1f, 1f, 1f, 1f);
                endStencil();
            }
        }

        @Override
        public boolean shouldCombineTextures() { return false; }
    }
}
