package dev.lunarlite.module;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.lunarlite.LunarLite;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
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
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
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
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
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
 * - Animation gõ chữ (ký tự nảy lên + đổi màu), xoá chữ (rơi xuống + mờ dần), con trỏ trượt mượt
 * - Tối ưu Bedwars: giữ tin sự kiện quan trọng, avatar + giường màu team, giường bị phá thì giường xám
 *
 * Đăng ký: new ChatBarModule().register();
 */
public class ChatBarModule extends Module {

    // ===== Tùy chỉnh: hiện trong menu Zhwii Client -> Tiện ích -> Chat Bar (lưu chung config của client) =====
    private static final String SEC_CHAT = "Chat", SEC_ICON = "Avatar", SEC_BAR = "Thanh nhập", SEC_POS = "Vị trí";

    public static final BoolSetting PLAYER_ONLY = new BoolSetting("playerOnly", "Chỉ tin người chơi", true);
    public static final BoolSetting BEDWARS = new BoolSetting("bedwars", "Giữ tin sự kiện Bedwars", true);
    public static final BoolSetting SHOW_KILLS = new BoolSetting("kills", "Tin giết người có avatar", true);
    public static final BoolSetting AUTO_TRANSLATE = new BoolSetting("translate", "Dịch sang tiếng Việt", true);
    public static final BoolSetting TRANSLATE_OUT = new BoolSetting("translateOut", "Dịch tin gửi sang tiếng Anh", true);
    public static final BoolSetting SHOW_OUT_ECHO = new BoolSetting("showOutEcho", "Hiện bản dịch đã gửi", true);
    public static final BoolSetting HIDE_ORIGINAL = new BoolSetting("hideOriginal", "Tự ẩn tin gốc", false);
    public static final BoolSetting CHAR_COUNTER = new BoolSetting("charCounter", "Bộ đếm ký tự", true);
    public static final BoolSetting PLAYER_HEADS = new BoolSetting("heads", "Avatar + giường team", true);
    public static final SliderSetting ICON_SIZE = new SliderSetting("iconSize", "Cỡ avatar", 8, 6, 14, 1, " px");
    public static final SliderSetting ICON_X = new SliderSetting("iconX", "Avatar lệch ngang", 0, -20, 20, 1, " px");
    public static final SliderSetting ICON_Y = new SliderSetting("iconY", "Avatar lệch dọc", 0, -10, 10, 1, " px");
    public static final SliderSetting BAR_WIDTH = new SliderSetting("barW", "Rộng thanh nhập (0 = tự động)", 0, 0, 600, 5, " px");
    public static final SliderSetting CHAT_WIDTH = new SliderSetting("chatWidth", "Rộng mỗi dòng chat", 320, 120, 900, 10, " px");
    public static final SliderSetting CHAT_GAP = new SliderSetting("chatGap", "Khoảng cách dòng chat", 0, 0, 4, 1, " dòng");
    public static final SliderSetting BAR_OPACITY = new SliderSetting("barOpacity", "Độ mờ thanh nhập", 55, 10, 100, 5, "%");
    public static final BoolSetting ANIM_MESSAGES = new BoolSetting("animMessages", "Animation tin nhắn mới", true);
    public static final BoolSetting ANIM_BAR = new BoolSetting("animBar", "Animation thanh nhập", true);
    public static final BoolSetting ANIM_TYPING = new BoolSetting("animTyping", "Animation gõ / xoá chữ", true);
    public static final BoolSetting BAR_BORDER = new BoolSetting("barBorder", "Viền màu thanh nhập", true);
    public static final BoolSetting HIDE_BAR_BACKGROUND = new BoolSetting("hideBarBackground", "Ẩn nền thanh nhập", false);
    public static final BoolSetting HIDE_CHAT_BACKGROUND = new BoolSetting("hideChatBackground", "Ẩn nền khung chat", false);
    public static final BoolSetting FIT_CHAT_BACKGROUND = new BoolSetting("fitChatBackground", "Nền chat vừa chiều dài chữ", false);
    public static final BoolSetting CHAT_GLASS = new BoolSetting("chatGlass", "Khung chat kiểu kính (bo góc, viền màu)", true);
    public static final BoolSetting HIDE_CHAT_SCROLLBAR = new BoolSetting("hideChatScrollbar", "Ẩn thanh cuộn chat", false);

    public static String translateTo = "vi";
    public static String outLang = "en"; // ngôn ngữ tin gửi đi
    public static int accent = 0xFF55AAFF;     // ARGB
    public static int background = 0x90000000; // ARGB

    // Vị trí tùy ý (px GUI, tính từ vị trí mặc định; x phải +, y xuống +). Đặt bằng kéo thả, không phải thanh trượt.
    public static int windowX = 0, windowY = 0; // khung hiển thị tin nhắn
    public static int barX = 0, barY = 0;       // thanh nhập chat

    public ChatBarModule() {
        super("chatbar", "Chat Bar",
                "Thanh chat bo góc, avatar + giường team, tự dịch tin nhận sang tiếng Việt và tin gửi sang tiếng Anh; kéo thả khung chat tới vị trí tùy ý.",
                Category.UTILITY, true);
        add(PLAYER_ONLY, SEC_CHAT);
        add(BEDWARS, SEC_CHAT, PLAYER_ONLY);
        add(SHOW_KILLS, SEC_CHAT);
        add(AUTO_TRANSLATE, SEC_CHAT);
        add(TRANSLATE_OUT, SEC_CHAT);
        add(HIDE_ORIGINAL, SEC_CHAT, AUTO_TRANSLATE);
        add(CHAR_COUNTER, SEC_BAR);
        add(BAR_BORDER, SEC_BAR);
        add(HIDE_BAR_BACKGROUND, SEC_BAR);
        add(ANIM_BAR, SEC_BAR);
        add(ANIM_TYPING, SEC_BAR, ANIM_BAR);
        add(ANIM_MESSAGES, SEC_CHAT);
        add(CHAT_GLASS, SEC_CHAT);
        add(HIDE_CHAT_BACKGROUND, SEC_CHAT);
        add(FIT_CHAT_BACKGROUND, SEC_CHAT);
        add(HIDE_CHAT_SCROLLBAR, SEC_CHAT);
        add(BAR_OPACITY, SEC_BAR);
        add(PLAYER_HEADS, SEC_ICON);
        add(ICON_SIZE, SEC_ICON, PLAYER_HEADS);
        add(ICON_X, SEC_ICON, PLAYER_HEADS);
        add(ICON_Y, SEC_ICON, PLAYER_HEADS);
        add(BAR_WIDTH, SEC_POS);
        add(CHAT_WIDTH, SEC_POS);
        add(CHAT_GAP, SEC_POS);
    }

    /** Nút "Kéo thả vị trí chat…" trong bảng tuỳ chỉnh của menu. */
    @Override
    public List<Action> actions() {
        List<Action> l = new ArrayList<Action>();
        l.add(new Action("Kéo thả vị trí chat…", new Runnable() {
            @Override public void run() { openPositionEditor(); }
        }, null));
        return l;
    }

