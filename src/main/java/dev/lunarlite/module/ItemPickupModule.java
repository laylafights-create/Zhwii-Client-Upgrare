package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Thông báo nhặt đồ: thẻ bo góc xếp dọc, canh phải. Mỗi thẻ gồm vạch màu bên trái, ô icon, tên vật phẩm
 * và huy hiệu số lượng "3x" bên phải, thanh thời gian mảnh ở đáy.
 * Phát hiện bằng cách so sánh túi đồ (và điểm kinh nghiệm) giữa các tick.
 *
 * Hiệu ứng: dòng mới trượt vào từ phải (nảy nhẹ) + mờ hiện dần, biểu tượng bật to ra, số lượng đếm tăng dần,
 * nhặt thêm cùng món thì dòng nháy sáng và chữ nảy lên, các dòng tự trượt lên lấp chỗ trống,
 * thanh thời gian chạy dưới mỗi dòng, hết giờ thì trượt ra và mờ dần.
 */
public class ItemPickupModule extends HudModule {
    private static final int W = 130, ROW_H = 24, STRIDE = 27, ICON = 14;
    private static final int XP_COLOR = 0xFFFF55;

    private static final class Entry {
        String key, name;
        ItemStack icon; // null = Kinh nghiệm
        int count;
        long born, time;
        long bump;       // lần nhặt thêm gần nhất (0 = chưa có)
        float posY;      // vị trí dòng đang hiển thị (đơn vị: số dòng), trượt dần về vị trí thật
        float shown;     // số lượng đang hiển thị (đếm tăng dần)
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private Map<String, Integer> last;
    private int lastXp = -1;
    private EntityPlayer owner;

    private final SliderSetting duration = add(new SliderSetting("duration", "Thời gian hiện", 3, 1, 10, 1, "s"), "Thông báo");
    private final SliderSetting maxEntries = add(new SliderSetting("maxentries", "Số dòng tối đa", 5, 1, 10, 1, ""), "Thông báo");
    private final BoolSetting viNames = add(new BoolSetting("vi_names", "Tên vật phẩm tiếng Việt", true), "Thông báo");
    private final BoolSetting animOn = add(new BoolSetting("anim", "Hiệu ứng chuyển động", true), "Hiệu ứng");
    private final BoolSetting bumpFx = add(new BoolSetting("bump", "Nảy và nháy khi nhặt thêm", true), "Hiệu ứng");
    private final BoolSetting timerBar = add(new BoolSetting("timer_bar", "Thanh thời gian", true), "Hiệu ứng");

    public ItemPickupModule() {
        super("itempickup", "Thông báo nhặt đồ", "Thông báo khi nhặt vật phẩm / kinh nghiệm, kèm icon vật phẩm.",
                Category.HUD, false, 300, 150);
        border.withDefault(false);
    }

    // ------------------------------------------------------------------ tên tiếng Việt

    private static final Map<String, String> VI = new HashMap<String, String>();

    private static void v(String en, String vi) {
        VI.put(en.toLowerCase(Locale.ROOT), vi);
    }

