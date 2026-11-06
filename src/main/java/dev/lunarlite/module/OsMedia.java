package dev.lunarlite.module;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Đọc bài đang phát của hệ điều hành không phải Windows:
 *  - Linux: qua playerctl (MPRIS: Spotify, Firefox, Chrome, VLC, mpv...). Cần cài playerctl.
 *  - macOS: qua osascript, hỗ trợ ứng dụng Spotify và Music.
 * Kết quả trả về cùng định dạng JSON với music_info.ps1 của Windows nên phần còn lại của module dùng chung.
 * Android (PojavLauncher...) không có media session cho tiến trình game, nên {@link #supported()} trả về false.
 */
final class OsMedia {
    static final int NONE = 0, LINUX = 1, MAC = 2;

    private static final String MAC_SCRIPT =
            "set sep to (ASCII character 9)\n"
            + "set r to \"\"\n"
            + "if application \"Spotify\" is running then\n"
            + "  tell application \"Spotify\"\n"
            + "    if player state is not stopped then\n"
            + "      set st to \"paused\"\n"
            + "      if player state is playing then set st to \"playing\"\n"
            + "      set r to \"Spotify\" & sep & st & sep & (round ((player position) * 1000)) & sep & (duration of current track) & sep & (artist of current track) & sep & (name of current track) & sep & (album of current track) & sep & (artwork url of current track) & sep & (sound volume)\n"
            + "    end if\n"
            + "  end tell\n"
            + "end if\n"
            + "if r is \"\" and application \"Music\" is running then\n"
            + "  tell application \"Music\"\n"
            + "    if player state is not stopped then\n"
            + "      set st to \"paused\"\n"
            + "      if player state is playing then set st to \"playing\"\n"
            + "      set r to \"Music\" & sep & st & sep & (round ((player position) * 1000)) & sep & (round ((duration of current track) * 1000)) & sep & (artist of current track) & sep & (name of current track) & sep & (album of current track) & sep & \"\" & sep & (sound volume)\n"
            + "    end if\n"
            + "  end tell\n"
            + "end if\n"
            + "return r";

    private final int os;
    private final File dir;
    private volatile String macApp = "Spotify";
    private String coverKey = "", coverPath = "";
    private int coverN;

    OsMedia(File dir) {
        this.dir = dir;
        String n = System.getProperty("os.name", "").toLowerCase();
        if (n.contains("mac") || n.contains("darwin")) os = MAC;
        else if (n.contains("linux") && !isAndroid() && exists("playerctl")) os = LINUX;
        else os = NONE;
    }

    boolean supported() { return os != NONE; }

    /** Android / PojavLauncher: os.name vẫn là "Linux" nên phải nhận biết bằng thuộc tính khác. */
    static boolean isAndroid() {
        String vm = System.getProperty("java.vm.vendor", "") + System.getProperty("java.vendor", "");
        return System.getenv("ANDROID_ROOT") != null || System.getenv("POJAV_RENDERER") != null
                || System.getenv("POJAV_HOME") != null || System.getProperty("pojav.path.minecraft") != null
                || vm.toLowerCase().contains("android") || new File("/system/build.prop").exists();
    }

    static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    private static boolean exists(String bin) {
        try {
            Process p = new ProcessBuilder(bin, "--version").redirectErrorStream(true).start();
            p.getOutputStream().close();
            drain(p.getInputStream());
            if (!p.waitFor(3, TimeUnit.SECONDS)) { p.destroy(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- lấy trạng thái

    /** Trả về JSON cùng định dạng music_info.ps1: {ok,title,artist,album,playing,pos,dur,vol,thumb}. */
    JsonObject poll() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        try {
            String[] f = os == LINUX ? pollLinux() : os == MAC ? pollMac() : null;
            if (f == null) return o;
            boolean playing = f[1].equalsIgnoreCase("playing");
            long pos = num(f[2]), dur = num(f[3]);
            if (os == LINUX) { pos /= 1000L; dur /= 1000L; } // MPRIS dùng micro giây
            if (dur > 0 && pos > dur) pos = dur;
            if (pos < 0) pos = 0;
            int vol = (int) Math.round(Double.parseDouble(f[8].trim().isEmpty() ? "0" : f[8].trim()) * (os == LINUX ? 100.0 : 1.0));
            if (os == MAC) macApp = f[0];
            o.addProperty("ok", true);
            o.addProperty("title", f[5]);
            o.addProperty("artist", f[4]);
            o.addProperty("album", f[6]);
            o.addProperty("playing", playing);
            o.addProperty("pos", pos);
            o.addProperty("dur", dur);
            o.addProperty("vol", Math.max(0, Math.min(100, vol)));
            o.addProperty("thumb", cover(f[5] + "|" + f[4] + "|" + dur, f[7].trim()));
        } catch (Exception ignored) {
            o.addProperty("ok", false);
        }
        return o;
    }

    private static long num(String s) {
        s = s.trim();
        if (s.isEmpty()) return 0L;
        try { return (long) Double.parseDouble(s); } catch (NumberFormatException e) { return 0L; }
    }

    /** Thứ tự cột: nguồn, trạng thái, vị trí, độ dài, nghệ sĩ, tên bài, album, link ảnh bìa, âm lượng. */
    private String[] pollLinux() throws IOException, InterruptedException {
        String fmt = "Linux\t{{status}}\t{{position}}\t{{mpris:length}}\t{{artist}}\t{{title}}\t{{album}}\t{{mpris:artUrl}}\t{{volume}}";
        String out = run(5, "playerctl", "metadata", "--format", fmt);
        return parseRow(out);
    }

    private String[] pollMac() throws IOException, InterruptedException {
        String out = run(5, "osascript", "-e", MAC_SCRIPT);
        return parseRow(out);
    }

    private static String[] parseRow(String out) {
        if (out == null) return null;
        out = out.replace("\r", "").trim();
        if (out.isEmpty()) return null;
        String[] f = out.split("\t", -1);
        if (f.length < 9) return null;
        if (f[1].equalsIgnoreCase("stopped")) return null;
        return f;
    }

    /** Ảnh bìa: file:// dùng thẳng, http(s):// tải về thư mục tạm (chỉ tải lại khi đổi bài). */
    private String cover(String key, String url) {
        if (url.isEmpty()) return "";
        if (key.equals(coverKey)) return coverPath;
        coverKey = key;
        coverPath = "";
        try {
            if (url.startsWith("file://")) {
                coverPath = new File(new java.net.URI(url)).getAbsolutePath();
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "ZhwiiClient/2.0");
                InputStream in = c.getInputStream();
                File f = new File(dir, "cover" + (++coverN) + ".img");
                FileOutputStream fo = new FileOutputStream(f);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                } finally { fo.close(); in.close(); }
                coverPath = f.getAbsolutePath();
            }
        } catch (Exception ignored) { coverPath = ""; }
        return coverPath;
    }

    // ---------------------------------------------------------------- điều khiển

    /** Nhận lệnh giống music_cmd.ps1: play | next | prev | vol:N. */
    void command(String c) {
        try {
            c = c.trim();
            if (os == LINUX) {
                if (c.equals("play")) run(3, "playerctl", "play-pause");
                else if (c.equals("next")) run(3, "playerctl", "next");
                else if (c.equals("prev")) run(3, "playerctl", "previous");
                else if (c.startsWith("vol:")) run(3, "playerctl", "volume", String.format(java.util.Locale.ROOT, "%.2f", clamp(c) / 100.0));
            } else if (os == MAC) {
                String app = macApp;
                String act = null;
                if (c.equals("play")) act = "playpause";
                else if (c.equals("next")) act = "next track";
                else if (c.equals("prev")) act = "previous track";
                else if (c.startsWith("vol:")) act = "set sound volume to " + clamp(c);
                if (act != null) run(3, "osascript", "-e", "tell application \"" + app + "\" to " + act);
            }
        } catch (Exception ignored) { }
    }

    private static int clamp(String c) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(c.substring(4).trim()))); } catch (Exception e) { return 0; }
    }

    // ---------------------------------------------------------------- tiện ích

    /** Chạy lệnh, trả về stdout nếu thoát mã 0, ngược lại null. Có timeout để không treo luồng. */
    private static String run(int timeoutSec, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(Arrays.asList(cmd));
        Process p = pb.start();
        p.getOutputStream().close();
        drainAsync(p.getErrorStream());
        final ByteArrayOutputStream bo = new ByteArrayOutputStream();
        final InputStream in = p.getInputStream();
        Thread t = new Thread(new Runnable() {
            public void run() {
                try { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); } catch (IOException ignored) { }
            }
        });
        t.setDaemon(true);
        t.start();
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
        t.join(500);
        if (p.exitValue() != 0) return null;
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void drain(InputStream in) {
        try { BufferedReader r = new BufferedReader(new InputStreamReader(in)); while (r.readLine() != null) { } } catch (IOException ignored) { }
    }

    private static void drainAsync(final InputStream in) {
        Thread t = new Thread(new Runnable() {
            public void run() { try { while (in.read() != -1) { } } catch (IOException ignored) { } }
        });
        t.setDaemon(true);
        t.start();
    }
}
