package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import java.util.LinkedList;

public class CpsModule extends TextHudModule {
    private static final LinkedList<Long> LEFT = new LinkedList<Long>();
    private static final LinkedList<Long> RIGHT = new LinkedList<Long>();

    private final BoolSetting showRight = add(new BoolSetting("show_right", "Show Right Click", true));

    public CpsModule() {
        super("cps", "CPS", "Số lần click chuột mỗi giây (trái | phải).", Category.PVP, true, 4, 24);
    }

    public static void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT.add(now);
        else if (button == 1) RIGHT.add(now);
    }

    private static int count(LinkedList<Long> list) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!list.isEmpty() && list.getFirst() < cutoff) list.removeFirst();
        return list.size();
    }

    public static int left() { return count(LEFT); }
    public static int right() { return count(RIGHT); }

    @Override
    protected String[] getLines(boolean preview) {
        return new String[] { "CPS: " + left() + (showRight.value ? " | " + right() : "") };
    }
}
