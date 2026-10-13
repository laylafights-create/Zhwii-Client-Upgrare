package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Việt hoá chat Hypixel (không HUD): lobby, party, guild, Bed Wars, SkyWars, Duels,
 * Murder Mystery, TNT, Build Battle, Arcade, SkyBlock... Giữ nguyên màu, click và hover của tin nhắn.
 * Tin nhắn của người chơi khác KHÔNG bị dịch (chỉ dịch tin hệ thống).
 * Từ điển riêng: config/lunarlite/hypixel_vi.txt (UTF-8)
 *   English=Tiếng Việt     |     r:regex=thay thế ($1, $2)     |     # ghi chú
 */
public class HypixelViModule extends Module {

    private final BoolSetting onlyHypixel = add(new BoolSetting("only_hypixel", "Only On Hypixel", true));
    private final BoolSetting general = add(new BoolSetting("general", "Lobby & General", true));
    private final BoolSetting social = add(new BoolSetting("social", "Party & Friends", true));
    private final BoolSetting bedwars = add(new BoolSetting("bedwars", "Bed Wars", true));
    private final BoolSetting guild = add(new BoolSetting("guild", "Guild", true));
    private final BoolSetting skywars = add(new BoolSetting("skywars", "SkyWars", true));
    private final BoolSetting duels = add(new BoolSetting("duels", "Duels", true));
    private final BoolSetting murder = add(new BoolSetting("murder", "Murder Mystery", true));
    private final BoolSetting minigames = add(new BoolSetting("minigames", "TNT / Build Battle / Arcade / UHC", true));
    private final BoolSetting skyblock = add(new BoolSetting("skyblock", "SkyBlock", true));
    private final BoolSetting menus = add(new BoolSetting("menus", "Menus & Tooltips", true));
    private final BoolSetting custom = add(new BoolSetting("custom", "Custom Dictionary", true));
    /** Ghi các tin hệ thống CHƯA được dịch vào config/lunarlite/hypixel_untranslated.txt để bổ sung từ điển. */
    private final BoolSetting logMissing = add(new BoolSetting("log_untranslated", "Log Untranslated", false));

    private static final class Dict {
        final Map<String, String> exact = new LinkedHashMap<String, String>();
        final List<Pattern> rx = new ArrayList<Pattern>();
        final List<String> rep = new ArrayList<String>();

        Dict a(String en, String vi) { exact.put(en, vi); return this; }

        Dict r(String regex, String vi) {
            try { rx.add(Pattern.compile(regex)); rep.add(vi); } catch (Exception ignored) { }
            return this;
        }

        String apply(String s) {
            for (Map.Entry<String, String> e : exact.entrySet()) {
                if (s.contains(e.getKey())) s = s.replace(e.getKey(), e.getValue());
            }
            for (int i = 0; i < rx.size(); i++) s = rx.get(i).matcher(s).replaceAll(rep.get(i));
            return s;
        }
    }

    private final Dict dGeneral = new Dict();
    private final Dict dSocial = new Dict();
    private final Dict dBedwars = new Dict();
    private final Dict dGuild = new Dict();
    private final Dict dSkywars = new Dict();
    private final Dict dDuels = new Dict();
    private final Dict dMurder = new Dict();
    private final Dict dMini = new Dict();
    private final Dict dSkyblock = new Dict();
    private final Dict dMenu = new Dict();
    private final Dict dCustom = new Dict();

    private final Set<String> seenMissing = new HashSet<String>();
    private File missingFile;

