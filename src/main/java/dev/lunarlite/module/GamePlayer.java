package dev.lunarlite.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Phát nhạc ngay trong game (Windows): danh sách phát từ nhạc miễn phí (Openverse, giấy phép Creative Commons,
 * không cần API key) hoặc file nhạc trong thư mục zhwii-music. Âm thanh do MediaPlayer của Windows phát qua
 * music_play.ps1, nên không cần thêm thư viện giải mã nào.
 */
final class GamePlayer {
    /** Một bài trong danh sách phát. */
    static final class Track {
        final String title, artist, lyricArtist, target, thumb;
        final long dur;
        Track(String title, String artist, String lyricArtist, String target, String thumb, long dur) {
            this.title = title; this.artist = artist; this.lyricArtist = lyricArtist;
            this.target = target; this.thumb = thumb == null ? "" : thumb; this.dur = dur;
        }
    }

    private static final String[] LOCAL_EXT = {".mp3", ".wav", ".wma", ".m4a", ".aac", ".flac"};
    private static final List<String> ONLINE_TYPES = Arrays.asList("mp3", "wav", "wma", "m4a", "aac", "flac", "mpeg", "mpga");

    private final List<Track> queue = new ArrayList<Track>();
    private int idx = -1, gen;
    private Track cur;
    private File dir;
    private Process proc;
    private Writer out;

    private volatile boolean active, playing;
    private volatile long pos, posNanos, dur;
    private volatile int vol = 50;
    private volatile String note = "";
    private volatile long noteUntil;
    private long holdUntil; // sau khi bấm phát/dừng, bỏ qua trạng thái cũ còn đang trên đường về

