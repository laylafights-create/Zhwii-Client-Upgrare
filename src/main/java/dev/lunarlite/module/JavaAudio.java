package dev.lunarlite.module;

import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/**
 * Trình phát nhạc bằng Java thuần cho Android (PojavLauncher), Linux và macOS, nơi không có MediaPlayer của Windows.
 * Giải mã MP3 bằng JLayer và WAV bằng javax.sound, rồi phát qua OpenAL của chính Minecraft
 * (nếu OpenAL không dùng được thì chuyển sang Java Sound).
 *
 * Nói chuyện theo cùng giao thức với music_play.ps1: lệnh vào qua {@link #send}, trạng thái trả về qua {@link Listener}:
 *   open:&lt;gen&gt;|&lt;đường dẫn hoặc link&gt;   pause   resume   stop   vol:&lt;0-100&gt;   quit
 *   {"gen":1,"state":"playing|paused|ended|failed|stopped","pos":ms,"dur":ms}
 */
final class JavaAudio {
    interface Listener { void onStatus(JsonObject o); }

    private final Listener listener;
    private Worker worker;
    private volatile int vol = 50;

    JavaAudio(Listener l) { listener = l; }

    synchronized void send(String c) {
        c = c.trim();
        if (c.startsWith("open:")) {
            int bar = c.indexOf('|');
            if (bar < 0) return;
            int gen;
            try { gen = Integer.parseInt(c.substring(5, bar)); } catch (NumberFormatException e) { return; }
            cancel();
            worker = new Worker(gen, c.substring(bar + 1));
            worker.start();
        } else if (c.equals("pause")) {
            if (worker != null) worker.setPaused(true);
        } else if (c.equals("resume")) {
            if (worker != null) worker.setPaused(false);
        } else if (c.equals("stop") || c.equals("quit")) {
            cancel();
        } else if (c.startsWith("vol:")) {
            try { vol = Math.max(0, Math.min(100, Integer.parseInt(c.substring(4).trim()))); } catch (NumberFormatException e) { return; }
            if (worker != null) worker.applyVolume();
        }
    }

    synchronized void shutdown() { cancel(); }

    private void cancel() {
        if (worker != null) { worker.cancel(); worker = null; }
    }

    // ================================================================ luồng phát

    private final class Worker extends Thread {
        private final int gen;
        private final String target;
        private volatile boolean cancelled, paused;
        private volatile Sink sink;
        private volatile InputStream cur;
        private long dur;
        private long lastStatus;

        Worker(int gen, String target) {
            super("zhwii-music-java");
            setDaemon(true);
            this.gen = gen;
            this.target = target;
        }

        void cancel() {
            cancelled = true;
            interrupt();
            InputStream c = cur;
            if (c != null) { try { c.close(); } catch (IOException ignored) { } } // mở khoá luồng đang chờ mạng
        }

        void setPaused(boolean p) {
            paused = p;
            Sink s = sink;
            if (s != null) { if (p) s.pause(); else s.resume(); }
        }

        void applyVolume() {
            Sink s = sink;
            if (s != null) s.volume(vol / 100f);
        }

        private void status(String state) {
            if (cancelled) return;
            JsonObject o = new JsonObject();
            Sink s = sink;
            long pos = s == null ? 0L : s.playedMs();
            if (dur > 0 && pos > dur) pos = dur;
            o.addProperty("gen", gen);
            o.addProperty("state", state);
            o.addProperty("pos", pos);
            o.addProperty("dur", dur);
            listener.onStatus(o);
            lastStatus = System.nanoTime();
        }

        private void tick() {
            if (System.nanoTime() - lastStatus >= 250000000L) status(paused ? "paused" : "playing");
        }

        public void run() {
            try {
                status("playing");
                InputStream raw = open(target);
                BufferedInputStream in = new BufferedInputStream(raw, 1 << 16);
                cur = in;
                try {
                    in.mark(16);
                    byte[] head = new byte[4];
                    int n = 0;
                    while (n < 4) { int r = in.read(head, n, 4 - n); if (r < 0) break; n += r; }
                    in.reset();
                    boolean wav = n == 4 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F';
                    if (wav) playWav(in); else playMp3(in);
                    while (!cancelled && sink != null && !sink.finished()) sleepTick();
                    if (!cancelled) status("ended");
                } finally {
                    try { in.close(); } catch (IOException ignored) { }
                }
            } catch (InterruptedException e) {
                // bị huỷ: không báo gì
            } catch (Throwable t) {
                if (!cancelled) status("failed");
            } finally {
                Sink s = sink;
                sink = null;
                if (s != null) s.close();
            }
        }

