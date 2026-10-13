package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bedwars HUD, bố cục NGANG:
 *   [ kill của bản thân ] | [ giường từng đội, 1 hàng — 2 / 4 / 6 / 7 / 8 đội ]
 * (tuỳ chọn) bảng kill cả phòng nằm bên dưới. Có thể ẩn bossbar.
 * Mọi thứ được parse ở tick / chat và cache; draw() chỉ đọc dữ liệu đã cache.
 */
public class KillBoardModule extends HudModule {
    private final BoolSetting showSelf = add(new BoolSetting("show_self", "Hiện kill của bản thân", true), "Bảng kill");
    private final BoolSetting showBeds = add(new BoolSetting("show_beds", "Hiện giường các đội", true), "Bảng kill");
    private final BoolSetting showHeads = add(new BoolSetting("show_heads", "Hiện avatar", true), "Bảng kill");
    private final BoolSetting hideBoss = add(new BoolSetting("hide_bossbar", "Ẩn bossbar", true), "Bảng kill");
    private final BoolSetting showRoom = add(new BoolSetting("show_room", "Hiện bảng kill cả phòng", false), "Bảng kill");
    private final SliderSetting maxRows = add(new SliderSetting("rows", "Số người tối đa (cả phòng)", 5, 1, 8, 1, ""), "Bảng kill", showRoom);

    private static final int PAD = 4;
    private static final int SH = 22;          // chiều cao hàng ngang
    private static final int HDR = 14;
    private static final int HDR_GAP = 3;
    private static final int SEC_GAP = 5;
    private static final int ROW_H = 21;
    private static final int HEAD = 16;
    private static final int ROOM_W = 150;
    private static final int CHIPS_W = 104; // tổng độ rộng hàng giường (gần cố định cho mọi số đội)

    private static final int KILL_ACCENT = 0xFFFF6B6B;
    private static final int BED_ACCENT = 0xFFFFB84D;
    private static final int GREEN = 0xFF5CE08A;
    private static final int ORANGE = 0xFFFFA45C;
    private static final int GOLD = 0xFFFFC857;

    private static final Pattern DEATH = Pattern.compile("^(\\w{3,16}) .*?\\b(?:by|escaping|fighting) (\\w{3,16})\\b");
    private static final Pattern BED_BREAK = Pattern.compile("^BED DESTRUCTION > .* was destroyed by (\\w{3,16})");
    private static final Pattern TEAM = Pattern.compile("^[A-Z] (Red|Blue|Green|Yellow|Aqua|White|Pink|Gray):?\\s*(.*)$");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final int[] MC_COLORS = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

    /** Thống kê từng người (kèm dữ liệu render đã cache). */
    private static final class P {
        final String name;
        int kills, finals, beds;
        String ks = "0", fvs = "0", bs = "0", fs = "";
        float shown;
        int color = 0xFFFFFFFF;
        ResourceLocation skin;
        boolean me;
        P(String name) { this.name = name; }
        void update() {
            ks = Integer.toString(kills);
            fvs = Integer.toString(finals);
            bs = Integer.toString(beds);
            fs = finals > 0 ? "F" + finals : "";
        }
    }

    /** Một đội từ scoreboard. state: 0 = còn giường, 1 = mất giường (còn left người), 2 = bị loại. */
    private static final class T {
        String name;
        int color, state, left;
        String leftStr = "";
        boolean you;
    }

    private static final P ZERO = new P("");

    private final Map<String, P> stats = new HashMap<>();
    private final List<P> top = new ArrayList<>();
    private List<T> teams = new ArrayList<>();
    private P selfP, zeroSelf;
    private World lastWorld;
    private boolean dirty;
    private int tick;
    private int totalKills;
    private String killBadge = "0", killPill = "";
    private String bedBadge = "0", bedPill = "";

    private final List<P> sampleTop = new ArrayList<>();
    private final List<T> sampleTeams = new ArrayList<>();

