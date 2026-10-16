package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;

/**
 * Nhận biết Hypixel và lọc "người chơi thật":
 *  - Hypixel dùng UUID phiên bản 2 cho NPC / bot, người chơi thật (kể cả nick) là phiên bản 4.
 *  - Sảnh (lobby) có sidebar tiêu đề "HYPIXEL": ở đó chỉ có NPC / người đứng chờ, không cần aim hay viền.
 * Các kết quả được cache vài trăm ms để gọi mỗi khung hình vẫn nhẹ.
 */
public final class HypixelUtil {
    private static long hostStamp, lobbyStamp;
    private static boolean host, lobby;

    private HypixelUtil() {}

    public static boolean isHypixel() {
        long now = System.currentTimeMillis();
        if (now - hostStamp > 1000L) {
            hostStamp = now;
            ServerData sd = Minecraft.getMinecraft().getCurrentServerData();
            host = sd != null && sd.serverIP != null && sd.serverIP.toLowerCase().contains("hypixel");
        }
        return host;
    }

    /** Đang ở sảnh Hypixel (sidebar có tiêu đề "HYPIXEL"). */
    public static boolean inLobby() {
        if (!isHypixel()) return false;
        long now = System.currentTimeMillis();
        if (now - lobbyStamp > 500L) {
            lobbyStamp = now;
            lobby = false;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                Scoreboard sb = mc.theWorld.getScoreboard();
                ScoreObjective obj = sb == null ? null : sb.getObjectiveInDisplaySlot(1);
                if (obj != null) {
                    String title = StringUtils.stripControlCodes(obj.getDisplayName());
                    lobby = title != null && title.trim().equalsIgnoreCase("HYPIXEL");
                }
            }
        }
        return lobby;
    }

    private static boolean validName(String n) {
        if (n == null || n.length() < 3 || n.length() > 16) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return true;
    }

    /**
     * Người chơi thật.
     *  - Hypixel: UUID v4 + tên hợp lệ (tab list hay nháy nên không bắt buộc).
     *  - Server khác: có trong tab list là đủ; nếu server ẩn / ghi đè tab list thì vẫn nhận khi UUID không phải kiểu NPC (v2) và tên hợp lệ.
     */
    public static boolean isRealPlayer(EntityPlayer p) {
        NetHandlerPlayClient nh = Minecraft.getMinecraft().getNetHandler();
        boolean inTab = nh != null && nh.getPlayerInfo(p.getUniqueID()) != null;
        int ver = p.getUniqueID().version();
        if (!isHypixel()) return inTab || (ver != 2 && validName(p.getName()));
        return ver == 4 && validName(p.getName());
    }
}