    private final ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) { Thread t = new Thread(r, "zhwii-music-free"); t.setDaemon(true); return t; }
    });

    // ---------------------------------------------------------------- trạng thái (đọc từ luồng render)

    boolean active() { return active; }
    boolean playing() { return playing; }
    synchronized Track current() { return cur; }
    long dur() { return dur; }
    int volume() { return vol; }
    long pos() { return pos; }
    long posNanos() { return posNanos; }

    /** Thông báo ngắn (đang tìm, không thấy...). Rỗng nếu đã hết hạn. */
    String note() { return System.currentTimeMillis() < noteUntil ? note : ""; }

    private void setNote(String s, long ms) { note = s; noteUntil = System.currentTimeMillis() + ms; }

    private long currentPos() {
        return playing ? pos + (System.nanoTime() - posNanos) / 1000000L : pos;
    }

    void setDir(File d) { dir = d; }

    // ---------------------------------------------------------------- điều khiển

    synchronized void toggle() {
        if (!active) return;
        pos = currentPos(); posNanos = System.nanoTime();
        playing = !playing;
        holdUntil = System.nanoTime() + 600000000L;
        send(playing ? "resume" : "pause");
    }

    synchronized void next() { if (active && !queue.isEmpty()) playAt((idx + 1) % queue.size()); }

    synchronized void prev() {
        if (!active || queue.isEmpty()) return;
        if (currentPos() > 3000) playAt(idx); // đã nghe quá 3 giây thì phát lại từ đầu
        else playAt((idx - 1 + queue.size()) % queue.size());
    }

    synchronized void setVolume(int v) {
        vol = Math.max(0, Math.min(100, v));
        send("vol:" + vol);
    }

    synchronized void stop() {
        active = false; playing = false; cur = null;
        send("stop");
    }

    /** Dừng hẳn và tắt tiến trình PowerShell (khi tắt module / thoát game). */
    synchronized void shutdown() {
        active = false; playing = false; cur = null;
        if (proc != null) { proc.destroy(); proc = null; }
        out = null;
    }

    // ---------------------------------------------------------------- tìm kiếm

    void searchOnline(final String q) {
        if (!ready()) return;
        if (q.isEmpty()) { setNote("Gõ tên bài hoặc nghệ sĩ để tìm nhạc miễn phí", 6000); return; }
        setNote("Đang tìm \"" + q + "\"...", 20000);
        pool.submit(new Runnable() {
            public void run() {
                try {
                    String json = MusicPlayerModule.http("https://api.openverse.org/v1/audio/?page_size=20&q=" + MusicPlayerModule.enc(q));
                    List<Track> list = parseOnline(json);
                    if (list.isEmpty()) { setNote("Không tìm thấy nhạc miễn phí cho \"" + q + "\"", 6000); return; }
                    setNote("", 0);
                    playList(list);
                } catch (Exception e) {
                    setNote("Không kết nối được tới nguồn nhạc miễn phí", 6000);
                }
            }
        });
    }

    void searchLocal(final String q, final File musicDir) {
        if (!ready()) return;
        setNote("Đang quét thư mục nhạc...", 20000);
        pool.submit(new Runnable() {
            public void run() {
                try {
                    musicDir.mkdirs();
                    List<File> files = new ArrayList<File>();
                    scan(musicDir, 0, files);
                    Collections.sort(files);
                    String[] toks = fold(q).split("\\s+");
                    List<Track> list = new ArrayList<Track>();
                    for (File f : files) {
                        String name = f.getName();
                        String base = name.substring(0, name.lastIndexOf('.'));
                        String key = fold(base);
                        boolean ok = true;
                        for (String t : toks) if (!t.isEmpty() && !key.contains(t)) { ok = false; break; }
                        if (!ok) continue;
                        File par = f.getParentFile();
                        String artist = par != null && !par.equals(musicDir) ? par.getName() : "Nhạc trong máy";
                        list.add(new Track(base, artist, "", f.getAbsolutePath(), "", 0L));
                    }
                    if (list.isEmpty()) {
                        setNote(files.isEmpty() ? "Thư mục nhạc trống: bỏ file mp3/wav/m4a/flac vào " + musicDir.getPath()
                                : "Không có bài nào khớp \"" + q + "\"", 9000);
                        return;
                    }
                    setNote("", 0);
                    playList(list);
                } catch (Exception e) {
                    setNote("Không đọc được thư mục nhạc", 6000);
                }
            }
        });
    }

    private boolean ready() {
        if (dir == null) { setNote("Chỉ phát được trong game trên Windows", 6000); return false; }
        return true;
    }

    private static void scan(File d, int depth, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null || out.size() >= 500) return;
        for (File f : fs) {
            if (f.isDirectory()) { if (depth < 3) scan(f, depth + 1, out); continue; }
            String n = f.getName().toLowerCase();
            for (String e : LOCAL_EXT) if (n.endsWith(e)) { out.add(f); break; }
        }
    }

    /** Bỏ dấu + chữ thường để lọc tên file tiếng Việt dễ hơn. */
    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase().trim();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static List<Track> parseOnline(String json) {
        List<Track> list = new ArrayList<Track>();
        if (json == null) return list;
        JsonArray arr = new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("results");
        if (arr == null) return list;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String url = str(o, "url");
            if (url.isEmpty()) continue;
            String ft = str(o, "filetype").toLowerCase();
            if (!ft.isEmpty() && !ONLINE_TYPES.contains(ft)) continue; // Windows không phát được ogg...
            String title = str(o, "title"), who = str(o, "creator");
            if (title.isEmpty()) title = "Không tên";
            long d = 0;
            try { JsonElement de = o.get("duration"); if (de != null && !de.isJsonNull()) d = de.getAsLong(); } catch (Exception ignored) { }
            list.add(new Track(title, who.isEmpty() ? "Nhạc miễn phí" : who, who, url, str(o, "thumbnail"), d));
            if (list.size() >= 15) break;
        }
        return list;
    }

    // ---------------------------------------------------------------- phát

    private synchronized void playList(List<Track> list) {
        queue.clear();
        queue.addAll(list);
        playAt(0);
    }

    private synchronized void playAt(int i) {
        if (i < 0 || i >= queue.size()) { active = false; playing = false; return; }
        idx = i;
        cur = queue.get(i);
        gen++;
        active = true; playing = true;
        pos = 0; dur = cur.dur; posNanos = System.nanoTime();
        if (!ensureStarted()) { active = false; playing = false; setNote("Không khởi động được trình phát", 6000); return; }
        send("open:" + gen + "|" + cur.target);
    }

    /** Hết bài: sang bài kế tiếp, hết danh sách thì thôi (trả lại cho nhạc đang phát của Windows). */
    private synchronized void advance() {
        if (idx + 1 < queue.size()) playAt(idx + 1);
        else { active = false; playing = false; cur = null; }
    }

    private synchronized boolean ensureStarted() {
        if (proc != null) {
            try { proc.exitValue(); proc = null; out = null; } catch (IllegalThreadStateException alive) { return true; }
        }
        if (dir == null) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-File", new File(dir, "music_play.ps1").getAbsolutePath(), "-Dir", dir.getAbsolutePath());
            final Process p = pb.start();
            proc = p;
            out = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
            final InputStream err = p.getErrorStream();
            Thread d = new Thread(new Runnable() {
                public void run() { try { while (err.read() != -1) { } } catch (IOException ignored) { } }
            });
            d.setDaemon(true);
            d.start();
            Thread t = new Thread(new Runnable() { public void run() { readLoop(p); } }, "zhwii-music-play");
            t.setDaemon(true);
            t.start();
            out.write("vol:" + vol + "\n");
            out.flush();
            return true;
        } catch (IOException e) {
            proc = null; out = null;
            return false;
        }
    }

    private synchronized void send(String c) {
        try {
            Writer w = out;
            if (w != null) { w.write(c + "\n"); w.flush(); }
        } catch (IOException ignored) { }
    }

    private void readLoop(Process p) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) {
                try { onStatus(new JsonParser().parse(l).getAsJsonObject()); } catch (Exception ignored) { }
            }
        } catch (IOException ignored) { }
    }

    private synchronized void onStatus(JsonObject o) {
        if (o.get("gen").getAsInt() != gen || !active) return; // trạng thái của bài đã bị bỏ qua
        String st = str(o, "state");
        long p = o.get("pos").getAsLong(), d = o.get("dur").getAsLong();
        if ("playing".equals(st) || "paused".equals(st)) {
            if (System.nanoTime() < holdUntil) return;
            playing = "playing".equals(st);
            pos = p; posNanos = System.nanoTime();
            if (d > 0) dur = d;
        } else if ("ended".equals(st)) {
            advance();
        } else if ("failed".equals(st)) {
            setNote("Không phát được: " + (cur == null ? "" : cur.title), 6000);
            advance();
        }
    }
}