    public KillBoardModule() {
        super("killboard", "Bảng kill", "Kill của bản thân và trạng thái giường từng đội (Bedwars), xếp ngang.", Category.PVP, true, 520, 2);
        String[] names = {"Qtiey", "Xin_yanQwQ", "xBianca12", "RealMineKat"};
        int[] k = {7, 5, 3, 2}, f = {2, 0, 1, 0};
        int[] col = {0xFFFF5555, 0xFFFF77DD, 0xFF55FFFF, 0xFF5577FF};
        for (int i = 0; i < names.length; i++) {
            P p = new P(names[i]);
            p.kills = k[i];
            p.finals = f[i];
            p.beds = i == 0 ? 1 : 0;
            p.update();
            p.color = col[i];
            p.shown = k[i] / 7f;
            p.me = i == 0;
            sampleTop.add(p);
        }
        String[] tn = {"Red", "Blue", "Green", "Yellow", "Aqua", "White", "Pink", "Gray"};
        int[] st = {0, 0, 1, 0, 2, 0, 1, 0}, left = {0, 0, 2, 0, 0, 0, 1, 0};
        for (int i = 0; i < tn.length; i++) sampleTeams.add(makeTeam(tn[i], st[i], left[i], i == 0));
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ───────────────────────── thu thập dữ liệu ─────────────────────────

    private static int teamColor(String n) {
        switch (n) {
            case "Red": return 0xFFFF5555;
            case "Blue": return 0xFF5577FF;
            case "Green": return 0xFF55FF55;
            case "Yellow": return 0xFFFFFF55;
            case "Aqua": return 0xFF55FFFF;
            case "White": return 0xFFFFFFFF;
            case "Pink": return 0xFFFF77DD;
            default: return 0xFFAAAAAA;
        }
    }

    private static T makeTeam(String name, int state, int left, boolean you) {
        T t = new T();
        t.name = name;
        t.color = teamColor(name);
        t.state = state;
        t.left = left;
        t.leftStr = state == 1 ? Integer.toString(left) : "";
        t.you = you;
        return t;
    }

    private void reset() {
        stats.clear();
        top.clear();
        teams = new ArrayList<>();
        selfP = null;
        zeroSelf = null;
        totalKills = 0;
        dirty = true;
    }

    private P player(String name) {
        P p = stats.get(name);
        if (p == null) { p = new P(name); stats.put(name, p); }
        return p;
    }

    /** Ẩn bossbar (thanh máu rồng / tiêu đề server ở giữa trên). */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type == RenderGameOverlayEvent.ElementType.BOSSHEALTH && hideBoss.value && enabled) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || e.message == null) return;
        String t = EnumChatFormatting.getTextWithoutFormattingCodes(e.message.getUnformattedText());
        if (t == null || t.length() > 140) return;
        if (t.contains("Protect your bed")) { reset(); return; }
        if (t.startsWith("BED DESTRUCTION")) {
            Matcher bm = BED_BREAK.matcher(t);
            if (bm.find()) {
                P p = player(bm.group(1));
                p.beds++;
                p.update();
                dirty = true;
            }
            return;
        }
        if (t.indexOf(':') >= 0) return; // chat người chơi
        Matcher m = DEATH.matcher(t);
        if (!m.find()) return;
        String victim = m.group(1), killer = m.group(2);
        if (victim.equals(killer)) return;
        NetHandlerPlayClient nh = Minecraft.getMinecraft().getNetHandler();
        if (nh == null || nh.getPlayerInfo(victim) == null || nh.getPlayerInfo(killer) == null) return;
        P p = player(killer);
        p.kills++;
        if (t.contains("FINAL KILL")) p.finals++;
        p.update();
        totalKills++;
        dirty = true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            reset();
        }
        if (mc.theWorld == null || mc.thePlayer == null) return;
        tick++;
        if (tick % 10 == 0) readTeams(mc);
        if (dirty || tick % 20 == 0) rebuild(mc);
    }

    private void readTeams(Minecraft mc) {
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective obj = sb == null ? null : sb.getObjectiveInDisplaySlot(1);
        String title = obj == null ? null : EnumChatFormatting.getTextWithoutFormattingCodes(obj.getDisplayName());
        if (title == null || !title.toUpperCase().contains("BED WARS")) {
            if (!teams.isEmpty()) teams = new ArrayList<>();
            return;
        }
        List<T> out = new ArrayList<>(8);
        Collection<Score> scores = sb.getSortedScores(obj);
        for (Score s : scores) {
            String pn = s.getPlayerName();
            if (pn == null || pn.startsWith("#")) continue;
            String line = EnumChatFormatting.getTextWithoutFormattingCodes(ScorePlayerTeam.formatPlayerName(sb.getPlayersTeam(pn), pn));
            if (line == null) continue;
            Matcher m = TEAM.matcher(line.trim());
            if (!m.matches()) continue;
            String rest = m.group(2);
            int state = 0, left = 0;
            if (rest.contains("\u2714") || rest.contains("\u2713")) state = 0;
            else {
                Matcher d = DIGITS.matcher(rest);
                if (d.find()) {
                    try { left = Integer.parseInt(d.group()); } catch (NumberFormatException ignored) { }
                    state = 1;
                } else if (rest.contains("\u2718") || rest.contains("\u2717")) state = 2;
            }
            out.add(makeTeam(m.group(1), state, left, rest.contains("YOU")));
        }
        Collections.reverse(out); // scoreboard trả về từ dưới lên
        teams = out;
        int bedsAlive = 0, teamsAlive = 0;
        for (T t : out) {
            if (t.state == 0) bedsAlive++;
            if (t.state != 2) teamsAlive++;
        }
        bedBadge = Integer.toString(bedsAlive);
        bedPill = teamsAlive + " đội";
    }

    private static int prefixColor(NetworkPlayerInfo info) {
        if (info == null || info.getPlayerTeam() == null) return 0xFFFFFFFF;
        String pre = info.getPlayerTeam().getColorPrefix();
        if (pre == null) return 0xFFFFFFFF;
        for (int i = 0; i + 1 < pre.length(); i++) {
            if (pre.charAt(i) == '\u00a7') {
                int idx = "0123456789abcdef".indexOf(Character.toLowerCase(pre.charAt(i + 1)));
                if (idx >= 0) return MC_COLORS[idx];
            }
        }
        return 0xFFFFFFFF;
    }

    private void rebuild(Minecraft mc) {
        NetHandlerPlayClient nh = mc.getNetHandler();
        String me = mc.thePlayer.getName();
        for (P p : stats.values()) {
            NetworkPlayerInfo info = nh == null ? null : nh.getPlayerInfo(p.name);
            p.skin = info == null ? null : info.getLocationSkin();
            p.color = prefixColor(info);
            p.me = p.name.equals(me);
        }
        P sp = stats.get(me);
        if (sp == null) {
            if (zeroSelf == null || !zeroSelf.name.equals(me)) zeroSelf = new P(me);
            NetworkPlayerInfo info = nh == null ? null : nh.getPlayerInfo(me);
            zeroSelf.skin = info == null ? null : info.getLocationSkin();
            zeroSelf.color = prefixColor(info);
            zeroSelf.me = true;
            sp = zeroSelf;
        }
        selfP = sp;

        List<P> all = new ArrayList<>(stats.values());
        Collections.sort(all, (a, b) -> {
            if (a.kills != b.kills) return b.kills - a.kills;
            if (a.finals != b.finals) return b.finals - a.finals;
            return a.name.compareTo(b.name);
        });
        top.clear();
        int n = Math.min(all.size(), 8);
        for (int i = 0; i < n; i++) top.add(all.get(i));
        killBadge = Integer.toString(totalKills);
        killPill = "TOP " + Math.min(top.size(), maxRows.getInt());
        dirty = false;
    }

    // ───────────────────────── bố cục ─────────────────────────

    private List<T> teamsFor(boolean preview) { return preview ? sampleTeams : teams; }
    private List<P> topFor(boolean preview) { return preview ? sampleTop : top; }
    private P selfFor(boolean preview) { return preview ? sampleTop.get(0) : (selfP != null ? selfP : ZERO); }
    private boolean hasData() { return !teams.isEmpty() || !stats.isEmpty(); }

    private boolean selfVisible(boolean preview) { return showSelf.value && (preview || hasData()); }
    private boolean bedsVisible(boolean preview) { return showBeds.value && !teamsFor(preview).isEmpty(); }
    private int roomCount(boolean preview) { return showRoom.value ? Math.min(topFor(preview).size(), maxRows.getInt()) : 0; }

    /** Tổng độ rộng hàng giường gần như cố định dù 2–8 đội, để bảng luôn vừa góc màn hình. */
    private int chipW(int n) { return Math.max(12, Math.min(28, (CHIPS_W - (n - 1) * 2) / n)); }

    private int pillW(String tag, String val) { return fr().getStringWidth(tag) + fr().getStringWidth(val) + 7; }
    private int bedPillW(String val) { return 10 + 3 + fr().getStringWidth(val) + 6; }

    private int selfW(P p) {
        int w = 3;
        if (showHeads.value) w += HEAD + 3;
        return w + bedPillW(p.ks) + 2 + bedPillW(p.bs) + 3;
    }

    private int stripW(boolean preview) {
        boolean s = selfVisible(preview), b = bedsVisible(preview);
        int w = PAD * 2;
        if (s) w += selfW(selfFor(preview));
        if (s && b) w += 7;
        if (b) {
            int n = teamsFor(preview).size();
            w += n * chipW(n) + (n - 1) * 2;
        }
        return w;
    }

    @Override public int getWidth(boolean preview) {
        boolean strip = selfVisible(preview) || bedsVisible(preview);
        boolean room = roomCount(preview) > 0;
        if (!strip && !room) return 0;
        int w = strip ? stripW(preview) : 0;
        if (room) w = Math.max(w, ROOM_W);
        return w;
    }

    @Override public int getHeight(boolean preview) {
        boolean strip = selfVisible(preview) || bedsVisible(preview);
        int rc = roomCount(preview);
        if (!strip && rc == 0) return 20;
        int h = PAD * 2;
        if (strip) h += SH;
        if (rc > 0) h += (strip ? SEC_GAP : 0) + HDR + HDR_GAP + rc * ROW_H - 2;
        return h;
    }

    // ───────────────────────── vẽ ─────────────────────────

    private void header(int x, int y, int w, String title, String badge, String pill, int accent) {
        Draw.rrect(x + PAD, y, x + w - PAD, y + HDR, 5, Draw.withAlpha(accent, 0x28));
        int ix = x + PAD + 5, iy = y + 3;
        Draw.rrect(ix, iy, ix + 8, iy + 8, 4, accent);
        Draw.rrect(ix + 2, iy + 2, ix + 6, iy + 6, 2, 0xAA000000);
        int tx = ix + 8 + 5;
        fr().drawString(title, tx, y + 3, accent, shadow.value);
        int bx = tx + fr().getStringWidth(title) + 6;
        int bw = fr().getStringWidth(badge) + 8;
        Draw.rrect(bx, y + 2, bx + bw, y + HDR - 2, 5, Draw.withAlpha(accent, 0x55));
        fr().drawString(badge, bx + 4, y + 3, 0xFFFFFFFF, false);
        if (pill != null && pill.length() > 0) {
            int pw = fr().getStringWidth(pill) + 8;
            int px = x + w - PAD - 4 - pw;
            Draw.rrect(px, y + 2, px + pw, y + HDR - 2, 5, 0x30FFFFFF);
            fr().drawString(pill, px + 4, y + 3, 0xFFD0D0D0, false);
        }
    }

    /** Giường 10x7 (hoặc 8x5 khi ô hẹp): gối + chăn màu đội + khung gỗ; xám khi đã mất. */
    private static void bedIcon(int x, int y, int blanket, boolean broken, boolean small) {
        int pillow = broken ? 0xFF777777 : 0xFFFFFFFF;
        int cover = broken ? 0xFF555555 : blanket;
        int frame = broken ? 0xFF3A3A3A : 0xFF8B5A2B;
        if (small) {
            Gui.drawRect(x, y, x + 2, y + 3, pillow);
            Gui.drawRect(x + 2, y, x + 8, y + 3, cover);
            Gui.drawRect(x, y + 3, x + 8, y + 5, frame);
            return;
        }
        Gui.drawRect(x, y, x + 3, y + 4, pillow);
        Gui.drawRect(x + 3, y, x + 10, y + 4, cover);
        Gui.drawRect(x, y + 4, x + 10, y + 6, frame);
        Gui.drawRect(x, y + 6, x + 1, y + 7, frame);
        Gui.drawRect(x + 9, y + 6, x + 10, y + 7, frame);
    }

    private static void px(int x, int y, int[][] cells, int color) {
        for (int[] c : cells) Gui.drawRect(x + c[0], y + c[1], x + c[0] + 1, y + c[1] + 1, color);
    }

    /** Kiếm sắt pixel 9x9 (vẽ tay, na ná kiếm sắt Minecraft): mũi chỉ lên trái, cán nâu ở dưới phải. */
    private static void swordIcon(int x, int y) {
        px(x, y, new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {2, 0}, {3, 1}, {4, 2}, {5, 3}}, 0xFF2E2E2E);   // viền tối
        px(x, y, new int[][] {{0, 0}, {1, 1}, {2, 2}, {3, 3}}, 0xFFEDEDED);                                      // lưỡi sáng
        px(x, y, new int[][] {{1, 0}, {2, 1}, {3, 2}, {4, 3}}, 0xFFB4B4B4);                                      // cạnh lưỡi
        px(x, y, new int[][] {{6, 2}, {5, 3}, {4, 4}, {3, 5}, {2, 6}}, 0xFF6E6E6E);                              // chắn tay
        px(x, y, new int[][] {{5, 5}, {6, 6}, {7, 7}}, 0xFF7A4A1E);                                              // cán
        px(x, y, new int[][] {{5, 6}, {6, 7}}, 0xFF4F2E0F);                                                      // bóng cán
        px(x, y, new int[][] {{8, 8}}, 0xFF9A9A9A);                                                              // núm
    }

    private static void fillRound(int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 >= 2) Draw.rrect(x1, y1, x2, y2, 1, color);
        else if (x2 > x1) Gui.drawRect(x1, y1, x2, y2, color);
    }

    private void drawHead(P p, int hx, int hy) {
        if (p.skin == null) {
            Draw.rrect(hx, hy, hx + HEAD, hy + HEAD, 3, 0x40FFFFFF);
            String q = p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase();
            fr().drawString(q, hx + (HEAD - fr().getStringWidth(q)) / 2, hy + (HEAD - 8) / 2, 0xFFFFFFFF, true);
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(p.skin);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Gui.drawScaledCustomSizeModalRect(hx, hy, 8f, 8f, 8, 8, HEAD, HEAD, 64f, 64f);   // mặt
        Gui.drawScaledCustomSizeModalRect(hx, hy, 40f, 8f, 8, 8, HEAD, HEAD, 64f, 64f);  // lớp mũ
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void tagPill(int x, int y, int w, int accent, String tag, String val, int valColor) {
        Draw.rrect(x, y, x + w, y + 10, 4, Draw.withAlpha(accent, 0x38));
        fr().drawString(tag, x + 3, y + 1, 0xFF9A9A9A, false);
        fr().drawString(val, x + 4 + fr().getStringWidth(tag), y + 1, valColor, false);
    }

    /** Kill của bản thân: [avatar] [K n] [giường n]. Trả về độ rộng đã dùng. */
    private int drawSelf(int x, int y, P p) {
        int w = selfW(p);
        Draw.rrect(x, y, x + w, y + SH, 5, 0x1CFFFFFF);
        int cx = x + 3;
        if (showHeads.value) {
            int hy = y + (SH - HEAD) / 2;
            Draw.rrect(cx - 1, hy - 1, cx + HEAD + 1, hy + HEAD + 1, 3, 0x66000000);
            drawHead(p, cx, hy);
            Draw.ring(cx - 1, hy - 1, cx + HEAD + 1, hy + HEAD + 1, Draw.withAlpha(p.color, 0xAA));
            cx += HEAD + 3;
        }
        int py = y + (SH - 10) / 2;
        int kw = bedPillW(p.ks);
        Draw.rrect(cx, py, cx + kw, py + 10, 4, Draw.withAlpha(KILL_ACCENT, 0x38));
        swordIcon(cx + 3, py + 1);
        fr().drawString(p.ks, cx + 16, py + 1, 0xFFFFFFFF, false);
        cx += kw + 2;
        int bw = bedPillW(p.bs);
        Draw.rrect(cx, py, cx + bw, py + 10, 4, Draw.withAlpha(BED_ACCENT, 0x38));
        bedIcon(cx + 3, py + 2, BED_ACCENT, false, false);
        fr().drawString(p.bs, cx + 16, py + 1, 0xFFFFFFFF, false);
        return w;
    }

    /** Một hàng ngang các giường: số đội 2/4/6/7/8 tự đổi độ rộng ô. */
    private void drawBeds(int x, int y, List<T> tl) {
        int n = tl.size(), cw = chipW(n);
        for (int i = 0; i < n; i++) {
            T t = tl.get(i);
            int bx = x + i * (cw + 2);
            Draw.rrect(bx, y, bx + cw, y + SH, 4, t.you ? 0x34FFFFFF : 0x18FFFFFF);
            int inset = cw < 16 ? 2 : 3;
            Draw.rrect(bx + inset, y + SH - 3, bx + cw - inset, y + SH - 1, 1, Draw.withAlpha(t.color, t.state == 2 ? 0x55 : 0xFF));
            boolean small = cw < 16;
            int iw = small ? 8 : 10;
            bedIcon(bx + (cw - iw) / 2, y + (small ? 4 : 3), t.color, t.state != 0, small);
            int mid = bx + cw / 2;
            if (t.state == 0) {
                Draw.rrect(mid - 2, y + 12, mid + 2, y + 16, 2, GREEN);
            } else if (t.state == 1) {
                fr().drawString(t.leftStr, mid - fr().getStringWidth(t.leftStr) / 2, y + 11, ORANGE, false);
            } else {
                fr().drawString("x", mid - fr().getStringWidth("x") / 2, y + 11, 0xFF777777, false);
            }
        }
    }

    @Override protected void draw(int x, int y, boolean preview) {
        boolean self = selfVisible(preview), beds = bedsVisible(preview);
        boolean strip = self || beds;
        int rc = roomCount(preview);
        if (!strip && rc == 0) return;
        int w = getWidth(preview), h = getHeight(preview);
        drawBox(x, y, w, h);
        int cy = y + PAD;

        if (strip) {
            int cx = x + PAD;
            if (self) cx += drawSelf(cx, cy, selfFor(preview));
            if (self && beds) {
                cx += 3;
                Gui.drawRect(cx, cy + 3, cx + 1, cy + SH - 3, 0x30FFFFFF);
                cx += 4;
            }
            if (beds) drawBeds(cx, cy, teamsFor(preview));
            cy += SH + SEC_GAP;
        }

        if (rc > 0) {
            List<P> pl = topFor(preview);
            int lead = Math.max(1, pl.get(0).kills);
            header(x, cy, w, "BẢNG KILL", preview ? "17" : killBadge, preview ? "TOP " + rc : killPill, KILL_ACCENT);
            cy += HDR + HDR_GAP;
            boolean heads = showHeads.value;
            int rowRight = x + w - PAD - 2;
            for (int i = 0; i < rc; i++) {
                P p = pl.get(i);
                int ry = cy + i * ROW_H;
                int rh = ROW_H - 2;
                Draw.rrect(x + PAD, ry, x + w - PAD, ry + rh, 4, p.me ? 0x26FFFFFF : 0x12FFFFFF);
                Draw.rrect(x + PAD, ry + 2, x + PAD + 2, ry + rh - 2, 1, p.color);
                int textX = x + PAD + 7;
                if (heads) {
                    int hx = x + PAD + 6, hy = ry + (rh - HEAD) / 2;
                    Draw.rrect(hx - 1, hy - 1, hx + HEAD + 1, hy + HEAD + 1, 3, 0x66000000);
                    drawHead(p, hx, hy);
                    Draw.ring(hx - 1, hy - 1, hx + HEAD + 1, hy + HEAD + 1, Draw.withAlpha(p.color, 0xAA));
                    textX = hx + HEAD + 6;
                }
                int pr = rowRight;
                int kw = fr().getStringWidth(p.ks) + 8;
                Draw.rrect(pr - kw, ry + 2, pr, ry + 12, 4, Draw.withAlpha(p.color, 0x38));
                fr().drawString(p.ks, pr - kw + 4, ry + 3, 0xFFFFFFFF, false);
                pr -= kw + 3;
                fr().drawString(fr().trimStringToWidth(p.name, Math.max(10, pr - textX)), textX, ry + 3, p.color, shadow.value);
                float target = p.kills / (float) lead;
                p.shown += (target - p.shown) * 0.2f;
                int by = ry + 13, bw = rowRight - textX;
                Draw.rrect(textX, by, textX + bw, by + 3, 1, 0x55000000);
                fillRound(textX, by, textX + Math.round(bw * p.shown), by + 3, p.color);
            }
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