        private void sleepTick() throws InterruptedException {
            Thread.sleep(8);
            tick();
        }

        // ---------------------------------------------------------------- giải mã

        private void playMp3(InputStream in) throws Exception {
            if (!target.startsWith("http")) dur = scanMp3(target);
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            byte[] buf = new byte[1 << 17];
            int len = 0, ch = 2, rate = 44100;
            int bad = 0;
            Header h;
            while (!cancelled && (h = bs.readFrame()) != null) {
                try {
                    SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                    ch = sb.getChannelCount();
                    rate = sb.getSampleFrequency();
                    short[] s = sb.getBuffer();
                    int cnt = sb.getBufferLength();
                    int chunk = rate * ch * 2 / 5; // ~200ms mỗi lần đẩy vào bộ đệm
                    for (int i = 0; i < cnt; i++) {
                        buf[len++] = (byte) (s[i] & 0xFF);
                        buf[len++] = (byte) ((s[i] >> 8) & 0xFF);
                    }
                    bad = 0;
                    if (len >= chunk || len > buf.length - 4608 * 2) { emit(buf, len, ch, rate); len = 0; }
                } catch (JavaLayerException e) {
                    if (++bad > 200) throw e; // file hỏng nặng
                } finally {
                    bs.closeFrame();
                }
            }
            if (len > 0 && !cancelled) emit(buf, len, ch, rate);
            if (sink == null && !cancelled) throw new IOException("no audio");
        }

        /** Đo độ dài file mp3 cục bộ bằng cách đếm frame (nhanh, không giải mã). */
        private long scanMp3(String path) {
            try {
                InputStream in = new BufferedInputStream(new FileInputStream(path), 1 << 16);
                try {
                    Bitstream bs = new Bitstream(in);
                    float ms = 0f;
                    Header h;
                    while ((h = bs.readFrame()) != null) { ms += h.ms_per_frame(); bs.closeFrame(); }
                    return (long) ms;
                } finally { in.close(); }
            } catch (Exception e) {
                return 0L;
            }
        }

        private void playWav(InputStream in) throws Exception {
            AudioInputStream a = AudioSystem.getAudioInputStream(in);
            AudioFormat f = a.getFormat();
            if (f.getChannels() > 2) throw new IOException("channels");
            if (f.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || f.getSampleSizeInBits() != 16 || f.isBigEndian()) {
                AudioFormat t = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, f.getChannels(),
                        f.getChannels() * 2, f.getSampleRate(), false);
                a = AudioSystem.getAudioInputStream(t, a);
                f = t;
            }
            long frames = a.getFrameLength();
            if (frames > 0 && f.getFrameRate() > 0) dur = (long) (frames * 1000.0 / f.getFrameRate());
            int ch = f.getChannels(), rate = (int) f.getSampleRate();
            int chunk = Math.max(4, rate * ch * 2 / 5);
            chunk -= chunk % (ch * 2);
            byte[] buf = new byte[chunk];
            while (!cancelled) {
                int n = 0;
                while (n < chunk) { int r = a.read(buf, n, chunk - n); if (r < 0) break; n += r; }
                if (n <= 0) break;
                n -= n % (ch * 2);
                if (n > 0) emit(buf, n, ch, rate);
                if (n < chunk) break;
            }
        }

