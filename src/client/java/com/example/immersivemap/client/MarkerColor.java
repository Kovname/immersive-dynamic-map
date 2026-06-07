package com.example.immersivemap.client;

import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.registry.entry.RegistryEntry;

public enum MarkerColor {
    WHITE("white", MapDecorationTypes.BANNER_WHITE),
    RED("red", MapDecorationTypes.BANNER_RED),
    BLUE("blue", MapDecorationTypes.BANNER_BLUE),
    GREEN("green", MapDecorationTypes.BANNER_GREEN),
    YELLOW("yellow", MapDecorationTypes.BANNER_YELLOW),
    PURPLE("purple", MapDecorationTypes.BANNER_PURPLE),
    CYAN("cyan", MapDecorationTypes.BANNER_CYAN),
    BLACK("black", MapDecorationTypes.BANNER_BLACK);

    private final String id;
    private final RegistryEntry<MapDecorationType> decorationType;

    MarkerColor(String id, RegistryEntry<MapDecorationType> decorationType) {
        this.id = id;
        this.decorationType = decorationType;
    }

    public String id() {
        return id;
    }

    public RegistryEntry<MapDecorationType> decorationType() {
        return decorationType;
    }

    public MarkerColor next() {
        MarkerColor[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static MarkerColor byId(String id) {
        for (MarkerColor color : values()) {
            if (color.id.equals(id)) {
                return color;
            }
        }

        return RED;
    }
}
