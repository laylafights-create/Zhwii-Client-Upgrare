package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Ẩn giáp của bản thân: chỉ thay đổi cách VẼ nhân vật của bạn (client-side).
 * Giáp vẫn đang mặc, chỉ số giáp / độ bền / người khác nhìn thấy đều không đổi.
 * Cách làm: ngay trước khi vẽ người chơi, tạm gỡ giáp khỏi ô armorInventory,
 * vẽ xong (Post) thì trả lại đúng chỗ cũ.
 */
public class HideArmorModule extends Module {
    private final BoolSetting helmet = add(new BoolSetting("hide_helmet", "Hide Helmet", true));
    private final BoolSetting chest = add(new BoolSetting("hide_chest", "Hide Chestplate", true));
    private final BoolSetting legs = add(new BoolSetting("hide_legs", "Hide Leggings", true));
    private final BoolSetting boots = add(new BoolSetting("hide_boots", "Hide Boots", true));
    private final BoolSetting keepInGui = add(new BoolSetting("keep_in_gui", "Show In Inventory Preview", true));

    private final ItemStack[] saved = new ItemStack[4];
    private boolean swapped;
    private EntityPlayer swappedFor;

    public HideArmorModule() {
        super("hide_armor", "Hide Armor", "Ẩn giáp trên nhân vật của bạn (chỉ hiển thị phía bạn, không ảnh hưởng chỉ số giáp).",
                Category.VISUAL, false);
    }

    private boolean hide(int slot) {
        // armorInventory: 0 = giày, 1 = quần, 2 = áo, 3 = mũ
        switch (slot) {
            case 0: return boots.value;
            case 1: return legs.value;
            case 2: return chest.value;
            default: return helmet.value;
        }
    }

    public void onPre(RenderPlayerEvent.Pre e) {
        if (!enabled || swapped) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer me = mc.thePlayer;
        if (me == null || e.entityPlayer != me) return;
        if (keepInGui.value && mc.currentScreen != null) return;
        ItemStack[] armor = me.inventory.armorInventory;
        for (int i = 0; i < 4; i++) {
            saved[i] = armor[i];
            if (hide(i)) armor[i] = null;
        }
        swapped = true;
        swappedFor = me;
    }

    public void onPost(RenderPlayerEvent.Post e) {
        restore();
    }

    private void restore() {
        if (!swapped) return;
        EntityPlayer me = Minecraft.getMinecraft().thePlayer;
        // người chơi đổi (đổi thế giới / respawn) thì bỏ, không trả nhầm giáp
        if (me != null && me == swappedFor) {
            ItemStack[] armor = me.inventory.armorInventory;
            for (int i = 0; i < 4; i++) armor[i] = saved[i];
        }
        for (int i = 0; i < 4; i++) saved[i] = null;
        swapped = false;
        swappedFor = null;
    }

    /** Lưới an toàn: nếu Post bị bỏ qua (event bị huỷ / lỗi) thì tick sau vẫn trả giáp lại. */
    @Override
    public void onTick() {
        restore();
    }

    @Override
    public void onToggle() {
        restore();
    }
}