        private void emit(byte[] pcm, int len, int ch, int rate) throws InterruptedException {
            if (sink == null) {
                sink = makeSink(ch, rate);
                sink.volume(vol / 100f);
                if (paused) sink.pause();
            }
            while (!cancelled && !sink.canWrite(len)) sleepTick();
            if (cancelled) throw new InterruptedException();
            sink.write(pcm, len);
            tick();
        }
    }

    // ================================================================ đầu ra âm thanh

    private interface Sink {
        boolean canWrite(int len);
        void write(byte[] pcm, int len);
        long playedMs();
        boolean finished();
        void pause();
        void resume();
        void volume(float v);
        void close();
    }

    private static Sink makeSink(int ch, int rate) {
        try {
            if (AL.isCreated()) return new AlSink(ch, rate);
        } catch (Throwable ignored) { }
        return new LineSink(ch, rate);
    }

    /** Phát qua OpenAL của Minecraft: hàng đợi 4 buffer, mỗi buffer ~200ms. */
    private static final class AlSink implements Sink {
        private static final int NBUF = 4;
        private final int src, rate, format;
        private final ArrayDeque<Integer> free = new ArrayDeque<Integer>();
        private final Map<Integer, Integer> frames = new HashMap<Integer, Integer>();
        private final int[] all = new int[NBUF];
        private long playedFrames;
        private volatile boolean paused;

        AlSink(int ch, int rate) {
            this.rate = rate;
            format = ch == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            src = AL10.alGenSources();
            for (int i = 0; i < NBUF; i++) { all[i] = AL10.alGenBuffers(); free.add(all[i]); }
            AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(src, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE);
        }

        private synchronized void reclaim() {
            int n = AL10.alGetSourcei(src, AL10.AL_BUFFERS_PROCESSED);
            for (int i = 0; i < n; i++) {
                int b = AL10.alSourceUnqueueBuffers(src);
                Integer fr = frames.remove(b);
                if (fr != null) playedFrames += fr;
                free.add(b);
            }
        }

        public synchronized boolean canWrite(int len) { reclaim(); return !free.isEmpty(); }

        public synchronized void write(byte[] pcm, int len) {
            reclaim();
            Integer b = free.poll();
            if (b == null) return;
            ByteBuffer bb = BufferUtils.createByteBuffer(len);
            bb.put(pcm, 0, len);
            bb.flip();
            AL10.alBufferData(b, format, bb, rate);
            frames.put(b, len / (format == AL10.AL_FORMAT_MONO16 ? 2 : 4));
            AL10.alSourceQueueBuffers(src, b);
            if (!paused && AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(src); // chạy lại sau khi hết đệm
        }

        public synchronized long playedMs() {
            reclaim();
            long f = playedFrames + AL10.alGetSourcei(src, AL11.AL_SAMPLE_OFFSET);
            return f * 1000L / rate;
        }

        public synchronized boolean finished() {
            reclaim();
            return free.size() == NBUF;
        }

        public synchronized void pause() { paused = true; AL10.alSourcePause(src); }

        public synchronized void resume() {
            paused = false;
            if (free.size() < NBUF) AL10.alSourcePlay(src);
        }

        public synchronized void volume(float v) { AL10.alSourcef(src, AL10.AL_GAIN, Math.max(0f, Math.min(1f, v))); }

        public synchronized void close() {
            try {
                AL10.alSourceStop(src);
                AL10.alSourcei(src, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(src);
                for (int b : all) AL10.alDeleteBuffers(b);
            } catch (Throwable ignored) { }
        }
    }

    /** Dự phòng khi OpenAL chưa khởi tạo: SourceDataLine của Java Sound. */
    private static final class LineSink implements Sink {
        private final SourceDataLine line;
        private volatile boolean paused;

        LineSink(int ch, int rate) {
            try {
                AudioFormat fmt = new AudioFormat(rate, 16, ch, true, false);
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, Math.max(4096, rate * ch * 2 * 4 / 5));
                line.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean canWrite(int len) { return line.available() >= len; }
        public void write(byte[] pcm, int len) { line.write(pcm, 0, len); }
        public long playedMs() { return line.getMicrosecondPosition() / 1000L; }
        public boolean finished() { return line.available() >= line.getBufferSize(); }
        public void pause() { paused = true; line.stop(); }
        public void resume() { paused = false; line.start(); }

        public void volume(float v) {
            try {
                FloatControl c = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float db = v <= 0.0001f ? c.getMinimum() : (float) (20.0 * Math.log10(v));
                c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
            } catch (Exception ignored) { }
        }

        public void close() { try { line.stop(); line.close(); } catch (Exception ignored) { } }
    }

    // ================================================================ mở nguồn

    private static InputStream open(String target) throws IOException {
        if (!target.startsWith("http://") && !target.startsWith("https://")) return new FileInputStream(new File(target));
        String url = target;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "ZhwiiClient/2.0");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect");
                url = new URL(new URL(url), loc).toString(); // hỗ trợ cả chuyển hướng http -> https
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code);
            return c.getInputStream();
        }
        throw new IOException("too many redirects");
    }
}