    static {
        v("Wheat Seeds", "Hạt lúa mì"); v("Wheat", "Lúa mì"); v("Rotten Flesh", "Thịt thối");
        v("Diamond", "Kim cương"); v("Iron Ingot", "Thỏi sắt"); v("Gold Ingot", "Thỏi vàng");
        v("Emerald", "Ngọc lục bảo"); v("Coal", "Than đá"); v("Charcoal", "Than củi");
        v("Redstone", "Bột đá đỏ"); v("Lapis Lazuli", "Ngọc lưu ly"); v("Stick", "Gậy");
        v("String", "Sợi chỉ"); v("Feather", "Lông vũ"); v("Flint", "Đá lửa"); v("Bone", "Xương");
        v("Arrow", "Mũi tên"); v("Bow", "Cung"); v("Gunpowder", "Thuốc súng"); v("Slimeball", "Cục nhớt");
        v("Ender Pearl", "Ngọc ender"); v("Eye of Ender", "Mắt ender"); v("Blaze Rod", "Gậy blaze");
        v("Blaze Powder", "Bột blaze"); v("Nether Wart", "Mụn Nether"); v("Ghast Tear", "Nước mắt Ghast");
        v("Golden Apple", "Táo vàng"); v("Apple", "Táo"); v("Bread", "Bánh mì"); v("Steak", "Bít tết");
        v("Raw Beef", "Thịt bò sống"); v("Cooked Porkchop", "Sườn heo nướng"); v("Raw Porkchop", "Sườn heo sống");
        v("Cooked Chicken", "Gà nướng"); v("Raw Chicken", "Gà sống"); v("Carrot", "Cà rốt");
        v("Potato", "Khoai tây"); v("Baked Potato", "Khoai tây nướng"); v("Melon", "Dưa hấu");
        v("Pumpkin Seeds", "Hạt bí ngô"); v("Melon Seeds", "Hạt dưa hấu"); v("Sugar", "Đường");
        v("Egg", "Trứng"); v("Leather", "Da"); v("Paper", "Giấy"); v("Book", "Sách");
        v("Clay", "Cục đất sét"); v("Brick", "Gạch"); v("Snowball", "Cầu tuyết");
        v("Bucket", "Xô"); v("Water Bucket", "Xô nước"); v("Lava Bucket", "Xô dung nham");
        v("Cobblestone", "Đá cuội"); v("Stone", "Đá"); v("Dirt", "Đất"); v("Sand", "Cát"); v("Gravel", "Sỏi");
        v("Obsidian", "Obsidian"); v("Glowstone Dust", "Bột đá phát sáng"); v("Glowstone", "Đá phát sáng");
        v("Torch", "Đuốc"); v("TNT", "TNT"); v("Diamond Sword", "Kiếm kim cương"); v("Iron Sword", "Kiếm sắt");
        v("Diamond Pickaxe", "Cuốc chim kim cương"); v("Iron Pickaxe", "Cuốc chim sắt");
        v("Fishing Rod", "Cần câu"); v("Bowl", "Bát"); v("Mushroom Stew", "Súp nấm");
        v("Red Mushroom", "Nấm đỏ"); v("Brown Mushroom", "Nấm nâu"); v("Spider Eye", "Mắt nhện");
        v("Magma Cream", "Kem magma"); v("Bottle o' Enchanting", "Bình kinh nghiệm");
        v("Enchanted Book", "Sách phù phép"); v("Name Tag", "Thẻ tên");
    }

    private String displayName(Entry e) {
        if (e.icon == null) return viNames.value ? "Kinh nghiệm" : "Experience Orb";
        if (!viNames.value) return e.name;
        String t = VI.get(e.name.toLowerCase(Locale.ROOT));
        return t != null ? t : e.name;
    }

    // ------------------------------------------------------------------ theo dõi túi đồ

    @Override
    public void onToggle() {
        last = null;
        lastXp = -1;
        entries.clear();
        lossCredit.clear();
        lossTime.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (!enabled || p == null) {
            last = null;
            lastXp = -1;
            owner = null;
            return;
        }

        Map<String, Integer> cur = new HashMap<String, Integer>();
        Map<String, ItemStack> samples = new HashMap<String, ItemStack>();
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null) continue;
            String key = s.getUnlocalizedName() + ":" + (s.getHasSubtypes() ? s.getMetadata() : 0);
            Integer c = cur.get(key);
            cur.put(key, (c == null ? 0 : c) + s.stackSize);
            if (!samples.containsKey(key)) {
                ItemStack one = s.copy();
                one.stackSize = 1;
                samples.put(key, one);
            }
        }
        int xp = p.experienceTotal;

        // đổi thế giới / đang mở rương, túi đồ: chỉ cập nhật mốc, không báo
        boolean quiet = last == null || owner != p || mc.currentScreen instanceof GuiContainer;
        long nowT = System.currentTimeMillis();
        if (quiet) {
            lossCredit.clear();
            lossTime.clear();
        } else {
            // Ghi nhận phần bị mất (đặt block, ném, dùng...) để khi server trả lại số lượng cũ thì không báo nhầm là nhặt.
            for (Map.Entry<String, Integer> e : last.entrySet()) {
                Integer c = cur.get(e.getKey());
                int drop = e.getValue() - (c == null ? 0 : c);
                if (drop > 0) {
                    Integer old = lossCredit.get(e.getKey());
                    Long ot = lossTime.get(e.getKey());
                    boolean fresh = old != null && ot != null && nowT - ot <= LOSS_WINDOW;
                    lossCredit.put(e.getKey(), (fresh ? old : 0) + drop);
                    lossTime.put(e.getKey(), nowT);
                }
            }
            for (Map.Entry<String, Integer> e : cur.entrySet()) {
                Integer prev = last.get(e.getKey());
                int gain = e.getValue() - (prev == null ? 0 : prev);
                if (gain > 0) gain = absorb(e.getKey(), gain, nowT);
                ItemStack st = samples.get(e.getKey());
                if (gain > 0) push(e.getKey(), st.getDisplayName(), st, gain);
            }
            if (lastXp >= 0 && xp > lastXp) push("xp", "Experience Orb", null, xp - lastXp);
        }
        last = cur;
        lastXp = xp;
        owner = p;

