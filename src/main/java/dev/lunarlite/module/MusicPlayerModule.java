package dev.lunarlite.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import java.awt.Desktop;
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
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.FloatBuffer;
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
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Trình phát nhạc (Windows): lấy bài đang phát từ Windows media session (Spotify, YouTube Music...),
 * lời bài hát đồng bộ từ lrclib.net. Nút điều khiển bấm được khi mở chat.
 * Đăng ký: MUSIC.register() trong LunarLite.init.
 */
public class MusicPlayerModule extends HudModule {
    private static final String[] RES = {"common.ps1", "music_info.ps1", "music_cmd.ps1", "music_play.ps1"};
    private static final int PW = 232;
    private static final int B_PREV = 0, B_PLAY = 1, B_NEXT = 2, B_VOLDN = 3, B_VOLUP = 4, B_LYR = 5, B_SEARCH = 6;

    private final BoolSetting showLyrics = add(new BoolSetting("lyrics", "Show Lyrics", true), "Music");
    private final BoolSetting showCover = add(new BoolSetting("cover", "Show Cover", true), "Music");
    private final SliderSetting lyricOffset = add(new SliderSetting("lyric_offset", "Lyrics Offset", 0, -3000, 3000, 50, "ms"), "Music", showLyrics);
    private static final String[] SRC_NAME = {"YouTube Music", "YouTube", "Spotify", "SoundCloud", "Nhạc miễn phí", "Nhạc trong máy"};
    private final SliderSetting searchSrc = add(new SliderSetting("search_src", "Search Source", 1, 1, 6, 1, ""), "Music");
    private final BoolSetting animOn = add(new BoolSetting("anim", "Animations", true), "Music");
    private final SliderSetting animSpeed = add(new SliderSetting("anim_speed", "Animation Speed", 100, 25, 200, 5, "%"), "Music", animOn);

    // ---- trạng thái bài hát (ghi từ luồng đọc, đọc từ luồng render) ----
    private volatile boolean hasSession, playing;
    private volatile String title = "", artist = "";
    private volatile long basePos, dur, baseNanos, lastUpdate;
    private volatile int vol = 50;

    // ---- lời bài hát ----
    private static class Line {
        final long t; final String s;
        String shown; int sw = -1; // chuỗi đã cắt vừa khung + độ rộng, tính một lần rồi dùng lại
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

