package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

public class PingModule extends TextHudModule {
    public PingModule() {
        super("ping", "Ping", "Độ trễ tới server (ms).", Category.HUD, false, 4, 164);
    }

    @Override
    protected String[] getLines(boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        int ping = 0;
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) ping = info.getResponseTime();
        }
        return new String[] { "\u00a7a" + ping + "\u00a7fms" };
    }
}
