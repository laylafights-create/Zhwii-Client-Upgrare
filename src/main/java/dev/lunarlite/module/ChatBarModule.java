package dev.lunarlite.modules;

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
