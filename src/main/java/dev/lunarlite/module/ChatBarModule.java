package dev.lunarlite.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat bar module (Forge 1.8.9)
 * - Thanh chat bo góc, nền mờ, viền màu accent
 * - Hiệu ứng trượt lên + fade khi mở chat
 * - Bộ đếm ký tự (x/100)
 * - Tối ưu Bedwars: giữ tin sự kiện quan trọng, avatar + giường màu team, giường bị phá thì giường xám
 *
 * Đăng ký: new ChatBarModule().register();
 */
public class ChatBarModule {

    // ===== Cấu hình =====
    public static boolean enabled = true;
    public static boolean hideOriginal = false; // tự ẩn tin gốc khi đã có bản dịch (chỉ giữ bản dịch)
    public static boolean showKills = true;    // tin giết người: [avt người giết] giết [avt người bị giết]
    public static boolean bedwars = true;      // giữ tin sự kiện Bedwars (phá giường, final kill...) khi playerOnly
    public static boolean charCounter = true;
    public static boolean playerHeads = true;  // avatar + giường màu team trước tin nhắn
    public static int iconSize = 8;            // kích thước avatar (px), 6..14
    public static int iconX = 0;               // dịch icon ngang (px)
    public static int iconY = 0;               // dịch icon dọc (px, âm = lên)
    public static boolean autoTranslate = true; // tự dịch tin người chơi sang tiếng Việt
    public static String translateTo = "vi";
    public static boolean playerOnly = true;   // chỉ hiện tin người chơi, chặn tin hệ thống
    public static int accent = 0xFF55AAFF;     // ARGB
    public static int background = 0x90000000; // ARGB

    // ===== Lưu / tải cấu hình (config/lunarlite_chatbar.cfg) =====
    private static Configuration cfg;
    private static boolean openRequested;

    private static void load() {
        cfg = new Configuration(new File(Minecraft.getMinecraft().mcDataDir, "config/lunarlite_chatbar.cfg"));
        cfg.load();
        enabled = cfg.get("chatbar", "enabled", enabled).getBoolean();
        playerHeads = cfg.get("chatbar", "heads", playerHeads).getBoolean();
        autoTranslate = cfg.get("chatbar", "translate", autoTranslate).getBoolean();
        showKills = cfg.get("chatbar", "kills", showKills).getBoolean();
        hideOriginal = cfg.get("chatbar", "hideOriginal", hideOriginal).getBoolean();
        playerOnly = cfg.get("chatbar", "playerOnly", playerOnly).getBoolean();
        bedwars = cfg.get("chatbar", "bedwars", bedwars).getBoolean();
        iconSize = cfg.getInt("iconSize", "chatbar", iconSize, 6, 14, "Kích thước avatar (px)");
        iconX = cfg.getInt("iconX", "chatbar", iconX, -20, 20, "Lệch ngang");
        iconY = cfg.getInt("iconY", "chatbar", iconY, -10, 10, "Lệch dọc");
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        cfg.get("chatbar", "enabled", true).set(enabled);
        cfg.get("chatbar", "heads", true).set(playerHeads);
        cfg.get("chatbar", "translate", true).set(autoTranslate);
        cfg.get("chatbar", "kills", true).set(showKills);
        cfg.get("chatbar", "hideOriginal", false).set(hideOriginal);
        cfg.get("chatbar", "playerOnly", true).set(playerOnly);
        cfg.get("chatbar", "bedwars", true).set(bedwars);
        cfg.get("chatbar", "iconSize", 8).set(iconSize);
        cfg.get("chatbar", "iconX", 0).set(iconX);
        cfg.get("chatbar", "iconY", 0).set(iconY);
        cfg.save();
    }

