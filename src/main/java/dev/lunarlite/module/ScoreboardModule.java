package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

/**
 * Chỉ có một tính năng: ẩn scoreboard (sidebar) của server khi module được bật.
 * Dữ liệu server không bị thay đổi: sidebar chỉ bị gỡ tạm lúc game vẽ HUD rồi trả lại ngay,
 * nên các module khác (vd. Bảng kill) vẫn đọc được scoreboard bình thường.
 */
public class ScoreboardModule extends HudModule {
    // Slot + objective đang bị gỡ tạm khỏi scoreboard vanilla trong lúc game vẽ HUD.
    private int stashedSlot = -1;
    private ScoreObjective stashedObjective;

    public ScoreboardModule() {
        super("scoreboard", "Ẩn Scoreboard",
                "Ẩn scoreboard (bảng bên phải) của server.",
                Category.HUD, false, 520, 80);
    }

    // Không vẽ gì cả: module này chỉ ẩn scoreboard gốc.
    @Override public int getWidth(boolean preview) { return 0; }
    @Override public int getHeight(boolean preview) { return 0; }
    @Override protected void draw(int x, int y, boolean preview) { }

    /** Slot sidebar mà vanilla sẽ vẽ (ưu tiên slot theo màu team như GuiIngame). */
    private static int getSidebarSlot(Scoreboard scoreboard, Minecraft mc) {
        ScorePlayerTeam team = scoreboard.getPlayersTeam(mc.thePlayer.getName());
        if (team != null) {
            int colorIndex = team.getChatFormat().getColorIndex();
            if (colorIndex >= 0
                && scoreboard.getObjectiveInDisplaySlot(3 + colorIndex) != null) {
                return 3 + colorIndex;
            }
        }
        return 1;
    }

    /** Gọi ở RenderGameOverlayEvent.Pre (ALL): gỡ tạm sidebar để vanilla không vẽ. */
    public void hideVanilla() {
        stashedSlot = -1;
        stashedObjective = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return;
        int slot = getSidebarSlot(scoreboard, mc);
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(slot);
        if (objective == null) return;
        stashedSlot = slot;
        stashedObjective = objective;
        scoreboard.setObjectiveInDisplaySlot(slot, null);
    }

    /** Gọi ở RenderGameOverlayEvent.Post (ALL): trả sidebar về chỗ cũ để server cập nhật bình thường. */
    public void restoreVanilla() {
        Minecraft mc = Minecraft.getMinecraft();
        if (stashedObjective != null && mc.theWorld != null) {
            Scoreboard scoreboard = mc.theWorld.getScoreboard();
            if (scoreboard != null
                && scoreboard.getObjective(stashedObjective.getName()) == stashedObjective
                && scoreboard.getObjectiveInDisplaySlot(stashedSlot) == null) {
                scoreboard.setObjectiveInDisplaySlot(stashedSlot, stashedObjective);
            }
        }
        stashedSlot = -1;
        stashedObjective = null;
    }
}
