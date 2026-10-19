package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.config.Configuration;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Tự động nhảy khi đang đánh để ăn chí mạng (critical).
 * Chỉ nhảy khi ĐANG ĐÁNH VÀ tâm ngắm đang trỏ vào một mục tiêu trong tầm;
 * không đánh hoặc không có mục tiêu thì không nhảy. Không có HUD.
 * Bật / tắt bằng ô trong menu, hoặc bằng một phím riêng gắn ngay trong bảng tuỳ chỉnh của tính năng
 * (bấm "Phím bật/tắt", rồi bấm phím hoặc nút chuột bất kỳ; Backspace / Delete = bỏ phím, ESC = huỷ).
 */
public class AimAssistModule extends Module {
    private static final String SEC_TRIGGER = "Kích hoạt";
    private static final String SEC_JUMP = "Nhảy";
    private static final String SEC_TARGET = "Mục tiêu";
    private static final String SEC_COND = "Điều kiện";
    private static final String SEC_KEY = "Phím tắt";

    // ---- Kích hoạt: khi nào được coi là "đang đánh"
    private final ModeSetting trigger = add(new ModeSetting("aim_crit_trigger", "Điều kiện đánh", 0,
            "Giữ hoặc click", "Chỉ giữ chuột", "Chỉ click"), SEC_TRIGGER);
    private final SliderSetting clickWindow = add(new SliderSetting("aim_crit_click_window", "Thời gian nhớ cú click", 3, 1, 10, 1, " tick"), SEC_TRIGGER);
    private final SliderSetting range = add(new SliderSetting("aim_crit_range", "Tầm kích hoạt", 3.0, 2.0, 6.0, 0.1, " khối"), SEC_TRIGGER);

    // ---- Nhảy
    private final SliderSetting delay = add(new SliderSetting("aim_crit_delay", "Độ trễ giữa các lần nhảy", 1, 0, 10, 1, " tick"), SEC_JUMP);
    private final SliderSetting delayRand = add(new SliderSetting("aim_crit_delay_rand", "Trễ ngẫu nhiên thêm", 0, 0, 6, 1, " tick"), SEC_JUMP);
    private final SliderSetting chance = add(new SliderSetting("aim_crit_chance", "Tỉ lệ nhảy", 100, 5, 100, 5, "%"), SEC_JUMP);
    private final BoolSetting onlySprint = add(new BoolSetting("aim_crit_only_sprint", "Chỉ khi đang chạy", false), SEC_JUMP);
    private final BoolSetting skipSneak = add(new BoolSetting("aim_crit_skip_sneak", "Không nhảy khi đang ngồi (Shift)", false), SEC_JUMP);
    private final SliderSetting headroom = add(new SliderSetting("aim_crit_headroom", "Chiều cao trần tối thiểu", 1.0, 0.5, 2.0, 0.1, " khối"), SEC_JUMP);

    // ---- Mục tiêu
    private final BoolSetting onlyPlayers = add(new BoolSetting("aim_crit_only_players", "Chỉ người chơi", true), SEC_TARGET);
    private final BoolSetting ignoreTeam = add(new BoolSetting("aim_ignore_team", "Bỏ qua đồng đội", true), SEC_TARGET);
    private final BoolSetting ignoreInvisible = add(new BoolSetting("aim_ignore_invisible", "Bỏ qua người tàng hình", false), SEC_TARGET);
    private final BoolSetting ignoreBots = add(new BoolSetting("aim_crit_ignore_bots", "Bỏ qua NPC / bot Hypixel", true), SEC_TARGET);

    // ---- Điều kiện
    private final BoolSetting onlySword = add(new BoolSetting("aim_crit_only_sword", "Chỉ khi cầm kiếm", false), SEC_COND);
    private final BoolSetting skipUsing = add(new BoolSetting("aim_crit_skip_using", "Bỏ qua khi đang ăn / uống / chặn", true), SEC_COND);
    private final BoolSetting lowHealth = add(new BoolSetting("aim_crit_low_health", "Dừng nhảy khi máu thấp", false), SEC_COND);
    private final SliderSetting minHealth = add(new SliderSetting("aim_crit_min_health", "Máu tối thiểu để nhảy", 6, 1, 20, 1, " HP"), SEC_COND, lowHealth);
    private final BoolSetting skipLobby = add(new BoolSetting("aim_crit_skip_lobby", "Tắt ở sảnh Hypixel", true), SEC_COND);

