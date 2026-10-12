package dev.lunarlite.module;

public enum Category {
    HUD("HUD"), PVP("PvP"), VISUAL("Hình ảnh"), UTILITY("Tiện ích");

    public final String label;

    Category(String label) { this.label = label; }
}
