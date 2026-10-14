package dev.lunarlite.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
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
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * - Timestamp [HH:mm] trước mỗi tin nhắn
 *
 * Đăng ký: new ChatBarModule().register();
 */
public class ChatBarModule {

    // ===== Cấu hình =====
    public static boolean enabled = true;
    public static boolean timestamps = true;
    public static boolean charCounter = true;
    public static boolean playerHeads = true;  // avatar + giường màu team trước tin nhắn
    public static boolean autoTranslate = true; // tự dịch tin người chơi sang tiếng Việt
    public static String translateTo = "vi";
    public static boolean playerOnly = true;   // chỉ hiện tin người chơi, chặn tin hệ thống
    public static int accent = 0xFF55AAFF;     // ARGB
    public static int background = 0x90000000; // ARGB

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm");

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
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

    private static void requestTranslate(final String name, String body) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && name.equalsIgnoreCase(mc.thePlayer.getName())) return; // bỏ qua tin của mình
        final String text = body == null ? "" : body.trim();
        if (text.length() < 2 || !text.matches(".*\\p{L}.*")) return;
        final String src = text.length() > 200 ? text.substring(0, 200) : text;

        TRANSLATOR.submit(new Runnable() {
            @Override public void run() {
                try {
                    final String[] r = translate(src);
                    if (r[1].toLowerCase().startsWith(translateTo) || r[0].equalsIgnoreCase(src)) return;
                    final Minecraft m = Minecraft.getMinecraft();
                    m.addScheduledTask(new Runnable() {
                        @Override public void run() {
                            if (m.thePlayer == null) return;
                            m.thePlayer.addChatMessage(new ChatComponentText(
                                    (playerHeads ? ICON_SPACE : "") + EnumChatFormatting.AQUA + "[VI] "
                                            + EnumChatFormatting.GRAY + r[0]));
                        }
                    });
                } catch (Exception ignored) {
                    // lỗi mạng: bỏ qua, không làm phiền chat
                }
            }
        });
    }

    // ===== Avatar + giường =====
    private static final String S = "\u00A7";
    private static final String SENT = S + "r" + S + "r" + S + "r";
    private static final String ICON_SPACE = "     "; // 5 dấu cách = 20px chừa chỗ cho icon
    private static final int[] MC_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static class Icon {
        final String name; final int color;
        Icon(String name, int color) { this.name = name; this.color = color; }
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
    private static String makeMarker(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return "";
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
        if (info == null) return "";
        int id = nextId++ & 0xFFF;
        ICONS.put(id, new Icon(name, teamColor(info)));
        String h = String.format("%03x", id);
        return SENT + S + h.charAt(0) + S + h.charAt(1) + S + h.charAt(2) + S + "r" + ICON_SPACE;
    }

    private static Icon decode(String s) {
        int p = SENT.length();
        if (!s.startsWith(SENT) || s.length() < p + 6) return null;
        try {
            return ICONS.get(Integer.parseInt("" + s.charAt(p + 1) + s.charAt(p + 3) + s.charAt(p + 5), 16));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void drawIcon(Minecraft mc, Icon ic, int y, int a) {
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(ic.name);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, a / 255f);
        if (info != null) {
            mc.getTextureManager().bindTexture(info.getLocationSkin());
            Gui.drawScaledCustomSizeModalRect(0, y - 8, 8f, 8f, 8, 8, 8, 8, 64f, 64f);   // mặt
            Gui.drawScaledCustomSizeModalRect(0, y - 8, 40f, 8f, 8, 8, 8, 8, 64f, 64f);  // lớp mũ
        }
        if (ic.color != -1) {
            int x = 10, t = y - 7, al = a << 24;
            Gui.drawRect(x, t + 2, x + 9, t + 6, al | 0x8B5A2B);        // khung gỗ
            Gui.drawRect(x + 3, t + 1, x + 9, t + 4, al | ic.color);    // chăn màu team
            Gui.drawRect(x, t + 1, x + 3, t + 3, al | 0xFFFFFF);        // gối
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
            Icon ic = decode(line.getChatComponent().getFormattedText());
            if (ic != null) drawIcon(mc, ic, -i * 9, a);
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // Lọc tin hệ thống + timestamp + marker icon
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!enabled || e.type == 2) return; // 2 = action bar, giữ nguyên

        String[] pc = parsePlayerChat(e);
        String name = pc == null ? null : pc[0];
        if (playerOnly && name == null) {
            e.setCanceled(true);
            return;
        }

        if (autoTranslate && pc != null) requestTranslate(pc[0], pc[1]);

        String marker = (playerHeads && name != null) ? makeMarker(name) : "";
        if (marker.isEmpty() && !timestamps) return;

        IChatComponent root = new ChatComponentText(marker);
        if (timestamps) {
            root.appendSibling(new ChatComponentText(
                    EnumChatFormatting.GRAY + "[" + TIME.format(new Date()) + "] " + EnumChatFormatting.RESET));
        }
        root.appendSibling(e.message);
        e.message = root;
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