    // ---- Phím tắt riêng
    private final BoolSetting notifyToggle = add(new BoolSetting("aim_crit_notify", "Báo trong chat khi bật / tắt", true), SEC_KEY);

    /** Phím bật/tắt: &gt; 0 = phím bàn phím, &lt; 0 = nút chuột (-100 + nút), 0 = chưa gán. */
    private int toggleKey;
    private boolean binding;
    /** Chỉ nhận phím sau khi mọi phím / nút đã nhả (tránh nuốt luôn cú click vừa bấm nút "Phím bật/tắt"). */
    private boolean bindReady;
    private boolean keyWasDown;
    /** Nhãn nút gán phím đã đổi, menu cần dựng lại. */
    private boolean uiDirty;

    private final Random rng = new Random();
    /** Số tick client đã chạy; dùng làm đồng hồ cho cooldown, không phụ thuộc FPS. */
    private int tick;
    private int lastClickTick = -100;
    private boolean wasDown;
    private int lastJump = -100;
    /** Số tick chờ cần thiết sau lần nhảy / lần thử gần nhất (đã cộng phần ngẫu nhiên). */
    private int waitTicks;

    public AimAssistModule() {
        super("aimassist", "Aim Assist", "Tự động nhảy khi đang đánh vào mục tiêu để ăn chí mạng. Không đánh thì không nhảy.", Category.PVP, false);
    }

    /** Ghi nhận cạnh lên của nút tấn công (một cú click), kể cả khi nhả chuột trước khi hết tick. */
    private void sampleAttack(Minecraft mc) {
        boolean down = mc.gameSettings.keyBindAttack.isKeyDown();
        if (down && !wasDown) lastClickTick = tick;
        wasDown = down;
    }

    /** Gọi mỗi khung hình (RenderTickEvent): xử lý phím bật/tắt và bắt các cú click nhanh giữa hai tick. */
    public void onRender(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        if (binding) pollBinding();
        else if (toggleKey != 0) pollToggleKey(mc);
        if (!enabled) return;
        if (mc.thePlayer != null && mc.currentScreen == null) sampleAttack(mc);
    }

    // ------------------------------------------------------------------ phím bật / tắt

    private static boolean isDown(int code) {
        if (code > 0) return Keyboard.isKeyDown(code);
        if (code < 0) return Mouse.isButtonDown(code + 100);
        return false;
    }

    private static String keyName(int code) {
        if (code == 0) return "Chưa gán";
        if (code < 0) return "Chuột " + (code + 100 + 1);
        String n = Keyboard.getKeyName(code);
        return n == null ? "Phím " + code : n;
    }

