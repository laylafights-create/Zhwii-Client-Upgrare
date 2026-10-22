package dev.lunarlite.gui;

import dev.lunarlite.ProfileManager;
import dev.lunarlite.module.ThemeModule;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Nhập / xuất cài đặt:
 *  - danh sách hồ sơ (.cfg) trong config/zhwii-profiles, chọn rồi bấm "Nhập" (hoặc bấm đúp);
 *  - "Dán từ clipboard": nhập từ nội dung đã sao chép;
 *  - "Xuất file mới": lưu cài đặt hiện tại thành hồ sơ mới;
 *  - "Sao chép mã": chép cài đặt hiện tại vào clipboard để gửi cho người khác.
 * Trước mỗi lần nhập, cài đặt hiện tại được tự sao lưu vào before-import.cfg.
 */
public class ProfileScreen extends GuiScreen {
    private static final int VW = 340, VH = 258, PAD = 14;
    private static final int LIST_Y = 62, ROW_H = 24, VISIBLE = 4, LIST_H = ROW_H * VISIBLE;
    private static final int ST_Y = 164, BH = 20;
    private static final int LIST_W = VW - 2 * PAD;

    private static final int A_IMPORT = 0, A_CLIP = 1, A_EXPORT = 2, A_COPY = 3, A_FOLDER = 4, A_BACK = 5;
    private static final String[] LABELS = {
        "Nhập file đã chọn", "Dán từ clipboard", "Xuất file mới", "Sao chép mã", "Mở thư mục", "« Quay lại"
    };
    private static final int[] BX = { 14, 173, 14, 120, 226, 14 };
    private static final int[] BY = { 178, 178, 204, 204, 204, 230 };
    private static final int[] BW = { 153, 153, 100, 100, 100, 312 };

    private final List<File> files = new ArrayList<File>();
    private final float[] hov = new float[LABELS.length];
    private int sel = -1;
    private int first;
    private String status = "";
    private int statusCol = Skin.DIM;
    private long lastClick;
    private int lastClickIdx = -1;

    @Override
    public void initGui() {
        refresh(null);
    }

    // ------------------------------------------------------------------ danh sách

