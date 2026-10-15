package dev.lunarlite.gui;

import dev.lunarlite.module.AnimationsModule;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.inventory.GuiHopper;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;

/**
 * Các màn hình container KHÔNG có nền đen + animation mở mượt:
 * kho đồ, rương, bàn chế tạo, lò nung, dân làng (trade), bàn phù phép, đe, bếp thuốc, dispenser, hopper, beacon, ngựa, creative.
 */
public final class CleanGuis {
    private CleanGuis() {}

    /** Đánh dấu màn hình đã tự xử lý animation + nền (AnimationsModule sẽ không áp thêm lần nữa). */
    public interface Clean {}

    /** Trả về bản sạch của màn hình; nếu không hỗ trợ / lỗi thì trả lại đúng màn hình gốc. */
    public static GuiScreen wrap(GuiScreen gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (gui == null || mc.thePlayer == null || gui instanceof Clean) return gui;
        InventoryPlayer inv = mc.thePlayer.inventory;
        World w = mc.theWorld;
        Class<?> c = gui.getClass();
        try {
            if (c == GuiInventory.class) return new Inv(mc.thePlayer);
            if (c == GuiChest.class) {
                ContainerChest cc = (ContainerChest) ((GuiChest) gui).inventorySlots;
                return new Chest(inv, cc.getLowerChestInventory());
            }
            if (c == GuiCrafting.class) return new Crafting(inv, w);
            if (c == GuiRepair.class) return new Repair(inv, w);
            if (c == GuiMerchant.class) {
                IMerchant m = find(gui, IMerchant.class);
                return m == null ? gui : new Merchant(inv, m, w);
            }
            if (c == GuiEnchantment.class) {
                IWorldNameable n = find(gui, IWorldNameable.class);
                return n == null ? gui : new Enchant(inv, w, n);
            }
            if (c == GuiContainerCreative.class) return new Creative(mc.thePlayer);
            if (c == GuiScreenHorseInventory.class) {
                EntityHorse h = find(gui, EntityHorse.class);
                IInventory hi = find(gui, IInventory.class);
                return h == null || hi == null ? gui : new Horse(inv, hi, h);
            }
            Container cont = ((GuiContainer) gui).inventorySlots;
            if (c == GuiFurnace.class) {
                IInventory i = find(cont, IInventory.class);
                return i == null ? gui : new Furnace(inv, i);
            }
            if (c == GuiBrewingStand.class) {
                IInventory i = find(cont, IInventory.class);
                return i == null ? gui : new Brewing(inv, i);
            }
            if (c == GuiDispenser.class) {
                IInventory i = find(cont, IInventory.class);
                return i == null ? gui : new Dispenser(inv, i);
            }
            if (c == GuiBeacon.class) {
                IInventory i = find(cont, IInventory.class);
                return i == null ? gui : new Beacon(inv, i);
            }
            if (c == GuiHopper.class) {
                IInventory i = find(cont, IInventory.class);
                return i == null ? gui : new Hopper(inv, i);
            }
        } catch (Throwable ignored) {
            // lỗi gì thì dùng màn hình gốc
        }
        return gui;
    }

    /** Tìm field private đầu tiên thuộc kiểu `type` (bỏ qua túi đồ người chơi) - chạy được cả khi bị obfuscate. */
    private static <T> T find(Object o, Class<T> type) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !type.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (type.isInstance(v) && !(v instanceof InventoryPlayer)) return type.cast(v);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ các lớp màn hình

    public static class Inv extends GuiInventory implements Clean {
        private final long start = System.currentTimeMillis();
        public Inv(EntityPlayer p) { super(p); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Chest extends GuiChest implements Clean {
        private final long start = System.currentTimeMillis();
        public Chest(IInventory upper, IInventory lower) { super(upper, lower); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Crafting extends GuiCrafting implements Clean {
        private final long start = System.currentTimeMillis();
        public Crafting(InventoryPlayer inv, World w) { super(inv, w); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Repair extends GuiRepair implements Clean {
        private final long start = System.currentTimeMillis();
        public Repair(InventoryPlayer inv, World w) { super(inv, w); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    /** Dân làng (trade). */
    public static class Merchant extends GuiMerchant implements Clean {
        private final long start = System.currentTimeMillis();
        public Merchant(InventoryPlayer inv, IMerchant m, World w) { super(inv, m, w); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Enchant extends GuiEnchantment implements Clean {
        private final long start = System.currentTimeMillis();
        public Enchant(InventoryPlayer inv, World w, IWorldNameable n) { super(inv, w, n); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Furnace extends GuiFurnace implements Clean {
        private final long start = System.currentTimeMillis();
        public Furnace(InventoryPlayer inv, IInventory i) { super(inv, i); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Brewing extends GuiBrewingStand implements Clean {
        private final long start = System.currentTimeMillis();
        public Brewing(InventoryPlayer inv, IInventory i) { super(inv, i); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Dispenser extends GuiDispenser implements Clean {
        private final long start = System.currentTimeMillis();
        public Dispenser(InventoryPlayer inv, IInventory i) { super(inv, i); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Hopper extends GuiHopper implements Clean {
        private final long start = System.currentTimeMillis();
        public Hopper(InventoryPlayer inv, IInventory i) { super(inv, i); }
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Beacon extends GuiBeacon implements Clean {
        public Beacon(InventoryPlayer inv, IInventory i) { super(inv, i); }
        private final long start = System.currentTimeMillis();
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Horse extends GuiScreenHorseInventory implements Clean {
        public Horse(IInventory player, IInventory horseInv, EntityHorse h) { super(player, horseInv, h); }
        private final long start = System.currentTimeMillis();
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }

    public static class Creative extends GuiContainerCreative implements Clean {
        public Creative(EntityPlayer p) { super(p); }
        private final long start = System.currentTimeMillis();
        @Override public void drawDefaultBackground() { }
        @Override public void drawScreen(int mx, int my, float pt) {
            AnimationsModule.Pose p = AnimationsModule.begin(this, start);
            try { super.drawScreen(p.mx(mx), p.my(my), pt); } finally { p.end(); }
        }
    }
}