    /** Mở màn hình cài đặt (gọi được từ nút trong MenuScreen hoặc bằng lệnh /chatbar). */
    public static void openSettings() {
        openRequested = true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !openRequested) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.currentScreen == null) {
            openRequested = false;
            mc.displayGuiScreen(new SettingsScreen());
        }
    }

    public void register() {
        load();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "chatbar"; }
            @Override public String getCommandUsage(ICommandSender s) { return "/chatbar (mở cài đặt) | /chatbar <size|x|y> <số> | reset"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }
            @Override public void processCommand(ICommandSender s, String[] a) throws CommandException {
                String msg;
                try {
                    if (a.length == 0) { openSettings(); return; }
                    if (a.length == 2 && a[0].equalsIgnoreCase("hide")) { hideOriginalLine(Integer.parseInt(a[1])); return; }
                    if (a.length == 1 && a[0].equalsIgnoreCase("reset")) {
                        iconSize = 8; iconX = 0; iconY = 0;
                        msg = "Đã reset icon chat.";
                    } else if (a.length == 2) {
                        int v = Integer.parseInt(a[1]);
                        if (a[0].equalsIgnoreCase("size")) iconSize = Math.max(6, Math.min(14, v));
                        else if (a[0].equalsIgnoreCase("x")) iconX = Math.max(-20, Math.min(20, v));
                        else if (a[0].equalsIgnoreCase("y")) iconY = Math.max(-10, Math.min(10, v));
                        else { s.addChatMessage(new ChatComponentText(getCommandUsage(s))); return; }
                        msg = "size=" + iconSize + " x=" + iconX + " y=" + iconY + " (áp dụng cho tin mới)";
                    } else {
                        msg = getCommandUsage(s);
                    }
                } catch (NumberFormatException ex) {
                    msg = getCommandUsage(s);
                }
                save();
                s.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + msg));
            }
        });
    }

    // Thay GuiChat mặc định bằng GuiChat của module
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (!enabled || e.gui == null) return;
        if (e.gui.getClass() == GuiChat.class) {
            String def = "";
            try {
                def = ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat) e.gui,
                        "defaultInputFieldText", "field_146409_v");
            } catch (Exception ignored) {}
            e.gui = new FancyChat(def);
        }
    }

    // ===== Lọc tin người chơi =====
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:\\[[^\\]]{1,20}\\]\\s*)*(?:<([A-Za-z0-9_]{1,16})>\\s*(\\S.*)|([A-Za-z0-9_]{1,16})\\s*[:»>]\\s*(\\S.*))$");
    private static final Pattern NAME_TAIL = Pattern.compile("([A-Za-z0-9_]{1,16})\\s*$");

    private static String argText(Object o) {
        String t = o instanceof IChatComponent ? ((IChatComponent) o).getUnformattedText() : String.valueOf(o);
        return EnumChatFormatting.getTextWithoutFormattingCodes(t);
    }

    /** Trả về {tên, nội dung} nếu là tin người chơi, ngược lại null. */
    private static String[] parsePlayerChat(ClientChatReceivedEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.message instanceof ChatComponentTranslation) {
            ChatComponentTranslation t = (ChatComponentTranslation) e.message;
            if (!"chat.type.text".equals(t.getKey())) return null;
            Object[] a = t.getFormatArgs();
            if (a.length == 0) return null;
            String raw = argText(a[0]).replaceAll("\\[[^\\]]*\\]", "").trim();
            Matcher m = NAME_TAIL.matcher(raw);
            return new String[]{m.find() ? m.group(1) : "?", a.length > 1 ? argText(a[1]) : ""};
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(e.message.getUnformattedText());
        if (plain == null) return null;
        Matcher m = PLAYER_CHAT.matcher(plain.trim());
        if (!m.matches()) return null;
        boolean angle = m.group(1) != null;
        String n = angle ? m.group(1) : m.group(3);
        String body = angle ? m.group(2) : m.group(4);
        // server tùy biến: tên phải đang online để tránh nhầm "Info: ..."
        if (mc.getNetHandler() == null || mc.getNetHandler().getPlayerInfo(n) == null) return null;
        return new String[]{n, body};
    }

    // ===== Tự dịch tiếng Việt =====
    private static final ExecutorService TRANSLATOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ChatTranslate");
            t.setDaemon(true);
            return t;
        }
    });
    private static final Map<String, String[]> CACHE = new LinkedHashMap<String, String[]>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) { return size() > 256; }
    };

    /** Gọi Google Translate (endpoint công khai) -> {bản dịch, mã ngôn ngữ gốc}. */
    private static String[] translate(String text) throws IOException {
        synchronized (CACHE) {
            String[] hit = CACHE.get(text);
            if (hit != null) return hit;
        }
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                + translateTo + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();

        JsonArray root = new JsonParser().parse(new String(out.toByteArray(), "UTF-8")).getAsJsonArray();
        StringBuilder sb = new StringBuilder();
        for (com.google.gson.JsonElement seg : root.get(0).getAsJsonArray()) {
            sb.append(seg.getAsJsonArray().get(0).getAsString());
        }
        String lang = root.size() > 2 && !root.get(2).isJsonNull() ? root.get(2).getAsString() : "";
        String[] res = {sb.toString(), lang};
        synchronized (CACHE) { CACHE.put(text, res); }
        return res;
    }

    // Tin gốc đã in ra chat (id -> component) để nút [Ẩn gốc] xoá đúng dòng
    private static final Map<Integer, IChatComponent> ORIGINALS = new LinkedHashMap<Integer, IChatComponent>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, IChatComponent> eldest) { return size() > 128; }
    };
    private static int nextOriginal = 0;

    /** Xoá tin gốc khỏi chat, chỉ giữ bản dịch. */
    private static void hideOriginalLine(int id) {
        IChatComponent orig = ORIGINALS.remove(id);
        if (orig == null) return;
        GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
        try {
            List<ChatLine> all = ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "chatLines", "field_146252_h");
            for (Iterator<ChatLine> it = all.iterator(); it.hasNext(); ) {
                if (it.next().getChatComponent() == orig) it.remove();
            }
            chat.refreshChat();
        } catch (Exception ignored) {
        }
    }

    private static void requestTranslate(final String name, String body, final String plain, IChatComponent shown) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && name.equalsIgnoreCase(mc.thePlayer.getName())) return; // bỏ qua tin của mình
        final String text = body == null ? "" : body.trim();
        if (text.length() < 2 || !text.matches(".*\\p{L}.*")) return;
        final String src = text.length() > 200 ? text.substring(0, 200) : text;
        final int id = nextOriginal++;
        ORIGINALS.put(id, shown);

        TRANSLATOR.submit(new Runnable() {
            @Override public void run() {
                try {
                    final String[] r = translate(src);
                    if (r[1].toLowerCase().startsWith(translateTo) || r[0].equalsIgnoreCase(src)) return;
                    final Minecraft m = Minecraft.getMinecraft();
                    m.addScheduledTask(new Runnable() {
                        @Override public void run() {
                            if (m.thePlayer == null) return;
                            // dòng dịch tự mang avatar + tên để vẫn đọc được khi tin gốc bị ẩn
                            String head = playerHeads ? makeMarker(name, plain, true) : "";
                            IChatComponent line = new ChatComponentText(head + EnumChatFormatting.AQUA + "[VI] "
                                    + teamColorOf(name) + name + EnumChatFormatting.GRAY + ": " + r[0]);
                            if (!hideOriginal) {
                                ChatComponentText btn = new ChatComponentText(" " + EnumChatFormatting.RED + "[Ẩn gốc]");
                                btn.setChatStyle(new ChatStyle()
                                        .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatbar hide " + id))
                                        .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                new ChatComponentText("Ẩn tin gốc, chỉ giữ bản dịch"))));
                                line.appendSibling(btn);
                            }
                            m.thePlayer.addChatMessage(line);
                            if (hideOriginal) hideOriginalLine(id);
                        }
                    });
                } catch (Exception ignored) {
                    // lỗi mạng: bỏ qua, không làm phiền chat
                }
            }
        });
    }

    // ===== Bedwars =====
    private static final Pattern BED_BROKEN = Pattern.compile("BED DESTRUCTION\\s*>\\s*(\\w+) Bed");
    private static final Pattern TEAM_TAG = Pattern.compile("\\[(RED|BLUE|GREEN|YELLOW|AQUA|WHITE|PINK|GRAY)\\]");
    /** Tin hệ thống được giữ lại khi playerOnly bật. Thêm/bớt từ khóa tại đây. */
    private static final Pattern BW_EVENT = Pattern.compile(
            "BED DESTRUCTION|FINAL KILL|TEAM ELIMINATED|has been eliminated|You have been eliminated"
                    + "|Protect your bed|The game starts in|Victory|Your bed was");
    private static final Set<Integer> DEAD_BEDS = new HashSet<Integer>(); // màu RGB của team đã mất giường

    private static int colorByName(String n) {
        if (n.equals("RED")) return MC_COLORS[12];
        if (n.equals("BLUE")) return MC_COLORS[9];
        if (n.equals("GREEN")) return MC_COLORS[10];
        if (n.equals("YELLOW")) return MC_COLORS[14];
        if (n.equals("AQUA")) return MC_COLORS[11];
        if (n.equals("WHITE")) return MC_COLORS[15];
        if (n.equals("PINK")) return MC_COLORS[13];
        if (n.equals("GRAY")) return MC_COLORS[8];
        return -1;
    }

    private static void trackBeds(String plain) {
        Matcher m = BED_BROKEN.matcher(plain);
        if (!m.find()) return;
        String t = m.group(1);
        int c;
        if (t.equalsIgnoreCase("Your")) {
            Minecraft mc = Minecraft.getMinecraft();
            NetworkPlayerInfo me = mc.thePlayer == null || mc.getNetHandler() == null
                    ? null : mc.getNetHandler().getPlayerInfo(mc.thePlayer.getName());
            c = me == null ? -1 : teamColor(me);
        } else {
            c = colorByName(t.toUpperCase());
        }
        if (c != -1) DEAD_BEDS.add(c);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        DEAD_BEDS.clear(); // sang ván mới
    }

    // ===== Tin giết người =====
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern KILL_VERB = Pattern.compile(
            "\\b(killed|shot|knocked|struck|fell|died|burned|drowned|exploded|blown|suffocated|starved|squashed"
                    + "|withered|perished|slain|slaughtered|thrown|pushed|punched|fireballed|melted|zapped|hit)\\b");

    /** {người bị giết, người giết hoặc null} nếu là tin chết/giết, ngược lại null. */
    private static String[] parseKill(String plain) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null || !KILL_VERB.matcher(plain).find()) return null;
        Matcher m = WORD.matcher(plain);
        String victim = null, killer = null;
        while (m.find()) {
            String w = m.group();
            if (mc.getNetHandler().getPlayerInfo(w) == null) continue;
            if (victim == null) {
                if (m.start() != 0) return null; // tin chết luôn bắt đầu bằng tên người bị giết
                victim = w;
            } else if (!w.equals(victim)) {
                killer = w;
            }
        }
        return victim == null ? null : new String[]{victim, killer};
    }

    private static String colorCode(int rgb) {
        for (int i = 0; i < 16; i++) if (MC_COLORS[i] == rgb) return S + "0123456789abcdef".charAt(i);
        return S + "7";
    }

    private static String teamColorOf(String name) {
        NetworkPlayerInfo info = Minecraft.getMinecraft().getNetHandler().getPlayerInfo(name);
        int c = info == null ? -1 : teamColor(info);
        return c == -1 ? S + "7" : colorCode(c);
    }

    /** [avt người giết] Tên giết [avt người bị giết] Tên */
    private static IChatComponent buildKill(String victim, String killer, boolean fin, String plain) {
        String mk = playerHeads ? makeMarker(killer, plain, false) : "";
        String mv = playerHeads ? makeMarker(victim, plain, false) : "";
        StringBuilder sb = new StringBuilder();
        sb.append(mk).append(teamColorOf(killer)).append(killer);
        sb.append(S).append("c giết ");
        sb.append(mv).append(teamColorOf(victim)).append(victim);
        if (fin) sb.append(S).append("6").append(S).append("l FINAL KILL!");
        return new ChatComponentText(sb.toString());
    }

    // ===== Avatar + giường =====
    private static final String S = "\u00A7";
    private static final String SENT = S + "r" + S + "r" + S + "r";
    private static final int[] MC_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** Chừa chỗ cho icon: đầu (+ giường) + khoảng cách, mỗi dấu cách rộng 4px. */
    private static String iconSpace(boolean bed) {
        int w = iconSize + 2 + (bed ? (int) Math.ceil(9 * iconSize / 8f) + 1 : 0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (w + 3) / 4; i++) sb.append(' ');
        return sb.toString();
    }

    private static class Icon {
        final String name; final int color; final boolean bed;
        Icon(String name, int color, boolean bed) { this.name = name; this.color = color; this.bed = bed; }
    }

    private static final Map<Integer, Icon> ICONS = new LinkedHashMap<Integer, Icon>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Icon> eldest) { return size() > 512; }
    };
    private static int nextId = 0;

    private static int teamColor(NetworkPlayerInfo info) {
        ScorePlayerTeam t = info.getPlayerTeam();
        if (t == null) return -1;
        String p = t.getColorPrefix();
        for (int i = p.length() - 2; i >= 0; i--) {
            if (p.charAt(i) == '\u00A7') {
                int idx = "0123456789abcdef".indexOf(Character.toLowerCase(p.charAt(i + 1)));
                if (idx >= 0) return MC_COLORS[idx];
            }
        }
        return -1;
    }

    /** Tạo marker ẩn (chỉ gồm mã màu, rộng 0px) + khoảng trống cho icon. */
    private static String makeMarker(String name, String plain, boolean bed) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return "";
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
        if (info == null) return "";
        int id = nextId++ & 0xFFF;
        int color = teamColor(info);
        if (color == -1) { // chưa có team scoreboard: lấy từ tag [RED]/[BLUE]... trong tin
            Matcher tm = TEAM_TAG.matcher(plain);
            if (tm.find()) color = colorByName(tm.group(1));
        }
        ICONS.put(id, new Icon(name, color, bed));
        String h = String.format("%03x", id);
        return SENT + S + h.charAt(0) + S + h.charAt(1) + S + h.charAt(2) + S + "r" + iconSpace(bed);
    }

    /**
     * Vẽ mọi icon trong dòng: x = độ rộng chữ đứng trước marker.
     * Dòng chat thường mở đầu bằng "§r" (component rỗng) nên "§r" + marker tạo ra một khớp SENT sai
     * ở vị trí 0 -> khớp sai thì dịch 1 ký tự rồi tìm tiếp, không được nhảy qua marker thật.
     */
    private static void drawIcons(Minecraft mc, String fmt, int y, int a) {
        int from = 0;
        while (true) {
            int i = fmt.indexOf(SENT, from);
            if (i < 0) return;
            int p = i + SENT.length();
            from = i + 1;
            if (fmt.length() < p + 6) continue;
            try {
                Icon ic = ICONS.get(Integer.parseInt("" + fmt.charAt(p + 1) + fmt.charAt(p + 3) + fmt.charAt(p + 5), 16));
                if (ic != null) {
                    drawIcon(mc, ic, y, a, mc.fontRendererObj.getStringWidth(fmt.substring(0, i)));
                    from = p + 6;
                }
            } catch (NumberFormatException ex) {
                // khớp sai, thử vị trí kế tiếp
            }
        }
    }

    private static void drawIcon(Minecraft mc, Icon ic, int y, int a, int x0) {
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(ic.name);
        int sz = iconSize;
        int top = y - 8 + (8 - sz) / 2 + iconY;
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, a / 255f);
        if (info != null) {
            mc.getTextureManager().bindTexture(info.getLocationSkin());
            Gui.drawScaledCustomSizeModalRect(x0 + iconX, top, 8f, 8f, 8, 8, sz, sz, 64f, 64f);   // mặt
            Gui.drawScaledCustomSizeModalRect(x0 + iconX, top, 40f, 8f, 8, 8, sz, sz, 64f, 64f);  // lớp mũ
        }
        if (ic.bed && ic.color != -1) {
            float k = sz / 8f;
            int al = a << 24;
            boolean dead = DEAD_BEDS.contains(ic.color);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x0 + iconX + sz + 2, top + sz / 2f - 3f * k, 0f);
            GlStateManager.scale(k, k, 1f);
            Gui.drawRect(0, 2, 9, 6, al | (dead ? 0x444444 : 0x8B5A2B));   // khung gỗ
            Gui.drawRect(3, 1, 9, 4, al | (dead ? 0x777777 : ic.color));   // chăn: xám nếu mất giường
            Gui.drawRect(0, 1, 3, 3, al | (dead ? 0x999999 : 0xFFFFFF));   // gối
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // Vẽ icon lên từng dòng chat có marker
    @SubscribeEvent
    public void onRenderChat(RenderGameOverlayEvent.Post e) {
        if (!enabled || !playerHeads || e.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN || mc.getNetHandler() == null) return;

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        List<ChatLine> lines;
        Integer scroll;
        try {
            lines = ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "drawnChatLines", "field_146253_i");
            scroll = ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "scrollPos", "field_146250_j");
        } catch (Exception ex) {
            return;
        }

        boolean open = chat.getChatOpen();
        float opacity = mc.gameSettings.chatOpacity * 0.9f + 0.1f;
        int tick = mc.ingameGUI.getUpdateCounter();
        float sc = chat.getChatScale();

        GlStateManager.pushMatrix();
        GlStateManager.translate(2f, e.resolution.getScaledHeight() - 48 + 20f, 0f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.enableBlend();
        for (int i = 0; i + scroll < lines.size() && i < chat.getLineCount(); i++) {
            ChatLine line = lines.get(i + scroll);
            if (line == null) continue;
            int age = tick - line.getUpdatedCounter();
            if (age >= 200 && !open) continue;
            double d = Math.max(0.0, Math.min(1.0, (1.0 - age / 200.0) * 10.0));
            int a = (int) ((open ? 255 : (int) (255 * d * d)) * opacity);
            if (a <= 3) continue;
            drawIcons(mc, line.getChatComponent().getFormattedText(), -i * 9, a);
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // Lọc tin hệ thống (giữ sự kiện Bedwars) + marker icon
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ClientChatReceivedEvent e) {
        if (!enabled || e.type == 2) return; // 2 = action bar, giữ nguyên

        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(e.message.getUnformattedText());
        if (plain == null) plain = "";
        if (bedwars) trackBeds(plain);

        String[] pc = parsePlayerChat(e);
        String name = pc == null ? null : pc[0];

        // tin giết người: viết lại thành [avt] Người giết giết [avt] Người bị giết
        if (showKills && name == null) {
            String[] k = parseKill(plain);
            if (k != null) {
                if (k[1] != null) {
                    e.message = buildKill(k[0], k[1], plain.contains("FINAL KILL"), plain);
                } else if (playerHeads) { // tự chết (rơi hố, cháy...): giữ nguyên tin, thêm avt người chết
                    IChatComponent r = new ChatComponentText(makeMarker(k[0], plain, false));
                    r.appendSibling(e.message);
                    e.message = r;
                }
                return;
            }
        }
        if (playerOnly && name == null && !(bedwars && BW_EVENT.matcher(plain).find())) {
            e.setCanceled(true);
            return;
        }

        String marker = (playerHeads && name != null) ? makeMarker(name, plain, true) : "";
        if (!marker.isEmpty()) {
            IChatComponent root = new ChatComponentText(marker);
            root.appendSibling(e.message);
            e.message = root;
        }

        // e.message lúc này chính là đối tượng sẽ được in -> dùng để tìm lại và ẩn khi bấm [Ẩn gốc]
        if (autoTranslate && pc != null) requestTranslate(pc[0], pc[1], plain, e.message);
    }

    // ===== Màn hình cài đặt =====
    /** Thanh kéo số nguyên. */
    public static class ValueSlider extends GuiButton {
        public interface Setter { void set(int v); }

        private final String label;
        private final int min, max;
        private final Setter setter;
        private float frac;
        private boolean dragging;

        ValueSlider(int id, int x, int y, int w, String label, int min, int max, int value, Setter setter) {
            super(id, x, y, w, 20, label + ": " + value);
            this.label = label; this.min = min; this.max = max; this.setter = setter;
            this.frac = (value - min) / (float) (max - min);
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            if (dragging) update(mouseX);
            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1f, 1f, 1f, 1f);
            int hx = xPosition + (int) (frac * (width - 8));
            drawTexturedModalRect(hx, yPosition, 0, 66, 4, 20);
            drawTexturedModalRect(hx + 4, yPosition, 196, 66, 4, 20);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (!super.mousePressed(mc, mouseX, mouseY)) return false;
            dragging = true;
            update(mouseX);
            return true;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
            save();
        }

        private void update(int mouseX) {
            float f = Math.max(0f, Math.min(1f, (mouseX - (xPosition + 4)) / (float) (width - 8)));
            int v = min + Math.round(f * (max - min));
            frac = (v - min) / (float) (max - min);
            setter.set(v);
            displayString = label + ": " + v;
        }
    }

    public static class SettingsScreen extends GuiScreen {
        private static final String[] LABELS = {"Chat bar", "Avatar + giường", "Dịch tiếng Việt",
                "Tin giết người", "Chỉ tin người chơi", "Tối ưu Bedwars", "Tự ẩn tin gốc"};

        private static boolean get(int i) {
            switch (i) {
                case 0: return enabled;
                case 1: return playerHeads;
                case 2: return autoTranslate;
                case 3: return showKills;
                case 4: return playerOnly;
                case 5: return bedwars;
                default: return hideOriginal;
            }
        }

        private static void flip(int i) {
            switch (i) {
                case 0: enabled = !enabled; break;
                case 1: playerHeads = !playerHeads; break;
                case 2: autoTranslate = !autoTranslate; break;
                case 3: showKills = !showKills; break;
                case 4: playerOnly = !playerOnly; break;
                case 5: bedwars = !bedwars; break;
                default: hideOriginal = !hideOriginal; break;
            }
        }

        private int top() {
            return Math.max(24, (height - 184) / 2);
        }

        @Override
        public void initGui() {
            buttonList.clear();
            int lx = width / 2 - 156, rx = width / 2 + 6, y = top();

            // Cột trái: công tắc bật/tắt
            for (int i = 0; i < LABELS.length; i++) {
                buttonList.add(new GuiButton(i, lx, y + i * 22, 150, 20,
                        LABELS[i] + ": " + (get(i) ? EnumChatFormatting.GREEN + "BẬT" : EnumChatFormatting.RED + "TẮT")));
            }

            // Cột phải: thanh kéo kích thước / vị trí
            buttonList.add(new ValueSlider(10, rx, y, 150, "Cỡ avatar", 6, 14, iconSize,
                    new ValueSlider.Setter() { public void set(int v) { iconSize = v; } }));
            buttonList.add(new ValueSlider(11, rx, y + 22, 150, "Avatar ngang", -20, 20, iconX,
                    new ValueSlider.Setter() { public void set(int v) { iconX = v; } }));
            buttonList.add(new ValueSlider(12, rx, y + 44, 150, "Avatar dọc", -10, 10, iconY,
                    new ValueSlider.Setter() { public void set(int v) { iconY = v; } }));
            // Kích thước khung chat (thanh kéo gốc của game)
            buttonList.add(new GuiOptionSlider(20, rx, y + 66, GameSettings.Options.CHAT_SCALE));
            buttonList.add(new GuiOptionSlider(21, rx, y + 88, GameSettings.Options.CHAT_WIDTH));
            buttonList.add(new GuiOptionSlider(22, rx, y + 110, GameSettings.Options.CHAT_HEIGHT_FOCUSED));
            buttonList.add(new GuiOptionSlider(23, rx, y + 132, GameSettings.Options.CHAT_OPACITY));

            buttonList.add(new GuiButton(98, lx, y + 160, 150, 20, "Mặc định"));
            buttonList.add(new GuiButton(99, rx, y + 160, 150, 20, "Xong"));
        }

        @Override
        protected void actionPerformed(GuiButton b) throws IOException {
            if (b instanceof ValueSlider || b instanceof GuiOptionSlider) return; // không dựng lại màn hình khi đang kéo
            if (b.id < LABELS.length) {
                flip(b.id);
            } else if (b.id == 98) {
                enabled = playerHeads = autoTranslate = showKills = playerOnly = bedwars = true;
                hideOriginal = false;
                iconSize = 8; iconX = 0; iconY = 0;
            } else if (b.id == 99) {
                save();
                mc.displayGuiScreen(null);
                return;
            }
            save();
            initGui();
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Chat Bar", width / 2, top() - 14, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    // ===== GUI =====
    public static class FancyChat extends GuiChat {
        private long openTime;

        public FancyChat(String text) {
            super(text);
        }

        @Override
        public void initGui() {
            super.initGui();
            openTime = System.currentTimeMillis();
        }

        private float progress() {
            float t = Math.min(1f, (System.currentTimeMillis() - openTime) / 180f);
            return 1f - (float) Math.pow(1f - t, 3); // ease-out cubic
        }

        private static int withAlpha(int argb, float mul) {
            int a = (int) (((argb >> 24) & 0xFF) * mul);
            return (a << 24) | (argb & 0xFFFFFF);
        }

        private static void roundRect(int x1, int y1, int x2, int y2, int color) {
            drawRect(x1 + 2, y1, x2 - 2, y2, color);
            drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
            drawRect(x1, y1 + 2, x2, y2 - 2, color);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            float p = progress();
            float offset = (1f - p) * 16f;

            int x1 = 2, x2 = width - 2;
            int y1 = height - 15, y2 = height - 2;

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, offset, 0);

            // viền accent + nền
            roundRect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, withAlpha(accent, p * 0.9f));
            roundRect(x1, y1, x2, y2, withAlpha(background, p));

            // ô nhập
            inputField.drawTextBox();

            // bộ đếm ký tự
            if (charCounter) {
                int len = inputField.getText().length();
                String s = len + "/100";
                int col = len >= 100 ? 0xFFFF5555 : 0xFFAAAAAA;
                fontRendererObj.drawStringWithShadow(s, x2 - fontRendererObj.getStringWidth(s) - 4,
                        y1 + 3, col);
            }

            GlStateManager.popMatrix();

            // hover component (link, item...)
            IChatComponent comp = mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
            if (comp != null && comp.getChatStyle().getChatHoverEvent() != null) {
                handleComponentHover(comp, mouseX, mouseY);
            }

            // GuiScreen.drawScreen (buttons/labels) - không gọi super vì sẽ vẽ lại thanh mặc định
            for (Object b : buttonList) ((GuiButton) b).drawButton(mc, mouseX, mouseY);
            for (Object l : labelList) ((GuiLabel) l).drawLabel(mc, mouseX, mouseY);
        }
    }
}
