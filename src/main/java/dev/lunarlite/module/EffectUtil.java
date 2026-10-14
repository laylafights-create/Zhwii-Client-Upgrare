package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;

/** Hàm dùng chung cho nhóm module Hiệu ứng: bảng hạt (particle) và hàm sinh hạt. */
public final class EffectUtil {
    private EffectUtil() {}

    /** Tên hạt hiển thị trong menu (khớp với {@link #type(String)}). */
    public static final String[] PARTICLES = {
        "Crit", "Magic Crit", "Flame", "Heart", "Note", "Smoke", "Portal", "Cloud",
        "Blood", "Slime", "Happy Villager", "Firework"
    };

    public static EnumParticleTypes type(String name) {
        if ("Crit".equals(name)) return EnumParticleTypes.CRIT;
        if ("Magic Crit".equals(name)) return EnumParticleTypes.CRIT_MAGIC;
        if ("Flame".equals(name)) return EnumParticleTypes.FLAME;
        if ("Heart".equals(name)) return EnumParticleTypes.HEART;
        if ("Note".equals(name)) return EnumParticleTypes.NOTE;
        if ("Smoke".equals(name)) return EnumParticleTypes.SMOKE_NORMAL;
        if ("Portal".equals(name)) return EnumParticleTypes.PORTAL;
        if ("Cloud".equals(name)) return EnumParticleTypes.CLOUD;
        if ("Blood".equals(name)) return EnumParticleTypes.REDSTONE;
        if ("Slime".equals(name)) return EnumParticleTypes.SLIME;
        if ("Happy Villager".equals(name)) return EnumParticleTypes.VILLAGER_HAPPY;
        if ("Firework".equals(name)) return EnumParticleTypes.FIREWORKS_SPARK;
        return EnumParticleTypes.CRIT;
    }

    /** Sinh một hạt phía client (không gửi gì lên server). */
    public static void spawn(EnumParticleTypes t, double x, double y, double z, double mx, double my, double mz) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        mc.theWorld.spawnParticle(t, x, y, z, mx, my, mz);
    }

    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
