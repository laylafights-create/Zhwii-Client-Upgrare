package dev.lunarlite.gui;

import dev.lunarlite.module.Category;
import dev.lunarlite.module.Module;
import net.minecraft.client.gui.Gui;

/**
 * Biểu tượng kiểu menu Sword Art Online: nét đậm, bo tròn, vẽ bằng đường / cung / đa giác mịn trong khung 16x16
 * (không dùng ảnh) và có thêm chi tiết bên trong (tia sáng, nét phụ mờ). Dùng cho nút tròn và các hàng trong menu.
 */
public final class SaoIcons {
    private SaoIcons() {}

    // ------------------------------------------------------------------ chọn biểu tượng

    public static String cat(Category c) {
        if (c == null) return "grid";
        switch (c) {
            case HUD: return "monitor";
            case PVP: return "swords";
            case VISUAL: return "eye";
            case EFFECT: return "sparkle";
            case UTILITY: return "sliders";
            default: return "grid";
        }
    }

    public static String forModule(Module m) {
        String id = m.id;
        if ("fps".equals(id) || "ping".equals(id)) return "chart";
        if ("cps".equals(id)) return "mouse";
        if ("keystrokes".equals(id)) return "keys";
        if ("bowtrajectory".equals(id) || "aimassist".equals(id) || "enemy_detector".equals(id)
                || "targetinfo".equals(id)) return "crosshair";
        if ("combo".equals(id) || "killboard".equals(id)) return "swords";
        if ("armor".equals(id) || "hide_armor".equals(id)) return "shield";
        if ("potions".equals(id)) return "drop";
        if ("itempickup".equals(id) || "itemphysics".equals(id) || "blockoutline".equals(id)) return "cube";
        if ("serverip".equals(id) || "scoreboard".equals(id)) return "monitor";
        if ("togglesprint".equals(id) || "velocity".equals(id) || "bridge".equals(id)) return "chevron";
        if ("zoom".equals(id)) return "magnifier";
        if ("fullbright".equals(id)) return "sun";
        if ("farhorizons".equals(id)) return "mountain";
        if ("animations".equals(id) || "hotbar_animation".equals(id)) return "clock";
        if ("chatbar".equals(id)) return "bubble";
        if ("guitheme".equals(id)) return "sparkle";
        return cat(m.category);
    }

    // ------------------------------------------------------------------ hình cơ bản (toạ độ trong khung 16x16)

    private static final double PI = Math.PI;
    /** Vệt sáng trắng mờ (chi tiết bên trong hình đặc) và vệt tối mờ. */
    private static final int GL = 0x70FFFFFF, DK = 0x38000000;

