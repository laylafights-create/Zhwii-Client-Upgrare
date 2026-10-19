package dev.lunarlite.gui;

import dev.lunarlite.module.Category;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Gui;

/**
 * Biểu tượng pixel 8x8 riêng cho từng tính năng và từng nhóm.
 * Mỗi biểu tượng là một số 64 bit: bit (hàng * 8 + cột) = 1 thì ô đó được tô.
 * Vẽ bằng các hình chữ nhật nên không cần file ảnh.
 */
public final class Icons {
    private Icons() {}

    private static final Map<String, Long> MAP = new HashMap<String, Long>();

    static {
        MAP.put("logo", 0x007E060C1830607EL);
        MAP.put("default", 0x0000363600363600L);
        MAP.put("fps", 0xFFDBDBD8D8C0C000L);
        MAP.put("cps", 0x3C4242427E5A5A3CL);
        MAP.put("keystrokes", 0x7E81819999BD997EL);
        MAP.put("enemy_detector", 0x24247EFF7E183C18L);
        MAP.put("bowtrajectory", 0xE0C0A01011090600L);
        MAP.put("targetinfo", 0x3C4299A5A599423CL);
        MAP.put("combo", 0x02060C187E0C1830L);
        MAP.put("killboard", 0x00543C7E99997E3CL);
        MAP.put("aimassist", 0x181818E7E7181818L);
        MAP.put("armor", 0x183C7E7EFFFFFFFFL);
        MAP.put("hide_armor", 0x18142A4A91A181FFL);
        MAP.put("potions", 0x7EBDBD5A2418183CL);
        MAP.put("itempickup", 0xFF81FF183C7E1818L);
        MAP.put("ping", 0xAAAAA8A8A0A08080L);
        MAP.put("serverip", 0x003C5A99FF995A3CL);
        MAP.put("scoreboard", 0xFF8D81BD81BD81FFL);
        MAP.put("togglesprint", 0x0912244848241209L);
        MAP.put("zoom", 0x80C0602E2121211EL);
        MAP.put("fullbright", 0x99423CBDBD3C4299L);
        MAP.put("blockoutline", 0x001F1171515F447CL);
        MAP.put("farhorizons", 0xFFFFBE1C08006060L);
        MAP.put("itemphysics", 0x7E00183C7EFF7E3CL);
        MAP.put("animations", 0x00030F3FFF3F0F03L);
        MAP.put("hotbar_animation", 0xFF00C3C3DB181800L);
        MAP.put("chatbar", 0x080C7E8D81BD817EL);
        MAP.put("guitheme", 0x3E7F7F3E3E1C1C08L);
        MAP.put("cat_all", 0x00E7E7E700E7E7E7L);
        MAP.put("cat_hud", 0x00FF81B581AD81FFL);
        MAP.put("cat_pvp", 0x81C3663C3C66C381L);
        MAP.put("cat_visual", 0x003C429999423C00L);
        MAP.put("cat_effect", 0x082A1C7F1C2A0808L);
        MAP.put("cat_utility", 0x185A3CE7E73C5A18L);
    }

    /** Có biểu tượng riêng cho id này không. */
    public static boolean has(String id) {
        return MAP.containsKey(id);
    }

    /** Biểu tượng của một tính năng (theo {@code Module.id}); không có thì dùng biểu tượng mặc định. */
    public static long get(String id) {
        Long v = MAP.get(id);
        if (v == null) v = MAP.get("default");
        return v.longValue();
    }

    /** Id biểu tượng của một nhóm tính năng. */
    public static String cat(Category c) {
        if (c == null) return "cat_all";
        switch (c) {
            case HUD: return "cat_hud";
            case PVP: return "cat_pvp";
            case VISUAL: return "cat_visual";
            case EFFECT: return "cat_effect";
            case UTILITY: return "cat_utility";
            default: return "cat_all";
        }
    }

    /** Vẽ biểu tượng tại (x, y); mỗi ô vuông có cạnh {@code s} điểm ảnh, màu ARGB. */
    public static void draw(String id, int x, int y, int s, int argb) {
        long bits = get(id);
        for (int r = 0; r < 8; r++) {
            int c = 0;
            while (c < 8) {
                if (((bits >>> (r * 8 + c)) & 1L) == 0L) {
                    c++;
                    continue;
                }
                int c0 = c;
                while (c < 8 && ((bits >>> (r * 8 + c)) & 1L) != 0L) c++;
                Gui.drawRect(x + c0 * s, y + r * s, x + c * s, y + (r + 1) * s, argb);
            }
        }
    }
}
