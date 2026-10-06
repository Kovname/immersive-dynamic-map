package com.example.immersivemap.map;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * One map layer of a dimension: the vanilla surface map or an explored cave band of {@link #BAND_HEIGHT} blocks.
 */
public record LayerId(Identifier dimension, int band) {
    public static final int SURFACE = Integer.MIN_VALUE;
    public static final int BAND_HEIGHT = 16;
    /** Covers every build height a datapack can configure (-2032..2031). */
    public static final int MIN_BAND = -128;
    public static final int MAX_BAND = 127;

    public static LayerId surface(Identifier dimension) {
        return new LayerId(dimension, SURFACE);
    }

    public static LayerId cave(Identifier dimension, int band) {
        return new LayerId(dimension, band);
    }

    public static int bandOf(int y) {
        return Math.floorDiv(y, BAND_HEIGHT);
    }

    public boolean isSurface() {
        return band == SURFACE;
    }

    public boolean isValid() {
        return isSurface() || band >= MIN_BAND && band <= MAX_BAND;
    }

    public String folderName() {
        return isSurface() ? "surface" : "cave_" + band;
    }

    public static String dimensionFolder(Identifier dimension) {
        return URLEncoder.encode(dimension.toString(), StandardCharsets.UTF_8);
    }

    public static LayerId fromFolders(String dimensionFolder, String layerFolder) {
        Identifier dimension = Identifier.tryParse(URLDecoder.decode(dimensionFolder, StandardCharsets.UTF_8));
        if (dimension == null) {
            return null;
        }
        if (layerFolder.equals("surface")) {
            return surface(dimension);
        }
        if (layerFolder.startsWith("cave_")) {
            try {
                LayerId layer = cave(dimension, Integer.parseInt(layerFolder.substring(5)));
                return layer.isValid() ? layer : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(dimension);
        buf.writeInt(band);
    }

    public static LayerId read(PacketByteBuf buf) {
        return new LayerId(buf.readIdentifier(), buf.readInt());
    }
}
