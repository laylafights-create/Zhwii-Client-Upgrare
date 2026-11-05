package dev.lunarlite.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/**
 * Trình phát nhạc (Windows): lấy bài đang phát từ Windows media session (Spotify, YouTube Music...),
 * lời bài hát đồng bộ từ lrclib.net. Nút điều khiển bấm được khi mở chat.
 * Đăng ký: MUSIC.register() trong LunarLite.init.
 */
public class MusicPlayerModule extends HudModule {
    private static final String[] RES = {"common.ps1", "music_info.ps1", "music_cmd.ps1"};
    private static final int PW = 232;
    private static final int B_PREV = 0, B_PLAY = 1, B_NEXT = 2, B_VOLDN = 3, B_VOLUP = 4, B_LYR = 5;

    private final BoolSetting showLyrics = add(new BoolSetting("lyrics", "Show Lyrics", true), "Music");
    private final BoolSetting showCover = add(new BoolSetting("cover", "Show Cover", true), "Music");
    private final SliderSetting lyricOffset = add(new SliderSetting("lyric_offset", "Lyrics Offset", 0, -3000, 3000, 50, "ms"), "Music", showLyrics);

    // ---- trạng thái bài hát (ghi từ luồng đọc, đọc từ luồng render) ----
    private volatile boolean hasSession, playing;
    private volatile String title = "", artist = "";
    private volatile long basePos, dur, baseNanos, lastUpdate;
    private volatile int vol = 50;

    // ---- lời bài hát ----
    private static class Line {
        final long t; final String s;
        Line(long t, String s) { this.t = t; this.s = s; }
    }
    private volatile List<Line> lines = new ArrayList<Line>();
    private volatile String lyricsState = "";
    private volatile int lyricsGen = 0;
    private String lyricsKey = "";

    // ---- ảnh bìa ----
    private String coverFile = "";
    private ResourceLocation cover;
    private DynamicTexture coverTex;
    private int coverN = 0;

