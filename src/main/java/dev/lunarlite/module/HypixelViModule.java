package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Việt hoá chat Hypixel (không HUD). Giữ nguyên màu, click và hover của tin nhắn.
 * Tin nhắn của người chơi khác KHÔNG bị dịch (chỉ dịch tin hệ thống).
 * Từ điển riêng: config/lunarlite/hypixel_vi.txt (UTF-8)
 *   English=Tiếng Việt     |     r:regex=thay thế ($1, $2)     |     # ghi chú
 */
public class HypixelViModule extends Module {

    private final BoolSetting onlyHypixel = add(new BoolSetting("only_hypixel", "Only On Hypixel", true));
    private final BoolSetting general = add(new BoolSetting("general", "Lobby & General", true));
    private final BoolSetting social = add(new BoolSetting("social", "Party & Friends", true));
    private final BoolSetting bedwars = add(new BoolSetting("bedwars", "Bed Wars", true));
    private final BoolSetting custom = add(new BoolSetting("custom", "Custom Dictionary", true));

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
    private final Dict dCustom = new Dict();

    /** Tin chat của người chơi: [rank] Tên: nội dung (kể cả Party >, Guild >, From, To). */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:(?:Party|Guild|Officer|Co-op) > )?(?:(?:From|To) )?(?:\\[[^\\]]+\\]\\s*)*\\w{3,16}: .*", Pattern.DOTALL);

    public HypixelViModule() {
        super("hypixelvi", "Vietsub Hypixel", "Việt hoá tin nhắn hệ thống của Hypixel (lobby, party, Bed Wars).",
                Category.UTILITY, false);
        buildGeneral();
        buildSocial();
        buildBedwars();
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
        e.message = translate(e.message);
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
        if (social.value) s = dSocial.apply(s);
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
