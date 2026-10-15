# Zhwii Client

Client HUD / PvP tiếng Việt cho **Minecraft 1.8.9 (Forge)**. Giao diện tối giản, bám phong cách Minecraft gốc. Mọi tính năng đều bật/tắt và chỉnh được trong menu, các HUD kéo thả được tới vị trí tuỳ ý.

> Mod chỉ chạy phía client và chỉ hỗ trợ Minecraft 1.8.9.

## Tính năng

| Nhóm | Module |
|---|---|
| **PvP** | Keystrokes, CPS, Bow Trajectory, Thông tin mục tiêu, Combo, Bảng kill (Bedwars), Aim Assist, Armor Status, Potion Status, Thông báo nhặt đồ, Phát hiện kẻ địch, Hotbar Animation |
| **HUD** | FPS, Ping, Server Address |
| **Hình ảnh** | Zoom, Fullbright, Block Outline, Item Physics, Animations |
| **Tiện ích** | Toggle Sprint, Ẩn Scoreboard, Chat Bar, Màu giao diện |

### Điểm nhấn

- **Thông tin mục tiêu:** thẻ gọn gồm avatar, tên, % máu và thanh máu. Hiện được cả người chơi lẫn mob.
- **Phát hiện kẻ địch:** hiện người chơi trong bán kính bạn chọn, kèm khoảng cách, máu và viền kẻ địch.
- **Bảng kill:** số kill của bản thân và trạng thái giường từng đội trong Bedwars.
- **Zoom:** thu phóng mượt kiểu Zoomify, chỉnh độ nhạy bằng con lăn chuột.
- **Block Outline:** viền block đang nhìn vào đổi màu cầu vồng. Bảng cầu vồng được dùng chung cho nhiều module.
- **Item Physics:** vật phẩm rơi nằm phẳng trên đất, có tia sáng theo độ hiếm.
- **Animations:** animation mở/đóng menu, hover nút, chuyển trang, hiện HUD, mở kho đồ / rương.
- **Chat Bar:** thanh chat bo góc kiểu kính, chỉ hiện tin người chơi, avatar và giường team, tự dịch tin nhận sang tiếng Việt và tin gửi sang tiếng Anh, có thể kéo khung chat tới vị trí tuỳ ý.
- **Màu giao diện:** chọn màu nhấn có sẵn hoặc tự trộn màu cho menu và trình chỉnh HUD.

### Tính năng khác

- **Rung block khi đào:** block đang đào rung nhẹ và game tắt hạt vỡ block. Bật/tắt bằng phím tuỳ chỉnh (mặc định chưa gán) hoặc trong file config (`blockShake`).
- **Kho đồ không nền đen:** khi mở kho đồ hoặc rương thường, lớp nền tối phủ toàn màn hình được bỏ. Tắt trong file config (`cleanInv`). Nếu module Animations đang bật thì animation mở kho đồ do module đó đảm nhiệm.

## Phím mặc định

| Phím | Chức năng |
|---|---|
| `Right Shift` | Mở menu Zhwii Client |
| `C` | Zoom |
| `R` | Bật/tắt Aim Assist |
| *(chưa gán)* | Bật/tắt rung block |

Đổi phím trong *Cài đặt → Điều khiển → Zhwii Client*.

Trong menu: bấm tên tính năng để chỉnh bên phải, chuột phải lên tên để bật/tắt nhanh, `↑/↓` để đổi tính năng, nút **Sửa vị trí HUD…** để kéo thả HUD.

## Cài đặt

1. Cài Minecraft **1.8.9** với Forge **11.15.1.2318**.
2. Chép file `ZhwiiClient-1.8.9-2.0.0.jar` vào thư mục `mods`.
3. Mở game, nhấn `Right Shift` để vào menu.

## Build từ mã nguồn

Yêu cầu: **JDK 8**, **Gradle 2.14.1**, Forge `1.8.9-11.15.1.2318` (ForgeGradle 2.1).

```bash
gradle setupDecompWorkspace   # chạy 1 lần để chuẩn bị Forge
gradle build                  # file jar nằm ở build/libs/
```

Repo có sẵn workflow GitHub Actions (`.github/workflows/build.yml`) để tự build và tải jar. Có thể chạy tay từ tab *Actions*.

### Cấu trúc mã nguồn

```
src/main/java/dev/lunarlite/
├── LunarLite.java   # lớp mod chính, đăng ký module, phím, sự kiện
├── Lang.java        # bản dịch tiếng Việt
├── gui/             # menu, trình chỉnh vị trí HUD, hàm vẽ
├── module/          # các module tính năng
└── setting/         # kiểu cài đặt: bool, slider, mode, màu, cầu vồng
```

Muốn thêm tính năng mới: tạo class kế thừa `Module` (hoặc `HudModule` / `TextHudModule` cho HUD) trong `module/`, rồi thêm vào danh sách `MODULES` ở `LunarLite.init`.

## Lưu ý

- Config cũ giữ giá trị cũ. Xoá file config hoặc bấm **Reset** trong menu để dùng giá trị mặc định mới.
- **Chat Bar dịch tin nhắn qua dịch vụ Google Translate**, nên cần có mạng và nội dung chat sẽ được gửi tới dịch vụ này. Tắt tuỳ chọn dịch trong menu nếu không muốn.
- Một số tính năng có thể bị server cấm (ví dụ Aim Assist, Phát hiện kẻ địch). Hãy kiểm tra luật server trước khi dùng. Người dùng tự chịu trách nhiệm khi sử dụng.
- Mã nguồn chưa được kiểm thử đầy đủ. Nếu gặp lỗi khi build hoặc chạy, hãy mở issue kèm log.

## Bản quyền

Tôn trọng bản quyền của tác giả gốc. Nếu phân phối lại, hãy giữ thông tin ghi công và tuân thủ giấy phép của dự án gốc.