    /** Tin chat của người chơi: [rank] Tên: nội dung (kể cả Party >, Guild >, From, To). */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:(?:Party|Guild|Officer|Co-op) > )?(?:(?:From|To) )?(?:\\[[^\\]]+\\]\\s*)*\\w{3,16}: .*", Pattern.DOTALL);

    public HypixelViModule() {
        super("hypixelvi", "Vietsub Hypixel", "Việt hoá tin nhắn hệ thống của Hypixel (lobby, party, guild, Bed Wars, SkyWars, Duels, Murder Mystery, minigames, SkyBlock).",
                Category.UTILITY, false);
        buildGeneral();
        buildSocial();
        buildBedwars();
        buildGuild();
        buildSkywars();
        buildDuels();
        buildMurder();
        buildMini();
        buildSkyblock();
        buildMenu();
        missingFile = new File(Minecraft.getMinecraft().mcDataDir, "config/lunarlite/hypixel_untranslated.txt");
        loadCustom(new File(Minecraft.getMinecraft().mcDataDir, "config/lunarlite/hypixel_vi.txt"));
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ------------------------------------------------------------------ chat

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!enabled || e.message == null || e.type == 2) return; // bỏ action bar
        if (onlyHypixel.value && !HypixelUtil.isHypixel()) return;
        String plain = StringUtils.stripControlCodes(e.message.getUnformattedText());
        if (plain == null || plain.isEmpty() || PLAYER_CHAT.matcher(plain).matches()) return;
        IChatComponent t = translate(e.message);
        if (logMissing.value && !SEPARATOR.matcher(plain).matches()) {
            String after = StringUtils.stripControlCodes(t.getUnformattedText());
            if (plain.equals(after)) logUntranslated(plain);
        }
        e.message = t;
    }

    private static final Pattern SEPARATOR = Pattern.compile("^[\\-=_\u25AC\\s]*$");

    private void logUntranslated(String line) {
        line = line.replace("\n", " ").trim();
        if (line.isEmpty() || seenMissing.size() > 3000 || !seenMissing.add(line)) return;
        FileOutputStream fos = null;
        try {
            missingFile.getParentFile().mkdirs();
            fos = new FileOutputStream(missingFile, true);
            fos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) { }
        }
    }

    /** Việt hoá tooltip item trong menu server (GUI rương: My Profile, Achievements...). */
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent e) {
        if (!enabled || !menus.value || e.toolTip == null) return;
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChest)) return;
        if (onlyHypixel.value && !HypixelUtil.isHypixel()) return;
        for (int i = 0; i < e.toolTip.size(); i++) {
            String line = e.toolTip.get(i);
            String out = dMenu.apply(tr(line));
            e.toolTip.set(i, out);
            if (logMissing.value && out.equals(line)) {
                String plain = StringUtils.stripControlCodes(line);
                if (plain != null && !SEPARATOR.matcher(plain).matches()) logUntranslated("[menu] " + plain);
            }
        }
    }

    private IChatComponent translate(IChatComponent src) {
        IChatComponent out;
        if (src instanceof ChatComponentText) {
            out = new ChatComponentText(tr(((ChatComponentText) src).getChatComponentText_TextValue()));
            out.setChatStyle(src.getChatStyle().createShallowCopy());
        } else {
            out = src.createCopy();
            out.getSiblings().clear();
        }
        for (Object o : src.getSiblings()) out.appendSibling(translate((IChatComponent) o));
        return out;
    }

    private String tr(String s) {
        if (s == null || s.length() < 2) return s;
        if (custom.value) s = dCustom.apply(s);
        if (bedwars.value) s = dBedwars.apply(s);
        if (skywars.value) s = dSkywars.apply(s);
        if (duels.value) s = dDuels.apply(s);
        if (murder.value) s = dMurder.apply(s);
        if (minigames.value) s = dMini.apply(s);
        if (skyblock.value) s = dSkyblock.apply(s);
        if (social.value) s = dSocial.apply(s);
        if (guild.value) s = dGuild.apply(s);
        if (general.value) s = dGeneral.apply(s);
        return s;
    }

    // ------------------------------------------------------------ từ điển

    private void buildGeneral() {
        dGeneral
            .a("Welcome to Hypixel", "Chào mừng đến với Hypixel")
            .a("You are AFK. Move around to return from AFK.", "Bạn đang AFK. Hãy di chuyển để thoát AFK.")
            .a("You are already connected to this server!", "Bạn đã kết nối tới máy chủ này rồi!")
            .a("You are currently connected to server", "Bạn đang kết nối tới máy chủ")
            .a("Please don't spam the chat!", "Vui lòng không spam chat!")
            .a("You cannot say the same message twice!", "Bạn không thể gửi cùng một tin nhắn hai lần!")
            .a("You are sending commands too fast! Please slow down.", "Bạn gõ lệnh quá nhanh! Hãy chậm lại.")
            .a("Unknown command.", "Lệnh không tồn tại.")
            .a("Sending you to", "Đang chuyển bạn tới")
            .a("You are being transferred to", "Bạn đang được chuyển tới")
            .a("Can't find a player by the name of", "Không tìm thấy người chơi tên")
            .a("That player is not online", "Người chơi đó không trực tuyến")
            .a("The game starts in", "Trận đấu bắt đầu sau")
            .a("The game has started!", "Trận đấu đã bắt đầu!")
            .a("Waiting for players...", "Đang chờ người chơi...")
            .a("has quit!", "đã thoát!")
            .a("has joined", "đã tham gia")
            .a("Reward Summary", "Tổng kết phần thưởng")
            .a("Play Again?", "Chơi lại?")
            .a("Click here to play again", "Bấm vào đây để chơi lại")
            .a("VICTORY!", "CHIẾN THẮNG!")
            .a("GAME OVER!", "KẾT THÚC TRẬN!")
            .a("Winners", "Những người thắng")
            .a("Winner", "Người thắng")
            .a("spooked into the lobby!", "đã xuất hiện ma quái trong sảnh!")
            .a("[WATCHDOG ANNOUNCEMENT]", "[THÔNG BÁO WATCHDOG]")
            .a("Watchdog has banned", "Watchdog đã cấm")
            .a("players in the last 7 days.", "người chơi trong 7 ngày qua.")
            .a("Staff have banned an additional", "Staff đã cấm thêm")
            .a("Blacklisted modifications are a bannable offense!", "Dùng mod bị cấm sẽ bị ban!")
            .a("You were spawned in Limbo.", "Bạn bị đưa vào Limbo.")
            .a("You are currently in Limbo", "Bạn đang ở Limbo")
            .a("You do not have permission to execute this command!", "Bạn không có quyền dùng lệnh này!")
            .a("You can't do that right now.", "Bạn không thể làm điều đó lúc này.")
            .a("Please wait before doing that again!", "Vui lòng chờ trước khi làm lại!")
            .a("This server will restart soon:", "Máy chủ này sắp khởi động lại:")
            .a("The server is restarting", "Máy chủ đang khởi động lại")
            .a("You were kicked while joining that server!", "Bạn bị đá khi đang vào máy chủ đó!")
            .a("That server is full!", "Máy chủ đó đã đầy!")
            .a("Lobby is full", "Sảnh đã đầy")
            .a("You have been eliminated", "Bạn đã bị loại")
            .a("QUEST COMPLETE", "HOÀN THÀNH NHIỆM VỤ")
            .a("Quest Complete", "Hoàn thành nhiệm vụ")
            .a("Daily Quest", "Nhiệm vụ hằng ngày")
            .a("Weekly Quest", "Nhiệm vụ hằng tuần")
            .a("LEVEL UP!", "LÊN CẤP!")
            .a("Hypixel Level", "Cấp Hypixel")
            .a("DEFEAT!", "THẤT BẠI!")
            .a("Experience", "Kinh nghiệm")
            .a("Coins", "Xu")
            .a("Kills", "Số lần hạ gục")
            .a("Deaths", "Số lần chết")
            .a("Click to", "Bấm để")
            .a("Click here", "Bấm vào đây")
            .r("(?i)\\+(\\d+) coins", "+$1 xu")
            .r("(\\d+) seconds?", "$1 giây")
            .r("(\\d+) minutes?", "$1 phút")
            .r("\\+(\\d+) coins", "+$1 xu");
    }

    private void buildSocial() {
        dSocial
            .a("has sent you a friend request!", "đã gửi cho bạn lời mời kết bạn!")
            .a("Friend request from", "Lời mời kết bạn từ")
            .a("You are now friends with", "Bạn đã trở thành bạn bè với")
            .a("has invited you to join their party!", "đã mời bạn vào party của họ!")
            .a("You have 60 seconds to accept. Click here to join!", "Bạn có 60 giây để chấp nhận. Bấm vào đây để tham gia!")
            .a("The party was disbanded", "Party đã bị giải tán")
            .a("You left the party.", "Bạn đã rời party.")
            .a("joined the party.", "đã vào party.")
            .a("left the party.", "đã rời party.")
            .a("You are not in a party right now.", "Bạn hiện không ở trong party nào.")
            .a("You have been kicked from the party", "Bạn đã bị đuổi khỏi party")
            .a("ACCEPT", "CHẤP NHẬN")
            .a("DENY", "TỪ CHỐI")
            .a("IGNORE", "BỎ QUA");
    }

    private void buildBedwars() {
        dBedwars
            .a("Protect your bed and destroy the enemy beds.", "Bảo vệ giường của bạn và phá giường đối thủ.")
            .a("Upgrade yourself and your team by collecting", "Nâng cấp bản thân và đội bằng cách thu thập")
            .a("Iron, Gold, Emerald, and Diamond", "Sắt, Vàng, Ngọc lục bảo và Kim cương")
            .a("from generators to access powerful upgrades.", "từ máy phát để mua các nâng cấp mạnh.")
            .a("Your Bed was destroyed by", "Giường của bạn đã bị phá bởi")
            .a("Your Bed was destroyed", "Giường của bạn đã bị phá")
            .a("Bed was destroyed by", "Giường bị phá bởi")
            .a("BED DESTRUCTION", "PHÁ GIƯỜNG")
            .a("Your team has been eliminated!", "Đội của bạn đã bị loại!")
            .a("has been eliminated!", "đã bị loại!")
            .a("TEAM ELIMINATED", "ĐỘI BỊ LOẠI")
            .a("You will no longer respawn!", "Bạn sẽ không thể hồi sinh nữa!")
            .a("You will respawn in", "Bạn sẽ hồi sinh sau")
            .a("You have respawned!", "Bạn đã hồi sinh!")
            .a("RESPAWNED", "ĐÃ HỒI SINH")
            .a("YOU DIED!", "BẠN ĐÃ CHẾT!")
            .a("You have been eliminated!", "Bạn đã bị loại!")
            .a("All beds have been destroyed!", "Tất cả giường đã bị phá hủy!")
            .a("SUDDEN DEATH", "CÁI CHẾT ĐỘT NGỘT")
            .a("Sudden Death", "Cái Chết Đột Ngột")
            .a("Diamond Generators have been upgraded", "Máy phát Kim cương đã được nâng cấp")
            .a("Emerald Generators have been upgraded", "Máy phát Ngọc lục bảo đã được nâng cấp")
            .a("Diamond Generators has been upgraded", "Máy phát Kim cương đã được nâng cấp")
            .a("Emerald Generators has been upgraded", "Máy phát Ngọc lục bảo đã được nâng cấp")
            .r("upgraded to Tier ([IVX]+)", "nâng lên Cấp $1")
            .a("Red Team", "Đội Đỏ").a("Blue Team", "Đội Xanh dương").a("Green Team", "Đội Xanh lá")
            .a("Yellow Team", "Đội Vàng").a("Aqua Team", "Đội Xanh ngọc").a("White Team", "Đội Trắng")
            .a("Pink Team", "Đội Hồng").a("Gray Team", "Đội Xám")
            .a("was knocked into the void by", "bị hất xuống hư không bởi")
            .a("fell into the void.", "rơi xuống hư không.")
            .a("was shot by", "bị bắn bởi")
            .a("was killed by", "bị giết bởi")
            .a("burned to death", "bị thiêu chết")
            .a("was blown up by", "bị nổ tung bởi")
            .a("suffocated in a wall", "ngạt thở trong tường")
            .a("hit the ground too hard", "ngã quá mạnh")
            .r("\\bdrowned\\b", "chết đuối")
            .r("\\bdied\\.", "đã chết.")
            .a("FINAL KILL!", "HẠ GỤC CUỐI CÙNG!")
            .a("You purchased", "Bạn đã mua")
            .a("You don't have enough", "Bạn không đủ")
            .a("Trap was set off!", "Bẫy đã bị kích hoạt!")
            .a("It's a Trap!", "Bẫy Thường!")
            .a("Alarm Trap", "Bẫy Báo Động")
            .a("Counter-Offensive Trap", "Bẫy Phản Công")
            .a("Miner Fatigue Trap", "Bẫy Mệt Mỏi")
            .a("1st Killer", "Hạ gục nhiều nhất #1")
            .a("2nd Killer", "Hạ gục nhiều nhất #2")
            .a("3rd Killer", "Hạ gục nhiều nhất #3")
            .a("Teaming is not allowed", "Không được phép lập team");
    }


    private void buildGuild() {
        dGuild
            .a("has invited you to join their guild!", "đã mời bạn vào guild của họ!")
            .a("Click here to accept or type", "Bấm vào đây để chấp nhận hoặc gõ")
            .a("GUILD QUEST COMPLETED!", "HOÀN THÀNH NHIỆM VỤ GUILD!")
            .a("GUILD QUEST TIER", "NHIỆM VỤ GUILD CẤP")
            .a("Guild Quest", "Nhiệm vụ Guild")
            .a("Guild Level", "Cấp Guild")
            .a("Guild Master", "Chủ Guild")
            .a("You joined the guild", "Bạn đã vào guild")
            .a("You left the guild", "Bạn đã rời guild")
            .a("was kicked from the guild", "đã bị đuổi khỏi guild")
            .a("has joined the guild!", "đã vào guild!")
            .a("has left the guild!", "đã rời guild!");
    }

    private void buildSkywars() {
        dSkywars
            .a("Cages open in", "Lồng sẽ mở sau")
            .a("Cages opened! FIGHT!", "Lồng đã mở! CHIẾN!")
            .a("All chests have been refilled!", "Tất cả rương đã được làm đầy lại!")
            .a("Chests will refill in", "Rương sẽ được làm đầy lại sau")
            .a("Players Left:", "Người chơi còn lại:")
            .a("was shot and killed by", "bị bắn chết bởi")
            .a("was thrown into the void by", "bị ném xuống hư không bởi")
            .a("was slain by", "bị chém chết bởi")
            .a("was struck down by", "bị hạ gục bởi")
            .a("fell to their death", "ngã chết")
            .a("died.", "đã chết.")
            .a("Sky Wars", "SkyWars")
            .a("Solo Insane", "Solo Cực Khó")
            .a("Solo Normal", "Solo Thường")
            .a("Doubles Insane", "Đôi Cực Khó")
            .a("Doubles Normal", "Đôi Thường")
            .a("Lab Mode", "Chế độ Lab")
            .a("Soul Well", "Giếng Linh Hồn");
    }

    private void buildDuels() {
        dDuels
            .a("The duel starts in", "Trận duel bắt đầu sau")
            .a("Your opponent", "Đối thủ của bạn")
            .a("Opponent:", "Đối thủ:")
            .a("Current Winstreak", "Chuỗi thắng hiện tại")
            .a("Best Winstreak", "Chuỗi thắng cao nhất")
            .a("Winstreak", "Chuỗi thắng")
            .a("Damage Dealt", "Sát thương gây ra")
            .a("Health Regenerated", "Máu hồi lại")
            .a("Melee Accuracy", "Độ chính xác cận chiến")
            .a("Bow Accuracy", "Độ chính xác cung")
            .a("Rematch", "Đấu lại")
            .a("Duel Request", "Lời mời Duel")
            .a("has sent you a duel request", "đã gửi lời mời duel cho bạn")
            .a("You are now in the Duels queue", "Bạn đang trong hàng chờ Duels")
            .a("Classic Duel", "Duel Cổ Điển")
            .a("UHC Duel", "Duel UHC")
            .a("SkyWars Duel", "Duel SkyWars")
            .a("Bridge Duel", "Duel Bridge")
            .a("Sumo Duel", "Duel Sumo")
            .a("OP Duel", "Duel OP")
            .a("Bow Duel", "Duel Cung");
    }

    private void buildMurder() {
        dMurder
            .a("INNOCENTS WIN!", "DÂN THƯỜNG THẮNG!")
            .a("MURDERER WINS!", "SÁT NHÂN THẮNG!")
            .a("Innocents Win", "Dân thường thắng")
            .a("Murderer Wins", "Sát nhân thắng")
            .a("The Murderer has been eliminated", "Sát nhân đã bị hạ")
            .a("You are the MURDERER", "Bạn là SÁT NHÂN")
            .a("You are a DETECTIVE", "Bạn là THÁM TỬ")
            .a("You are an INNOCENT", "Bạn là DÂN THƯỜNG")
            .a("You picked up a Bow", "Bạn đã nhặt cây cung")
            .a("The Detective has died", "Thám tử đã chết")
            .a("Murderer", "Sát nhân")
            .a("Detective", "Thám tử")
            .a("Innocent", "Dân thường")
            .a("Assassins", "Sát thủ")
            .a("Infection", "Lây nhiễm")
            .a("Showdown", "Đối đầu");
    }

    private void buildMini() {
        dMini
            // TNT Games
            .a("You have been eliminated!", "Bạn đã bị loại!")
            .a("Double Jump", "Nhảy đôi")
            .a("Double Jumps", "Nhảy đôi")
            .a("You are IT!", "Bạn là NGƯỜI BỊ TNT!")
            .a("is IT!", "là NGƯỜI BỊ TNT!")
            // Build Battle
            .a("Theme:", "Chủ đề:")
            .a("The theme is", "Chủ đề là")
            .a("Voting Phase", "Giai đoạn bình chọn")
            .a("Time's up!", "Hết giờ!")
            .a("Rate this build", "Chấm điểm công trình này")
            .a("Super Poor", "Rất tệ")
            .a("Glorious", "Xuất sắc")
            // Arcade / Party Games
            .a("Hole in the Wall", "Xuyên Tường")
            .a("Capture The Wool", "Cướp Len")
            // UHC / Speed UHC
            .a("Final Heal", "Hồi máu lần cuối")
            .a("PvP is now enabled!", "PvP đã được bật!")
            .a("Border will shrink", "Biên giới sẽ thu hẹp")
            .a("The border is shrinking", "Biên giới đang thu hẹp")
            .a("Pvp will be enabled in", "PvP sẽ bật sau")
            .a("Grace Period", "Thời gian ân hạn")
            // Housing / Lobby chung
            .a("Welcome to", "Chào mừng đến")
            .r("\\bhas won the game\\b", "đã thắng trận")
            .r("\\bwins the game\\b", "thắng trận");
    }

    private void buildSkyblock() {
        dSkyblock
            .a("Welcome to Hypixel SkyBlock!", "Chào mừng đến Hypixel SkyBlock!")
            .a("Latest update:", "Cập nhật mới nhất:")
            .a("Your profile was loaded", "Hồ sơ của bạn đã được tải")
            .a("You are playing on profile:", "Bạn đang chơi trên hồ sơ:")
            .a("Profile ID:", "ID hồ sơ:")
            .a("Warping you to your SkyBlock island...", "Đang dịch chuyển bạn tới đảo SkyBlock của bạn...")
            .a("Warping...", "Đang dịch chuyển...")
            .a("Sending to server", "Đang chuyển tới máy chủ")
            .a("RARE DROP!", "VẬT PHẨM HIẾM RƠI RA!")
            .a("VERY RARE DROP!", "VẬT PHẨM RẤT HIẾM RƠI RA!")
            .a("CRAZY RARE DROP!", "VẬT PHẨM CỰC HIẾM RƠI RA!")
            .a("PET DROP!", "THÚ CƯNG RƠI RA!")
            .a("SKILL LEVEL UP", "LÊN CẤP KỸ NĂNG")
            .a("You don't have enough coins!", "Bạn không đủ xu!")
            .a("You can't afford this", "Bạn không đủ tiền mua")
            .a("Your inventory is full!", "Túi đồ của bạn đã đầy!")
            .a("Your Island", "Đảo của bạn")
            .a("Auction House", "Nhà đấu giá");
    }

    private void buildMenu() {
        dMenu
            .a("Track your progress as you unlock", "Theo dõi tiến trình khi bạn mở khoá")
            .a("Achievements and rack up points.", "thành tựu và tích điểm.")
            .a("Click to view your achievements!", "Bấm để xem thành tựu của bạn!")
            .a("Achievements", "Thành tựu")
            .a("My Profile", "Hồ sơ của tôi")
            .a("Click to view!", "Bấm để xem!")
            .a("Click to open!", "Bấm để mở!")
            .a("Click to select!", "Bấm để chọn!")
            .a("Click to browse!", "Bấm để duyệt!")
            .a("Click to claim!", "Bấm để nhận!")
            .a("Click to toggle!", "Bấm để bật/tắt!")
            .a("Click to purchase!", "Bấm để mua!")
            .a("Click to teleport!", "Bấm để dịch chuyển!")
            .a("Click to play!", "Bấm để chơi!")
            .a("Click to go back!", "Bấm để quay lại!")
            .a("Left-click to", "Chuột trái để")
            .a("Right-click to", "Chuột phải để")
            .a("Go Back", "Quay lại")
            .a("Close", "Đóng")
            .a("Next Page", "Trang sau")
            .a("Previous Page", "Trang trước")
            .a("Page", "Trang")
            .a("Quests & Challenges", "Nhiệm vụ & Thử thách")
            .a("Settings & Visibility", "Cài đặt & Hiển thị")
            .a("Social Menu", "Menu xã hội")
            .a("Game Menu", "Menu trò chơi")
            .a("Lobby Selector", "Chọn sảnh")
            .a("Cosmetics", "Trang trí")
            .a("Rewards", "Phần thưởng")
            .a("Shop", "Cửa hàng")
            .a("Unlocked", "Đã mở khoá")
            .a("Locked", "Đã khoá")
            .a("Selected", "Đã chọn")
            .a("Enabled", "Đã bật")
            .a("Disabled", "Đã tắt");
    }

    private void loadCustom(File f) {
        if (!f.isFile()) return;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                if (k.startsWith("r:")) dCustom.r(k.substring(2), v);
                else dCustom.a(k, v);
            }
        } catch (Exception ignored) {
        } finally {
            try { if (br != null) br.close(); } catch (Exception ignored) { }
        }
    }
}
