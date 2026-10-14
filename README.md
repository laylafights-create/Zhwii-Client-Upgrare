# Zhwii Client

Client HUD / PvP tiếng Việt cho **Minecraft 1.8.9** (Forge), giao diện tối giản, bám phong cách Minecraft gốc. Mọi tính năng đều chỉnh được trong menu, kéo thả vị trí HUD tuỳ ý.

## ✨ Tính năng

| Nhóm | Tính năng |
|---|---|
| **PvP** | Keystrokes, CPS, Bow Trajectory, Thông tin mục tiêu, Combo, Aim Assist, Armor Status, Potion Status, Resource Counter, Item Pickup, Phát hiện kẻ địch (kèm viền kẻ địch) |
| **HUD** | FPS, Speed, Ping, Server Address, Ẩn Scoreboard |
| **Hình ảnh** | Zoom (mượt, chỉnh bằng con lăn), Fullbright, Time Changer, Block Outline, Item Physics, Animations |
| **Hiệu ứng** | Sword Trail (vệt kiếm), Hit Effect, Kill Effect (sét/nổ/pháo hoa…), Jump Circle, Particle Trail |
| **Tiện ích** | Toggle Sprint |

Điểm nhấn:
- **Thông tin mục tiêu**: thẻ gọn gàng gồm avatar, tên, % máu và thanh máu; hiện được cả người chơi lẫn mob, chỉnh được màu, độ rộng, tầm xa.
- **Keystrokes**: tuỳ chỉnh màu, độ mờ và màu chữ khi bấm phím.
- **Cầu vồng** dùng chung cho nhiều module (viền, outline…).

## 🎮 Phím mặc định

| Phím | Chức năng |
|---|---|
| `Right Shift` | Mở menu Zhwii Client |
| `C` | Zoom |
| `R` | Bật/tắt Aim Assist |

Đổi phím trong *Cài đặt → Điều khiển → Zhwii Client*. Trong menu: bấm tên tính năng để chỉnh bên phải, chuột phải lên tên để bật/tắt nhanh, `↑/↓` đổi tính năng, nút **Sửa vị trí HUD…** để kéo thả HUD.

## 🛠️ Build từ mã nguồn

Yêu cầu: **JDK 8**, **Gradle 2.14.1**, Forge `1.8.9-11.15.1.2318` (ForgeGradle 2.1).

```bash
gradle setupDecompWorkspace   # chạy 1 lần để chuẩn bị Forge
gradle build                  # file jar nằm ở build/libs/
```

Chép file `ZhwiiClient-1.8.9-2.0.0.jar` vào thư mục `mods` của Minecraft 1.8.9 có cài Forge. Repo cũng có sẵn workflow GitHub Actions (`.github/workflows/build.yml`) để tự build và tải jar.

## ⚠️ Lưu ý

- Mod chỉ chạy phía client, chỉ hỗ trợ Minecraft **1.8.9**.
- Config cũ giữ giá trị cũ; xoá file config hoặc bấm **Reset** trong menu để dùng giá trị mặc định mới.
- Một số tính năng có thể bị server cấm (ví dụ Aim Assist). Hãy kiểm tra luật server trước khi dùng.
- Mã nguồn chưa được kiểm thử đầy đủ, nếu gặp lỗi khi build hoặc chạy, hãy mở issue kèm log.

## 📜 Bản quyền

Tôn trọng bản quyền của tác giả gốc. Nếu phân phối lại, hãy giữ thông tin ghi công và tuân thủ giấy phép của dự án gốc.
