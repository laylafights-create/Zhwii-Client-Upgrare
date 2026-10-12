package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Địa chỉ server kèm icon (favicon) của server, như ảnh mẫu. */
public class ServerIpModule extends TextHudModule {
    private static final ResourceLocation UNKNOWN = new ResourceLocation("textures/misc/unknown_server.png");
    private static final int ICON = 32, PAD = 2, GAP = 5;

    private final BoolSetting hide = add(new BoolSetting("hide", "Hide Address (Streamer)", false));
    private final BoolSetting showIcon = add(new BoolSetting("show_icon", "Show Icon", true));

    private ResourceLocation iconLoc;
    private String iconData;

    public ServerIpModule() {
        super("serverip", "Server Address", "Địa chỉ server đang chơi.", Category.HUD, true, 4, 244);
    }

    @Override
    protected String[] getLines(boolean preview) {
        ServerData sd = Minecraft.getMinecraft().getCurrentServerData();
        String ip = sd == null ? (preview ? "play.example.net" : "Singleplayer") : sd.serverIP;
        if (hide.value && sd != null) ip = "********";
        return new String[] { ip };
    }

    /** Lấy texture favicon của server hiện tại (tạo lại khi dữ liệu đổi), hoặc icon mặc định. */
    private ResourceLocation icon() {
        Minecraft mc = Minecraft.getMinecraft();
        ServerData sd = mc.getCurrentServerData();
        String data = sd == null ? null : sd.getBase64EncodedIconData();
        if (data == null || data.length() == 0) return UNKNOWN;
        if (data.startsWith("data:image/png;base64,")) data = data.substring("data:image/png;base64,".length());
        if (!data.equals(iconData)) {
            iconData = data;
            try {
                if (iconLoc != null) mc.getTextureManager().deleteTexture(iconLoc);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(data)));
                iconLoc = img == null ? null
                        : mc.getTextureManager().getDynamicTextureLocation("lunarlite_server_icon", new DynamicTexture(img));
            } catch (Exception e) {
                iconLoc = null;
            }
        }
        return iconLoc == null ? UNKNOWN : iconLoc;
    }

    @Override
    public int getWidth(boolean preview) {
        String[] lines = getLines(preview);
        int tw = fr().getStringWidth(lines[0]);
        return showIcon.value ? PAD + ICON + GAP + tw + PAD + 3 : tw + PAD * 2 + 6;
    }

    @Override
    public int getHeight(boolean preview) {
        return showIcon.value ? ICON + PAD * 2 : 14;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        String ip = getLines(preview)[0];
        int w = getWidth(preview), h = getHeight(preview);
        drawBox(x, y, w, h);
        int tx = x + PAD + 3;
        if (showIcon.value) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(icon());
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(x + PAD, y + PAD, 0f, 0f, 64, 64, ICON, ICON, 64f, 64f);
            tx = x + PAD + ICON + GAP;
        }
        drawText(ip, tx, y + (h - 8) / 2, 0);
    }
}