    /** Màu phụ: cùng màu nhưng mờ hơn, dùng cho nét phụ / chi tiết. */
    private static int dim(int c, float f) {
        int a = Math.round((c >>> 24) * f);
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static void cap(int x, int y, float x0, float y0, float x1, float y1, float w, int c) {
        Draw.capsule(x + x0, y + y0, x + x1, y + y1, w, c);
    }

    private static void bar(int x, int y, float x0, float y0, float x1, float y1, float w, int c) {
        Draw.line(x + x0, y + y0, x + x1, y + y1, w, c);
    }

    private static void dot(int x, int y, float cx, float cy, float r, int c) {
        Draw.disc(x + cx, y + cy, r, c);
    }

    private static void ringAt(int x, int y, float cx, float cy, float r, float w, int c) {
        Draw.circleRing(x + cx, y + cy, r - w / 2f, r + w / 2f, c);
    }

    private static void arcAt(int x, int y, float cx, float cy, float r, double a0, double a1, float w, int c) {
        Draw.arc(x + cx, y + cy, r, a0, a1, w, c);
    }

    private static void poly(int x, int y, int c, float... p) {
        float[] q = new float[p.length];
        for (int i = 0; i < p.length; i += 2) { q[i] = x + p[i]; q[i + 1] = y + p[i + 1]; }
        Draw.poly(q, c);
    }

    /** Hình sao nhọn n cánh. */
    private static void star(int x, int y, float cx, float cy, float ro, float ri, int n, double rot, int c) {
        float[] p = new float[n * 4];
        for (int i = 0; i < n * 2; i++) {
            double a = rot + PI * i / n;
            float r = (i % 2 == 0) ? ro : ri;
            p[2 * i] = x + cx + (float) Math.cos(a) * r;
            p[2 * i + 1] = y + cy + (float) Math.sin(a) * r;
        }
        Draw.poly(p, c);
    }

    /** Một thanh kiếm: mũi (tx,ty) -> chuôi (hx,hy); có rãnh sáng, chắn tay, cán và núm. */
    private static void sword(int x, int y, float tx, float ty, float hx, float hy, int c) {
        float dx = hx - tx, dy = hy - ty;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= len; dy /= len;
        float px = -dy, py = dx;
        cap(x, y, tx, ty, hx, hy, 2.1f, c);                                            // lưỡi
        bar(x, y, tx + dx * 2f + px * 0.35f, ty + dy * 2f + py * 0.35f,
                hx - dx * 1.6f + px * 0.35f, hy - dy * 1.6f + py * 0.35f, 0.7f, GL);   // rãnh sáng
        cap(x, y, hx - px * 2.7f, hy - py * 2.7f, hx + px * 2.7f, hy + py * 2.7f, 1.8f, c); // chắn tay
        cap(x, y, hx + dx * 0.6f, hy + dy * 0.6f, hx + dx * 3f, hy + dy * 3f, 1.7f, c);     // cán
        dot(x, y, hx + dx * 3.7f, hy + dy * 3.7f, 1.2f, c);                              // núm
    }

    // ------------------------------------------------------------------ biểu tượng (khung 16x16 tại x, y)

    public static void draw(String g, int x, int y, int c) {
        int d = dim(c, 0.5f);
        if ("grid".equals(g)) {
            for (int r = 0; r < 2; r++) {
                for (int q = 0; q < 2; q++) {
                    int sx = x + 1 + q * 8, sy = y + 1 + r * 8;
                    Draw.rrect(sx, sy, sx + 6, sy + 6, 2, (r + q) % 2 == 0 ? c : dim(c, 0.82f));
                    Gui.drawRect(sx + 1, sy + 1, sx + 5, sy + 2, GL); // vệt sáng mép trên mỗi ô
                    Gui.drawRect(sx + 1, sy + 4, sx + 5, sy + 5, DK);  // bóng mép dưới
                }
            }
        } else if ("monitor".equals(g)) {
            cap(x, y, 2.4f, 2.6f, 13.6f, 2.6f, 1.7f, c);
            cap(x, y, 13.6f, 2.6f, 13.6f, 10.4f, 1.7f, c);
            cap(x, y, 13.6f, 10.4f, 2.4f, 10.4f, 1.7f, c);
            cap(x, y, 2.4f, 10.4f, 2.4f, 2.6f, 1.7f, c);
            cap(x, y, 5.2f, 8.6f, 5.2f, 6.6f, 1.5f, d);   // biểu đồ nhỏ trong màn hình
            cap(x, y, 8f, 8.6f, 8f, 4.6f, 1.5f, d);
            cap(x, y, 10.8f, 8.6f, 10.8f, 5.8f, 1.5f, d);
            bar(x, y, 8f, 11.2f, 8f, 13.4f, 1.8f, c);      // chân đế
            cap(x, y, 4.8f, 14f, 11.2f, 14f, 1.8f, c);
            dot(x, y, 12.2f, 9.4f, 0.55f, GL);              // đèn nguồn
        } else if ("swords".equals(g)) {
            sword(x, y, 13.8f, 1.2f, 4.6f, 10.4f, c);
            sword(x, y, 2.2f, 1.2f, 11.4f, 10.4f, c);
        } else if ("eye".equals(g)) {
            double a = Math.toRadians(30.2);
            arcAt(x, y, 8f, 12.24f, 8.44f, PI + a, 2 * PI - a, 1.7f, c);   // mi trên
            arcAt(x, y, 8f, 3.76f, 8.44f, a, PI - a, 1.7f, c);              // mi dưới
            ringAt(x, y, 8f, 8f, 3.1f, 1.5f, c);                            // mống mắt
            dot(x, y, 8f, 8f, 1.4f, c);                                      // đồng tử
            dot(x, y, 9.3f, 6.8f, 0.7f, GL);                                 // điểm sáng
            bar(x, y, 8f, 0.6f, 8f, 1.9f, 1.1f, d);                          // lông mi
            bar(x, y, 4.2f, 1.5f, 4.9f, 2.7f, 1.1f, d);
            bar(x, y, 11.8f, 1.5f, 11.1f, 2.7f, 1.1f, d);
        } else if ("sparkle".equals(g)) {
            star(x, y, 7.4f, 8.2f, 7.4f, 1.7f, 4, -PI / 2, c);               // sao lớn 4 cánh
            star(x, y, 13f, 3f, 2.7f, 0.7f, 4, -PI / 2, d);                  // sao nhỏ
            star(x, y, 3f, 13.4f, 2.1f, 0.6f, 4, -PI / 2, d);
            dot(x, y, 14f, 12.5f, 0.8f, d);
        } else if ("sliders".equals(g)) {
            float[] ty = {3f, 8f, 13f};
            float[] kx = {10f, 5f, 11.5f};
            for (int i = 0; i < 3; i++) {
                cap(x, y, 1.6f, ty[i], kx[i], ty[i], 1.5f, c);                 // phần đã trượt
                cap(x, y, kx[i], ty[i], 14.4f, ty[i], 1.5f, d);                // phần còn lại (mờ)
                dot(x, y, kx[i], ty[i], 2.5f, c);                              // núm
                dot(x, y, kx[i] - 0.7f, ty[i] - 0.8f, 0.8f, GL);
            }
        } else if ("gear".equals(g)) {
            for (int i = 0; i < 8; i++) { // 8 răng
                double a = PI * i / 4;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                bar(x, y, 8f + cs * 5.2f, 8f + sn * 5.2f, 8f + cs * 7.6f, 8f + sn * 7.6f, 2.5f, c);
            }
            ringAt(x, y, 8f, 8f, 4.4f, 2.4f, c);                              // thân
            dot(x, y, 8f, 8f, 1.5f, c);                                         // trục
            arcAt(x, y, 8f, 8f, 4.4f, PI * 1.05, PI * 1.45, 0.8f, GL);         // vệt sáng trên thân
        } else if ("chart".equals(g)) {
            float[] top = {8.5f, 4.5f, 1.5f};
            for (int i = 0; i < 3; i++) {
                int bx = x + 2 + i * 4;
                Draw.rrect(bx, y + Math.round(top[i]), bx + 3, y + 14, 1, c);
                Gui.drawRect(bx, y + Math.round(top[i]) + 1, bx + 1, y + 13, GL); // vệt sáng cạnh trái
            }
            cap(x, y, 1.2f, 14.8f, 14.8f, 14.8f, 1.1f, d);                      // trục
            cap(x, y, 3.5f, 6.2f, 7.5f, 2.6f, 0.9f, d);                          // đường xu hướng
        } else if ("mouse".equals(g)) {
            arcAt(x, y, 8f, 5.6f, 3.9f, PI, 2 * PI, 1.6f, c);
            arcAt(x, y, 8f, 10.4f, 3.9f, 0, PI, 1.6f, c);
            bar(x, y, 4.1f, 5.6f, 4.1f, 10.4f, 1.6f, c);
            bar(x, y, 11.9f, 5.6f, 11.9f, 10.4f, 1.6f, c);
            bar(x, y, 4.3f, 7.1f, 11.7f, 7.1f, 1.3f, c);
            bar(x, y, 8f, 2f, 8f, 7.1f, 1.3f, c);
            Gui.drawRect(x + 5, y + 3, x + 7, y + 6, d);                        // nút trái đang nhấn
            cap(x, y, 8f, 8.6f, 8f, 10f, 1.3f, d);                              // con lăn
        } else if ("keys".equals(g)) {
            int[][] k = {{6, 1}, {1, 8}, {6, 8}, {11, 8}}; // bố cục W / A S D
            for (int[] q : k) {
                int kx = x + q[0], ky = y + q[1];
                Draw.rrect(kx, ky, kx + 4, ky + 6, 1, c);
                Gui.drawRect(kx + 1, ky + 1, kx + 3, ky + 2, GL);                // cạnh sáng phím
                Gui.drawRect(kx + 1, ky + 4, kx + 3, ky + 5, DK);                // chân phím
            }
        } else if ("shield".equals(g)) {
            float[] full = new float[2 * (3 + 12 + 12)];
            int m = 0;
            full[m++] = 2.2f; full[m++] = 2.6f; full[m++] = 8f; full[m++] = 0.8f; full[m++] = 13.8f; full[m++] = 2.6f;
            for (int i = 0; i <= 11; i++) { float t = i / 11f; full[m++] = 13.8f - 5.8f * t * t; full[m++] = 2.6f + 12.2f * t; }
            for (int i = 10; i >= 0; i--) { float t = i / 11f; full[m++] = 2.2f + 5.8f * t * t; full[m++] = 2.6f + 12.2f * t; }
            float[] sh = new float[m];
            System.arraycopy(full, 0, sh, 0, m);
            poly(x, y, c, sh);
            poly(x, y, GL, 2.2f, 2.6f, 8f, 0.8f, 8f, 15f, 3.2f, 8f);            // nửa trái sáng hơn
            star(x, y, 8f, 7.4f, 2.5f, 1.0f, 5, -PI / 2, DK);                     // huy hiệu ngôi sao
        } else if ("drop".equals(g)) {
            dot(x, y, 8f, 10f, 5f, c);
            poly(x, y, c, 8f, 0.8f, 3.84f, 7.22f, 12.16f, 7.22f);
            arcAt(x, y, 8f, 10f, 3.3f, PI * 0.62, PI * 0.95, 1f, GL);             // vệt phản chiếu
            dot(x, y, 5.9f, 7.6f, 0.7f, GL);
        } else if ("cube".equals(g)) {
            poly(x, y, dim(c, 0.4f), 8f, 1.5f, 14f, 4.6f, 8f, 7.8f, 2f, 4.6f);    // mặt trên
            poly(x, y, dim(c, 0.22f), 2f, 4.6f, 8f, 7.8f, 8f, 14.4f, 2f, 11.4f);  // mặt trái
            cap(x, y, 8f, 1.4f, 14f, 4.6f, 1.6f, c);
            cap(x, y, 14f, 4.6f, 14f, 11.4f, 1.6f, c);
            cap(x, y, 14f, 11.4f, 8f, 14.6f, 1.6f, c);
            cap(x, y, 8f, 14.6f, 2f, 11.4f, 1.6f, c);
            cap(x, y, 2f, 11.4f, 2f, 4.6f, 1.6f, c);
            cap(x, y, 2f, 4.6f, 8f, 1.4f, 1.6f, c);
            cap(x, y, 2f, 4.6f, 8f, 7.8f, 1.5f, c);
            cap(x, y, 14f, 4.6f, 8f, 7.8f, 1.5f, c);
            cap(x, y, 8f, 7.8f, 8f, 14.6f, 1.5f, c);
        } else if ("crosshair".equals(g)) {
            ringAt(x, y, 8f, 8f, 4.8f, 1.6f, c);
            bar(x, y, 8f, 0.4f, 8f, 4.4f, 1.7f, c);
            bar(x, y, 8f, 11.6f, 8f, 15.6f, 1.7f, c);
            bar(x, y, 0.4f, 8f, 4.4f, 8f, 1.7f, c);
            bar(x, y, 11.6f, 8f, 15.6f, 8f, 1.7f, c);
            dot(x, y, 8f, 8f, 1.3f, c);
            ringAt(x, y, 8f, 8f, 7.3f, 0.7f, d);                                    // vòng ngắm ngoài mờ
        } else if ("chevron".equals(g)) {
            cap(x, y, 2.4f, 3.2f, 6.8f, 8f, 2.1f, d);                               // vệt mờ phía sau
            cap(x, y, 6.8f, 8f, 2.4f, 12.8f, 2.1f, d);
            cap(x, y, 8.4f, 3.2f, 12.8f, 8f, 2.4f, c);
            cap(x, y, 12.8f, 8f, 8.4f, 12.8f, 2.4f, c);
        } else if ("magnifier".equals(g)) {
            dot(x, y, 6.5f, 6.5f, 4.2f, dim(c, 0.28f));                              // mặt kính
            ringAt(x, y, 6.5f, 6.5f, 4.6f, 1.8f, c);
            arcAt(x, y, 6.5f, 6.5f, 2.8f, PI * 1.05, PI * 1.45, 0.9f, GL);           // phản chiếu trên kính
            cap(x, y, 10.1f, 10.1f, 14.2f, 14.2f, 2.6f, c);                          // cán
            cap(x, y, 10.6f, 10.6f, 11.6f, 11.6f, 1f, DK);
        } else if ("sun".equals(g)) {
            dot(x, y, 8f, 8f, 3.4f, c);
            dot(x, y, 7f, 7f, 1.1f, GL);
            for (int i = 0; i < 8; i++) {
                double a = PI * i / 4;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                float r0 = 5.3f, r1 = (i % 2 == 0) ? 7.7f : 6.8f;
                cap(x, y, 8f + cs * r0, 8f + sn * r0, 8f + cs * r1, 8f + sn * r1, (i % 2 == 0) ? 1.7f : 1.4f, c);
            }
        } else if ("mountain".equals(g)) {
            poly(x, y, d, 7.2f, 14.6f, 11.6f, 6.6f, 15.6f, 14.6f);                   // núi phía sau
            poly(x, y, c, 0.6f, 14.6f, 6.4f, 2.6f, 12.2f, 14.6f);                    // núi trước
            poly(x, y, GL, 6.4f, 2.6f, 4.4f, 6.8f, 6.4f, 5.8f, 8.4f, 6.8f);          // chóp tuyết
            poly(x, y, DK, 6.4f, 2.6f, 12.2f, 14.6f, 7.6f, 14.6f);                   // mặt khuất
            dot(x, y, 13f, 3f, 1.3f, d);                                              // mặt trời nhỏ
        } else if ("clock".equals(g)) {
            ringAt(x, y, 8f, 8f, 6.4f, 1.7f, c);
            for (int i = 0; i < 12; i++) { // vạch giờ
                double a = PI * i / 6;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                float r0 = (i % 3 == 0) ? 4.2f : 4.9f;
                bar(x, y, 8f + cs * r0, 8f + sn * r0, 8f + cs * 5.4f, 8f + sn * 5.4f, (i % 3 == 0) ? 1.1f : 0.7f, d);
            }
            cap(x, y, 8f, 8f, 8f, 4.2f, 1.6f, c);                                    // kim phút
            cap(x, y, 8f, 8f, 10.6f, 9.4f, 1.6f, c);                                 // kim giờ
            dot(x, y, 8f, 8f, 1.2f, c);
        } else if ("bubble".equals(g)) {
            Draw.rrect(x + 1, y + 1, x + 15, y + 11, 4, c);
            poly(x, y, c, 4f, 10f, 4f, 15f, 9f, 10f);                                  // đuôi bóng thoại
            dot(x, y, 4.8f, 6f, 1.15f, DK);                                            // ba chấm
            dot(x, y, 8f, 6f, 1.15f, DK);
            dot(x, y, 11.2f, 6f, 1.15f, DK);
            Gui.drawRect(x + 4, y + 2, x + 12, y + 3, GL);
        } else if ("check".equals(g)) {
            cap(x, y, 2.4f, 8.4f, 6.2f, 12.2f, 2.5f, c);
            cap(x, y, 6.2f, 12.2f, 13.8f, 4f, 2.5f, c);
            bar(x, y, 6.6f, 11f, 13.2f, 4.6f, 0.8f, GL);
        } else if ("arrows".equals(g)) {
            cap(x, y, 4f, 3f, 4f, 13f, 1.9f, c);
            cap(x, y, 1.4f, 5.6f, 4f, 3f, 1.9f, c);
            cap(x, y, 6.6f, 5.6f, 4f, 3f, 1.9f, c);
            cap(x, y, 12f, 3f, 12f, 13f, 1.9f, c);
            cap(x, y, 9.4f, 10.4f, 12f, 13f, 1.9f, c);
            cap(x, y, 14.6f, 10.4f, 12f, 13f, 1.9f, c);
        } else {
            Draw.disc(x + 8, y + 8, 4, c);
            ringAt(x, y, 8f, 8f, 6.2f, 0.9f, d);
        }
    }
}