    private Process info, cmd;
    private Writer cmdOut;
    private File dir;
    private boolean startFailed;
    private final ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) { Thread t = new Thread(r, "zhwii-music"); t.setDaemon(true); return t; }
    });

    public MusicPlayerModule() {
        super("music", "Music Player", "Hiện bài đang phát trên máy (Windows) cùng lời bài hát và nút điều khiển (mở chat để bấm).",
                Category.HUD, false, 4, 90);
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() { public void run() { stop(); } }));
    }

    // ================================================================ tiến trình Windows

    private static boolean isWin() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    @Override
    public void onTick() {
        if (enabled) { if (info == null && !startFailed) start(); }
        else if (info != null) stop();
        if (hasSession && System.nanoTime() - lastUpdate > 5000000000L) hasSession = false;
    }

    @Override
    public void onToggle() {
        if (enabled) { startFailed = false; start(); } else stop();
    }

    private synchronized void start() {
        if (info != null || !isWin()) return;
        try {
            dir = new File(System.getProperty("java.io.tmpdir"), "zhwii-music");
            dir.mkdirs();
            for (String r : RES) {
                InputStream in = MusicPlayerModule.class.getResourceAsStream("/assets/lunarlite/music/" + r);
                if (in == null) { startFailed = true; return; }
                Files.copy(in, new File(dir, r).toPath(), StandardCopyOption.REPLACE_EXISTING);
                in.close();
            }
            info = ps("music_info.ps1");
            cmd = ps("music_cmd.ps1");
            cmdOut = new OutputStreamWriter(cmd.getOutputStream(), StandardCharsets.UTF_8);
            final Process p = info;
            Thread t = new Thread(new Runnable() { public void run() { readLoop(p); } }, "zhwii-music-read");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            startFailed = true;
            stop();
        }
    }

    private Process ps(String script) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", new File(dir, script).getAbsolutePath(), "-Dir", dir.getAbsolutePath());
        Process p = pb.start();
        final InputStream err = p.getErrorStream();
        Thread d = new Thread(new Runnable() {
            public void run() { try { while (err.read() != -1) { } } catch (IOException ignored) { } }
        });
        d.setDaemon(true);
        d.start();
        return p;
    }

    private synchronized void stop() {
        if (info != null) { info.destroy(); info = null; }
        if (cmd != null) { cmd.destroy(); cmd = null; }
        cmdOut = null;
        hasSession = false;
    }

    private void send(String c) {
        try {
            Writer w = cmdOut;
            if (w != null) { w.write(c + "\n"); w.flush(); }
        } catch (IOException ignored) { }
    }

    private void readLoop(Process p) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) {
                try { handle(new JsonParser().parse(l).getAsJsonObject()); } catch (Exception ignored) { }
            }
        } catch (IOException ignored) { }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private void handle(JsonObject o) {
        lastUpdate = System.nanoTime();
        if (!o.get("ok").getAsBoolean()) { hasSession = false; return; }
        String t = str(o, "title"), a = str(o, "artist");
        long d = o.get("dur").getAsLong();
        basePos = o.get("pos").getAsLong();
        baseNanos = System.nanoTime();
        playing = o.get("playing").getAsBoolean();
        vol = o.get("vol").getAsInt();
        title = t; artist = a; dur = d;
        hasSession = true;
        String key = t + "|" + a + "|" + d;
        if (!key.equals(lyricsKey)) { lyricsKey = key; fetchLyrics(t, a, d); }
        String th = str(o, "thumb");
        if (!th.equals(coverFile)) {
            coverFile = th;
            BufferedImage img = null;
            if (!th.isEmpty()) { try { img = ImageIO.read(new File(th)); } catch (Exception ignored) { } }
            queueCover(img);
        }
    }

    private void queueCover(final BufferedImage img) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (coverTex != null && cover != null) { mc.getTextureManager().deleteTexture(cover); }
                coverTex = null; cover = null;
                if (img != null) {
                    coverTex = new DynamicTexture(img);
                    cover = new ResourceLocation("lunarlite", "music/cover" + (coverN++));
                    mc.getTextureManager().loadTexture(cover, coverTex);
                }
            }
        });
    }

    // ================================================================ lời bài hát (lrclib.net)

    private void fetchLyrics(final String t, final String a, final long d) {
        final int gen = ++lyricsGen;
        lines = new ArrayList<Line>();
        lyricsState = "loading";
        pool.submit(new Runnable() {
            public void run() {
                List<Line> res = null;
                try {
                    String q = "track_name=" + enc(t) + "&artist_name=" + enc(a) + (d > 0 ? "&duration=" + (d / 1000) : "");
                    res = parseSynced(http("https://lrclib.net/api/get?" + q));
                    if (res == null) {
                        String s = http("https://lrclib.net/api/search?q=" + enc(a + " " + t));
                        if (s != null) {
                            for (JsonElement e : new JsonParser().parse(s).getAsJsonArray()) {
                                res = parseSynced(e.toString());
                                if (res != null) break;
                            }
                        }
                    }
                } catch (Exception ignored) { }
                if (gen != lyricsGen) return;
                if (res == null || res.isEmpty()) { lines = new ArrayList<Line>(); lyricsState = "none"; }
                else { lines = res; lyricsState = ""; }
            }
        });
    }

    private static String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s, "UTF-8"); }

    private static String http(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "ZhwiiClient/2.0");
        if (c.getResponseCode() != 200) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = br.readLine()) != null) sb.append(l).append('\n');
        br.close();
        return sb.toString();
    }

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s*(.*)");

    private static List<Line> parseSynced(String json) {
        if (json == null) return null;
        try {
            JsonElement e = new JsonParser().parse(json).getAsJsonObject().get("syncedLyrics");
            if (e == null || e.isJsonNull()) return null;
            List<Line> out = new ArrayList<Line>();
            for (String ln : e.getAsString().split("\n")) {
                Matcher m = LRC.matcher(ln.trim());
                if (m.matches()) {
                    long t = (long) (Integer.parseInt(m.group(1)) * 60000L + Double.parseDouble(m.group(2)) * 1000);
                    out.add(new Line(t, m.group(3)));
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception ex) { return null; }
    }

    // ================================================================ bố cục

    private int lyricsH() { return showLyrics.value ? 28 : 0; }
    private int barY() { return showLyrics.value ? 69 : 40; }
    private int rowY() { return barY() + 11; }

    @Override
    public int getWidth(boolean preview) { return (hasSession || preview) ? PW : 0; }

    @Override
    public int getHeight(boolean preview) {
        if (!(hasSession || preview)) return 0;
        return rowY() + 11 + 5;
    }

    /** Hình chữ nhật (x,y,w,h, toạ độ cục bộ) của từng nút. */
    private int[] rect(int id) {
        int ry = rowY();
        int cx = PW / 2;
        switch (id) {
            case B_PREV: return new int[] {cx - 34, ry - 1, 14, 12};
            case B_PLAY: return new int[] {cx - 9, ry - 3, 18, 16};
            case B_NEXT: return new int[] {cx + 20, ry - 1, 14, 12};
            case B_VOLDN: return new int[] {6, ry, 10, 11};
            case B_VOLUP: return new int[] {6 + 12 + fr().getStringWidth("100%") + 2, ry, 10, 11};
            default: {
                int w = fr().getStringWidth("Lời") + 10;
                return new int[] {PW - 6 - w, ry, w, 11};
            }
        }
    }

    /** Toạ độ chuột (trong hệ toạ độ cục bộ của HUD), hoặc null nếu không áp dụng. */
    private float[] localMouse() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        float mx = Mouse.getX() * (float) sr.getScaledWidth() / mc.displayWidth;
        float my = sr.getScaledHeight() - Mouse.getY() * (float) sr.getScaledHeight() / mc.displayHeight - 1;
        float s = getScale();
        return new float[] {(mx - renderX(sr, false)) / s, (my - renderY(sr, false)) / s};
    }

    private static boolean hit(int[] r, float mx, float my) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    // ================================================================ bấm nút (khi mở chat)

    @SubscribeEvent
    public void onClick(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!enabled || !hasSession || !(e.gui instanceof GuiChat)) return;
        if (!(Mouse.getEventButton() == 0 && Mouse.getEventButtonState())) return;
        float[] m = localMouse();
        for (int id = 0; id <= B_LYR; id++) {
            if (hit(rect(id), m[0], m[1])) {
                press(id);
                e.setCanceled(true);
                return;
            }
        }
    }

    private void press(int id) {
        switch (id) {
            case B_PREV: send("prev"); break;
            case B_PLAY: send("play"); playing = !playing; basePos = pos(); baseNanos = System.nanoTime(); break;
            case B_NEXT: send("next"); break;
            case B_VOLDN: vol = Math.max(0, vol - 5); send("vol:" + vol); break;
            case B_VOLUP: vol = Math.min(100, vol + 5); send("vol:" + vol); break;
            default: showLyrics.toggle(); break;
        }
    }

    // ================================================================ vẽ

    private long pos() {
        long p = basePos + (playing ? (System.nanoTime() - baseNanos) / 1000000L : 0);
        return dur > 0 ? Math.min(p, dur) : p;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private String fit(String s, int w) {
        FontRenderer f = fr();
        if (f.getStringWidth(s) <= w) return s;
        return f.trimStringToWidth(s, w - f.getStringWidth("...")) + "...";
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        boolean fake = preview && !hasSession;
        String tt = fake ? "Tên bài hát" : title, ar = fake ? "Nghệ sĩ" : artist;
        boolean pl = fake || playing;
        long p = fake ? 83000L : pos(), d = fake ? 187000L : dur;
        int vo = fake ? 69 : vol;
        FontRenderer f = fr();
        int w = PW, h = getHeight(preview);
        int ac = accentColor(0);
        float[] m = (!preview && Minecraft.getMinecraft().currentScreen instanceof GuiChat) ? localMouse() : new float[] {-999, -999};

        drawBox(x, y, w, h);

        // ảnh bìa
        int tx = x + 8;
        if (showCover.value) {
            if (cover != null && !fake) {
                GlStateManager.enableTexture2D();
                Minecraft.getMinecraft().getTextureManager().bindTexture(cover);
                GlStateManager.color(1f, 1f, 1f, 1f);
                Gui.drawScaledCustomSizeModalRect(x + 6, y + 6, 0, 0, 1, 1, 30, 30, 1, 1);
            } else {
                Draw.rrect(x + 6, y + 6, x + 36, y + 36, 3, 0xFF2A2D33);
                f.drawString("\u266A", x + 18, y + 17, 0xFF8A8F98, false);
            }
            tx = x + 42;
        }
        drawText(fit(tt, w - (tx - x) - 8), tx, y + 9, 0);
        f.drawString(fit(ar, w - (tx - x) - 8), tx, y + 21, 0xFF9AA0A9, shadow.value);

        // lời bài hát
        if (showLyrics.value) {
            int ly = y + 38;
            Draw.rrect(x + 6, ly, x + w - 6, ly + 28, 3, 0x66000000);
            List<Line> L = fake ? null : lines;
            if (L != null && !L.isEmpty()) {
                long pp = p + (long) lyricOffset.value;
                int cur = -1;
                for (int i = 0; i < L.size(); i++) { if (L.get(i).t <= pp) cur = i; else break; }
                Draw.rrect(x + 8, ly + 10, x + w - 8, ly + 19, 2, Draw.withAlpha(ac & 0xFFFFFF, 0x40));
                lyric(f, L, cur - 1, x, ly + 1, 0xFF6C717A);
                lyric(f, L, cur, x, ly + 10, ac);
                lyric(f, L, cur + 1, x, ly + 19, 0xFF6C717A);
            } else {
                String s = fake ? "Lời bài hát hiện ở đây" : ("loading".equals(lyricsState) ? "Đang tải lời..." : "Không có lời bài hát");
                f.drawString(s, x + (w - f.getStringWidth(s)) / 2f, ly + 10, 0xFF6C717A, false);
            }
        }

        // thanh tiến trình
        int by = y + barY();
        String t0 = fmt(p), t1 = fmt(d);
        int tw = f.getStringWidth("00:00");
        f.drawString(t0, x + 6, by, 0xFF9AA0A9, false);
        f.drawString(t1, x + w - 6 - f.getStringWidth(t1), by, 0xFF9AA0A9, false);
        Draw.hbar(x + 6 + tw + 5, by + 3, w - 12 - 2 * tw - 10, 3, d > 0 ? p / (float) d : 0f, ac, 0xFF3A3D44);

        // hàng nút
        int ry = y + rowY();
        icon(f, B_VOLDN, x, y, "-", m, 0xFFB8BDC6);
        f.drawString(vo + "%", x + 6 + 12, ry + 2, 0xFFFFFFFF, false);
        icon(f, B_VOLUP, x, y, "+", m, 0xFFB8BDC6);

        int[] r;
        r = rect(B_PREV);
        int c = hit(r, m[0], m[1]) ? 0xFFFFFFFF : 0xFFB8BDC6;
        float cx = x + r[0] + r[2] / 2f, cy = y + r[1] + r[3] / 2f;
        Gui.drawRect((int) cx - 4, (int) cy - 4, (int) cx - 3, (int) cy + 4, c);
        Draw.poly(new float[] {cx + 4, cy - 4, cx + 4, cy + 4, cx - 3, cy}, c);

        r = rect(B_NEXT);
        c = hit(r, m[0], m[1]) ? 0xFFFFFFFF : 0xFFB8BDC6;
        cx = x + r[0] + r[2] / 2f; cy = y + r[1] + r[3] / 2f;
        Draw.poly(new float[] {cx - 4, cy - 4, cx - 4, cy + 4, cx + 3, cy}, c);
        Gui.drawRect((int) cx + 3, (int) cy - 4, (int) cx + 4, (int) cy + 4, c);

        r = rect(B_PLAY);
        cx = x + r[0] + r[2] / 2f; cy = y + r[1] + r[3] / 2f;
        Draw.disc(cx, cy, hit(r, m[0], m[1]) ? 8.5f : 8f, ac);
        if (pl) {
            Gui.drawRect((int) cx - 3, (int) cy - 4, (int) cx - 1, (int) cy + 4, 0xFF101216);
            Gui.drawRect((int) cx + 1, (int) cy - 4, (int) cx + 3, (int) cy + 4, 0xFF101216);
        } else {
            Draw.poly(new float[] {cx - 3, cy - 4, cx - 3, cy + 4, cx + 4, cy}, 0xFF101216);
        }

        r = rect(B_LYR);
        boolean hv = hit(r, m[0], m[1]);
        Draw.rrect(x + r[0], y + r[1], x + r[0] + r[2], y + r[1] + r[3], 4,
                showLyrics.value ? (hv ? ac : Draw.withAlpha(ac & 0xFFFFFF, 0xCC)) : (hv ? 0xFF4A4D55 : 0xFF3A3D44));
        f.drawString("Lời", x + r[0] + 5, y + r[1] + 2, showLyrics.value ? 0xFF101216 : 0xFFB8BDC6, false);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
    }

    private void icon(FontRenderer f, int id, int x, int y, String s, float[] m, int col) {
        int[] r = rect(id);
        boolean hv = hit(r, m[0], m[1]);
        f.drawString(s, x + r[0] + (r[2] - f.getStringWidth(s)) / 2f, y + r[1] + 2, hv ? 0xFFFFFFFF : col, false);
    }

    private void lyric(FontRenderer f, List<Line> L, int i, int x, int y, int col) {
        if (i < 0 || i >= L.size()) return;
        String s = fit(L.get(i).s, PW - 24);
        f.drawString(s, x + (PW - f.getStringWidth(s)) / 2f, y + 1, col, false);
    }
}
