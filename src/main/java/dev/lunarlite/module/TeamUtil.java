package dev.lunarlite.module;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.StringUtils;
import net.minecraft.world.World;

/**
 * Phân biệt đồng đội / kẻ địch, dùng được cả trên Hypixel.
 * Hypixel (Bedwars...) không luôn xếp cùng đội vào cùng một scoreboard team nên {@code isOnSameTeam} hay sai.
 * Thứ tự kiểm tra (cái nào có đủ dữ liệu cho cả hai người thì quyết định luôn):
 *  1. Màu giáp da đã nhuộm (mũ / áo) - Bedwars nhuộm giáp theo màu đội.
 *  2. Màu trong prefix ngắn của team (kiểu "R ", "B ") - bỏ qua prefix dài như rank "[MVP+]".
 *  3. Cùng scoreboard team (cách của vanilla).
 */
public final class TeamUtil {
    private static final String COLORS = "0123456789abcdef";

    private TeamUtil() {}

    private static final class Verdict {
        long until;
        boolean mate;
    }

    private static final Map<Integer, Verdict> CACHE = new HashMap<Integer, Verdict>();
    private static World lastWorld;

    /** Kết quả được cache ~0.4s cho mỗi người (aim chạy mỗi khung hình nên không tính lại liên tục). */
    public static boolean isTeammate(EntityPlayer me, EntityPlayer p) {
        if (me == null || p == null) return false;
        if (me == p) return true;
        World w = Minecraft.getMinecraft().theWorld;
        if (w != lastWorld || CACHE.size() > 256) {
            CACHE.clear();
            lastWorld = w;
        }
        long now = System.currentTimeMillis();
        Verdict v = CACHE.get(p.getEntityId());
        if (v == null) {
            v = new Verdict();
            CACHE.put(p.getEntityId(), v);
        }
        if (now >= v.until) {
            v.mate = compute(me, p);
            v.until = now + 400L;
        }
        return v.mate;
    }

    private static boolean compute(EntityPlayer me, EntityPlayer p) {

        int a = dyedArmor(me), b = dyedArmor(p);
        if (a != -1 && b != -1) return a == b;

        int ca = teamMarkerColor(me), cb = teamMarkerColor(p);
        if (ca != -1 && cb != -1) return ca == cb;

        Team ta = me.getTeam(), tb = p.getTeam();
        return ta != null && tb != null && ta.isSameTeam(tb);
    }

    /** Màu (RGB) của món giáp da đã nhuộm đầu tiên tìm thấy (mũ, áo, quần, giày), hoặc -1. */
    private static int dyedArmor(EntityPlayer p) {
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack s = p.inventory.armorInventory[slot];
            if (s == null || !(s.getItem() instanceof ItemArmor)) continue;
            ItemArmor ia = (ItemArmor) s.getItem();
            if (ia.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER && ia.hasColor(s)) {
                return ia.getColor(s) & 0xFFFFFF;
            }
        }
        return -1;
    }

    /** Chỉ số màu (0-15) trong prefix của team nếu prefix là ký hiệu đội ngắn, ngược lại -1. */
    private static int teamMarkerColor(EntityPlayer p) {
        Team t = p.getTeam();
        if (!(t instanceof ScorePlayerTeam)) return -1;
        String prefix = ((ScorePlayerTeam) t).getColorPrefix();
        if (prefix == null) return -1;
        int visible = StringUtils.stripControlCodes(prefix).trim().length();
        if (visible < 1 || visible > 2) return -1; // rỗng hoặc dài kiểu "[MVP+]" -> không phải ký hiệu đội
        int color = -1;
        for (int i = 0; i + 1 < prefix.length(); i++) {
            if (prefix.charAt(i) == '\u00a7') {
                int idx = COLORS.indexOf(Character.toLowerCase(prefix.charAt(i + 1)));
                if (idx >= 0) color = idx;
            }
        }
        return color;
    }
}
