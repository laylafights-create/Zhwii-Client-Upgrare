package dev.lunarlite;

import dev.lunarlite.module.Module;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

/**
 * Nhập / xuất cài đặt. Hồ sơ là file .cfg cùng định dạng với file config chính, nằm trong
 * {@code config/zhwii-profiles/}. Trước mỗi lần nhập, cài đặt hiện tại được tự sao lưu vào
 * {@link #BACKUP} (nhập lại file này để hoàn tác).
 *
 * Khi nhập, chỉ các tính năng có mặt trong file mới bị ghi đè; tính năng không có trong file giữ nguyên.
 */
public final class ProfileManager {
    public static final String BACKUP = "before-import.cfg";
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ProfileManager() {}

    public static File dir() {
        File d = new File(LunarLite.configDir(), "zhwii-profiles");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Các file .cfg trong thư mục hồ sơ, mới sửa nhất lên đầu. */
    public static List<File> list() {
        List<File> out = new ArrayList<File>();
        File[] fs = dir().listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".cfg")) out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return out;
    }

    /** Ghi cài đặt hiện tại ra file mới có tên theo thời gian. */
    public static File exportNew() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File f = new File(dir(), "profile-" + stamp + ".cfg");
        writeTo(f);
        return f;
    }

    /** Cài đặt hiện tại dạng văn bản (để dán cho người khác). */
    public static String exportText() throws IOException {
        File tmp = new File(dir(), ".export.tmp");
        try {
            writeTo(tmp);
            return new String(Files.readAllBytes(tmp.toPath()), UTF8);
        } finally {
            tmp.delete();
        }
    }

    private static void writeTo(File f) throws IOException {
        try {
            Configuration c = new Configuration(f);
            LunarLite.fill(c);
            c.save();
        } catch (RuntimeException e) {
            throw new IOException("Không ghi được file.");
        }
        if (!f.isFile()) throw new IOException("Không ghi được file.");
    }

    /** Nhập từ một file .cfg. Trả về số tính năng đã nạp. */
    public static int importFile(File f) throws IOException {
        if (f == null || !f.isFile()) throw new IOException("Không tìm thấy file.");
        if (f.length() > MAX_BYTES) throw new IOException("File quá lớn.");
        Configuration c;
        try {
            c = new Configuration(f);
            c.load();
        } catch (RuntimeException e) {
            throw new IOException("Không đọc được file.");
        }
        if (countModules(c) == 0) throw new IOException("Đây không phải file cài đặt của Zhwii Client.");
        // c đã nằm trong bộ nhớ nên ghi đè BACKUP an toàn kể cả khi f chính là BACKUP
        writeTo(new File(dir(), BACKUP));
        return LunarLite.applyConfig(c);
    }

    /** Nhập từ văn bản (vd. dán từ clipboard). */
    public static int importText(String text) throws IOException {
        if (text == null || text.trim().length() == 0) throw new IOException("Clipboard đang trống.");
        if (text.length() > MAX_BYTES) throw new IOException("Nội dung quá lớn.");
        File tmp = new File(dir(), ".import.tmp");
        try {
            Files.write(tmp.toPath(), text.getBytes(UTF8));
            return importFile(tmp);
        } finally {
            tmp.delete();
        }
    }

    private static int countModules(Configuration c) {
        Set<String> names = c.getCategoryNames();
        int n = 0;
        for (Module m : LunarLite.MODULES) {
            if (has(names, m.id)) n++;
        }
        return n;
    }

    /** Forge có thể hạ chữ thường tên mục, nên so cả hai dạng. */
    public static boolean has(Set<String> names, String id) {
        return names.contains(id) || names.contains(id.toLowerCase(Locale.ROOT));
    }
}