    /** Mở màn hình kéo thả; Xong / Esc quay lại màn hình đang mở (menu). */
    public static void openPositionEditor() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new PositionScreen(mc.currentScreen));
    }

    // Vị trí kéo thả nằm ngoài danh sách Setting nên lưu / tải thêm ở đây
    @Override
    public void load(Configuration c) {
        super.load(c);
        windowX = c.getInt("windowX", id, 0, -4000, 4000, "Vị trí ngang khung tin nhắn");
        windowY = c.getInt("windowY", id, 0, -4000, 4000, "Vị trí dọc khung tin nhắn");
        barX = c.getInt("barX", id, 0, -4000, 4000, "Vị trí ngang thanh nhập");
        barY = c.getInt("barY", id, 0, -4000, 4000, "Vị trí dọc thanh nhập");
    }

    @Override
    public void save(Configuration c) {
        super.save(c);
        c.get(id, "windowX", 0).set(windowX);
        c.get(id, "windowY", 0).set(windowY);
        c.get(id, "barX", 0).set(barX);
        c.get(id, "barY", 0).set(barY);
    }

    /** "Đặt lại tuỳ chỉnh" trong menu cũng đưa khung chat về vị trí mặc định. */
    @Override
    public void resetSettings() {
        super.resetSettings();
        resetPositions();
    }

    public static void resetPositions() {
        windowX = windowY = barX = barY = 0;
    }

    static void saveNow() {
        LunarLite.saveConfig();
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        // Lệnh nội bộ cho nút [Ẩn gốc] trong chat. Cài đặt nằm trong menu, không còn lệnh mở GUI.
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "chatbar"; }
            @Override public String getCommandUsage(ICommandSender s) { return "/chatbar hide <id>"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }
            @Override public void processCommand(ICommandSender s, String[] a) throws CommandException {
                try {
                    if (a.length == 2 && a[0].equalsIgnoreCase("hide")) {
                        hideOriginalLine(Integer.parseInt(a[1]));
                        return;
                    }
                } catch (NumberFormatException ignored) {
                }
                s.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA
                        + "Cài đặt Chat Bar: mở menu Zhwii Client -> Tiện ích -> Chat Bar."));
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
            "^(?:(?:Guild|Party|Officer|Co-op|From|To)\\s*>?\\s*)?(?:\\[[^\\]]{1,24}\\]\\s*)*(?:<([A-Za-z0-9_]{1,16})>\\s*(\\S.*)|([A-Za-z0-9_]{1,16})\\s*[:»>]\\s*(\\S.*))$");
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
        // tên phải đang online, hoặc tin có tag [RANK]/[TEAM] ở đầu (người đã chết rời tab list), để tránh nhầm "Info: ..."
        if (mc.getNetHandler() == null) return null;
        boolean tagged = plain.trim().startsWith("[");
        if (mc.getNetHandler().getPlayerInfo(n) == null && !tagged) return null;
        return new String[]{n, body};
    }

    // ===== Tự dịch tiếng Việt =====
    private static ExecutorService daemonPool(final String name, int threads) {
        return Executors.newFixedThreadPool(threads, new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            }
        });
    }

    private static final ExecutorService TRANSLATOR = daemonPool("ChatTranslate", 2);
    // Hàng đợi riêng, 1 luồng: nhiều tin gõ liên tiếp luôn được gửi đúng thứ tự gõ
    // (dùng chung pool dịch 2 luồng thì tin nào dịch xong trước sẽ chen lên trước).
    private static final ExecutorService OUT_SENDER = daemonPool("ChatTranslateOut", 1);

    // ===== Cache dịch: LRU thật (accessOrder), lưu ra file để còn sau khi tắt game =====
    private static final int CACHE_MAX = 1024;
    private static final int SAVE_EVERY = 20; // số mục mới trước khi ghi file
    private static final Map<String, String[]> CACHE = new LinkedHashMap<String, String[]>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) { return size() > CACHE_MAX; }
    };
    private static boolean cacheLoaded;
    private static int unsaved;

    private static File cacheFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/lunarlite_translate_cache.json");
    }

    private static void loadCache() {
        synchronized (CACHE) {
            if (cacheLoaded) return;
            cacheLoaded = true;
            File f = cacheFile();
            if (f.isFile()) {
                try {
                    InputStreamReader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
                    try {
                        Map<String, String[]> m = new Gson().fromJson(r,
                                new TypeToken<LinkedHashMap<String, String[]>>() {}.getType());
                        if (m != null) {
                            for (Map.Entry<String, String[]> en : m.entrySet()) {
                                String[] v = en.getValue();
                                if (v != null && v.length >= 2 && v[0] != null && v[1] != null) CACHE.put(en.getKey(), v);
                            }
                        }
                    } finally {
                        r.close();
                    }
                } catch (Exception ignored) {
                    // file hỏng: bỏ qua, lần ghi sau sẽ tạo lại
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() { saveCache(); }
            }, "ChatTranslateCacheSave"));
        }
    }

    /** Ghi cache ra file (qua file tạm rồi đổi tên, game crash giữa chừng cũng không hỏng file). */
    static void saveCache() {
        String json;
        synchronized (CACHE) {
            if (!cacheLoaded || unsaved == 0) return;
            json = new Gson().toJson(new LinkedHashMap<String, String[]>(CACHE));
            unsaved = 0;
        }
        try {
            File f = cacheFile();
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                w.write(json);
            } finally {
                w.close();
            }
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static String[] cacheGet(String key) {
        synchronized (CACHE) { return CACHE.get(key); }
    }

    private static void cachePut(String key, String[] val) {
        boolean save;
        synchronized (CACHE) {
            CACHE.put(key, val);
            save = ++unsaved >= SAVE_EVERY;
        }
        if (save) {
            TRANSLATOR.submit(new Runnable() {
                @Override public void run() { saveCache(); }
            });
        }
    }

    // ===== HTTP + cooldown khi lỗi mạng / bị giới hạn =====
    private static final class HttpStatusException extends IOException {
        final int code;
        HttpStatusException(int code) { super("HTTP " + code); this.code = code; }
    }

    private static volatile long translateFailUntil; // Google Translate tạm nghỉ đến mốc này
    private static volatile long restoreFailUntil;   // Google Input Tools tạm nghỉ đến mốc này
    private static int translateFails;                // số lần lỗi liên tiếp (1 lần lỗi lẻ chưa nghỉ)

    private static String httpGet(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new HttpStatusException(code);
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
        } finally {
            c.disconnect();
        }
    }

    private static void noteTranslateFailure(IOException ex) {
        boolean limited = ex instanceof HttpStatusException
                && (((HttpStatusException) ex).code == 429 || ((HttpStatusException) ex).code == 403);
        synchronized (CACHE) {
            translateFails++;
            if (limited || translateFails >= 2) {
                translateFailUntil = System.currentTimeMillis() + (limited ? 60000L : 15000L);
                translateFails = 0;
            }
        }
    }

    /** Gọi Google Translate (endpoint công khai) -> {bản dịch, mã ngôn ngữ gốc}. */
    private static String[] translate(String text) throws IOException {
        return translate(text, translateTo);
    }

    /** Dịch sang ngôn ngữ đích tuỳ chọn (vd "en" cho tin gửi đi). */
    private static String[] translate(String text, String target) throws IOException {
        return translate(text, target, 4000);
    }

    private static String[] translate(String text, String target, int timeoutMs) throws IOException {
        return translate(text, "auto", target, timeoutMs);
    }

    /** src = "auto" (tự nhận diện) hoặc mã ngôn ngữ cố định như "vi". */
    private static String[] translate(String text, String src, String target, int timeoutMs) throws IOException {
        loadCache();
        final String key = src + ">" + target + "\u0000" + text;
        String[] hit = cacheGet(key);
        if (hit != null) return hit;
        if (System.currentTimeMillis() < translateFailUntil) throw new IOException("translate cooldown");

        String[] res;
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + src + "&tl="
                    + target + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
            JsonArray root = new JsonParser().parse(httpGet(url, timeoutMs)).getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (com.google.gson.JsonElement seg : root.get(0).getAsJsonArray()) {
                sb.append(seg.getAsJsonArray().get(0).getAsString());
            }
            String lang = root.size() > 2 && !root.get(2).isJsonNull() ? root.get(2).getAsString() : "";
            res = new String[]{sb.toString(), lang};
        } catch (IOException ex) {
            noteTranslateFailure(ex);
            throw ex;
        } catch (RuntimeException ex) { // JSON sai định dạng
            throw new IOException("bad translate response", ex);
        }
        synchronized (CACHE) { translateFails = 0; }
        cachePut(key, res);
        return res;
    }

    // ===== Tiếng Việt không dấu: mở rộng viết tắt + khôi phục dấu trước khi dịch sang tiếng Anh =====
    /** Có dấu thanh/dấu mũ (không tính "đ", vì "đc", "đi" vẫn cần xử lý tiếp). */
    private static final Pattern VI_TONE = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹ]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WORD_TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    /** Viết tắt hay gặp khi chat -> dạng không dấu đầy đủ (sẽ được khôi phục dấu ở bước sau). */
    private static final Map<String, String> VI_SLANG = new java.util.HashMap<String, String>();
    static {
        String[][] p = {
                {"ko", "khong"}, {"k", "khong"}, {"hok", "khong"}, {"hong", "khong"}, {"kg", "khong"}, {"khg", "khong"},
                {"dc", "duoc"}, {"đc", "duoc"}, {"dk", "duoc"}, {"dcj", "duoc"},
                {"j", "gi"}, {"gj", "gi"}, {"r", "roi"}, {"rui", "roi"},
                {"vs", "voi"}, {"vz", "vay"}, {"v", "vay"}, {"z", "vay"},
                {"bn", "bao nhieu"}, {"bnh", "bao nhieu"}, {"ntn", "nhu the nao"},
                {"ae", "anh em"}, {"mn", "moi nguoi"}, {"mk", "minh"}, {"mik", "minh"},
                {"ng", "nguoi"}, {"nc", "noi chuyen"}, {"trc", "truoc"}, {"sr", "xin loi"},
                {"cx", "cung"}, {"cg", "cung"}, {"lm", "lam"}, {"nma", "nhung ma"}, {"nhg", "nhung"},
                {"thx", "cam on"}, {"tks", "cam on"}, {"bt", "binh thuong"}, {"ik", "di"},
                {"ak", "a"}, {"ah", "a"}, {"e", "em"}, {"a", "anh"}
        };
        for (String[] x : p) VI_SLANG.put(x[0], x[1]);
    }

    /** Từ game / viết tắt quốc tế: giữ nguyên, không đưa vào bước khôi phục dấu. */
    private static final Set<String> KEEP_WORDS = new HashSet<String>(java.util.Arrays.asList(
            "gg", "ez", "wp", "gl", "hf", "bed", "bw", "rush", "pls", "plz", "afk", "lol", "ping", "lag",
            "noob", "pro", "team", "final", "kill", "void", "diamond", "emerald", "iron", "gold", "ok", "oke",
            "xp", "op", "hp", "pvp", "gm", "brb", "wtf", "omg", "fps", "dm", "tp", "bridge", "invis"));

    /** Từ tiếng Anh phổ biến (đã loại các từ trùng tiếng Việt không dấu như me, do, to, no, can, day, may...). */
    private static final Set<String> EN_COMMON = new HashSet<String>(java.util.Arrays.asList(
            "the", "is", "are", "was", "were", "you", "your", "we", "they", "it", "this", "that", "what", "why",
            "how", "who", "where", "when", "have", "has", "with", "for", "and", "but", "not", "will", "would",
            "just", "please", "thanks", "thank", "hello", "hi", "hey", "yes", "go", "get", "come", "need", "want",
            "know", "good", "bad", "nice", "base", "attack", "defend", "dont", "im", "i", "my", "our", "he", "she",
            "of", "on", "at", "from", "there", "here", "now", "up", "down", "help", "stop", "wait", "come", "lets"));

    private static String[] splitWords(String s) {
        return s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+");
    }

    /** Tin không dấu nhưng thật ra là tiếng Anh -> bỏ qua khôi phục dấu, để Google tự nhận diện. */
    private static boolean looksEnglish(String text) {
        if (VI_TONE.matcher(text).find()) return false;
        String[] t = splitWords(text);
        if (t.length == 0) return false;
        int en = 0, keep = 0;
        for (String w : t) {
            String l = w.toLowerCase().replaceAll("[^\\p{L}']", "");
            if (EN_COMMON.contains(l)) en++;
            else if (KEEP_WORDS.contains(l)) keep++;
        }
        if (en + keep == t.length) return true;       // toàn từ Anh / từ game
        return t.length >= 2 && en * 10 >= t.length * 6; // >= 60% là từ Anh phổ biến
    }

    private static boolean needsRestore(String s) {
        return !VI_TONE.matcher(s).find() && s.matches(".*\\p{L}.*");
    }

    private static String expandSlang(String s) {
        Matcher m = WORD_TOKEN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String w = m.group().toLowerCase();
            String rep = KEEP_WORDS.contains(w) ? m.group() : VI_SLANG.containsKey(w) ? VI_SLANG.get(w) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString().replace('đ', 'd').replace('Đ', 'D'); // để Input Tools tự đoán lại đ/d theo ngữ cảnh
    }

    /** Khôi phục dấu qua Google Input Tools. Lỗi / kết quả lạ thì trả về nguyên chuỗi vào. */
    private static String restoreDiacritics(String text) {
        loadCache();
        final String key = "restore\u0000" + text;
        String[] hit = cacheGet(key);
        if (hit != null) return hit[0];
        if (System.currentTimeMillis() < restoreFailUntil) return text;
        try {
            String url = "https://inputtools.google.com/request?itc=vi-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8"
                    + "&app=demopage&text=" + URLEncoder.encode(text, "UTF-8");
            JsonArray root = new JsonParser().parse(httpGet(url, 2000)).getAsJsonArray();
            if (!"SUCCESS".equals(root.get(0).getAsString())) return text;
            String best = root.get(1).getAsJsonArray().get(0).getAsJsonArray()
                    .get(1).getAsJsonArray().get(0).getAsString().trim();
            if (best.isEmpty() || best.length() > text.length() * 2 + 4) return text;

            // Giữ nguyên từ game (rush, bed, gg...) nếu số từ trước/sau khớp nhau
            String[] in = splitWords(text), out = splitWords(best);
            if (in.length == out.length) {
                for (int i = 0; i < in.length; i++) {
                    if (KEEP_WORDS.contains(in[i].toLowerCase())) out[i] = in[i];
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < out.length; i++) sb.append(i > 0 ? " " : "").append(out[i]);
                best = sb.toString();
            }
            cachePut(key, new String[]{best, ""});
            return best;
        } catch (Exception ex) {
            restoreFailUntil = System.currentTimeMillis() + 30000L; // lỗi: 30s sau mới thử lại
            return text;
        }
    }

    /** Chuẩn hoá tin tiếng Việt trước khi dịch: không dấu -> mở rộng viết tắt -> khôi phục dấu. */
    private static String normalizeVi(String text) {
        if (!needsRestore(text)) return text;
        return restoreDiacritics(expandSlang(text));
    }

    /** Cắt bản dịch thành tối đa 3 tin <= 100 ký tự, ưu tiên cắt ở dấu cách để không đứt giữa từ. */
    private static List<String> splitChat(String s) {
        List<String> parts = new ArrayList<String>();
        s = s.trim();
        while (s.length() > 100 && parts.size() < 2) {
            int cut = s.lastIndexOf(' ', 100);
            if (cut < 40) cut = 100;
            parts.add(s.substring(0, cut).trim());
            s = s.substring(cut).trim();
        }
        if (s.length() > 100) s = s.substring(0, 100);
        if (!s.isEmpty()) parts.add(s);
        return parts;
    }

    /** Dịch tin gõ -> tiếng đích (thử tối đa 2 lần) -> bản dịch, hoặc null nếu lỗi. */
    private static String translateOut(String text) {
        // tin tiếng Anh gõ nhầm: để Google tự nhận diện; còn lại ép nguồn là tiếng Việt
        // (tin ngắn / không dấu không bị nhận nhầm), sau khi đã khôi phục dấu 1 lần duy nhất
        final boolean english = looksEnglish(text);
        final String src = english ? text : normalizeVi(text);
        final String from = english ? "auto" : "vi";
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String[] r = translate(src, from, outLang, 3000);
                String t = r[0].trim();
                if (!t.isEmpty()) return t;
            } catch (Exception ignored) {
                // mạng chập chờn hoặc đang cooldown: thử lại 1 lần rồi bỏ cuộc
            }
        }
        return null;
    }

    /** Dịch tin bạn gõ sang tiếng Anh rồi gửi. Lỗi/timeout thì gửi nguyên văn, không nuốt tin. */
    static void sendTranslated(final String text) {
        OUT_SENDER.submit(new Runnable() {
            @Override public void run() {
                final String translated = translateOut(text);
                final boolean ok = translated != null;
                final List<String> parts = splitChat(ok ? translated : text);
                final Minecraft m = Minecraft.getMinecraft();
                for (int i = 0; i < parts.size(); i++) {
                    final String send = parts.get(i);
                    final boolean last = i == parts.size() - 1;
                    m.addScheduledTask(new Runnable() {
                        @Override public void run() {
                            if (m.thePlayer == null) return;
                            m.thePlayer.sendChatMessage(send);
                        }
                    });
                    if (!last) {
                        try { Thread.sleep(700); } catch (InterruptedException e) { return; } // tránh bị server coi là spam
                    }
                }
            }
        });
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
                            String head = PLAYER_HEADS.value ? makeMarker(name, plain, false) : "";
                            IChatComponent line = new ChatComponentText(head + EnumChatFormatting.AQUA + "[VI] "
                                    + teamColorOf(name) + name + EnumChatFormatting.GRAY + ": " + r[0]);
                            if (!HIDE_ORIGINAL.value) {
                                ChatComponentText btn = new ChatComponentText(" " + EnumChatFormatting.RED + "[Ẩn gốc]");
                                btn.setChatStyle(new ChatStyle()
                                        .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatbar hide " + id))
                                        .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                new ChatComponentText("Ẩn tin gốc, chỉ giữ bản dịch"))));
                                line.appendSibling(btn);
                            }
                            m.thePlayer.addChatMessage(line);
                            if (HIDE_ORIGINAL.value) hideOriginalLine(id);
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
    private static final Pattern TEAM_TAG = Pattern.compile("\\[(RED|BLUE|GREEN|YELLOW|AQUA|WHITE|PINK|GRAY|GREY)\\]", Pattern.CASE_INSENSITIVE);
    /** Tin hệ thống được giữ lại khi "Chỉ tin người chơi" bật. Thêm/bớt từ khóa tại đây. */
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
        if (n.equals("GRAY") || n.equals("GREY")) return MC_COLORS[8];
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
        WIDTHS.clear();
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

    /** Áp màu đội lên toàn bộ component chat người chơi, giữ nguyên cấu trúc component/click events. */
    private static void applyTeamChatColor(IChatComponent message, String name) {
        if (message == null || name == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
        int rgb = info == null ? -1 : teamColor(info);
        if (rgb == -1) return;
        int idx = -1;
        for (int i = 0; i < MC_COLORS.length; i++) if (MC_COLORS[i] == rgb) { idx = i; break; }
        EnumChatFormatting[] colors = { EnumChatFormatting.BLACK, EnumChatFormatting.DARK_BLUE,
                EnumChatFormatting.DARK_GREEN, EnumChatFormatting.DARK_AQUA, EnumChatFormatting.DARK_RED,
                EnumChatFormatting.DARK_PURPLE, EnumChatFormatting.GOLD, EnumChatFormatting.GRAY,
                EnumChatFormatting.DARK_GRAY, EnumChatFormatting.BLUE, EnumChatFormatting.GREEN,
                EnumChatFormatting.AQUA, EnumChatFormatting.RED, EnumChatFormatting.LIGHT_PURPLE,
                EnumChatFormatting.YELLOW, EnumChatFormatting.WHITE };
        if (idx >= 0) message.setChatStyle(message.getChatStyle().setColor(colors[idx]));
    }

    /** [avt người giết] Tên giết [avt người bị giết] Tên */
    private static IChatComponent buildKill(String victim, String killer, boolean fin, String plain) {
        String mk = PLAYER_HEADS.value ? makeMarker(killer, plain, false) : "";
        String mv = PLAYER_HEADS.value ? makeMarker(victim, plain, false) : "";
        StringBuilder sb = new StringBuilder();
        sb.append(mk).append(teamColorOf(killer)).append(killer);
        sb.append(S).append("c giết  ");
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
        int w = ICON_SIZE.getInt() + 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (w + 3) / 4; i++) sb.append(' ');
        return sb.toString();
    }

    private static class Icon {
        final String name; final int color; final boolean bed; final ResourceLocation skin;
        Icon(String name, int color, boolean bed, ResourceLocation skin) {
            this.name = name; this.color = color; this.bed = bed; this.skin = skin;
        }
    }

    private static final Map<Integer, Icon> ICONS = new LinkedHashMap<Integer, Icon>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Icon> eldest) { return size() > 4096; }
    };
    private static int nextId = 0;

    /** Màu RGB (bảng 16 màu MC) của mã § cuối cùng trong chuỗi, hoặc -1. */
    private static int lastColor(String p) {
        if (p == null) return -1;
        for (int i = p.length() - 2; i >= 0; i--) {
            if (p.charAt(i) == '\u00A7') {
                int idx = "0123456789abcdef".indexOf(Character.toLowerCase(p.charAt(i + 1)));
                if (idx >= 0) return MC_COLORS[idx];
            }
        }
        return -1;
    }

    private static int teamColor(NetworkPlayerInfo info) {
        ScorePlayerTeam t = info.getPlayerTeam();
        int c = -1;
        if (t != null) {
            c = lastColor(t.getColorPrefix());                       // prefix: "§cR "
            if (c == -1) c = lastColor(ScorePlayerTeam.formatPlayerName(t, info.getGameProfile().getName())); // prefix + tên + suffix
            if (c == -1) c = lastColor(t.getColorSuffix());
        }
        if (c == -1 && info.getDisplayName() != null) c = lastColor(info.getDisplayName().getFormattedText());
        return c;
    }

    /** Màu đội theo màu giáp da đã nhuộm của người chơi đang ở gần (Bedwars nhuộm giáp theo đội), hoặc -1. */
    private static int armorColor(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return -1;
        EntityPlayer p = mc.theWorld.getPlayerEntityByName(name);
        if (p == null) return -1;
        for (int slot = 3; slot >= 0; slot--) {
            net.minecraft.item.ItemStack st = p.inventory.armorInventory[slot];
            if (st == null || !(st.getItem() instanceof net.minecraft.item.ItemArmor)) continue;
            net.minecraft.item.ItemArmor ia = (net.minecraft.item.ItemArmor) st.getItem();
            if (ia.getArmorMaterial() == net.minecraft.item.ItemArmor.ArmorMaterial.LEATHER && ia.hasColor(st)) {
                int rgb = ia.getColor(st) & 0xFFFFFF;
                int best = -1, bd = Integer.MAX_VALUE;
                for (int mcCol : MC_COLORS) {
                    int dr = ((rgb >> 16) & 255) - ((mcCol >> 16) & 255), dg = ((rgb >> 8) & 255) - ((mcCol >> 8) & 255),
                            db = (rgb & 255) - (mcCol & 255);
                    int d = dr * dr + dg * dg + db * db;
                    if (d < bd) { bd = d; best = mcCol; }
                }
                return best;
            }
        }
        return -1;
    }

    /** Tạo marker ẩn (chỉ gồm mã màu, rộng 0px) + khoảng trống cho icon. */
    private static String makeMarker(String name, String plain, boolean bed) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return "";
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
        // người không còn trong tab list (đã chết / thoát): vẫn chừa chỗ và vẽ skin mặc định
        ResourceLocation skin = info != null ? info.getLocationSkin() : DefaultPlayerSkin.getDefaultSkinLegacy();
        int id = nextId++ & 0xFFF;
        int color = info != null ? teamColor(info) : -1;
        if (color == -1) { // chưa có team scoreboard: lấy từ tag [RED]/[BLUE]... trong tin
            Matcher tm = TEAM_TAG.matcher(plain);
            if (tm.find()) color = colorByName(tm.group(1).toUpperCase());
        }
        if (color == -1) color = armorColor(name); // dự phòng: màu giáp da
        ICONS.put(id, new Icon(name, color, bed, skin));
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
                    drawIcon(mc, ic, y, a, i == 0 ? 0 : measure(mc, fmt.substring(0, i)));
                    from = p + 6;
                }
            } catch (NumberFormatException ex) {
                // khớp sai, thử vị trí kế tiếp
            }
        }
    }

    private static final Map<String, Integer> WIDTHS = new LinkedHashMap<String, Integer>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) { return size() > 1024; }
    };

    /**
     * Độ rộng thật của đoạn chữ: vẽ nó ra ngoài màn hình và lấy toạ độ x kết thúc mà FontRenderer trả về.
     * Chính xác hơn getStringWidth (lệch với chữ có dấu / font resource pack, làm avatar dính vào chữ "giết").
     */
    private static int measure(Minecraft mc, String s) {
        Integer c = WIDTHS.get(s);
        if (c != null) return c;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-30000f, -30000f, 0f);
        int w = mc.fontRendererObj.drawString(s, 0, 0, 0x01FFFFFF, false);
        GlStateManager.popMatrix();
        WIDTHS.put(s, w);
        return w;
    }

    private static void drawIcon(Minecraft mc, Icon ic, int y, int a, int x0) {
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(ic.name);
        int sz = ICON_SIZE.getInt();
        int top = y - 8 + (8 - sz) / 2 + ICON_Y.getInt();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, a / 255f);
        mc.getTextureManager().bindTexture(info != null ? info.getLocationSkin() : ic.skin);
        Gui.drawScaledCustomSizeModalRect(x0 + ICON_X.getInt(), top, 8f, 8f, 8, 8, sz, sz, 64f, 64f);   // mặt
        Gui.drawScaledCustomSizeModalRect(x0 + ICON_X.getInt(), top, 40f, 8f, 8, 8, sz, sz, 64f, 64f);  // lớp mũ
        
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // ===== Animation tin nhắn mới =====
    private static java.lang.reflect.Field F_LINES, F_SCROLL;
    private static Object lastNewest;
    private static float slideY;      // độ lệch dọc còn lại (px GUI), về 0 dần
    private static int curSlide;      // giá trị đã làm tròn của khung hình này (icon dùng chung)
    private static long lastFrame;
    private static final class GapLine extends ChatLine {
        GapLine(int counter) { super(counter, new ChatComponentText(""), -1); }
    }
    private static boolean isGap(ChatLine l) { return l instanceof GapLine; }
    private static boolean spacingApplied;

    // ===== Tối ưu: cache chữ + độ rộng từng dòng (trước đây tính lại mỗi khung hình) =====
    private static final class LineCache {
        final String text;
        final int width;
        LineCache(String text, int width) { this.text = text; this.width = width; }
    }
    private static final Map<ChatLine, LineCache> LINE_CACHE = new java.util.WeakHashMap<ChatLine, LineCache>();

    private static LineCache cached(Minecraft mc, ChatLine line) {
        LineCache c = LINE_CACHE.get(line);
        if (c == null) {
            String t = line.getChatComponent().getFormattedText();
            c = new LineCache(t, measure(mc, t)); // đo bằng đúng FontRenderer vẽ chữ, getStringWidth bị lệch với font tuỳ chỉnh
            LINE_CACHE.put(line, c);
        }
        return c;
    }

    // Chữ ký danh sách chat lần dựng dòng đệm gần nhất: không đổi thì khỏi dựng lại
    private static List<ChatLine> gapList;
    private static int gapSize = -1, gapCount = -1;
    private static Object gapFirst;

    /** Có dòng mới ở cuối thì đẩy cả khung xuống đúng số dòng đó, rồi cho trượt lên về vị trí cũ. */
    private static void updateSlide() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        slideY *= (float) Math.exp(-dt * 16f * dev.lunarlite.module.AnimationsModule.speed());
        if (Math.abs(slideY) < 0.4f) slideY = 0f;

        try {
            GuiNewChat chat = mc.ingameGUI.getChatGUI();
            if (F_LINES == null) {
                F_LINES = ReflectionHelper.findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
                F_SCROLL = ReflectionHelper.findField(GuiNewChat.class, "scrollPos", "field_146250_j");
            }
            List<?> lines = (List<?>) F_LINES.get(chat);
            if (lines.isEmpty()) { lastNewest = null; curSlide = Math.round(slideY); return; }
            Object newest = lines.get(0);
            if (newest != lastNewest) {
                if (lastNewest != null && F_SCROLL.getInt(chat) == 0) {
                    int idx = -1;
                    for (int i = 0; i < lines.size() && i < 8; i++) {
                        if (lines.get(i) == lastNewest) { idx = i; break; }
                    }
                    if (idx > 0) slideY = Math.min(40f, slideY + 9f * idx * chat.getChatScale());
                }
                lastNewest = newest;
            }
        } catch (Exception ignored) {
        }
        curSlide = Math.round(slideY);
    }

    // Dời khung hiển thị tin nhắn tới vị trí người dùng chọn
    @SubscribeEvent
    public void onChatPosition(RenderGameOverlayEvent.Chat e) {
        if (!enabled) return;
        applyChatWidth();
        if (ANIM_MESSAGES.value && AnimationsModule.hud()) updateSlide();
        else { slideY = 0f; curSlide = 0; }
        e.posX += windowX;
        e.posY += windowY + curSlide;
        if (CHAT_GLASS.value || HIDE_CHAT_BACKGROUND.value || FIT_CHAT_BACKGROUND.value || HIDE_CHAT_SCROLLBAR.value) {
            e.setCanceled(true);          // chặn cách vẽ gốc (có nền đen)
            drawChatNoBackground(e);      // tự vẽ lại chữ + icon, không có nền
        }
    }

    /** Vẽ chat giống vanilla nhưng KHÔNG có nền đen mờ sau mỗi dòng. */
    private void drawChatNoBackground(RenderGameOverlayEvent.Chat e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) return;
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        List<ChatLine> lines;
        int scroll;
        try {
            if (F_LINES == null) {
                F_LINES = ReflectionHelper.findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
                F_SCROLL = ReflectionHelper.findField(GuiNewChat.class, "scrollPos", "field_146250_j");
            }
            lines = (List<ChatLine>) F_LINES.get(chat);
            scroll = F_SCROLL.getInt(chat);
        } catch (Exception ex) {
            return;
        }
        boolean open = chat.getChatOpen();
        boolean glass = CHAT_GLASS.value && !HIDE_CHAT_BACKGROUND.value && !FIT_CHAT_BACKGROUND.value;
        if (lines == null) return;
        if (lines.isEmpty() && !(glass && open)) return;

        float opacity = mc.gameSettings.chatOpacity * 0.9f + 0.1f;
        int tick = mc.ingameGUI.getUpdateCounter();
        float sc = chat.getChatScale();
        int max = chat.getLineCount();
        int visible = 0;
        final int fullW = net.minecraft.util.MathHelper.ceiling_float_int((float) chat.getChatWidth() / sc) + 4;

        GlStateManager.pushMatrix();
        GlStateManager.translate(e.posX + 2f, e.posY + 20f, 0f);
        GlStateManager.scale(sc, sc, 1f);
        // khung kính: hiện/ẩn mượt theo trạng thái mở chat
        long pn = System.nanoTime();
        float pdt = panelLast == 0L ? 0.016f : Math.min(0.1f, (pn - panelLast) / 1e9f);
        panelLast = pn;
        float pTarget = open ? 1f : 0f;
        panelA = (AnimationsModule.hud() && ANIM_MESSAGES.value) ? panelA + (pTarget - panelA) * (1f - (float) Math.exp(-pdt * 14f)) : pTarget;
        if (Math.abs(pTarget - panelA) < 0.01f) panelA = pTarget;
        if (glass && panelA > 0.02f) glassPanel(-4, -max * 9 - 4, fullW + 4, 3, 0.58f * opacity * panelA, panelA);
        try {
            for (int i = 0; i + scroll < lines.size() && i < max; i++) {
                ChatLine line;
                try { line = lines.get(i + scroll); } catch (IndexOutOfBoundsException ex) { break; }
                if (line == null || line.getChatComponent() == null) continue;
                int age = tick - line.getUpdatedCounter();
                if (age >= 200 && !open) continue;
                double d = Math.max(0.0, Math.min(1.0, (1.0 - age / 200.0) * 10.0));
                int a = (int) ((open ? 255 : (int) (255 * d * d)) * opacity);
                visible++;
                if (a <= 3) continue;
                LineCache lc = cached(mc, line);
                String text = lc.text;
                if (glass) {
                    // đóng chat: mỗi dòng là một viên bo góc ôm sát chữ; mở chat: nền do khung kính đảm nhiệm
                    if (!open && !isGap(line) && !EnumChatFormatting.getTextWithoutFormattingCodes(text).trim().isEmpty()) {
                        roundRect(-3, -i * 9 - 9, lc.width + 6, -i * 9 + 1, (((int) (a * 0.62f)) << 24) | 0x000000);
                    }
                } else if (!HIDE_CHAT_BACKGROUND.value && FIT_CHAT_BACKGROUND.value && !isGap(line)
                        && !EnumChatFormatting.getTextWithoutFormattingCodes(text).trim().isEmpty()) {
                    // nền ôm sát chữ của từng dòng (bỏ qua dòng đệm rỗng)
                    roundRect(-3, -i * 9 - 9, lc.width + 6, -i * 9 + 1, (a / 2) << 24); // đệm 3px hai bên, avatar nằm gọn trong nền
                } else if (!HIDE_CHAT_BACKGROUND.value && !FIT_CHAT_BACKGROUND.value && !isGap(line)) {
                    // chỉ ẩn thanh cuộn: giữ nền vanilla dài hết khung
                    Gui.drawRect(0, -i * 9 - 9, fullW, -i * 9, (a / 2) << 24);
                }
                GlStateManager.enableBlend();
                mc.fontRendererObj.drawStringWithShadow(text, 0f, -i * 9 - 8, 0xFFFFFF + (a << 24));
                GlStateManager.disableAlpha();
                GlStateManager.disableBlend();
            }

            // thanh cuộn (chỉ khi đang mở chat)
            if (open && !HIDE_CHAT_SCROLLBAR.value) {
                int fh = mc.fontRendererObj.FONT_HEIGHT;
                int total = lines.size();
                int l2 = total * fh + total;
                int i3 = visible * fh + visible;
                if (l2 != i3) {
                    int j3 = scroll * i3 / total;
                    int k1 = i3 * i3 / l2;
                    int k3 = j3 > 0 ? 170 : 96;
                    if (glass) {
                        // thanh cuộn mảnh: rãnh mờ + con trượt màu accent khi đã cuộn
                        int sx = fullW - 1;
                        Gui.drawRect(sx, -i3, sx + 2, 0, 0x14FFFFFF);
                        Gui.drawRect(sx, -j3 - k1, sx + 2, -j3, scroll > 0 ? (accent & 0xFFFFFF) | 0xDD000000 : 0x66FFFFFF);
                    } else {
                    int col = scroll > 0 ? 13382451 : 3355562;
                    GlStateManager.translate(-3f, 0f, 0f);
                    Gui.drawRect(0, -j3, 2, -j3 - k1, col + (k3 << 24));
                    Gui.drawRect(2, -j3, 1, -j3 - k1, 13421772 + (k3 << 24));
                    }
                }
            }
        } catch (RuntimeException ex) {
            // lỗi vẽ không được làm sập game
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }

        if (PLAYER_HEADS.value) renderChatIcons(e.resolution);
    }

    /** Đặt độ rộng khung chat theo thanh trượt (px). Game cho phép > 320 px nếu gán thẳng giá trị float. */
    static void applyChatWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        float want = (CHAT_WIDTH.getInt() - 40f) / 280f;
        if (Math.abs(mc.gameSettings.chatWidth - want) > 0.0005f) {
            mc.gameSettings.chatWidth = want;
            mc.ingameGUI.getChatGUI().refreshChat(); // ngắt dòng lại theo độ rộng mới
        }
    }

    // ===== Hình học khung chat / thanh nhập (dùng chung cho chat thật và màn hình kéo thả) =====

    /** Khung tin nhắn: {x1, y1, x2, y2}, đã kẹp trong màn hình. */
    static int[] windowRect(int sw, int sh) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        float sc = chat.getChatScale();
        int w = (int) Math.ceil((GuiNewChat.calculateChatboxWidth(mc.gameSettings.chatWidth) + 4) * sc);
        int h = (int) Math.ceil(GuiNewChat.calculateChatboxHeight(mc.gameSettings.chatHeightFocused) * sc);
        int x1 = Math.max(0, Math.min(sw - w, 2 + windowX));
        int y2 = Math.max(Math.min(h, sh), Math.min(sh, sh - 28 + windowY));
        return new int[]{x1, y2 - h, x1 + w, y2};
    }

    static final String TOG_LABEL = "VI>EN";

    /** Bề rộng nút VI>EN (nằm ngoài thanh nhập, bên phải). */
    static int togBtnW() {
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(TOG_LABEL) + 10;
    }

    /** Chỗ chừa bên phải thanh nhập: nút + khoảng cách 4px. */
    static int togReserve() { return togBtnW() + 4; }

    static final int BAR_H = 16, BAR_BASE = 18; // cao thanh nhập / mốc mặc định cách đáy màn hình

    /** Thanh nhập: {x1, y1, x2, y2}, đã kẹp trong màn hình. */
    static int[] barRect(int sw, int sh) {
        int rs = togReserve(); // chừa chỗ bên phải thanh cho nút VI>EN
        int bw = BAR_WIDTH.getInt() > 0 ? BAR_WIDTH.getInt() : sw - 4 - rs - Math.max(0, barX);
        bw = Math.max(140, Math.min(sw - 4 - rs, bw));
        int x1 = Math.max(0, Math.min(sw - bw - 2 - rs, 2 + barX));
        int y1 = Math.max(0, Math.min(sh - BAR_H, sh - BAR_BASE + barY));
        return new int[]{x1, y1, x1 + bw, y1 + BAR_H};
    }

    /** Tọa độ chuột thô đã bù vị trí khung chat, để click/hover link đúng chỗ. */
    static int rawMouseX(Minecraft mc) {
        return Mouse.getX() - windowX * new ScaledResolution(mc).getScaleFactor();
    }

    static int rawMouseY(Minecraft mc) {
        return Mouse.getY() + windowY * new ScaledResolution(mc).getScaleFactor();
    }

    // Vẽ icon lên từng dòng chat có marker
    @SubscribeEvent
    public void onRenderChat(RenderGameOverlayEvent.Post e) {
        if (!enabled || !PLAYER_HEADS.value || e.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        renderChatIcons(e.resolution);
    }

    private void renderChatIcons(ScaledResolution res) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN || mc.getNetHandler() == null) return;

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        List<ChatLine> lines;
        int scroll;
        try {
            if (F_LINES == null) {
                F_LINES = ReflectionHelper.findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
                F_SCROLL = ReflectionHelper.findField(GuiNewChat.class, "scrollPos", "field_146250_j");
            }
            lines = (List<ChatLine>) F_LINES.get(chat);
            scroll = F_SCROLL.getInt(chat);
        } catch (Exception ex) {
            return;
        }
        if (lines == null || lines.isEmpty()) return;

        // Chèn dòng đệm (nội dung rỗng, nhận diện bằng class GapLine) để tạo khoảng cách giữa các tin nhắn.
        int gap = CHAT_GAP.getInt();
        boolean unchanged = lines == gapList && gap == gapCount
                && lines.size() == gapSize && lines.get(0) == gapFirst;
        if (!unchanged) try {
            List<ChatLine> mutable = lines;
            // xoá hết dòng đệm cũ rồi chèn lại theo số dòng hiện tại
            for (Iterator<ChatLine> it = mutable.iterator(); it.hasNext();) {
                if (isGap(it.next())) it.remove();
            }
            if (gap > 0) {
                // dựng danh sách mới một lượt (nhanh hơn chèn giữa ArrayList từng dòng)
                List<ChatLine> rebuilt = new ArrayList<ChatLine>(mutable.size() * (gap + 1));
                for (ChatLine line : mutable) {
                    rebuilt.add(line);
                    if (line == null) continue;
                    for (int g = 0; g < gap; g++) rebuilt.add(new GapLine(line.getUpdatedCounter()));
                }
                mutable.clear();
                mutable.addAll(rebuilt);
            }
            gapList = mutable;
            gapCount = gap;
            gapSize = mutable.size();
            gapFirst = mutable.isEmpty() ? null : mutable.get(0);
        } catch (RuntimeException ignored) {
            gapSize = -1; // lỗi: lần sau thử dựng lại
            // Không để tùy chỉnh khoảng cách làm gián đoạn render chat.
        }

        boolean open = chat.getChatOpen();
        float opacity = mc.gameSettings.chatOpacity * 0.9f + 0.1f;
        int tick = mc.ingameGUI.getUpdateCounter();
        float sc = chat.getChatScale();

        GlStateManager.pushMatrix();
        GlStateManager.translate(2f + windowX, res.getScaledHeight() - 48 + 20f + windowY + curSlide, 0f);
        GlStateManager.scale(sc, sc, 1f);
        GlStateManager.enableBlend();
        try {
        final int maxLines = chat.getLineCount();
        for (int i = 0; i + scroll < lines.size() && i < maxLines; i++) {
            ChatLine line;
            try { line = lines.get(i + scroll); } catch (IndexOutOfBoundsException ex) { break; } // chat đổi giữa chừng
            if (line == null || line.getChatComponent() == null) continue;
            int age = tick - line.getUpdatedCounter();
            if (age >= 200 && !open) continue;
            double d = Math.max(0.0, Math.min(1.0, (1.0 - age / 200.0) * 10.0));
            int a = (int) ((open ? 255 : (int) (255 * d * d)) * opacity);
            if (a <= 3) continue;
            drawIcons(mc, cached(mc, line).text, -i * 9, a);
        }
        } catch (RuntimeException ex) {
            // lỗi vẽ icon không được làm sập game / hỏng chat
        } finally {
            GlStateManager.enableTexture2D(); // Gui.drawRect tắt texture, trả lại cho phần HUD vẽ sau
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    // Lọc tin hệ thống (giữ sự kiện Bedwars) + marker icon
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ClientChatReceivedEvent e) {
        if (!enabled || e.type == 2) return; // 2 = action bar, giữ nguyên
        try {
            handleChat(e);
        } catch (RuntimeException ex) {
            // lỗi xử lý không được làm mất tin nhắn: giữ nguyên tin gốc
        }
    }

    private void handleChat(ClientChatReceivedEvent e) {

        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(e.message.getUnformattedText());
        if (plain == null) plain = "";
        if (BEDWARS.value) trackBeds(plain);

        String[] pc = parsePlayerChat(e);
        String name = pc == null ? null : pc[0];

        // tin giết người: viết lại thành [avt] Người giết giết [avt] Người bị giết
        if (SHOW_KILLS.value && name == null) {
            String[] k = parseKill(plain);
            if (k != null) {
                if (k[1] != null) {
                    e.message = buildKill(k[0], k[1], plain.contains("FINAL KILL"), plain);
                } else if (PLAYER_HEADS.value) { // tự chết (rơi hố, cháy...): giữ nguyên tin, thêm avt người chết
                    IChatComponent r = new ChatComponentText(makeMarker(k[0], plain, false));
                    r.appendSibling(e.message);
                    e.message = r;
                }
                return;
            }
        }
        if (PLAYER_ONLY.value && name == null && !(BEDWARS.value && BW_EVENT.matcher(plain).find())) {
            e.setCanceled(true);
            return;
        }

        // Tô màu nội dung chat theo team của người gửi; bỏ biểu tượng giường, chỉ giữ avatar.
        if (name != null) applyTeamChatColor(e.message, name);
        String marker = (PLAYER_HEADS.value && name != null) ? makeMarker(name, plain, false) : "";
        if (!marker.isEmpty()) {
            IChatComponent root = new ChatComponentText(marker);
            root.appendSibling(e.message);
            e.message = root;
        }

        // e.message lúc này chính là đối tượng sẽ được in -> dùng để tìm lại và ẩn khi bấm [Ẩn gốc]
        if (AUTO_TRANSLATE.value && pc != null) requestTranslate(pc[0], pc[1], plain, e.message);
    }

    /** Thanh trượt gắn với một SliderSetting (dùng trong màn hình kéo thả). */
    static class SettingSlider extends GuiButton {
        private final SliderSetting s;
        private boolean dragging;

        SettingSlider(int id, int x, int y, int w, SliderSetting s) {
            super(id, x, y, w, 20, "");
            this.s = s;
            refresh();
        }

        private void refresh() { displayString = s.label + ": " + s.display(); }

        private void fromMouse(int mx) {
            s.setRatio((mx - (xPosition + 4)) / (double) (width - 8));
            refresh();
        }

        @Override
        protected int getHoverState(boolean mouseOver) { return 0; }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            if (dragging) fromMouse(mouseX);
            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1f, 1f, 1f, 1f);
            int hx = xPosition + (int) (s.ratio() * (width - 8));
            drawTexturedModalRect(hx, yPosition, 0, 66, 4, 20);
            drawTexturedModalRect(hx + 4, yPosition, 196, 66, 4, 20);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (!super.mousePressed(mc, mouseX, mouseY)) return false;
            dragging = true;
            fromMouse(mouseX);
            return true;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) { dragging = false; }
    }

    /** Màn hình kéo thả: kéo khung tin nhắn và thanh nhập tới bất kỳ đâu. */
    public static class PositionScreen extends GuiScreen {
        private final GuiScreen parent;
        private int drag; // 0 = không kéo, 1 = khung tin nhắn, 2 = thanh nhập
        private int startMx, startMy, startX, startY;

        public PositionScreen(GuiScreen parent) { this.parent = parent; }

        @Override
        public void initGui() {
            buttonList.clear();
            buttonList.add(new GuiButton(1, width / 2 - 102, height / 2 + 20, 100, 20, "Mặc định"));
            buttonList.add(new GuiButton(0, width / 2 + 2, height / 2 + 20, 100, 20, "Xong"));
            // Kích thước khung chat: thanh kéo gốc của game (lưu vào options.txt khi thoát)
            int lx = width / 2 - 156, rx = width / 2 + 6, y = height / 2 + 46;
            buttonList.add(new GuiOptionSlider(20, lx, y, GameSettings.Options.CHAT_SCALE));
            buttonList.add(new SettingSlider(21, rx, y, 150, CHAT_WIDTH));
            buttonList.add(new GuiOptionSlider(22, lx, y + 22, GameSettings.Options.CHAT_HEIGHT_FOCUSED));
            buttonList.add(new GuiOptionSlider(23, rx, y + 22, GameSettings.Options.CHAT_OPACITY));
        }

        @Override
        public void onGuiClosed() {
            mc.gameSettings.saveOptions();
            saveNow();
        }

        @Override
        protected void actionPerformed(GuiButton b) throws IOException {
            if (b.id == 1) {
                resetPositions();
                saveNow();
            } else if (b.id == 0) {
                saveNow();
                mc.displayGuiScreen(parent);
            }
        }

        @Override
        protected void keyTyped(char c, int key) throws IOException {
            if (key == Keyboard.KEY_ESCAPE) {
                saveNow();
                mc.displayGuiScreen(parent);
            }
        }

        private static boolean in(int[] r, int x, int y) {
            return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
        }

        @Override
        protected void mouseClicked(int mx, int my, int btn) throws IOException {
            if (btn == 0) {
                // nút / thanh kéo nằm trên cùng: ưu tiên chúng trước khi kéo khung chat bên dưới
                for (Object o : buttonList) {
                    GuiButton b = (GuiButton) o;
                    if (mx >= b.xPosition && my >= b.yPosition && mx < b.xPosition + b.width && my < b.yPosition + b.height) {
                        super.mouseClicked(mx, my, btn);
                        return;
                    }
                }
                int[] bar = barRect(width, height);
                int[] win = windowRect(width, height);
                if (in(bar, mx, my)) { drag = 2; startX = barX; startY = barY; }
                else if (in(win, mx, my)) { drag = 1; startX = windowX; startY = windowY; }
                if (drag != 0) { startMx = mx; startMy = my; return; }
            }
            super.mouseClicked(mx, my, btn);
        }

        @Override
        protected void mouseClickMove(int mx, int my, int btn, long time) {
            if (drag == 0) return;
            int nx = startX + mx - startMx, ny = startY + my - startMy;
            if (drag == 1) {
                windowX = nx; windowY = ny;
                int[] r = windowRect(width, height); // kẹp trong màn hình
                windowX = r[0] - 2;
                windowY = r[3] - (height - 28);
            } else {
                barX = nx; barY = ny;
                int[] r = barRect(width, height);
                barX = r[0] - 2;
                barY = r[1] - (height - BAR_BASE);
            }
        }

        @Override
        protected void mouseReleased(int mx, int my, int state) {
            if (drag != 0) saveNow();
            drag = 0;
            super.mouseReleased(mx, my, state);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            applyChatWidth(); // thanh trượt độ rộng đổi khung xem trước ngay
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Kéo khung chat và thanh nhập tới vị trí bạn muốn",
                    width / 2, height / 2 - 12, 0xFFFFFF);

            int[] win = windowRect(width, height);
            drawRect(win[0], win[1], win[2], win[3], 0x80000000);
            drawRect(win[0], win[1], win[2], win[1] + 1, accent);
            drawRect(win[0], win[3] - 1, win[2], win[3], accent);
            drawRect(win[0], win[1], win[0] + 1, win[3], accent);
            drawRect(win[2] - 1, win[1], win[2], win[3], accent);
            fontRendererObj.drawStringWithShadow("Khung tin nhắn", win[0] + 4, win[1] + 4, 0xFFFFFF);

            int[] bar = barRect(width, height);
            if (!HIDE_BAR_BACKGROUND.value) drawBarBox(bar, 1f);
            fontRendererObj.drawStringWithShadow("Thanh nhập", bar[0] + 18, bar[1] + 4, 0x99FFFFFF);

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    static int mulA(int argb, float m) {
        int a = (int) (((argb >> 24) & 0xFF) * Math.max(0f, Math.min(1f, m)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Viền 1px theo hình bo góc của roundRect (không đè lên phần nền nên không bị đục). */
    static void ring(int x1, int y1, int x2, int y2, int c) {
        Gui.drawRect(x1 + 2, y1, x2 - 2, y1 + 1, c);
        Gui.drawRect(x1 + 2, y2 - 1, x2 - 2, y2, c);
        Gui.drawRect(x1, y1 + 2, x1 + 1, y2 - 2, c);
        Gui.drawRect(x2 - 1, y1 + 2, x2, y2 - 2, c);
        Gui.drawRect(x1 + 1, y1 + 1, x1 + 2, y1 + 2, c);
        Gui.drawRect(x2 - 2, y1 + 1, x2 - 1, y1 + 2, c);
        Gui.drawRect(x1 + 1, y2 - 2, x1 + 2, y2 - 1, c);
        Gui.drawRect(x2 - 2, y2 - 2, x2 - 1, y2 - 1, c);
    }

    /** Nền thanh nhập: kính mờ tối nhẹ, viền màu mảnh, bóng đổ rất nhẹ. p = 0..1 (mờ dần khi mở). */
    static void drawBarBox(int[] r, float p) {
        drawBarBox(r, p, 0f);
    }

    static int lerpColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Tô khối bo góc 2px với gradient dọc top -> bottom (RGB), alpha 0..1. */
    static void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom, float alpha) {
        int h = y2 - y1;
        for (int i = 0; i < h; i++) {
            int inset = (i == 0 || i == h - 1) ? 2 : (i == 1 || i == h - 2) ? 1 : 0;
            int rgb = lerpColor(top, bottom, h <= 1 ? 0f : i / (float) (h - 1));
            Gui.drawRect(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, mulA(0xFF000000 | rgb, alpha));
        }
    }

    static void drawBarBox(int[] r, float p, float glow) {
        int x1 = r[0], y1 = r[1], x2 = r[2], y2 = r[3];
        float op = BAR_OPACITY.getInt() / 100f;
        int acc = accent | 0xFF000000;
        // nền đen bo góc, cùng tông với nền các dòng chat
        roundRect(x1, y1, x2, y2, mulA(0xFF000000, op * p));
        // viền mảnh: trắng mờ khi nghỉ, ngả màu accent khi đang gõ
        if (BAR_BORDER.value) ring(x1, y1, x2, y2, mulA(0xFF000000 | lerpColor(0xFFFFFF, accent & 0xFFFFFF, glow), (0.10f + 0.42f * glow) * p));
        // biểu tượng ">" (prompt) bên trái: vẽ bằng pixel cho nét sắc, sáng + nhích sang phải khi đang gõ
        int cx = x1 + 7 + Math.round(glow), cy = y1 + (y2 - y1 - 7) / 2;
        int[] off = {0, 1, 2, 3, 2, 1, 0};
        for (int i = 0; i < 7; i++) {
            Gui.drawRect(cx + off[i], cy + i, cx + off[i] + 2, cy + i + 1, mulA(acc, (0.65f + 0.35f * glow) * p));
        }
    }

    private static float panelA;
    private static long panelLast;

    /** Khung kính cho cửa sổ chat: bóng đổ mềm, nền gradient xanh đen, viền accent mảnh, vệt sáng mép trên. */
    static void glassPanel(int x1, int y1, int x2, int y2, float fillA, float fx) {
        int acc = accent | 0xFF000000;
        ring(x1 - 2, y1 - 2, x2 + 2, y2 + 2, mulA(0xFF000000, 0.10f * fx));
        ring(x1 - 1, y1 - 1, x2 + 1, y2 + 1, mulA(0xFF000000, 0.22f * fx));
        int ix1 = x1 + 1, iy1 = y1 + 1, ix2 = x2 - 1, iy2 = y2 - 1, h = iy2 - iy1;
        int y = 0;
        while (y < h) { // dải 3px cho nhanh, 2 hàng đầu/cuối vát góc
            int step = (y < 2 || y >= h - 2) ? 1 : Math.min(3, h - 2 - y);
            int inset = (y == 0 || y == h - 1) ? 2 : (y == 1 || y == h - 2) ? 1 : 0;
            int rgb = lerpColor(0x121212, 0x000000, h <= 1 ? 0f : (y + step / 2f) / h);
            Gui.drawRect(ix1 + inset, iy1 + y, ix2 - inset, iy1 + y + step, mulA(0xFF000000 | rgb, fillA));
            y += step;
        }
        ring(x1, y1, x2, y2, mulA(0xFFFFFFFF, 0.10f * fx));
    }

    static void roundRect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1 + 2, y1, x2 - 2, y2, color);
        Gui.drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
        Gui.drawRect(x1, y1 + 2, x2, y2 - 2, color);
    }

    // ===== GUI =====
    public static class FancyChat extends GuiChat {
        private long openTime;

        // ---- animation gõ / xoá chữ ----
        /** Một ký tự đang hiển thị. x = vị trí hiện tại (px, không gian chữ) đang trượt về vị trí đích; x < 0 = chưa đặt. */
        private static final class Glyph {
            final char c; float x; final long born;
            Glyph(char c, float x, long born) { this.c = c; this.x = x; this.born = born; }
        }

        /** Ký tự vừa bị xoá, đang rơi xuống và mờ dần. */
        private static final class Ghost {
            final char c; final float x; final long died;
            Ghost(char c, float x, long died) { this.c = c; this.x = x; this.died = died; }
        }

        private final List<Glyph> glyphs = new ArrayList<Glyph>();
        private final List<Ghost> ghosts = new ArrayList<Ghost>();
        private String prevText = "";
        private int textX, textW;               // vùng chữ (px GUI)
        private float scrollX, scrollTarget;    // cuộn ngang khi chữ dài hơn ô nhập
        private float caretX = -1f;             // vị trí con trỏ đang trượt
        private int lastCursor = -1;
        private long lastAct;                   // lần cuối gõ / di chuyển con trỏ (con trỏ đứng yên không nhấp nháy)
        private long lastFrame;
        private boolean dragging;
        private float glow;                     // 0..1: viền sáng dần khi đang gõ
        private float hintA = 1f;               // độ hiện của chữ gợi ý
        private float tgOn = -1f, tgHov;        // trạng thái mượt của nút VI>EN
        private float fillF;                    // vạch tiến độ ký tự (mượt)
        private int cntRgb = 0xB8C4DC;          // màu bộ đếm (mượt)

        public FancyChat(String text) {
            super(text);
        }

        @Override
        public void initGui() {
            super.initGui();
            // dựng lại ô nhập theo vị trí/độ rộng người dùng chọn
            String t = inputField.getText();
            int[] r = barRect(width, height);
            textX = r[0] + 18;
            textW = r[2] - r[0] - 22 - (CHAR_COUNTER.value ? 48 : 0) - 4;
            inputField = new GuiTextField(0, fontRendererObj, textX, r[1] + 4, textW, 12);
            inputField.setMaxStringLength(100);
            inputField.setEnableBackgroundDrawing(false);
            inputField.setFocused(true);
            inputField.setText(t);
            inputField.setCanLoseFocus(false);
            openTime = System.currentTimeMillis();
            lastAct = openTime;
            resetGlyphs(t);
        }

        private void resetGlyphs(String t) {
            glyphs.clear();
            ghosts.clear();
            float x = 0f;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                glyphs.add(new Glyph(c, x, 0L));
                x += fontRendererObj.getCharWidth(c);
            }
            prevText = t;
            scrollX = scrollTarget = 0f;
            caretX = -1f;
            lastCursor = -1;
        }

        // Enter: dịch tin tiếng Việt sang tiếng Anh rồi mới gửi (lệnh bắt đầu bằng "/" giữ nguyên)
        @Override
        protected void keyTyped(char c, int key) throws IOException {
            if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && TRANSLATE_OUT.value) {
                final String s = inputField.getText().trim();
                if (!s.isEmpty() && !s.startsWith("/") && s.matches(".*\\p{L}.*")) {
                    mc.ingameGUI.getChatGUI().addToSentMessages(s);
                    mc.displayGuiScreen(null);
                    sendTranslated(s);
                    return;
                }
            }
            super.keyTyped(c, key);
        }

        // Nút bật/tắt dịch tin gửi đi, nằm ngay trên thanh nhập (bên trái bộ đếm ký tự)
        private int[] toggleRect() {
            int[] r = barRect(width, height);
            return new int[]{r[2] + 4, r[1], r[2] + 4 + togBtnW(), r[3]};
        }

        /** Vị trí con trỏ (chỉ số ký tự) tương ứng toạ độ x của chuột. */
        private int indexAt(int mx) {
            float rel = mx - textX + scrollX;
            float w = 0f;
            for (int i = 0; i < glyphs.size(); i++) {
                float cw = fontRendererObj.getCharWidth(glyphs.get(i).c);
                if (rel < w + cw / 2f) return i;
                w += cw;
            }
            return glyphs.size();
        }

        @Override
        protected void mouseClicked(int mx, int my, int btn) throws IOException {
            if (btn == 0) {
                int[] t = toggleRect();
                if (mx >= t[0] && mx < t[2] && my >= t[1] && my < t[3]) {
                    TRANSLATE_OUT.value = !TRANSLATE_OUT.value;
                    saveNow();
                    return;
                }
                IChatComponent c = mc.ingameGUI.getChatGUI().getChatComponent(rawMouseX(mc), rawMouseY(mc));
                if (handleComponentClick(c)) return;
                int[] r = barRect(width, height);
                if (mx >= textX && mx < textX + textW && my >= r[1] && my < r[3]) {
                    inputField.setCursorPosition(Math.min(indexAt(mx), inputField.getText().length()));
                    dragging = true;
                    return;
                }
            }
            inputField.mouseClicked(mx, my, btn);
        }

        // kéo chuột để bôi chọn chữ
        @Override
        protected void mouseClickMove(int mx, int my, int btn, long time) {
            if (dragging) inputField.setSelectionPos(Math.min(indexAt(mx), inputField.getText().length()));
            super.mouseClickMove(mx, my, btn, time);
        }

        @Override
        protected void mouseReleased(int mx, int my, int state) {
            dragging = false;
            super.mouseReleased(mx, my, state);
        }

        private float progress() {
            if (!ANIM_BAR.value || !AnimationsModule.hud()) return 1f;
            float t = Math.min(1f, (System.currentTimeMillis() - openTime) / 180f);
            return 1f - (float) Math.pow(1f - t, 3); // ease-out cubic
        }

        private static float k(float dt, float rate) {
            return 1f - (float) Math.exp(-dt * rate);
        }

        private static float easeOutBack(float t) {
            float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
            return 1f + c3 * u * u * u + c1 * u * u;
        }

        /** So sánh chữ cũ/mới: ký tự thêm vào nảy lên, ký tự bị xoá rơi xuống + mờ dần. */
        private void syncText(boolean anim, long now) {
            String cur = inputField.getText();
            if (cur.equals(prevText)) return;
            if (glyphs.size() != prevText.length()) { resetGlyphs(cur); return; }
            int max = Math.min(prevText.length(), cur.length());
            int pl = 0;
            while (pl < max && prevText.charAt(pl) == cur.charAt(pl)) pl++;
            int sl = 0;
            while (sl < max - pl && prevText.charAt(prevText.length() - 1 - sl) == cur.charAt(cur.length() - 1 - sl)) sl++;
            for (int i = prevText.length() - sl - 1; i >= pl; i--) {
                Glyph g = glyphs.remove(i);
                if (anim) ghosts.add(new Ghost(g.c, Math.max(0f, g.x), now));
            }
            while (ghosts.size() > 40) ghosts.remove(0);
            int addEnd = cur.length() - sl;
            for (int i = pl; i < addEnd; i++) {
                // dán nhiều ký tự một lúc: nảy lên lần lượt thành một làn sóng
                long born = anim ? now + Math.min(i - pl, 24) * 14L : 0L;
                glyphs.add(i, new Glyph(cur.charAt(i), -1f, born));
            }
            prevText = cur;
            lastAct = now;
        }

        private void drawGlyph(char c, float gx, float gy, float scale, int color) {
            if (c == ' ' || ((color >> 24) & 0xFF) < 8) return; // alpha quá thấp sẽ bị FontRenderer vẽ đặc
            float w = fontRendererObj.getCharWidth(c);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.pushMatrix();
            GlStateManager.translate(gx + w / 2f, gy + 4f, 0f);
            GlStateManager.scale(scale, scale, 1f);
            GlStateManager.translate(-w / 2f, -4f, 0f);
            fontRendererObj.drawStringWithShadow(String.valueOf(c), 0f, 0f, color);
            GlStateManager.popMatrix();
        }

        /** Vẽ chữ đang nhập (thay cho GuiTextField.drawTextBox): animation từng ký tự, chọn chữ, con trỏ mượt. */
        private void drawTypedText(int[] r, float p, float offset, float dt, float spd, boolean anim, long now) {
            syncText(anim, now);
            int n = glyphs.size();
            int y1 = r[1];
            int acc = accent | 0xFF000000;

            // vị trí đích của từng ký tự
            float[] tx = new float[n + 1];
            float w = 0f;
            for (int i = 0; i < n; i++) {
                tx[i] = w;
                w += fontRendererObj.getCharWidth(glyphs.get(i).c);
            }
            tx[n] = w;
            float slide = k(dt, 24f * spd);
            for (int i = 0; i < n; i++) {
                Glyph g = glyphs.get(i);
                if (g.x < 0f || !anim) g.x = tx[i];
                else {
                    g.x += (tx[i] - g.x) * slide;
                    if (Math.abs(tx[i] - g.x) < 0.05f) g.x = tx[i];
                }
            }

            int cur = Math.max(0, Math.min(n, inputField.getCursorPosition()));
            int sel = Math.max(0, Math.min(n, inputField.getSelectionEnd()));
            if (cur != lastCursor) { lastCursor = cur; lastAct = now; }
            float caretTarget = tx[cur];
            if (caretX < 0f || !anim) caretX = caretTarget;
            else {
                caretX += (caretTarget - caretX) * k(dt, 30f * spd);
                if (Math.abs(caretTarget - caretX) < 0.05f) caretX = caretTarget;
            }

            // cuộn ngang để con trỏ luôn nằm trong ô
            float maxScroll = Math.max(0f, w - textW + 6f);
            if (caretTarget - scrollTarget > textW - 6f) scrollTarget = caretTarget - textW + 6f;
            if (caretTarget - scrollTarget < 6f) scrollTarget = caretTarget - 6f;
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
            if (anim) {
                scrollX += (scrollTarget - scrollX) * k(dt, 20f * spd);
                if (Math.abs(scrollTarget - scrollX) < 0.05f) scrollX = scrollTarget;
            } else scrollX = scrollTarget;

            // cắt phần chữ tràn ra ngoài ô nhập
            int f = new ScaledResolution(mc).getScaleFactor();
            int top = r[1] + 1 + Math.round(offset), bot = r[3] - 1 + Math.round(offset);
            boolean clip = textW > 0 && bot > top;
            if (clip) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(textX * f, (height - bot) * f, textW * f, (bot - top) * f);
            }

            // vùng chọn
            int lo = Math.min(cur, sel), hi = Math.max(cur, sel);
            if (lo != hi) {
                int sx1 = textX + Math.round(tx[lo] - scrollX), sx2 = textX + Math.round(tx[hi] - scrollX);
                drawRect(sx1, y1 + 3, sx2, y1 + 13, mulA(acc, 0.38f * p));
            }

            // ký tự đang hiện: nảy lên + đổi từ màu accent sang trắng
            for (int i = 0; i < n; i++) {
                Glyph g = glyphs.get(i);
                long age = now - g.born;
                if (age < 0) continue;
                float t = anim ? Math.min(1f, age * spd / 190f) : 1f;
                float e = anim ? easeOutBack(t) : 1f;
                float ct = anim ? Math.min(1f, age * spd / 380f) : 1f;
                int rgb = lerpColor(accent & 0xFFFFFF, 0xEAF0FF, ct);
                float alpha = Math.min(1f, t * 3f) * p;
                drawGlyph(glyphs.get(i).c, textX + g.x - scrollX, y1 + 4 + (1f - e) * 5f, 0.55f + 0.45f * e,
                        mulA(0xFF000000 | rgb, alpha));
            }

            // ký tự vừa xoá: rơi xuống, thu nhỏ, ngả đỏ rồi tan biến
            for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext(); ) {
                Ghost gh = it.next();
                float t = (now - gh.died) * spd / 210f;
                if (t >= 1f) { it.remove(); continue; }
                int rgb = lerpColor(0xEAF0FF, 0xFF6B6B, t);
                drawGlyph(gh.c, textX + gh.x - scrollX, y1 + 4 + t * t * 6f, 1f - 0.45f * t,
                        mulA(0xFF000000 | rgb, (1f - t) * p));
            }

            // con trỏ: trượt mượt, đứng yên một chút sau khi gõ rồi mới nhấp nháy
            long idle = now - lastAct;
            float ca = idle < 450 ? 1f : 0.5f + 0.5f * (float) Math.cos((idle - 450) * 2.0 * Math.PI / 1100.0);
            int cx = textX + Math.round(caretX - scrollX);
            drawRect(cx - 1, y1 + 2, cx + 2, y1 + 14, mulA(acc, 0.14f * ca * p));
            drawRect(cx, y1 + 3, cx + 1, y1 + 13, mulA(0xFFFFFFFF, 0.95f * ca * p));

            if (clip) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            float p = progress();
            float offset = (1f - p) * 16f;

            long nowN = System.nanoTime();
            float dt = lastFrame == 0L ? 0.016f : Math.min(0.1f, (nowN - lastFrame) / 1e9f);
            lastFrame = nowN;
            float spd = (float) AnimationsModule.speed();
            if (!(spd > 0f)) spd = 1f;
            boolean anim = ANIM_TYPING.value && AnimationsModule.hud();
            long now = System.currentTimeMillis();

            int[] r = barRect(width, height);
            int x1 = r[0], x2 = r[2];
            int y1 = r[1], y2 = r[3];
            boolean empty = inputField.getText().isEmpty();

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, offset, 0);

            glow += ((empty ? 0f : 1f) - glow) * k(dt, 16f);
            if (!HIDE_BAR_BACKGROUND.value) drawBarBox(r, p, glow);

            // chữ đang nhập (animation gõ / xoá)
            drawTypedText(r, p, offset, dt, spd, anim, now);

            // chữ gợi ý: mờ dần + trượt nhẹ sang phải khi bắt đầu gõ
            hintA += ((empty ? 1f : 0f) - hintA) * k(dt, 18f);
            if (hintA > 0.02f) {
                String hint = TRANSLATE_OUT.value ? "Gõ tiếng Việt, tự dịch sang tiếng Anh..." : "Nhập tin nhắn...";
                int hx = x1 + 18 + 3 + Math.round((1f - hintA) * 8f);
                int maxW = x2 - 5 - (CHAR_COUNTER.value ? fontRendererObj.getStringWidth("100/100") + 8 + 3 : 0) - hx;
                if (fontRendererObj.getStringWidth(hint) > maxW) {
                    int dotsW = fontRendererObj.getStringWidth("...");
                    hint = maxW > dotsW ? fontRendererObj.trimStringToWidth(hint, maxW - dotsW) + "..." : "";
                }
                if (!hint.isEmpty()) {
                    GlStateManager.enableBlend();
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    int ha = mulA(0xFFFFFFFF, 0.32f * hintA * p);
                    if (((ha >> 24) & 0xFF) >= 8) fontRendererObj.drawStringWithShadow(hint, hx, y1 + 4, ha);
                }
            }

            // nút VI>EN: xanh = đang dịch tin gửi, xám = tắt (đổi màu / hover mượt)
            int[] tg = toggleRect();
            boolean on = TRANSLATE_OUT.value;
            boolean hov = mouseX >= tg[0] && mouseX < tg[2] && mouseY >= tg[1] && mouseY < tg[3];
            if (tgOn < 0f) tgOn = on ? 1f : 0f;
            tgOn += ((on ? 1f : 0f) - tgOn) * k(dt, 14f);
            tgHov += ((hov ? 1f : 0f) - tgHov) * k(dt, 18f);
            int tcol = lerpColor(0x7A8499, 0x55FF88, tgOn);
            if (!HIDE_BAR_BACKGROUND.value) dev.lunarlite.gui.Draw.rrect(tg[0], tg[1], tg[2], tg[3], 4, mulA(0xFF000000, 0.45f * p));
            dev.lunarlite.gui.Draw.rrect(tg[0], tg[1], tg[2], tg[3], 4, mulA(0xFF000000 | tcol, (0.20f + 0.15f * tgHov) * p));
            fontRendererObj.drawStringWithShadow(TOG_LABEL, tg[0] + 5, tg[1] + 4, mulA(0xFF000000 | tcol, p));

            // bộ đếm ký tự (viên thuốc đổi màu mượt theo mức) + vạch tiến độ ở đáy thanh
            if (CHAR_COUNTER.value) {
                int len = inputField.getText().length();
                String s = len + "/100";
                int target = len >= 100 ? 0xFF5555 : len >= 80 ? 0xFFD866 : 0xB8C4DC;
                cntRgb = lerpColor(cntRgb, target, k(dt, 12f));
                int col = 0xFF000000 | cntRgb;
                int tw = fontRendererObj.getStringWidth(s);
                int px2 = x2 - 5, px1 = px2 - tw - 8;
                dev.lunarlite.gui.Draw.rrect(px1, y1 + 3, px2, y2 - 3, 4, mulA(0xFFFFFFFF, 0.07f * p));
                fontRendererObj.drawStringWithShadow(s, px1 + 4, y1 + 4, mulA(col, p));
                int trackW = x2 - x1 - 12;
                fillF += (Math.min(1f, len / 100f) - fillF) * k(dt, 14f);
                Gui.drawRect(x1 + 6, y2 - 3, x1 + 6 + trackW, y2 - 2, mulA(0xFFFFFFFF, 0.07f * p));
                int fw = Math.round(trackW * fillF);
                if (fw > 0) Gui.drawRect(x1 + 6, y2 - 3, x1 + 6 + fw, y2 - 2, mulA(col, 0.9f * p));
            }

            GlStateManager.popMatrix();

            // hover component (link, item...)
            IChatComponent comp = mc.ingameGUI.getChatGUI().getChatComponent(rawMouseX(mc), rawMouseY(mc));
            if (comp != null && comp.getChatStyle().getChatHoverEvent() != null) {
                handleComponentHover(comp, mouseX, mouseY);
            }

            // GuiScreen.drawScreen (buttons/labels) - không gọi super vì sẽ vẽ lại thanh mặc định
            for (Object b : buttonList) ((GuiButton) b).drawButton(mc, mouseX, mouseY);
            for (Object l : labelList) ((GuiLabel) l).drawLabel(mc, mouseX, mouseY);
        }
    }
}