    private final GamePlayer gp = new GamePlayer(); // phát nhạc ngay trong game (nguồn 5, 6)
    private boolean gameWas;
    private Process info, cmd;
    private Writer cmdOut;
    private File dir;
    private boolean startFailed;
    private boolean searching;
    private String query = "";
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
        if (enabled) syncGame();
        if (searching && !(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) searching = false;
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
            gp.setDir(dir);
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
        gp.shutdown();
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
        if (gp.active()) return; // đang phát trong game: bỏ qua nhạc của Windows
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
                    coverT = 0f; // phát animation hiện ảnh bìa mới
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
                    String ct = clean(t), ca = clean(a);
                    res = parseSynced(http("https://lrclib.net/api/get?" + q(t, a, d)));
                    if (res == null) res = parseSynced(http("https://lrclib.net/api/get?" + q(ct, ca, d)));
                    if (res == null) res = best(http("https://lrclib.net/api/search?track_name=" + enc(ct) + "&artist_name=" + enc(ca)), d);
                    if (res == null) res = best(http("https://lrclib.net/api/search?q=" + enc(ca + " " + ct)), d);
                } catch (Exception ignored) { }
                if (gen != lyricsGen) return;
                if (res == null || res.isEmpty()) { lines = new ArrayList<Line>(); lyricsState = "none"; }
                else { lines = res; lyricsState = ""; }
            }
        });
    }

    private static String q(String t, String a, long d) throws UnsupportedEncodingException {
        return "track_name=" + enc(t) + "&artist_name=" + enc(a) + (d > 0 ? "&duration=" + (d / 1000) : "");
    }

    /** Bỏ phần thừa trong tên bài/ca sĩ: (Official Video), - Topic, feat. ... */
    private static String clean(String s) {
        return s.replaceAll("(?i)\\s*[\\(\\[](official|lyrics?|lyric video|audio|video|mv|m/v|hd|4k|visuali[sz]er)[^\\)\\]]*[\\)\\]]", "")
                .replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("(?i)\\s*[\\(\\[]?(feat\\.?|ft\\.?)\\s.*$", "")
                .trim();
    }

    /** Chọn kết quả tìm kiếm có lời đồng bộ và độ dài gần bài đang phát nhất. */
    private static List<Line> best(String json, long durMs) {
        if (json == null) return null;
        try {
            List<Line> out = null;
            double bestDiff = Double.MAX_VALUE;
            for (JsonElement e : new JsonParser().parse(json).getAsJsonArray()) {
                List<Line> l = parseSynced(e.toString());
                if (l == null) continue;
                JsonElement de = e.getAsJsonObject().get("duration");
                double diff = (durMs > 0 && de != null && !de.isJsonNull()) ? Math.abs(de.getAsDouble() - durMs / 1000.0) : 0;
                if (durMs > 0 && diff > 8) continue;
                if (diff < bestDiff) { bestDiff = diff; out = l; }
            }
            return out;
        } catch (Exception ex) { return null; }
    }

    static String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s, "UTF-8"); }

    static String http(String u) throws IOException {
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

    // ================================================================ trạng thái animation

    private static final float[] NO_MOUSE = {-999f, -999f};
    private static final FloatBuffer MAT = BufferUtils.createFloatBuffer(16);
    // hình dạng icon phát/dừng: hai tứ giác nội suy qua lại (0 = dừng, 1 = phát)
    private static final float[] PAUSE_L = {-3, -4, -1, -4, -1, 4, -3, 4}, PLAY_L = {-3, -4, 0.5f, -2, 0.5f, 2, -3, 4};
    private static final float[] PAUSE_R = {1, -4, 3, -4, 3, 4, 1, 4}, PLAY_R = {0.5f, -2, 4, 0, 4, 0, 0.5f, 2};

    private long lastNanos;
    private float dt, clock;           // dt: bước thời gian đã nhân tốc độ; clock: đồng hồ animation (giây)
    private boolean on, wasVis;        // on: đang bật animation
    private float introT = 1f;         // hiện panel
    private float lyrAnim = -1f;       // mở/đóng khung lời (0..1)
    private float playAmt, morph = 1f; // playAmt: 0 dừng .. 1 đang phát; morph: 0 icon dừng .. 1 icon phát
    private float shownFrac, volShown = -1f, volFlash, ripple = 1f, searchT, coverT = 1f, trackT = 1f;
    private float lyricPos, lineFlash, linesFade = 1f, marqT0, mx = -999f, my = -999f;
    private final float[] hov = new float[B_SEARCH + 1], kick = new float[B_SEARCH + 1], eq = new float[4];
    private final MusicAnim.Spring pop = new MusicAnim.Spring(260f, 12f);
    private boolean lastPlaying;
    private int lastVol = -1, lastCur = -2;
    private String curTT, curAR, oldTT = "", oldAR = "";
    private long lastKeyNanos;
    private List<Line> lastLines, timesFor;
    private long[] times = new long[0];
    private int sf = 2, sfW, sfH, sfG = -1;
    private final int[][] rects = new int[B_SEARCH + 1][4];
    private int rectsKey = Integer.MIN_VALUE;
    private long fmtSecP = -1, fmtSecD = -1;
    private String fmtP = "00:00", fmtD = "00:00";
    private final float[] tmpQ = new float[8];

    private float ap(float cur, float target, float speed) {
        return on ? MusicAnim.approach(cur, target, speed, dt) : target;
    }

    /** Cập nhật mọi giá trị animation một lần mỗi khung hình (độc lập FPS). */
    private void update() {
        long now = System.nanoTime();
        float d = lastNanos == 0L ? 0f : (now - lastNanos) / 1.0e9f;
        lastNanos = now;
        boolean gap = d > 0.35f; // HUD vừa bị ẩn một lúc -> hiện lại bằng animation
        if (d > 0.1f) d = 0.1f;
        on = animOn.value;
        dt = on ? d * ((float) animSpeed.value / 100f) : 0f;
        boolean vis = visible(false);
        if (on && ((vis && !wasVis) || gap)) introT = 0f;
        wasVis = vis;
        introT = on ? Math.min(1f, introT + dt / 0.5f) : 1f;
        clock += dt;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth != sfW || mc.displayHeight != sfH || mc.gameSettings.guiScale != sfG) {
            sfW = mc.displayWidth; sfH = mc.displayHeight; sfG = mc.gameSettings.guiScale;
            sf = new ScaledResolution(mc).getScaleFactor();
        }

        boolean pl = hasSession && playing;
        if (pl != lastPlaying) {
            lastPlaying = pl;
            if (on) { ripple = 0f; pop.v += 2.5f; }
        }
        playAmt = ap(playAmt, pl ? 1f : 0f, 6f);
        morph = ap(morph, pl ? 0f : 1f, 14f);
        if (on) pop.update(dt); else { pop.x = 0f; pop.v = 0f; }
        ripple = on ? Math.min(1f, ripple + dt / 0.6f) : 1f;
        coverT = on ? Math.min(1f, coverT + dt / 0.5f) : 1f;
        trackT = on ? Math.min(1f, trackT + dt / 0.42f) : 1f;
        linesFade = on ? Math.min(1f, linesFade + dt / 0.4f) : 1f;
        if (lyrAnim < 0f) lyrAnim = showLyrics.value ? 1f : 0f;
        lyrAnim = ap(lyrAnim, showLyrics.value ? 1f : 0f, 12f);
        searchT = ap(searchT, searching ? 1f : 0f, 16f);
        lineFlash = ap(lineFlash, 0f, 4f);
        volFlash = ap(volFlash, 0f, 5f);

        int v = vol;
        if (lastVol < 0) { lastVol = v; volShown = v; }
        else if (v != lastVol) { lastVol = v; volFlash = 1f; }
        volShown = ap(volShown, v, 14f);

        for (int i = 0; i < eq.length; i++) {
            float tgt = pl ? 0.2f + 0.8f * (float) Math.abs(Math.sin(clock * (5.3f + i * 1.7f) + i * 1.9f)
                    * Math.cos(clock * (2.1f + i * 0.9f) + i)) : 0f;
            eq[i] = ap(eq[i], tgt, tgt > eq[i] ? 16f : 9f);
        }
    }

    // ================================================================ bố cục

    /** Mức mở của khung lời (0..1), animate khi bật/tắt lời. */
    private float lyrA() { return lyrAnim < 0f ? (showLyrics.value ? 1f : 0f) : lyrAnim; }
    private int barY() { return 40 + Math.round(29f * lyrA()); }
    private int rowY() { return barY() + 11; }

    @Override
    public int getWidth(boolean preview) { return visible(preview) ? PW : 0; }

    private boolean visible(boolean preview) {
        return hasSession || preview || Minecraft.getMinecraft().currentScreen instanceof GuiChat;
    }

    @Override
    public int getHeight(boolean preview) {
        if (!visible(preview)) return 0;
        return rowY() + 11 + 5;
    }

    private static void set(int[] r, int x, int y, int w, int h) { r[0] = x; r[1] = y; r[2] = w; r[3] = h; }

    /** Tính lại hình chữ nhật các nút chỉ khi bố cục đổi (không cấp phát mỗi khung hình). */
    private void ensureRects() {
        int key = barY();
        if (key == rectsKey) return;
        rectsKey = key;
        int ry = key + 11, cx = PW / 2;
        set(rects[B_PREV], cx - 34, ry - 1, 14, 12);
        set(rects[B_PLAY], cx - 9, ry - 3, 18, 16);
        set(rects[B_NEXT], cx + 20, ry - 1, 14, 12);
        set(rects[B_VOLDN], 6, ry, 10, 11);
        set(rects[B_VOLUP], 6 + 12 + fr().getStringWidth("100%") + 2, ry, 10, 11);
        int wl = fr().getStringWidth("Lời") + 10, ws = fr().getStringWidth("Tìm") + 10;
        set(rects[B_SEARCH], PW - 6 - wl - 4 - ws, ry, ws, 11);
        set(rects[B_LYR], PW - 6 - wl, ry, wl, 11);
    }

    /** Hình chữ nhật (x,y,w,h, toạ độ cục bộ) của từng nút. */
    private int[] rect(int id) {
        ensureRects();
        return rects[id];
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
        if (!enabled || !(e.gui instanceof GuiChat)) return;
        if (!(Mouse.getEventButton() == 0 && Mouse.getEventButtonState())) return;
        float[] m = localMouse();
        for (int id = 0; id <= B_SEARCH; id++) {
            if (hit(rect(id), m[0], m[1])) {
                press(id);
                e.setCanceled(true);
                return;
            }
        }
    }

    private void press(int id) {
        kick[id] = 1f; // hiệu ứng bấm (icon giật nhẹ, vệt mờ)
        switch (id) {
            case B_PREV: if (gp.active()) gp.prev(); else send("prev"); break;
            case B_PLAY:
                if (gp.active()) gp.toggle();
                else { send("play"); playing = !playing; basePos = pos(); baseNanos = System.nanoTime(); }
                pop.x = -0.14f;
                break;
            case B_NEXT: if (gp.active()) gp.next(); else send("next"); break;
            case B_VOLDN: setVol(Math.max(0, vol - 5)); break;
            case B_VOLUP: setVol(Math.min(100, vol + 5)); break;
            case B_SEARCH: searching = !searching; query = ""; lastKeyNanos = System.nanoTime(); break;
            default: showLyrics.toggle(); break;
        }
    }

    private void setVol(int v) {
        vol = v;
        if (gp.active()) gp.setVolume(v); else send("vol:" + v);
    }

    // ================================================================ phát trong game

    /** Khi đang phát trong game: lấy bài, vị trí, âm lượng từ trình phát của mod thay cho nhạc của Windows. */
    private void syncGame() {
        if (!gp.active()) {
            if (gameWas) { gameWas = false; lyricsKey = ""; coverFile = "\u0000"; } // trả lại cho nhạc của Windows
            return;
        }
        final GamePlayer.Track t = gp.current();
        if (t == null) return;
        gameWas = true;
        title = t.title; artist = t.artist;
        dur = gp.dur(); playing = gp.playing();
        basePos = gp.pos(); baseNanos = gp.posNanos();
        vol = gp.volume();
        hasSession = true;
        lastUpdate = System.nanoTime();
        String key = "game|" + t.title + "|" + t.lyricArtist + "|" + t.dur;
        if (!key.equals(lyricsKey)) { lyricsKey = key; fetchLyrics(t.title, t.lyricArtist, t.dur > 0 ? t.dur : gp.dur()); }
        if (!t.thumb.equals(coverFile)) {
            coverFile = t.thumb;
            final String url = t.thumb;
            pool.submit(new Runnable() {
                public void run() {
                    BufferedImage img = null;
                    if (!url.isEmpty()) {
                        try {
                            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                            c.setConnectTimeout(5000);
                            c.setReadTimeout(8000);
                            c.setRequestProperty("User-Agent", "ZhwiiClient/2.0");
                            InputStream in = c.getInputStream();
                            try { img = ImageIO.read(in); } finally { in.close(); }
                        } catch (Exception ignored) { }
                    }
                    queueCover(img);
                }
            });
        }
    }

    // ================================================================ tìm kiếm (mở YouTube Music trên trình duyệt)

    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre e) {
        if (!enabled || !searching || !(e.gui instanceof GuiChat)) return;
        e.setCanceled(true);
        if (!Keyboard.getEventKeyState()) return;
        lastKeyNanos = System.nanoTime(); // con trỏ sáng liên tục khi đang gõ
        int k = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        if (k == Keyboard.KEY_ESCAPE) {
            searching = false;
        } else if (k == Keyboard.KEY_RETURN || k == Keyboard.KEY_NUMPADENTER) {
            openSearch(query.trim());
            searching = false;
        } else if (k == Keyboard.KEY_TAB) {
            searchSrc.value = (srcIdx() + 1) % SRC_NAME.length + 1; // đổi nguồn: YT Music > YouTube > Spotify > SoundCloud
        } else if (k == Keyboard.KEY_BACK) {
            if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
        } else if (GuiScreen.isKeyComboCtrlV(k)) {
            String clip = GuiScreen.getClipboardString();
            if (clip != null) {
                StringBuilder sb = new StringBuilder(query);
                for (char ch : clip.toCharArray()) if (ChatAllowedCharacters.isAllowedCharacter(ch) && sb.length() < 80) sb.append(ch);
                query = sb.toString();
            }
        } else if (ChatAllowedCharacters.isAllowedCharacter(c) && query.length() < 80) {
            query += c;
        }
    }

    private static File musicFolder() {
        File cfg = dev.lunarlite.LunarLite.configDir();
        File root = cfg.getAbsoluteFile().getParentFile();
        return new File(root != null ? root : new File("."), "zhwii-music");
    }

    private int srcIdx() {
        int i = Math.round((float) searchSrc.value) - 1;
        return i < 0 ? 0 : (i >= SRC_NAME.length ? SRC_NAME.length - 1 : i);
    }

    private static String searchUrl(int src, String q) throws UnsupportedEncodingException {
        String e = enc(q);
        switch (src) {
            case 1: return q.isEmpty() ? "https://www.youtube.com" : "https://www.youtube.com/results?search_query=" + e;
            case 2: return q.isEmpty() ? "https://open.spotify.com" : "https://open.spotify.com/search/" + e.replace("+", "%20");
            case 3: return q.isEmpty() ? "https://soundcloud.com" : "https://soundcloud.com/search?q=" + e;
            default: return q.isEmpty() ? "https://music.youtube.com" : "https://music.youtube.com/search?q=" + e;
        }
    }

    private void openSearch(final String q) {
        final int src = srcIdx();
        if (src >= 4) { // nhạc miễn phí / nhạc trong máy: phát ngay trong game
            if (!isWin()) { gp.searchOnline(""); return; }
            if (src == 4) gp.searchOnline(q);
            else gp.searchLocal(q, musicFolder());
            return;
        }
        pool.submit(new Runnable() {
            public void run() {
                try {
                    String url = searchUrl(src, q);
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(url));
                    } else if (isWin()) {
                        new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    // ================================================================ tiện ích vẽ

    private long pos() {
        long p = basePos + (playing ? (System.nanoTime() - baseNanos) / 1000000L : 0);
        return dur > 0 ? Math.min(p, dur) : p;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Chuỗi mm:ss chỉ định dạng lại khi giây đổi (tránh String.format mỗi khung hình). */
    private String fmtPos(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s != fmtSecP) { fmtSecP = s; fmtP = fmt(ms); }
        return fmtP;
    }

    private String fmtDur(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s != fmtSecD) { fmtSecD = s; fmtD = fmt(ms); }
        return fmtD;
    }

    private String fit(String s, int w) {
        FontRenderer f = fr();
        if (f.getStringWidth(s) <= w) return s;
        return f.trimStringToWidth(s, w - f.getStringWidth("...")) + "...";
    }

    /** Cắt vùng vẽ theo hình chữ nhật cục bộ (tính từ ma trận hiện tại nên đúng cả khi HUD có scale / animation). */
    private void clipOn(float x, float y, float w, float h) {
        MAT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MAT);
        float sx = MAT.get(0), sy = MAT.get(5), tx = MAT.get(12), ty = MAT.get(13);
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int px = Math.round((sx * x + tx) * sf);
        int pw = Math.round(sx * w * sf);
        int ph = Math.round(sy * h * sf);
        int py = Math.round(Minecraft.getMinecraft().displayHeight - (sy * (y + h) + ty) * sf);
        GL11.glScissor(px, py, Math.max(0, pw), Math.max(0, ph));
    }

    private void clipOff() { GL11.glPopAttrib(); }

    /** Tô đa giác lồi toạ độ float (tự sửa chiều quay để không bị culling). */
    private static void fillPoly(float[] p, int n, int col) {
        float area = 0f;
        for (int i = 0; i < n; i += 2) {
            int j = (i + 2) % n;
            area += p[i] * p[j + 1] - p[j] * p[i + 1];
        }
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f, ((col >>> 24) & 255) / 255f);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        if (area < 0f) for (int i = 0; i < n; i += 2) GL11.glVertex2f(p[i], p[i + 1]);
        else for (int i = n - 2; i >= 0; i -= 2) GL11.glVertex2f(p[i], p[i + 1]);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int col) {
        float[] q = tmpQ;
        q[0] = x1; q[1] = y1; q[2] = x2; q[3] = y2; q[4] = x3; q[5] = y3; q[6] = x4; q[7] = y4;
        fillPoly(q, 8, col);
    }

    private void tri(float x1, float y1, float x2, float y2, float x3, float y3, int col) {
        float[] q = tmpQ;
        q[0] = x1; q[1] = y1; q[2] = x2; q[3] = y2; q[4] = x3; q[5] = y3;
        fillPoly(q, 6, col);
    }

    /** Icon trước / sau (bar + tam giác). */
    private void skipIcon(float cx, float cy, boolean next, int col) {
        if (next) {
            tri(cx - 4, cy - 4, cx - 4, cy + 4, cx + 3, cy, col);
            quad(cx + 3, cy - 4, cx + 4, cy - 4, cx + 4, cy + 4, cx + 3, cy + 4, col);
        } else {
            quad(cx - 4, cy - 4, cx - 3, cy - 4, cx - 3, cy + 4, cx - 4, cy + 4, col);
            tri(cx + 4, cy - 4, cx + 4, cy + 4, cx - 3, cy, col);
        }
    }

    /** Icon phát/dừng biến hình mượt: hai thanh dừng trượt thành tam giác phát. */
    private void playIcon(float cx, float cy, float sc, float m, int col) {
        float t = MusicAnim.easeInOutCubic(m);
        float[] q = tmpQ;
        for (int i = 0; i < 8; i++) q[i] = (((i & 1) == 0) ? cx : cy) + (PAUSE_L[i] + (PLAY_L[i] - PAUSE_L[i]) * t) * sc;
        fillPoly(q, 8, col);
        for (int i = 0; i < 8; i++) q[i] = (((i & 1) == 0) ? cx : cy) + (PAUSE_R[i] + (PLAY_R[i] - PAUSE_R[i]) * t) * sc;
        fillPoly(q, 8, col);
    }

    // ================================================================ vẽ

    @Override
    protected void draw(int x, int y, boolean preview) {
        update();
        boolean chat = !preview && Minecraft.getMinecraft().currentScreen instanceof GuiChat;
        float[] m = chat ? localMouse() : NO_MOUSE;
        mx = m[0]; my = m[1];
        for (int id = 0; id <= B_SEARCH; id++) {
            hov[id] = ap(hov[id], hit(rect(id), mx, my) ? 1f : 0f, 16f);
            kick[id] = ap(kick[id], 0f, 7f);
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        // hiện panel: phóng to nhẹ + trượt lên (vọt qua rồi vào chỗ)
        boolean xf = introT < 1f;
        if (xf) {
            float e = MusicAnim.easeOutBack(introT);
            float s = 0.9f + 0.1f * e;
            float cx = x + PW / 2f, cy = y + getHeight(preview) / 2f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy + (1f - e) * 8f, 0f);
            GlStateManager.scale(s, s, 1f);
            GlStateManager.translate(-cx, -cy, 0f);
        }
        try {
            body(x, y, preview);
        } finally {
            if (xf) GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
        }
    }

    private void body(int x, int y, boolean preview) {
        boolean fake = preview && !hasSession;
        boolean idle = !hasSession && !preview;
        String tt = fake ? "Tên bài hát" : idle ? "Chưa có bài đang phát" : title;
        String ar = fake ? "Nghệ sĩ" : idle ? "Bấm Tìm để tìm nhạc" : artist;
        String nt = gp.note();
        if (!nt.isEmpty() && !fake) ar = nt; // thông báo của trình phát trong game
        boolean pl = fake || (!idle && playing);
        long p = fake ? 83000L : idle ? 0L : pos(), d = fake ? 187000L : idle ? 0L : dur;
        int vo = fake ? 69 : Math.round(volShown);
        FontRenderer f = fr();
        int w = PW, h = getHeight(preview);
        int ac = accentColor(0), acRgb = ac & 0xFFFFFF;
        final int white = 0xFFFFFFFF, grey = 0xFFB8BDC6;

        // phát hiện đổi bài -> chữ cuộn lên kiểu "roll"
        if (curTT == null) { curTT = tt; curAR = ar; }
        else if (!tt.equals(curTT) || !ar.equals(curAR)) {
            oldTT = curTT; oldAR = curAR; curTT = tt; curAR = ar;
            if (on) trackT = 0f;
            marqT0 = clock;
        }

        drawBox(x, y, w, h);

        // ---- ảnh bìa
        int tx = x + 8;
        if (showCover.value) {
            if (on && playAmt > 0.02f && !idle) { // quầng sáng "thở" quanh ảnh khi đang phát
                float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 3f);
                int ga = (int) ((0x10 + 0x18 * pulse) * playAmt);
                Draw.rrect(x + 3, y + 3, x + 39, y + 39, 7, Draw.withAlpha(acRgb, ga));
                Draw.rrect(x + 4, y + 4, x + 38, y + 38, 6, Draw.withAlpha(acRgb, ga));
            }
            boolean hasCover = cover != null && !fake && !idle;
            if (!hasCover || coverT < 1f) {
                Draw.rrect(x + 6, y + 6, x + 36, y + 36, 3, 0xFF2A2D33);
                float bob = (on && pl) ? (float) Math.sin(clock * 6f) * 1.5f : 0f;
                GlStateManager.pushMatrix();
                GlStateManager.translate(0f, bob, 0f);
                f.drawString("♪", x + 18, y + 17, 0xFF8A8F98, false);
                GlStateManager.popMatrix();
            }
            if (hasCover) {
                float s = on ? 0.82f + 0.18f * MusicAnim.easeOutBack(coverT) : 1f;
                float a = on ? Math.min(1f, coverT * 2.5f) : 1f;
                float shade = on ? 0.6f + 0.4f * playAmt : 1f; // tối đi khi tạm dừng
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + 21f, y + 21f, 0f);
                GlStateManager.scale(s, s, 1f);
                GlStateManager.translate(-(x + 21f), -(y + 21f), 0f);
                GlStateManager.enableTexture2D();
                Minecraft.getMinecraft().getTextureManager().bindTexture(cover);
                GlStateManager.color(shade, shade, shade, a);
                Gui.drawScaledCustomSizeModalRect(x + 6, y + 6, 0, 0, 1, 1, 30, 30, 1, 1);
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.popMatrix();
            }
            tx = x + 42;
        }

        // ---- tiêu đề / nghệ sĩ (roll khi đổi bài, chạy ngang khi quá dài)
        int fullW = w - (tx - x) - 8;
        textRow(f, tx, y + 9, fullW - (on ? 15 : 0), tt, oldTT, true);
        textRow(f, tx, y + 21, fullW, ar, oldAR, false);

        // ---- equalizer
        if (on && !idle) {
            int ex = x + w - 17, eb = y + 18;
            int ecol = MusicAnim.alpha(ac, 0.55f + 0.45f * playAmt);
            for (int i = 0; i < eq.length; i++) {
                int eh = 2 + Math.round(eq[i] * 9f);
                Draw.rrect(ex + i * 3, eb - eh, ex + i * 3 + 2, eb, 1, ecol);
            }
        }

        // ---- ô tìm kiếm (trượt xuống, gạch chân chạy ra, con trỏ nhấp nháy mượt)
        if (searchT > 0.01f) {
            float e = MusicAnim.easeOutCubic(searchT);
            int oy = Math.round((1f - e) * -10f);
            Draw.rrect(x + 6, y + 5 + oy, x + w - 6, y + 35 + oy, 4, MusicAnim.alpha(0xF0181B20, e));
            int hc = MusicAnim.alpha(0xFF9AA0A9, e), wc = MusicAnim.alpha(white, e);
            if (MusicAnim.drawable(hc)) f.drawString("Tìm trên " + SRC_NAME[srcIdx()] + "  (Tab: đổi nguồn)", x + 12, y + 9 + oy, hc, false);
            String shown = f.trimStringToWidth(query, w - 30, true);
            if (MusicAnim.drawable(wc)) f.drawString(shown, x + 12, y + 21 + oy, wc, false);
            long since = System.nanoTime() - lastKeyNanos;
            float ca = since < 600000000L ? 1f : 0.5f + 0.5f * (float) Math.cos((since - 600000000L) / 1.0e9 * Math.PI * 2.0);
            int cc = MusicAnim.alpha(ac, ca * e);
            if (MusicAnim.drawable(cc)) {
                int cx = x + 12 + f.getStringWidth(shown);
                Gui.drawRect(cx + 1, y + 20 + oy, cx + 2, y + 30 + oy, cc);
            }
            Draw.rrect(x + 12, y + 32 + oy, x + 12 + Math.round((w - 24) * e), y + 33 + oy, 1, MusicAnim.alpha(ac, e));
        }

        // ---- lời bài hát (khung mở/đóng mượt, dòng cuộn mượt)
        float la = lyrA();
        if (la > 0.01f) {
            int ly = y + 38;
            int bh = Math.max(2, Math.round(28f * la));
            Draw.rrect(x + 6, ly, x + w - 6, ly + bh, Math.min(3, bh / 2), MusicAnim.alpha(0x66000000, la));
            clipOn(x + 6, ly, w - 12, bh);
            try {
                List<Line> L = fake ? null : lines;
                if (L != null && !L.isEmpty()) lyricRows(f, L, x, ly, p, ac, la);
                else lyricState(f, x, ly, fake, idle, la);
            } finally {
                clipOff();
            }
        }

        // ---- thanh tiến trình (mượt giữa các lần cập nhật từ Windows)
        int by = y + barY();
        int tw = f.getStringWidth("00:00");
        String t0 = fmtPos(p), t1 = fmtDur(d);
        f.drawString(t0, x + 6, by, 0xFF9AA0A9, false);
        f.drawString(t1, x + w - 6 - f.getStringWidth(t1), by, 0xFF9AA0A9, false);
        int barX = x + 6 + tw + 5, barW = w - 12 - 2 * tw - 10;
        float tgt = d > 0 ? MusicAnim.clamp(p / (float) d, 0f, 1f) : 0f;
        shownFrac = ap(shownFrac, tgt, 12f);
        Draw.hbar(barX, by + 3, barW, 3, shownFrac, ac, 0xFF3A3D44);
        if (d > 0 && !idle) { // đầu phát phát sáng nhẹ
            float hx = barX + barW * shownFrac, hy = by + 4.5f;
            float r = 2.2f + chatTBoost() + (on ? 0.5f * (0.5f + 0.5f * (float) Math.sin(clock * 5f)) * playAmt : 0f);
            Draw.disc(hx, hy, r + 2.2f, Draw.withAlpha(acRgb, 0x30));
            Draw.disc(hx, hy, r, white);
        }

        // ---- hàng nút
        int ry = y + rowY();
        icon(f, B_VOLDN, x, y, "-", grey);
        f.drawString(vo + "%", x + 6 + 12, ry + 2, MusicAnim.mix(white, ac, volFlash), false);
        icon(f, B_VOLUP, x, y, "+", grey);

        int[] r = rect(B_PREV);
        float cx = x + r[0] + r[2] / 2f, cy = y + r[1] + r[3] / 2f;
        int c = MusicAnim.mix(grey, white, hov[B_PREV]);
        if (kick[B_PREV] > 0.03f) skipIcon(cx + kick[B_PREV] * 6f, cy, false, MusicAnim.alpha(c, kick[B_PREV] * 0.45f));
        skipIcon(cx - hov[B_PREV] * 1.2f - kick[B_PREV] * 3f, cy, false, c);

        r = rect(B_NEXT);
        cx = x + r[0] + r[2] / 2f; cy = y + r[1] + r[3] / 2f;
        c = MusicAnim.mix(grey, white, hov[B_NEXT]);
        if (kick[B_NEXT] > 0.03f) skipIcon(cx - kick[B_NEXT] * 6f, cy, true, MusicAnim.alpha(c, kick[B_NEXT] * 0.45f));
        skipIcon(cx + hov[B_NEXT] * 1.2f + kick[B_NEXT] * 3f, cy, true, c);

        r = rect(B_PLAY);
        cx = x + r[0] + r[2] / 2f; cy = y + r[1] + r[3] / 2f;
        float sc = 1f + pop.x + 0.08f * hov[B_PLAY];
        if (on && ripple < 1f) { // sóng lan ra khi đổi trạng thái phát/dừng
            float rr = 8f + 9f * MusicAnim.easeOutCubic(ripple);
            Draw.disc(cx, cy, rr, Draw.withAlpha(acRgb, (int) (0x70 * (1f - ripple))));
        }
        Draw.disc(cx, cy, 8f * sc, MusicAnim.mix(ac, white, 0.18f * hov[B_PLAY]));
        playIcon(cx, cy, sc, morph, 0xFF101216);

        r = rect(B_LYR);
        int bgOff = MusicAnim.mix(0xFF3A3D44, 0xFF4A4D55, hov[B_LYR]);
        int bgOn = MusicAnim.mix(Draw.withAlpha(acRgb, 0xCC), ac, hov[B_LYR]);
        Draw.rrect(x + r[0], y + r[1], x + r[0] + r[2], y + r[1] + r[3], 4, MusicAnim.mix(bgOff, bgOn, la));
        f.drawString("Lời", x + r[0] + 5, y + r[1] + 2, MusicAnim.mix(grey, 0xFF101216, la), false);

        r = rect(B_SEARCH);
        bgOff = MusicAnim.mix(0xFF3A3D44, 0xFF4A4D55, hov[B_SEARCH]);
        bgOn = MusicAnim.mix(Draw.withAlpha(acRgb, 0xCC), ac, hov[B_SEARCH]);
        Draw.rrect(x + r[0], y + r[1], x + r[0] + r[2], y + r[1] + r[3], 4, MusicAnim.mix(bgOff, bgOn, searchT));
        f.drawString("Tìm", x + r[0] + 5, y + r[1] + 2, MusicAnim.mix(grey, 0xFF101216, searchT), false);
    }

    /** Đầu phát to hơn một chút khi mở chat. */
    private float chatTBoost() {
        return Minecraft.getMinecraft().currentScreen instanceof GuiChat ? 0.8f : 0f;
    }

    private void icon(FontRenderer f, int id, int x, int y, String s, int col) {
        int[] r = rect(id);
        float lift = kick[id] * 2f;
        f.drawString(s, x + r[0] + (r[2] - f.getStringWidth(s)) / 2f, y + r[1] + 2 - lift, MusicAnim.mix(col, 0xFFFFFFFF, hov[id]), false);
    }

    /** Một hàng chữ (tiêu đề / nghệ sĩ): cũ cuộn lên mờ dần, mới cuộn vào; chữ quá dài thì chạy ngang. */
    private void textRow(FontRenderer f, int tx, int ry, int availW, String text, String old, boolean isTitle) {
        boolean roll = on && trackT < 1f && old != null && !old.isEmpty();
        float e = MusicAnim.easeOutCubic(trackT);
        float tw = f.getStringWidth(text);
        boolean scroll = on && tw > availW;
        clipOn(tx, ry - 2, availW, 12);
        try {
            if (roll) {
                int col = MusicAnim.alpha(isTitle ? 0xFFFFFFFF : 0xFF9AA0A9, 1f - e);
                if (MusicAnim.drawable(col)) f.drawString(fit(old, availW), tx, ry - e * 10f, col, isTitle ? false : shadow.value);
            }
            float offY = roll ? (1f - e) * 10f : 0f;
            float offX = scroll ? MusicAnim.marquee(clock - marqT0, tw - availW) : 0f;
            String s = scroll ? text : fit(text, availW);
            GlStateManager.pushMatrix();
            GlStateManager.translate(-offX, offY, 0f);
            if (isTitle) drawText(s, tx, ry, 0);
            else f.drawString(s, tx, ry, 0xFF9AA0A9, shadow.value);
            GlStateManager.popMatrix();
        } finally {
            clipOff();
        }
    }

    /** Các dòng lời: cuộn mượt, dòng hiện tại sáng lên và to hơn, dòng xa mờ dần. */
    private void lyricRows(FontRenderer f, List<Line> L, int x, int ly, long p, int ac, float la) {
        int n = L.size();
        if (L != timesFor) {
            times = new long[n];
            for (int i = 0; i < n; i++) times[i] = L.get(i).t;
            timesFor = L;
        }
        int cur = MusicAnim.lastAtOrBefore(times, n, p + (long) lyricOffset.value);
        if (L != lastLines) { lastLines = L; lyricPos = cur; lastCur = cur; linesFade = on ? 0f : 1f; }
        if (cur != lastCur) { lastCur = cur; lineFlash = 1f; }
        if (Math.abs(cur - lyricPos) > 3.5f) lyricPos = cur; // tua xa -> nhảy thẳng
        else lyricPos = ap(lyricPos, cur, 9f);
        float a = la * linesFade;

        Draw.rrect(x + 8, ly + 10, x + PW - 8, ly + 19, 2,
                MusicAnim.alpha(Draw.withAlpha(ac & 0xFFFFFF, 0x40 + (int) (0x30 * lineFlash)), a));
        int first = (int) Math.floor(lyricPos) - 2;
        int lo = Math.max(0, first), hi = Math.min(n - 1, first + 6);
        for (int i = lo; i <= hi; i++) {
            float dist = Math.abs(i - lyricPos);
            float emph = MusicAnim.clamp(1f - dist, 0f, 1f);
            float fade = dist <= 1.05f ? 1f : MusicAnim.clamp(1f - (dist - 1.05f) * 1.3f, 0f, 1f);
            int col = MusicAnim.alpha(MusicAnim.mix(0xFF6C717A, ac, MusicAnim.easeOutCubic(emph)), fade * a);
            Line ln = L.get(i);
            if (!MusicAnim.drawable(col) || ln.s.isEmpty()) continue;
            if (ln.sw < 0) { ln.shown = fit(ln.s, PW - 24); ln.sw = f.getStringWidth(ln.shown); }
            float cy = ly + 15f + 9f * (i - lyricPos);
            float sc = on ? 0.9f + 0.1f * emph : 1f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + PW / 2f, cy, 0f);
            GlStateManager.scale(sc, sc, 1f);
            f.drawString(ln.shown, -ln.sw / 2f, -4f, col, false);
            GlStateManager.popMatrix();
        }
    }

    /** Trạng thái khi chưa có lời: chấm "..." chạy khi đang tải, các trạng thái khác hiện dần. */
    private void lyricState(FontRenderer f, int x, int ly, boolean fake, boolean idle, float la) {
        boolean loading = !fake && !idle && "loading".equals(lyricsState);
        String full = fake ? "Lời bài hát hiện ở đây" : idle ? "" : loading ? "Đang tải lời..." : "Không có lời bài hát";
        if (full.isEmpty()) return;
        String s = full;
        float a = la;
        if (loading) {
            int k = 1 + (int) (clock * 3f) % 3;
            s = "Đang tải lời" + "...".substring(0, k);
            a *= 0.6f + 0.4f * (0.5f + 0.5f * (float) Math.sin(clock * 4f));
        }
        int col = MusicAnim.alpha(0xFF6C717A, a);
        if (MusicAnim.drawable(col)) f.drawString(s, x + (PW - f.getStringWidth(full)) / 2f, ly + 10, col, false);
    }
}