    private void refresh(File keep) {
        files.clear();
        files.addAll(ProfileManager.list());
        sel = -1;
        if (keep != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).equals(keep)) {
                    sel = i;
                    break;
                }
            }
        }
        clampFirst();
        if (sel >= 0) {
            if (sel < first) first = sel;
            if (sel >= first + VISIBLE) first = sel - VISIBLE + 1;
        }
    }

    private void clampFirst() {
        first = Math.max(0, Math.min(Math.max(0, files.size() - VISIBLE), first));
    }

    private static String shortName(File f) {
        String n = f.getName();
        return n.toLowerCase(Locale.ROOT).endsWith(".cfg") ? n.substring(0, n.length() - 4) : n;
    }

    private void ok(String s) {
        status = s;
        statusCol = Skin.GREEN;
    }

    private void err(String s) {
        status = s == null ? "Có lỗi xảy ra." : s;
        statusCol = Skin.RED;
    }

    private void click() {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
    }

    // ------------------------------------------------------------------ hành động

    private void doImport() {
        if (sel < 0 || sel >= files.size()) {
            err("Hãy chọn một hồ sơ trong danh sách.");
            return;
        }
        File f = files.get(sel);
        try {
            int n = ProfileManager.importFile(f);
            ok("Đã nhập " + n + " tính năng từ " + shortName(f) + ".");
        } catch (IOException e) {
            err(e.getMessage());
        }
        refresh(f);
    }

    private void act(int id) {
        click();
        switch (id) {
            case A_IMPORT:
                doImport();
                break;
            case A_CLIP:
                try {
                    int n = ProfileManager.importText(getClipboardString());
                    ok("Đã nhập " + n + " tính năng từ clipboard.");
                } catch (IOException e) {
                    err(e.getMessage());
                }
                refresh(null);
                break;
            case A_EXPORT:
                try {
                    File f = ProfileManager.exportNew();
                    refresh(f);
                    ok("Đã xuất: " + f.getName());
                } catch (IOException e) {
                    err(e.getMessage());
                }
                break;
            case A_COPY:
                try {
                    String t = ProfileManager.exportText();
                    setClipboardString(t);
                    ok("Đã sao chép cài đặt (" + ((t.length() + 512) / 1024) + " KB) vào clipboard.");
                } catch (IOException e) {
                    err(e.getMessage());
                }
                break;
            case A_FOLDER:
                try {
                    if (!java.awt.Desktop.isDesktopSupported()) throw new IOException("unsupported");
                    java.awt.Desktop.getDesktop().open(ProfileManager.dir());
                    ok("Đã mở thư mục hồ sơ.");
                } catch (Throwable t) {
                    err("Không mở được. Thư mục: config/zhwii-profiles");
                }
                break;
            case A_BACK:
                back();
                break;
            default:
                break;
        }
    }

    private void back() {
        mc.displayGuiScreen(new MenuScreen());
    }

    // ------------------------------------------------------------------ khung ảo <-> màn hình

    private float ui() {
        return Math.min(1f, Math.min((width - 8f) / VW, (height - 8f) / VH));
    }

    private int vx(int mx) {
        return Math.round((mx - width / 2f) / ui() + VW / 2f);
    }

    private int vy(int my) {
        return Math.round((my - height / 2f) / ui() + VH / 2f);
    }

    // ------------------------------------------------------------------ vẽ

    private void drawScaled(String s, float x, float y, float sc, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(sc, sc, 1f);
        fontRendererObj.drawStringWithShadow(s, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private void drawCentered(String s, float cx, float y, int color) {
        fontRendererObj.drawStringWithShadow(s, cx - fontRendererObj.getStringWidth(s) / 2f, y, color);
    }

    @Override
    public void drawScreen(int rawX, int rawY, float partialTicks) {
        ThemeModule.apply();
        drawGradientRect(0, 0, width, height, 0xB00A0A12, 0xCC0A0A12);
        int mx = vx(rawX), my = vy(rawY);

        GlStateManager.pushMatrix();
        float u = ui();
        GlStateManager.translate(width / 2f, height / 2f, 0f);
        GlStateManager.scale(u, u, 1f);
        GlStateManager.translate(-VW / 2f, -VH / 2f, 0f);
        drawPanel(mx, my);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private int rowAt(int mx, int my) {
        for (int k = 0; k < VISIBLE; k++) {
            int i = first + k;
            if (i >= files.size()) break;
            if (Draw.in(mx, my, PAD + 3, LIST_Y + 2 + k * ROW_H, LIST_W - 14, ROW_H - 4)) return i;
        }
        return -1;
    }

    private void drawPanel(int mx, int my) {
        FontRenderer fr = fontRendererObj;
        int acc = Draw.ACCENT;

        Skin.shadow(VW, VH);
        Skin.box(0, 0, VW, VH, 7, 0xFF2C2C3C, Skin.BG);
        Draw.fadeLine(18, 0, VW - 36, acc & 0xFFFFFF, 0xCC);
        Draw.fadeLine(18, 1, VW - 36, acc & 0xFFFFFF, 0x60);

        // ---- tiêu đề
        Skin.tile(PAD, 10, 30, "gear", acc);
        drawScaled("Nhập / Xuất cài đặt", PAD + 38, 13, 1.3f, Skin.TEXT);
        fr.drawStringWithShadow("config/zhwii-profiles", PAD + 38, 28, Skin.DIM);
        String hint = "Chọn hồ sơ rồi bấm Nhập. Cài đặt hiện tại được tự sao lưu vào " + ProfileManager.BACKUP + ".";
        fr.drawStringWithShadow(fr.trimStringToWidth(hint, LIST_W), PAD, 48, Skin.DIM);

        // ---- danh sách
        Skin.box(PAD, LIST_Y, LIST_W, LIST_H, 5, 0xFF1C1C28, 0xFF0F0F15);
        if (files.isEmpty()) {
            drawCentered("Chưa có hồ sơ nào.", VW / 2f, LIST_Y + 30, Skin.SOFT);
            drawCentered(fr.trimStringToWidth("Bấm 'Xuất file mới' hoặc chép file .cfg vào thư mục.", LIST_W - 16),
                    VW / 2f, LIST_Y + 44, Skin.DIM);
        }
        SimpleDateFormat df = new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT);
        int hover = rowAt(mx, my);
        for (int k = 0; k < VISIBLE; k++) {
            int i = first + k;
            if (i >= files.size()) break;
            File f = files.get(i);
            int rx = PAD + 3, ry = LIST_Y + 2 + k * ROW_H, rw = LIST_W - 14, rh = ROW_H - 4;
            boolean on = i == sel;
            if (on) Skin.box(rx, ry, rw, rh, 4, acc, Draw.lerp(Skin.RAISED, acc, 0.2f));
            else Draw.rrect(rx, ry, rx + rw, ry + rh, 4, i == hover ? Skin.HOVER : Skin.RAISED);

            String right = ((f.length() + 512) / 1024) + " KB · " + df.format(new Date(f.lastModified()));
            int rwid = fr.getStringWidth(right);
            fr.drawStringWithShadow(right, rx + rw - 8 - rwid, ry + (rh - 8) / 2, Skin.DIM);
            String name = shortName(f);
            if (ProfileManager.BACKUP.equals(f.getName())) name = name + " (sao lưu)";
            fr.drawStringWithShadow(fr.trimStringToWidth(name, rw - rwid - 24), rx + 8, ry + (rh - 8) / 2,
                    on ? Skin.TEXT : Skin.SOFT);
        }
        if (files.size() > VISIBLE) { // thanh cuộn
            int tx = PAD + LIST_W - 8, ty0 = LIST_Y + 3, th = LIST_H - 6;
            Draw.rrect(tx, ty0, tx + 3, ty0 + th, 1, 0x55000000);
            int bh = Math.max(12, th * VISIBLE / files.size());
            int by = ty0 + Math.round((th - bh) * (first / (float) (files.size() - VISIBLE)));
            Draw.rrect(tx, by, tx + 3, by + bh, 1, Draw.ACCENT_LIGHT);
        }

        // ---- trạng thái
        if (status.length() > 0) fr.drawStringWithShadow(fr.trimStringToWidth(status, LIST_W), PAD, ST_Y, statusCol);

        // ---- nút
        for (int id = 0; id < LABELS.length; id++) {
            boolean over = Draw.in(mx, my, BX[id], BY[id], BW[id], BH);
            hov[id] += ((over ? 1f : 0f) - hov[id]) * 0.35f;
            if (Math.abs((over ? 1f : 0f) - hov[id]) < 0.01f) hov[id] = over ? 1f : 0f;
            Skin.button(fr, BX[id], BY[id], BW[id], BH, LABELS[id], hov[id],
                    id == A_IMPORT ? Skin.PRIMARY : Skin.NORMAL);
        }
    }

    // ------------------------------------------------------------------ nhập liệu

    @Override
    protected void mouseClicked(int rawX, int rawY, int btn) throws IOException {
        if (btn != 0) return;
        int mx = vx(rawX), my = vy(rawY);
        for (int id = 0; id < LABELS.length; id++) {
            if (Draw.in(mx, my, BX[id], BY[id], BW[id], BH)) {
                act(id);
                return;
            }
        }
        int i = rowAt(mx, my);
        if (i >= 0) {
            long now = System.currentTimeMillis();
            boolean dbl = i == lastClickIdx && now - lastClick < 350L;
            sel = i;
            lastClickIdx = i;
            lastClick = now;
            click();
            if (dbl) doImport();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            first += d > 0 ? -1 : 1;
            clampFirst();
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            back();
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            doImport();
        } else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            if (files.isEmpty()) return;
            int n = sel < 0 ? 0 : sel + (key == Keyboard.KEY_UP ? -1 : 1);
            sel = Math.max(0, Math.min(files.size() - 1, n));
            if (sel < first) first = sel;
            if (sel >= first + VISIBLE) first = sel - VISIBLE + 1;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