    /** Bấm phím đã gán (chỉ khi đang chơi, không mở GUI / chat) thì đảo trạng thái. */
    private void pollToggleKey(Minecraft mc) {
        boolean down = isDown(toggleKey);
        if (down && !keyWasDown && mc.currentScreen == null && mc.thePlayer != null) {
            toggle();
            dev.lunarlite.LunarLite.saveConfig();
            if (notifyToggle.value) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "\u00a77[Zhwii] \u00a7fAuto nhảy chí mạng: " + (enabled ? "\u00a7aBẬT" : "\u00a7cTẮT")));
            }
        }
        keyWasDown = down;
    }

    /** Đang chờ người chơi bấm phím để gán. */
    private void pollBinding() {
        boolean any = false;
        int found = 0;
        for (int k = 1; k < 256; k++) {
            if (Keyboard.isKeyDown(k)) { any = true; if (found == 0) found = k; }
        }
        // chuột trái / phải dùng để bấm menu nên bỏ qua; nhận chuột giữa và các nút phụ
        for (int b = 2; b < 8 && b < Mouse.getButtonCount(); b++) {
            if (Mouse.isButtonDown(b)) { any = true; if (found == 0) found = b - 100; }
        }
        if (!bindReady) {
            if (!any) bindReady = true;
            return;
        }
        if (found == 0) return;
        binding = false;
        uiDirty = true;
        if (found == Keyboard.KEY_ESCAPE) return; // huỷ, giữ nguyên phím cũ
        toggleKey = (found == Keyboard.KEY_BACK || found == Keyboard.KEY_DELETE) ? 0 : found;
        keyWasDown = true; // phím vừa gán đang được giữ: không tính là một lần bấm bật/tắt
        dev.lunarlite.LunarLite.saveConfig();
    }

    /** Nút gán phím trong bảng tuỳ chỉnh của menu (menu tự dựng lại sau mỗi lần bấm nên nhãn luôn mới). */
    @Override
    public List<Action> actions() {
        List<Action> l = new ArrayList<Action>();
        final String label = binding ? "Bấm một phím / nút chuột bất kỳ… (ESC: huỷ, Backspace: bỏ phím)"
                : "Phím bật/tắt: " + keyName(toggleKey);
        l.add(new Action(label, new Runnable() {
            @Override public void run() {
                binding = !binding;
                bindReady = false;
            }
        }, null));
        return l;
    }

    @Override
    public boolean consumeUiDirty() {
        boolean d = uiDirty;
        uiDirty = false;
        return d;
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        toggleKey = 0;
        binding = false;
    }

    @Override
    public void load(Configuration c) {
        super.load(c);
        toggleKey = c.getInt("toggle_key", id, 0, -100, 255, "Phím bật/tắt (0 = chưa gán, số âm = nút chuột)");
        keyWasDown = true; // coi như đang giữ: không tự bật/tắt ngay khi vừa tải config
    }

    @Override
    public void save(Configuration c) {
        super.save(c);
        c.get(id, "toggle_key", 0).set(toggleKey);
    }

    @Override
    public void onToggle() {
        lastClickTick = -100;
        wasDown = false;
    }

    private boolean attacking(Minecraft mc) {
        boolean held = mc.gameSettings.keyBindAttack.isKeyDown();
        boolean clicked = tick - lastClickTick <= clickWindow.getInt();
        switch (trigger.index) {
            case 1: return held;
            case 2: return clicked;
            default: return held || clicked;
        }
    }

    private void armWait() {
        waitTicks = delay.getInt() + (delayRand.getInt() > 0 ? rng.nextInt(delayRand.getInt() + 1) : 0);
    }

    @Override
    public void onTick() {
        tick++;
        if (!enabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        // Các điều kiện rẻ nhất kiểm tra trước; đa số tick sẽ thoát ngay ở đây.
        if (p == null || mc.theWorld == null || mc.currentScreen != null) return;
        sampleAttack(mc);
        if (!p.onGround) return;
        if (!attacking(mc)) return;
        if (tick - lastJump <= waitTicks) return;
        // Người chơi tự bấm nhảy thì để game tự xử lý, không chen vào.
        if (mc.gameSettings.keyBindJump.isKeyDown()) return;

        // Những trạng thái không thể ăn chí mạng (hoặc nhảy vô nghĩa).
        if (p.isSpectator() || p.capabilities.isFlying || p.isInWater() || p.isInLava()
                || p.isOnLadder() || p.isRiding() || p.isPotionActive(Potion.blindness)) return;
        if (onlySprint.value && !p.isSprinting()) return;
        if (skipSneak.value && p.isSneaking()) return;
        if (skipUsing.value && p.isUsingItem()) return;
        if (lowHealth.value && p.getHealth() < minHealth.getFloat()) return;
        if (onlySword.value) {
            ItemStack held = p.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemSword)) return;
        }
        if (skipLobby.value && HypixelUtil.inLobby()) return;

        // Phải đang trỏ vào một sinh vật còn sống trong tầm.
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        Entity t = mop.entityHit;
        if (!(t instanceof EntityLivingBase) || t.isDead || t instanceof EntityArmorStand) return;
        if (((EntityLivingBase) t).getHealth() <= 0.0F) return;
        if (p.getDistanceToEntity(t) > range.getFloat()) return;
        if (ignoreInvisible.value && t.isInvisible()) return;
        if (t instanceof EntityPlayer) {
            EntityPlayer tp = (EntityPlayer) t;
            if (tp.isSpectator()) return;
            if (ignoreBots.value && !HypixelUtil.isRealPlayer(tp)) return;
            if (ignoreTeam.value && TeamUtil.isTeammate(p, tp)) return;
        } else if (onlyPlayers.value) {
            return;
        }

        // Trần quá thấp thì nhảy chỉ đập đầu, không có đà rơi để ăn crit.
        if (!mc.theWorld.getCollidingBoundingBoxes(p, p.getEntityBoundingBox().offset(0.0D, headroom.get(), 0.0D)).isEmpty()) return;

        // Mọi điều kiện đã đạt. Tỉ lệ < 100%: thất bại thì chờ một nhịp rồi thử lại, không nhảy liên tục.
        lastJump = tick;
        armWait();
        if (chance.getInt() < 100 && rng.nextInt(100) >= chance.getInt()) return;
        p.jump();
    }
}