        long now = System.currentTimeMillis(), life = duration.getInt() * 1000L;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (now - entries.get(i).time > life) entries.remove(i);
        }
    }

    private static final long LOSS_WINDOW = 1500L;
    private final Map<String, Integer> lossCredit = new HashMap<String, Integer>();
    private final Map<String, Long> lossTime = new HashMap<String, Long>();

    /** Trừ phần vừa mất gần đây khỏi lượng "tăng"; trả về phần tăng thật sự (nhặt). */
    private int absorb(String key, int gain, long now) {
        Integer cr = lossCredit.get(key);
        Long t = lossTime.get(key);
        if (cr == null || t == null || now - t > LOSS_WINDOW) {
            lossCredit.remove(key);
            lossTime.remove(key);
            return gain;
        }
        int used = Math.min(cr, gain);
        if (cr - used <= 0) {
            lossCredit.remove(key);
            lossTime.remove(key);
        } else {
            lossCredit.put(key, cr - used);
        }
        return gain - used;
    }

    private void push(String key, String name, ItemStack icon, int gain) {
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            if (e.key.equals(key)) {
                e.count += gain;
                e.time = now;
                e.bump = now;
                return;
            }
        }
        Entry e = new Entry();
        e.key = key;
        e.name = name;
        e.icon = icon;
        e.count = gain;
        e.born = e.time = now;
        e.posY = entries.size();
        e.shown = gain > 1 ? gain * 0.3f : gain;
        entries.add(e);
        while (entries.size() > maxEntries.getInt()) entries.remove(0);
    }

    private List<Entry> rows(boolean preview) {
        if (entries.isEmpty() && preview) {
            long now = System.currentTimeMillis();
            List<Entry> demo = new ArrayList<Entry>();
            demo.add(sample("Wheat Seeds", new ItemStack(Items.wheat_seeds), 1, now));
            demo.add(sample("Rotten Flesh", new ItemStack(Items.rotten_flesh), 1, now));
            demo.add(sample("Experience Orb", null, 10, now));
            return demo;
        }
        return entries;
    }

    private static Entry sample(String name, ItemStack icon, int count, long now) {
        Entry e = new Entry();
        e.key = name;
        e.name = name;
        e.icon = icon;
        e.count = count;
        e.born = e.time = now + 100000L; // luôn hiện rõ, không mờ dần
        return e;
    }

    @Override
    public int getWidth(boolean preview) {
        return rows(preview).isEmpty() ? 0 : W;
    }

    @Override
    public int getHeight(boolean preview) {
        int n = rows(preview).size();
        return n == 0 ? 0 : n * STRIDE - (STRIDE - ROW_H);
    }

    // ------------------------------------------------------------------ hiệu ứng

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Nhanh dần rồi vọt quá đích một chút và quay lại (0 -> 1, có overshoot). */
    private static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        List<Entry> list = rows(preview);
        long now = System.currentTimeMillis(), life = duration.getInt() * 1000L;
        boolean a = !preview && animOn.value && AnimationsModule.hud();
        float sp = Math.max(0.2f, (float) AnimationsModule.speed());

        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            long age = now - e.time;

            // --- độ mờ / trượt vào - ra
            float outT = preview ? 0f : clamp01((400f - (life - age)) / 400f);
            float inT = a ? clamp01((now - e.born) * sp / 380f) : 1f;
            float slide = a ? easeOutBack(inT) : 1f;
            float fade = Math.min(a ? clamp01(inT * 3f) : 1f, 1f - outT);
            if (fade <= 0f) continue;
            int dx = a ? Math.round((1f - slide) * 46f + outT * outT * 40f) : 0;

            // --- trượt lên lấp chỗ trống
            if (a) {
                e.posY += (i - e.posY) * Math.min(1f, 0.22f * sp);
                if (Math.abs(i - e.posY) < 0.01f) e.posY = i;
            } else {
                e.posY = i;
            }
            int ry = y + Math.round(e.posY * STRIDE);

            // --- số lượng đếm tăng dần
            if (a) {
                e.shown += (e.count - e.shown) * Math.min(1f, 0.3f * sp);
                if (Math.abs(e.count - e.shown) < 0.5f) e.shown = e.count;
            } else {
                e.shown = e.count;
            }
            int shownCount = Math.max(1, Math.round(e.shown));

            // --- nảy / nháy khi nhặt thêm
            float pulse = 0f;
            if (a && bumpFx.value && e.bump > 0) {
                float bt = clamp01((now - e.bump) * sp / 320f);
                pulse = (1f - bt) * (1f - bt);
            }

            String cnt = shownCount + "x";
            String name = fr().trimStringToWidth(displayName(e), W - 62);
            int slot = ICON + 4;
            int cw = fr().getStringWidth(cnt) + 8;
            int rw = 7 + slot + 5 + fr().getStringWidth(name) + 6 + cw + 4;
            int rx = x + W - rw + dx;
            int rgb = e.icon == null ? XP_COLOR : (color.rgb() & 0xFFFFFF);
            int ta = Math.round(255 * fade);

            // --- thẻ nền bo góc, nháy sáng khi nhặt thêm, viền
            if (background.value) {
                int bgA = Math.round(Math.max(120, (bgColor() >>> 24) & 0xFF) * fade);
                Draw.rrect(rx, ry, rx + rw, ry + ROW_H, 4, (bgA << 24) | 0x0E1116);
            }
            if (pulse > 0.02f) Draw.rrect(rx, ry, rx + rw, ry + ROW_H, 4, (Math.round(60f * pulse * fade) << 24) | 0xFFFFFF);
            if (border.value) drawBorder(rx, ry, rw, ROW_H);

            // --- vạch màu bên trái + ô icon
            if (ta >= 4) Draw.rrect(rx + 2, ry + 3, rx + 4, ry + ROW_H - 4, 1, (ta << 24) | rgb);
            int sx = rx + 7, sy = ry + 2;
            int sa = Math.round(70f * fade);
            if (sa >= 4) Draw.rrect(sx, sy, sx + slot, sy + slot, 3, (sa << 24) | rgb);

            // --- thanh thời gian mảnh ở đáy thẻ
            if (timerBar.value && !preview) {
                float left = clamp01(1f - (float) age / (float) life);
                int full = rw - 12, bw = Math.round(full * left);
                int ta2 = Math.round(40f * fade), ba = Math.round(190f * fade);
                if (ta2 >= 4) Draw.rrect(rx + 6, ry + ROW_H - 3, rx + 6 + full, ry + ROW_H - 2, 1, (ta2 << 24) | 0xFFFFFF);
                if (bw > 2 && ba >= 4) Draw.rrect(rx + 6, ry + ROW_H - 3, rx + 6 + bw, ry + ROW_H - 2, 1, (ba << 24) | rgb);
            }

            // --- tên vật phẩm
            int nx = sx + slot + 5, ty = ry + 7;
            if (rainbow.active()) {
                drawText(name, nx, ty, i * STRIDE);
            } else if (ta >= 4) {
                fr().drawString(name, nx, ty, (ta << 24) | 0xF2F2F2, shadow.value);
            }

            // --- huy hiệu số lượng (phóng nhẹ khi nảy)
            int px = rx + rw - 4 - cw, py = ry + 5;
            int pa = Math.round(95f * fade);
            if (pa >= 4) Draw.rrect(px, py, px + cw, py + 12, 5, (pa << 24) | rgb);
            if (ta >= 4) {
                float ts = 1f + 0.22f * pulse;
                float ccx = px + cw / 2f, ccy = py + 6f;
                GlStateManager.pushMatrix();
                GlStateManager.translate(ccx, ccy, 0f);
                GlStateManager.scale(ts, ts, 1f);
                GlStateManager.translate(-fr().getStringWidth(cnt) / 2f, -4f, 0f);
                fr().drawString(cnt, 0, 0, (ta << 24) | 0xFFFFFF, true);
                GlStateManager.popMatrix();
            }

            // --- biểu tượng: bật to ra khi mới nhặt, nảy khi nhặt thêm, thu nhỏ khi biến mất
            float pop = a ? easeOutBack(clamp01((now - e.born - 60L) * sp / 320f)) : 1f;
            float is = Math.max(0f, pop) * (1f + 0.25f * pulse) * fade;
            int ix = sx + (slot - ICON) / 2, iy = sy + (slot - ICON) / 2;
            if (is > 0.05f) {
                if (e.icon != null) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(ix + ICON / 2f, iy + ICON / 2f, 0f);
                    GlStateManager.scale(ICON / 16f * is, ICON / 16f * is, 1f);
                    GlStateManager.translate(-8f, -8f, 0f);
                    RenderHelper.enableGUIStandardItemLighting();
                    Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(e.icon, 0, 0);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                    GlStateManager.disableLighting();
                    GlStateManager.enableBlend();
                    GlStateManager.color(1f, 1f, 1f, 1f);
                } else if (ta >= 4) {
                    int cx = ix + ICON / 2, cy = iy + ICON / 2;
                    int o = Math.round((ICON / 2f - 1f) * is), in = Math.round((ICON / 2f - 3f) * is);
                    if (o > 0) Gui.drawRect(cx - o, cy - o, cx + o, cy + o, (ta << 24) | 0x1F9E1F);
                    if (in > 0) Gui.drawRect(cx - in, cy - in, cx + in, cy + in, (ta << 24) | 0x7CFC00);
                }
            }
        }
    }
}
