package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/**
 * Item Physics + Loot Beam:
 *  - Vật phẩm rơi xuống đất nằm phẳng, lăn / xoay khi đang bay trên không.
 *  - Khi vật phẩm nằm yên sẽ hiện tia sáng dọc: màu theo độ hiếm / tài nguyên / tuỳ chọn,
 *    kích thước và độ rực theo cấp (tier), có bộ lọc vật phẩm.
 * Chỉ thay đổi cách VẼ vật phẩm, không đụng tới hitbox hay logic game.
 */
public class ItemPhysicsModule extends Module {
    private final BoolSetting physics = add(new BoolSetting("physics", "Item Physics", true));
    private final SliderSetting size = add(new SliderSetting("size", "Item Size", 1.0, 0.5, 2.0, 0.1, "x"));
    private final BoolSetting beam = add(new BoolSetting("beam", "Loot Beam", true));
    private final ModeSetting beamMode = add(new ModeSetting("beam_color", "Beam Color", 0, "Rarity", "Resource", "Auto", "Custom"), "Loot Beam", beam);
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFF), "Loot Beam", beam);
    private final SliderSetting beamHeight = add(new SliderSetting("beam_height", "Beam Height", 2.5, 1.0, 8.0, 0.5, " khối"), "Loot Beam", beam);
    private final SliderSetting beamWidth = add(new SliderSetting("beam_width", "Beam Width", 1.0, 0.5, 3.0, 0.1, "x"), "Loot Beam", beam);
    private final SliderSetting opacity = add(new SliderSetting("beam_opacity", "Beam Opacity", 80, 10, 100, 5, "%"), "Loot Beam", beam);
    private final BoolSetting groundGlow = add(new BoolSetting("ground_glow", "Ground Glow", true), "Loot Beam", beam);
    private final BoolSetting showCommon = add(new BoolSetting("show_common", "Show For Common Items", true), "Loot Beam", beam);
    private final SliderSetting maxDist = add(new SliderSetting("max_dist", "Max Distance", 48, 8, 128, 4, " khối"), "Loot Beam", beam);

    // Bộ lọc: khi bật, chỉ hiện tia cho các món được tick bên dưới.
    private final BoolSetting filterOn = add(new BoolSetting("filter", "Item Filter", false), "Loot Beam", beam);
    private final BoolSetting fIron = add(new BoolSetting("f_iron", "Iron Ingot", true), "Beam Filter", filterOn);
    private final BoolSetting fGold = add(new BoolSetting("f_gold", "Gold Ingot", true), "Beam Filter", filterOn);
    private final BoolSetting fDiamond = add(new BoolSetting("f_diamond", "Diamond", true), "Beam Filter", filterOn);
    private final BoolSetting fEmerald = add(new BoolSetting("f_emerald", "Emerald", true), "Beam Filter", filterOn);
    private final BoolSetting fGapple = add(new BoolSetting("f_gapple", "Golden Apple", true), "Beam Filter", filterOn);
    private final BoolSetting fPearl = add(new BoolSetting("f_pearl", "Ender Pearl", true), "Beam Filter", filterOn);
    private final BoolSetting fGear = add(new BoolSetting("f_gear", "Weapons & Tools", false), "Beam Filter", filterOn);
    private final BoolSetting fArmor = add(new BoolSetting("f_armor", "Armor", false), "Beam Filter", filterOn);
    private final BoolSetting fFood = add(new BoolSetting("f_food", "Food", false), "Beam Filter", filterOn);
    private final BoolSetting fPotion = add(new BoolSetting("f_potion", "Potions", false), "Beam Filter", filterOn);
    private final BoolSetting fEnchanted = add(new BoolSetting("f_enchanted", "Enchanted", true), "Beam Filter", filterOn);
    private final BoolSetting fRare = add(new BoolSetting("f_rare", "Uncommon+ Items", false), "Beam Filter", filterOn);

    private boolean installed;
    private int attempts;
    private static final long T0 = System.nanoTime();

    // Cấp tia: 0 = thường, 1 = uncommon, 2 = rare, 3 = epic. Cấp càng cao tia càng cao, rộng và rực.
    private static final float[] TIER_HEIGHT = { 0.7f, 0.9f, 1.1f, 1.4f };
    private static final float[] TIER_WIDTH = { 0.8f, 1.0f, 1.15f, 1.3f };
    private static final float[] TIER_GLOW = { 0.75f, 0.95f, 1.1f, 1.3f };

    public ItemPhysicsModule() {
        super("itemphysics", "Item Physics", "Vật phẩm rơi nằm phẳng xuống đất và có tia sáng theo độ hiếm / tài nguyên (kiểu Loot Beams).",
                Category.VISUAL, false);
    }

    /** Thay renderer vật phẩm mặc định bằng renderer của module (không phụ thuộc tên SRG, tự thử lại nếu chưa được). */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onTick() {
        if (installed || attempts++ > 200) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            RenderManager rm = mc.getRenderManager();
            if (rm == null || mc.getRenderItem() == null) return;
            PhysicsRenderer r = new PhysicsRenderer(rm, mc.getRenderItem(), this);
            for (Field f : RenderManager.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Map m = (Map) f.get(rm);
                if (m != null && m.containsKey(EntityItem.class)) {
                    m.put(EntityItem.class, r);
                    installed = true;
                    break;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static int rarityColor(ItemStack s) {
        switch (s.getRarity()) {
            case UNCOMMON: return 0xFFFF55;
            case RARE: return 0x55FFFF;
            case EPIC: return 0xFF55FF;
            default: return 0xFFFFFF;
        }
    }

    /** Tài nguyên: -1 = không phải; 0 sắt, 1 vàng, 2 kim cương, 3 ngọc lục bảo. */
    private static int resourceId(ItemStack s) {
        Item it = s.getItem();
        if (it == Items.iron_ingot) return 0;
        if (it == Items.gold_ingot) return 1;
        if (it == Items.diamond) return 2;
        if (it == Items.emerald) return 3;
        return -1;
    }

    private static int resourceColor(int id) {
        switch (id) {
            case 0: return 0xFFFFFF; // sắt: trắng
            case 1: return 0xFFD92B; // vàng: vàng
            case 2: return 0x3FA9FF; // kim cương: xanh lam
            default: return 0x2BE04A; // ngọc lục bảo: xanh lá
        }
    }

    /** Cache màu trung bình của texture theo (id, meta) để không phải quét pixel mỗi khung hình. */
    private static final Map<Integer, Integer> AUTO_CACHE = new HashMap<Integer, Integer>();

    /** Màu tự động cho MỌI vật phẩm: lấy màu trung bình của texture, tăng độ bão hoà để tia nổi bật. */
    private static int autoColor(ItemStack s, RenderItem ri) {
        int key = (Item.getIdFromItem(s.getItem()) << 16) | (s.getMetadata() & 0xFFFF);
        Integer c = AUTO_CACHE.get(key);
        if (c != null) return c;
        int out = rarityColor(s);
        try {
            IBakedModel model = ri.getItemModelMesher().getItemModel(s);
            TextureAtlasSprite sp = model.getParticleTexture();
            if (sp != null && sp.getFrameCount() > 0) {
                int[] px = sp.getFrameTextureData(0)[0];
                long rs = 0, gs = 0, bs = 0, n = 0;
                for (int p : px) {
                    if ((p >>> 24) < 128) continue;
                    rs += (p >> 16) & 255;
                    gs += (p >> 8) & 255;
                    bs += p & 255;
                    n++;
                }
                if (n > 0) {
                    float[] hsb = Color.RGBtoHSB((int) (rs / n), (int) (gs / n), (int) (bs / n), null);
                    if (hsb[1] < 0.15f) { hsb[1] = 0f; hsb[2] = 1f; }       // xám / trắng -> trắng
                    else { hsb[1] = Math.max(hsb[1], 0.6f); hsb[2] = 1f; }  // có màu -> đậm và sáng
                    out = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
                }
            }
        } catch (Throwable ignored) {
        }
        AUTO_CACHE.put(key, out);
        return out;
    }

    private int beamColor(ItemStack s, RenderItem ri) {
        if (beamMode.is("Custom")) return color.rgb() & 0xFFFFFF;
        if (beamMode.is("Auto")) return autoColor(s, ri);
        if (beamMode.is("Resource")) {
            int id = resourceId(s);
            if (id >= 0) return resourceColor(id);
        }
        return rarityColor(s);
    }

    /** Cấp theo chất liệu / giá trị của vật phẩm (0..3), áp dụng cho mọi item. */
    private static int valueTier(ItemStack s) {
        Item it = s.getItem();
        int id = resourceId(s);
        if (id >= 0) return id;
        if (it == Items.nether_star) return 3;
        if (it == Items.golden_apple) return s.getMetadata() == 1 ? 3 : 2;
        if (it == Items.ender_pearl || it == Items.ender_eye || it == Items.blaze_rod) return 1;
        if (it == Items.diamond_horse_armor) return 2;
        if (it instanceof ItemArmor) {
            switch (((ItemArmor) it).getArmorMaterial().ordinal()) { // LEATHER, CHAIN, IRON, GOLD, DIAMOND
                case 4: return 2;
                case 1: case 2: case 3: return 1;
                default: return 0;
            }
        }
        String mat = null;
        if (it instanceof ItemSword) mat = ((ItemSword) it).getToolMaterialName();
        else if (it instanceof ItemTool) mat = ((ItemTool) it).getToolMaterialName();
        if (mat != null) {
            if (mat.equals("EMERALD")) return 2; // kim cương trong 1.8.9
            if (mat.equals("IRON") || mat.equals("GOLD")) return 1;
            return 0;
        }
        return 0;
    }

    /** Cấp của tia = max(độ hiếm, giá trị vật phẩm), +1 nếu có phù phép. */
    private int tier(ItemStack s) {
        int t = Math.max(s.getRarity().ordinal(), valueTier(s));
        if (s.isItemEnchanted()) t++;
        return t < 0 ? 0 : t > 3 ? 3 : t;
    }

    private boolean passesFilter(ItemStack s) {
        if (!filterOn.value) return showCommon.value || s.getRarity() != EnumRarity.COMMON;
        Item it = s.getItem();
        if (it == Items.iron_ingot && fIron.value) return true;
        if (it == Items.gold_ingot && fGold.value) return true;
        if (it == Items.diamond && fDiamond.value) return true;
        if (it == Items.emerald && fEmerald.value) return true;
        if (it == Items.golden_apple && fGapple.value) return true;
        if (it == Items.ender_pearl && fPearl.value) return true;
        if (fGear.value && (it instanceof ItemSword || it instanceof ItemTool || it instanceof ItemBow)) return true;
        if (fArmor.value && it instanceof ItemArmor) return true;
        if (fFood.value && it instanceof ItemFood) return true;
        if (fPotion.value && it instanceof ItemPotion) return true;
        if (fEnchanted.value && s.isItemEnchanted()) return true;
        return fRare.value && s.getRarity() != EnumRarity.COMMON;
    }

    private static int layers(ItemStack s) {
        int n = s.stackSize;
        return n > 48 ? 5 : n > 32 ? 4 : n > 16 ? 3 : n > 1 ? 2 : 1;
    }

    /** Renderer vật phẩm: giống RenderEntityItem nhưng nằm phẳng + vẽ tia sáng. */
    public static final class PhysicsRenderer extends RenderEntityItem {
        private static final int SEGMENTS = 6;
        private static final int RING_POINTS = 24;

        private final ItemPhysicsModule m;
        private final RenderItem ri;
        /** Mỗi vật phẩm: {trọng số "đang bay" 0..1, góc xoay tích lũy, thời điểm cập nhật cuối (ns)}. */
        private final Map<EntityItem, double[]> state = new WeakHashMap<EntityItem, double[]>();

        public PhysicsRenderer(RenderManager rm, RenderItem ri, ItemPhysicsModule m) {
            super(rm, ri);
            this.ri = ri;
            this.m = m;
        }

        @Override
        public void doRender(EntityItem e, double x, double y, double z, float yaw, float pt) {
            ItemStack stack = e.getEntityItem();
            if (!m.enabled || stack == null) {
                super.doRender(e, x, y, z, yaw, pt);
                return;
            }
            double now = (System.nanoTime() - T0) / 1e9;
            double[] st = state.get(e);
            if (st == null) {
                st = new double[] { e.onGround ? 0.0 : 1.0, 0.0, now };
                state.put(e, st);
            }
            double dt = Math.max(0.0, Math.min(0.1, now - st[2]));
            st[2] = now;
            st[0] += ((e.onGround ? 0.0 : 1.0) - st[0]) * Math.min(1.0, dt * 12.0); // chuyển mượt khi chạm đất
            st[1] += st[0] * dt * 540.0;                                           // chỉ xoay khi đang bay
            float w = (float) st[0];

            if (m.physics.value) renderPhysics(e, stack, x, y, z, pt, w, (float) st[1]);
            else super.doRender(e, x, y, z, yaw, pt);
            if (m.beam.value) renderBeam(e, stack, x, y, z, pt, w);
        }

        private void renderPhysics(EntityItem e, ItemStack stack, double x, double y, double z, float pt, float w, float spin) {
            IBakedModel model = ri.getItemModelMesher().getItemModel(stack);
            boolean is3d = model.isGui3d();
            float size = m.size.getFloat();
            boolean blur = bindEntityTexture(e);
            if (blur) renderManager.renderEngine.getTexture(getEntityTexture(e)).setBlurMipmap(false, false);

            GlStateManager.enableRescaleNormal();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.enableBlend();
            RenderHelper.enableStandardItemLighting();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

            Random rnd = new Random(e.getEntityId() * 31L + 7L);
            float baseYaw = rnd.nextFloat() * 360f;
            float t = e.ticksExisted + pt;
            int n = layers(stack);
            for (int k = 0; k < n; k++) {
                GlStateManager.pushMatrix();
                GlStateManager.translate((float) x, (float) y, (float) z);
                float ox = 0f, oz = 0f;
                if (k > 0) {
                    ox = (rnd.nextFloat() - 0.5f) * 0.25f * size;
                    oz = (rnd.nextFloat() - 0.5f) * 0.25f * size;
                }
                float layerYaw = k == 0 ? baseYaw : rnd.nextFloat() * 360f;
                if (is3d) {
                    float sc = 0.5f * size;
                    GlStateManager.translate(ox, 0.125f * size + 0.003f, oz);
                    GlStateManager.rotate(layerYaw + spin, 0f, 1f, 0f);
                    GlStateManager.rotate(w * 25f * (float) Math.sin(t * 0.4f), 1f, 0f, 0f);
                    GlStateManager.scale(sc, sc, sc);
                } else {
                    float sc = 0.8f * size;
                    GlStateManager.translate(ox, 0.0125f * size + 0.003f + k * 0.03f * size + w * 0.1f * size, oz);
                    GlStateManager.rotate(layerYaw + spin, 0f, 1f, 0f);
                    GlStateManager.rotate(-90f + w * 60f * (float) Math.sin(t * 0.35f + k), 1f, 0f, 0f);
                    GlStateManager.scale(sc, sc, sc);
                }
                ri.renderItem(stack, model);
                GlStateManager.popMatrix();
            }

            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
            bindEntityTexture(e);
            if (blur) renderManager.renderEngine.getTexture(getEntityTexture(e)).restoreLastBlurMipmap();
        }

        private void renderBeam(EntityItem e, ItemStack stack, double x, double y, double z, float pt, float w) {
            float fade = 1f - w; // chỉ hiện khi vật phẩm đã nằm yên trên đất
            if (fade < 0.02f) return;
            if (!m.passesFilter(stack)) return;

            // Mờ dần ở 25% khoảng cách cuối thay vì biến mất đột ngột
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                double max = m.maxDist.value;
                double d2 = mc.thePlayer.getDistanceSqToEntity(e);
                if (d2 > max * max) return;
                double start = max * 0.75;
                double d = Math.sqrt(d2);
                if (d > start) fade *= (float) (1.0 - (d - start) / (max - start));
            }

            int tier = m.tier(stack);
            int rgb = m.beamColor(stack, ri);
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            // Lõi: pha màu về phía trắng để có cảm giác "nóng" ở giữa tia
            int cr = r + (int) ((255 - r) * 0.6f);
            int cg = g + (int) ((255 - g) * 0.6f);
            int cb = b + (int) ((255 - b) * 0.6f);

            float height = m.beamHeight.getFloat() * TIER_HEIGHT[tier];
            float hw = 0.04f * m.beamWidth.getFloat() * TIER_WIDTH[tier];
            float t = e.ticksExisted + pt;
            float phase = (e.getEntityId() % 16) * 0.7f;
            float pulse = 0.88f + 0.12f * (float) Math.sin(t * 0.12f + phase);
            float alpha = Math.min(1f, m.opacity.getFloat() / 100f * TIER_GLOW[tier]) * fade * pulse;

            GlStateManager.pushMatrix();
            GlStateManager.translate((float) x, (float) y, (float) z);
            GlStateManager.disableLighting();
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 1); // cộng sáng
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

            if (m.groundGlow.value) {
                float radius = (0.32f + 0.1f * tier) * m.beamWidth.getFloat();
                ring(radius, r, g, b, alpha * 0.55f);
            }
            // Gộp 3 lớp (quầng ngoài, thân, lõi) vào một lần draw để giảm chi phí khi có nhiều vật phẩm
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
            box(wr, hw * 2.8f, height, r, g, b, alpha * 0.22f);
            box(wr, hw * 1.4f, height, r, g, b, alpha * 0.55f);
            box(wr, hw * 0.55f, height * 0.9f, cr, cg, cb, alpha * 0.95f);
            tess.draw();

            GlStateManager.depthMask(true);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableBlend();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            int br = e.getBrightnessForRender(pt);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, br & 65535, br >> 16);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }

        /** Hộp đứng 4 mặt, đậm ở chân và mờ dần mượt (đường cong bậc hai) về đỉnh. */
        private void box(WorldRenderer wr, float hw, float h, int r, int g, int b, float alpha) {
            side(wr, -hw, -hw, hw, -hw, h, r, g, b, alpha);
            side(wr, hw, -hw, hw, hw, h, r, g, b, alpha);
            side(wr, hw, hw, -hw, hw, h, r, g, b, alpha);
            side(wr, -hw, hw, -hw, -hw, h, r, g, b, alpha);
        }

        private void side(WorldRenderer wr, float x1, float z1, float x2, float z2, float h, int r, int g, int b, float alpha) {
            for (int i = 0; i < SEGMENTS; i++) {
                float t0 = (float) i / SEGMENTS, t1 = (float) (i + 1) / SEGMENTS;
                int a0 = alphaAt(alpha, t0), a1 = alphaAt(alpha, t1);
                wr.pos(x1, h * t0, z1).color(r, g, b, a0).endVertex();
                wr.pos(x2, h * t0, z2).color(r, g, b, a0).endVertex();
                wr.pos(x2, h * t1, z2).color(r, g, b, a1).endVertex();
                wr.pos(x1, h * t1, z1).color(r, g, b, a1).endVertex();
            }
        }

        private static int alphaAt(float alpha, float t) {
            float f = 1f - t;
            return Math.max(0, Math.min(255, (int) (alpha * f * f * 255f)));
        }

        /** Vòng sáng trên mặt đất: đậm ở tâm, mờ dần ra rìa. */
        private void ring(float radius, int r, int g, int b, float alpha) {
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            int ac = Math.max(0, Math.min(255, (int) (alpha * 255f)));
            wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(0, 0.02, 0).color(r, g, b, ac).endVertex();
            for (int i = 0; i <= RING_POINTS; i++) {
                double a = Math.PI * 2.0 * i / RING_POINTS;
                wr.pos(Math.cos(a) * radius, 0.02, Math.sin(a) * radius).color(r, g, b, 0).endVertex();
            }
            tess.draw();
        }
    }
}
