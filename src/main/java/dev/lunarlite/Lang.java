package dev.lunarlite;

import java.util.HashMap;
import java.util.Map;

/** Bảng dịch tiếng Việt cho giao diện Zhwii Client. ID / key cấu hình nội bộ vẫn giữ nguyên tiếng Anh. */
public final class Lang {
    private Lang() {}

    private static final Map<String, String> VI = new HashMap<String, String>();

    private static void p(String en, String vi) { VI.put(en, vi); }

    static {
        // ---- tên module
        p("FPS", "FPS"); p("CPS", "CPS"); p("Keystrokes", "Phím bấm");
        p("Target Info", "Thông tin địch"); p("Combo", "Combo"); p("Armor Status", "Trạng thái giáp");
        p("Potion Status", "Hiệu ứng thuốc");
        p("Resource Counter", "Đếm vật phẩm");
        p("Speed", "Tốc độ"); p("Ping", "Ping"); p("Server Address", "Địa chỉ server");
        p("Toggle Sprint", "Chạy tự động"); p("Zoom", "Thu phóng");
        p("Fullbright", "Tăng sáng"); p("Time Changer", "Đổi thời gian"); p("Block Outline", "Viền block"); p("Item Physics", "Vật lý vật phẩm"); p("Scoreboard", "Bảng điểm");

        p("Hide Armor", "Ẩn giáp"); p("Hide Helmet", "Ẩn mũ"); p("Hide Chestplate", "Ẩn áo giáp"); p("Hide Leggings", "Ẩn quần giáp"); p("Hide Boots", "Ẩn giày");
        p("Show In Inventory Preview", "Vẫn hiện trong khung xem kho đồ");
        p("Ẩn giáp trên nhân vật của bạn (chỉ hiển thị phía bạn, không ảnh hưởng chỉ số giáp).", "Ẩn giáp trên nhân vật của bạn (chỉ phía bạn thấy, không ảnh hưởng chỉ số giáp).");

        // ---- tùy chỉnh
        p("Scale", "Tỉ lệ"); p("Text Color", "Màu chữ"); p("Background", "Nền"); p("Background opacity", "Độ mờ nền");
        p("Rounded Corners", "Bo góc"); p("Border", "Viền khung"); p("Text Shadow", "Bóng chữ");
        p("Rainbow", "Cầu vồng"); p("Mode", "Chế độ"); p("Saturation", "Độ bão hòa"); p("Brightness", "Độ sáng");
        p("Wave Size", "Cỡ sóng"); p("Speed", "Tốc độ");
        p("Reset After", "Đặt lại sau");
        p("Opacity", "Độ mờ"); p("Outline", "Viền");
        p("Color", "Màu"); p("Style", "Kiểu"); p("Hide Address (Streamer)", "Ẩn địa chỉ (Streamer)");
        p("Show Icon", "Hiện biểu tượng"); p("Preset", "Mẫu có sẵn"); p("Custom Time", "Giờ tùy chỉnh");
        p("Show Duration", "Hiện thời gian"); p("Use Effect Colors", "Màu theo hiệu ứng");
        p("Duration Bar", "Thanh thời gian"); p("Expiry Warning", "Cảnh báo sắp hết"); p("Sort By Time Left", "Xếp theo thời gian còn lại");
        p("Show Right Click", "Hiện chuột phải");
        p("Unit", "Đơn vị"); p("Decimals", "Số thập phân");
        p("Zoom FOV", "FOV khi zoom"); p("Activation", "Cách kích hoạt"); p("Cinematic Camera", "Camera mượt");
        p("Smooth Transition", "Chuyển cảnh mượt"); p("Transition Speed", "Tốc độ chuyển cảnh");
        p("Scroll To Adjust", "Lăn chuột để chỉnh"); p("Scroll Step", "Bước lăn chuột");
        p("Reduce Mouse Sensitivity", "Giảm độ nhạy chuột"); p("Zoom Sensitivity", "Độ nhạy khi zoom");
        p("Show Mouse Buttons", "Hiện nút chuột"); p("Show CPS", "Hiện CPS"); p("Show Spacebar", "Hiện phím cách");
        p("Show Status Text", "Hiện chữ trạng thái"); p("Label", "Nhãn");
        p("Show Held Item", "Hiện món đang cầm"); p("Durability", "Độ bền");
        p("Durability Color", "Màu độ bền"); p("Line Width", "Độ dày nét"); p("Through Walls", "Xuyên tường");
        p("Custom Red", "Đỏ (tùy chỉnh)"); p("Custom Green", "Lục (tùy chỉnh)"); p("Custom Blue", "Lam (tùy chỉnh)");
        p("Show Avatar", "Hiện avatar"); p("Show Health", "Hiện máu"); p("Show Health Text", "Hiện số máu");
        p("Show Distance", "Hiện khoảng cách"); p("Show Max Health", "Hiện máu tối đa"); p("Name Style", "Kiểu tên");
        p("Health Bar", "Thanh máu"); p("Max Range", "Tầm tối đa"); p("Bar Height", "Cao thanh máu");
        p("Card Width", "Rộng bảng"); p("Custom Health Color", "Màu máu tùy chỉnh"); p("Name Color", "Màu tên");

        // ---- giá trị của chế độ
        p("Wave", "Sóng"); p("Line", "Dải màu"); p("Solid", "Một màu"); p("Gradient", "Chuyển sắc"); p("Pulse", "Nhấp nháy");
        p("Custom", "Tùy chỉnh"); p("Day", "Ban ngày"); p("Sunset", "Hoàng hôn"); p("Night", "Ban đêm"); p("Midnight", "Nửa đêm");
        p("Blocks/s", "Khối/giây"); p("km/h", "km/giờ"); p("Hold", "Giữ phím"); p("Toggle", "Bấm bật/tắt");
        p("Percent", "Phần trăm"); p("Number", "Số"); p("Off", "Tắt");
        p("Display Name", "Tên hiển thị"); p("Username", "Tên tài khoản");
        p("Short", "Rút gọn"); p("Hidden", "Ẩn"); p("Green", "Xanh lá"); p("Red", "Đỏ");

        // ---- màu preset
        p("White", "Trắng"); p("Orange", "Cam"); p("Yellow", "Vàng"); p("Cyan", "Lam nhạt"); p("Blue", "Xanh dương");
        p("Purple", "Tím"); p("Pink", "Hồng"); p("Black", "Đen");

        // ---- mô tả (tooltip)
        p("Giáp đang mặc và vật phẩm cầm trên tay, kèm độ bền.", "Hiện giáp đang mặc và vật phẩm cầm tay, kèm độ bền.");
        p("Zoomify-style zoom with smooth transitions, scroll control and sensitivity options.",
          "Thu phóng mượt kiểu Zoomify, chỉnh bằng con lăn chuột và độ nhạy.");
        p("Shows the name, avatar and health of the living entity under your crosshair.",
          "Hiện tên, avatar và máu của mục tiêu dưới tâm ngắm.");
        p("Viền block đang nhìn vào đổi màu cầu vồng.", "Viền block đang nhìn vào đổi màu cầu vồng.");

        // ---- Enemy Outline
        p("Enemy Outline", "Viền kẻ địch"); p("Range", "Tầm xa");
        p("Show Invisible", "Hiện người tàng hình"); p("Ignore Teammates", "Bỏ qua đồng đội");
        p("Viền kẻ địch (người chơi khác), hỗ trợ rainbow.", "Vẽ viền ôm sát người chơi khác (cả giáp), có glow, xuyên tường và cầu vồng.");
        p("Smoothness", "Độ mượt"); p("Glow", "Hào quang"); p("Rainbow Outline", "Viền cầu vồng");

        // ---- Bow Trajectory
        p("Bow Trajectory", "Quỹ đạo cung"); p("Hit Color", "Màu khi trúng"); p("Max Flight Time", "Thời gian bay tối đa");
        p("Only When Drawing", "Chỉ khi giương cung"); p("Landing Marker", "Đánh dấu điểm rơi");
        p("Highlight Target", "Làm nổi mục tiêu");

        // ---- Subtle Effects
        p("Subtle Effects", "Hiệu ứng tinh tế");
        p("Block Effects", "Hiệu ứng khối"); p("Entity Effects", "Hiệu ứng sinh vật"); p("Movement", "Chuyển động"); p("General", "Chung");
        p("Torch Sparks", "Tia lửa đuốc"); p("Fire Sparks", "Tia lửa từ lửa"); p("Furnace Sparks", "Tia lửa lò nung");
        p("Lava Embers", "Tàn lửa dung nham"); p("Updated Smoke", "Khói mềm"); p("Falling Leaves", "Lá rơi");
        p("Glowing Block Dust", "Bụi sáng quanh khối"); p("Frosty Breath", "Hơi thở lạnh"); p("Burning Smoke", "Khói khi cháy");
        p("TNT Sparks", "Tia lửa TNT"); p("Item Rarity Sparkles", "Vật phẩm hiếm lấp lánh"); p("Falling Block Dust", "Bụi khối đang rơi");
        p("Animations", "Hiệu ứng chuyển động"); p("Menu Animations", "Animation menu"); p("HUD Animations", "Animation HUD"); p("Animation Speed", "Tốc độ animation"); p("Inventory Animations", "Animation kho đồ");
        p("World Animations", "Animation thế giới"); p("Ambient Snow", "Tuyết rơi nhẹ"); p("Fireflies", "Đom đóm"); p("Potion Swirl", "Xoáy hiệu ứng thuốc");
        p("Projectiles", "Vật bay"); p("Biomes", "Quần xã");
        p("Beacon Sparkles", "Đèn hiệu lấp lánh"); p("Dragon Egg Glow", "Trứng rồng phát sáng"); p("End Portal Glow", "Cổng End phát sáng");
        p("Brewing Stand Steam", "Hơi nước bàn pha thuốc"); p("Creeper Fuse Sparks", "Tia lửa Creeper sắp nổ");
        p("Chicken Feathers", "Lông gà bay"); p("Snow Golem Flakes", "Bông tuyết Snow Golem"); p("Slime Trails", "Vệt Slime");
        p("Magma Cube Trails", "Vệt Magma Cube"); p("End Crystal Glow", "Pha lê End phát sáng"); p("Drowning Bubbles", "Bong bóng khi ngạt nước");
        p("Extinguish Steam", "Hơi nước khi tắt lửa"); p("Mob Running Dust", "Bụi khi mob chạy"); p("Ender Pearl Trail", "Vệt Ender Pearl");
        p("Snowball Trail", "Vệt cầu tuyết"); p("XP Bottle Sparkles", "Lấp lánh bình XP"); p("Pollen", "Phấn hoa"); p("Mushroom Spores", "Bào tử nấm");
        p("Lava Rain Steam", "Hơi nước mưa trên dung nham"); p("Sparks Scale", "Cỡ tia lửa"); p("Smoke Opacity", "Độ đặc của khói"); p("Entity Range", "Tầm sinh vật");
        p("Sprint Dust", "Bụi khi chạy"); p("Landing Dust", "Bụi khi rơi xuống"); p("Density", "Mật độ"); p("Scan Range", "Phạm vi quét");

        // ---- Far Horizons (LOD)
        p("Far Horizons", "Chân trời xa"); p("LOD Distance", "Tầm LOD");
        p("Vẽ địa hình xa bằng LOD ngoài tầm vẽ của game (kiểu Distant Horizons).", "Vẽ địa hình xa (LOD) ngoài tầm vẽ của game, kiểu Distant Horizons. Chỉ thấy vùng đã từng tải.");

        // ---- Aim Assist
        p("Aim Assist", "Hỗ trợ ngắm"); p("Max Range", "Tầm tối đa"); p("Aim FOV", "Góc ngắm");
        p("Aim Smoothing", "Độ mượt khi ngắm"); p("Max Turn Speed", "Tốc độ xoay tối đa");
        p("Only While Attacking", "Chỉ khi đang đánh"); p("Ignore Invisible", "Bỏ qua người tàng hình");

        // ---- Velocity
        p("Velocity", "Giảm knockback"); p("Horizontal", "Ngang"); p("Vertical", "Dọc");
        p("Giảm lực đẩy (knockback) khi bị đánh theo tỉ lệ ngang / dọc.", "Giảm lực đẩy (knockback) khi bị đánh. Ngang / Dọc là % lực còn lại (100% = không đổi, 0% = không bị đẩy).");

        // ---- Bridge Assist
        p("Bridge Assist", "Hỗ trợ bridge"); p("Edge Crouch", "Tự ngồi ở mép"); p("Bridge Aim", "Tự chỉnh góc bridge");
        p("Auto Place", "Tự đặt block"); p("Only Holding Blocks", "Chỉ khi cầm block"); p("Min Pitch", "Góc nhìn xuống tối thiểu");
        p("Place Delay", "Độ trễ đặt block");
        p("Hỗ trợ bridge nhanh: tự ngồi ở mép, chỉnh góc ngắm và đặt block.", "Hỗ trợ bridge nhanh (bridge lùi): tự ngồi ở mép block, chỉnh góc nhìn xuống mặt bên block đang đứng và tự đặt block khi tâm ngắm trúng.");

        // ---- nhóm tùy chỉnh (tiêu đề mục trong menu)
        p("Appearance", "Giao diện"); p("Options", "Tùy chọn"); p("Loot Beam", "Tia sáng vật phẩm");
        p("Target Info", "Thông tin địch");

        // ---- Hiệu ứng (Effect)
        p("Sword Trail", "Vệt kiếm"); p("Hit Effect", "Hiệu ứng đánh trúng"); p("Kill Effect", "Hiệu ứng hạ gục");
        p("Jump Dust", "Bụi khi nhảy"); p("Dust Type", "Loại bụi"); p("Particle Trail", "Vệt hạt");
        p("Particle", "Loại hạt"); p("Effect", "Hiệu ứng"); p("Amount", "Số lượng"); p("Spread", "Độ tản");
        p("Trail Length", "Độ dài vệt"); p("Blade Length", "Chiều dài lưỡi"); p("Max Radius", "Bán kính tối đa");
        p("Duration", "Thời lượng"); p("Only When Moving", "Chỉ khi di chuyển");
        p("Show In First Person", "Hiện ở góc nhìn thứ nhất"); p("Show With Empty Hand", "Hiện khi tay không");
        p("Crit", "Chí mạng"); p("Magic Crit", "Chí mạng phép"); p("Flame", "Lửa"); p("Heart", "Tim"); p("Note", "Nốt nhạc");
        p("Smoke", "Khói"); p("Portal", "Cổng"); p("Cloud", "Mây"); p("Blood", "Máu"); p("Slime", "Slime");
        p("Happy Villager", "Dân làng vui"); p("Firework", "Pháo hoa");
        p("Lightning", "Sét"); p("Explosion", "Nổ"); p("Fireworks", "Pháo hoa"); p("Soul Burst", "Linh hồn bay lên");
        p("Hearts", "Trái tim"); p("Flames", "Ngọn lửa");
        p("Vệt sáng theo đường vung tay / kiếm của bạn.", "Vệt sáng theo đường vung tay / kiếm của bạn, hỗ trợ cầu vồng.");

        p("Hiện đường bay và điểm rơi của mũi tên khi giương cung.", "Vẽ đường bay và điểm rơi dự đoán của mũi tên khi giương cung; đổi màu khi trúng người.");
    }

    /** Dịch một chuỗi; không có trong bảng thì trả lại nguyên văn. */
    public static String vi(String s) {
        if (s == null) return "";
        String t = VI.get(s);
        return t != null ? t : s;
    }
}
