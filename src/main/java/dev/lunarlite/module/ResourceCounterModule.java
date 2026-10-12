package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.config.Configuration;

/**
 * Đếm số lượng các vật phẩm do người dùng chọn (icon + số lượng), giống "Resource Counter" trong ảnh mẫu.
 * Thêm / xoá vật phẩm bằng các nút Add / Remove trong menu.
 */
public class ResourceCounterModule extends HudModule {
    private static final String SPLASH_HEAL = "splash_health";
    private static final String DEFAULT = "golden_apple,ender_pearl";
    /** Các vật phẩm có thể thêm (Minecraft 1.8.9 không có Totem / Slow Falling như ảnh mẫu 1.13+). */
    private static final String[] CANDIDATES = {
        "golden_apple", "ender_pearl", SPLASH_HEAL, "arrow", "snowball", "experience_bottle",
        "web", "cooked_beef", "cobblestone", "iron_ingot", "gold_ingot", "diamond", "emerald"
    };
    private static final int ROW = 18, PAD = 3, ICON = 16;

    private final List<String> tracked = new ArrayList<String>();
    private final Map<String, ItemStack> icons = new HashMap<String, ItemStack>();

    public ResourceCounterModule() {
        super("resourcecounter", "Resource Counter", "Đếm số lượng vật phẩm bạn chọn trong túi đồ.",
                Category.PVP, true, 80, 140);
        parse(DEFAULT);
    }

    // ------------------------------------------------------------ danh sách

    private static boolean valid(String id) {
        return SPLASH_HEAL.equals(id) || Item.getByNameOrId(id) != null;
    }

    private void parse(String csv) {
        tracked.clear();
        if (csv == null) return;
        for (String part : csv.split(",")) {
            String id = part.trim();
            if (id.length() > 0 && !tracked.contains(id) && valid(id)) tracked.add(id);
        }
    }

    private String join() {
        StringBuilder sb = new StringBuilder();
        for (String id : tracked) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private ItemStack icon(String id) {
        ItemStack st = icons.get(id);
        if (st != null) return st;
        if (SPLASH_HEAL.equals(id)) {
            st = new ItemStack(Items.potionitem, 1, 16453); // splash Instant Health
        } else {
            Item item = Item.getByNameOrId(id);
            if (item == null) return null;
            st = new ItemStack(item);
        }
        icons.put(id, st);
        return st;
    }

    private String nameOf(String id) {
        ItemStack st = icon(id);
        if (SPLASH_HEAL.equals(id)) return "Bình máu";
        return st == null ? id : st.getDisplayName();
    }

    private String nextCandidate() {
        for (String id : CANDIDATES) {
            if (!tracked.contains(id) && valid(id)) return id;
        }
        return null;
    }

    @Override
    public List<Action> actions() {
        List<Action> out = new ArrayList<Action>();
        final String next = nextCandidate();
        if (next != null) {
            out.add(new Action("Thêm: " + nameOf(next), new Runnable() {
                @Override public void run() { tracked.add(next); }
            }, icon(next)));
        }
        for (final String id : new ArrayList<String>(tracked)) {
            out.add(new Action("Xóa: " + nameOf(id), new Runnable() {
                @Override public void run() { tracked.remove(id); }
            }, icon(id)));
        }
        return out;
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        parse(DEFAULT);
    }

    // --------------------------------------------------------------- đếm

    private static int count(String id, EntityPlayer p) {
        if (p == null) return 0;
        int n = 0;
        if (SPLASH_HEAL.equals(id)) {
            for (ItemStack s : p.inventory.mainInventory) {
                if (s == null || !(s.getItem() instanceof ItemPotion) || !ItemPotion.isSplash(s.getMetadata())) continue;
                List<?> effects = ((ItemPotion) s.getItem()).getEffects(s);
                if (effects == null) continue;
                for (Object o : effects) {
                    if (((PotionEffect) o).getPotionID() == Potion.heal.id) {
                        n += s.stackSize;
                        break;
                    }
                }
            }
            return n;
        }
        Item item = Item.getByNameOrId(id);
        if (item == null) return 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s != null && s.getItem() == item) n += s.stackSize;
        }
        return n;
    }

    private List<String> shown(boolean preview) {
        if (tracked.isEmpty() && preview) return Collections.singletonList("golden_apple");
        return tracked;
    }

    // ---------------------------------------------------------------- vẽ

    @Override
    public int getWidth(boolean preview) {
        List<String> ids = shown(preview);
        if (ids.isEmpty()) return 0;
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        int w = 6;
        for (String id : ids) w = Math.max(w, fr().getStringWidth(String.valueOf(count(id, p))));
        return PAD * 2 + ICON + 4 + w;
    }

    @Override
    public int getHeight(boolean preview) {
        int n = shown(preview).size();
        return n == 0 ? 0 : n * ROW - 2 + PAD * 2;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        List<String> ids = shown(preview);
        drawBox(x, y, getWidth(preview), getHeight(preview));
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int ry = y + PAD + i * ROW;
            Draw.rrect(x + 2, ry - 1, x + getWidth(preview) - 2, ry + ICON, 3, 0x1AFFFFFF);
            ItemStack st = icon(id);
            if (st != null) {
                GlStateManager.pushMatrix();
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(st, x + PAD, ry);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                GlStateManager.disableLighting();
            }
            int n = count(id, p);
            int base = n == 0 ? 0xFF5555 : color.rgb();
            rainbow.drawString(fr(), String.valueOf(n), x + PAD + ICON + 4, ry + 4, base, shadow.value, i * ROW);
        }
    }

    // ------------------------------------------------------------- config

    @Override
    public void load(Configuration c) {
        super.load(c);
        parse(c.getString("tracked", id, DEFAULT, "Danh sách vật phẩm đang đếm (cách nhau bằng dấu phẩy)"));
    }

    @Override
    public void save(Configuration c) {
        super.save(c);
        c.get(id, "tracked", DEFAULT).set(join());
    }
}
