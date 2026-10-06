package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.LayerId;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * 128x128 texture with exactly the pixels a vanilla filled map would show for this view: the most common map color
 * of each cell, shaded by the height difference to the cell north of it or by water depth, with the vanilla checker
 * dither. Unexplored pixels stay transparent so the parchment shows through. Rebuilt only when the view or the
 * underlying chunks change.
 */
public final class MapTexture {
    private static final int SIZE = MapController.MAP_SIZE;
    private static final MapColor.Brightness[] BRIGHTNESS = {
            MapColor.Brightness.LOW, MapColor.Brightness.NORMAL, MapColor.Brightness.HIGH
    };

    private final Identifier id = ImmersiveMapMod.id("dynamic_map");
    private final float[] previousRow = new float[SIZE];
    private final boolean[] previousExplored = new boolean[SIZE];
    private NativeImageBackedTexture texture;

    private int originX = Integer.MIN_VALUE;
    private int originZ;
    private int builtScale = -1;
    private LayerId builtLayer;
    private int builtRevision = -1;
    private MapStore builtStore;

    public Identifier id() {
        return id;
    }

    public void invalidate() {
        builtRevision = -1;
        builtStore = null;
    }

    /** Top-left map cell of the last build, in cells of the built scale. */
    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    /** Prepares the texture for a view centered on the given block position. */
    public void update(MapStore store, LayerId layer, int scale, double centerX, double centerZ) {
        int blocks = 1 << scale;
        int ox = Math.floorDiv((int) Math.floor(centerX), blocks) - SIZE / 2;
        int oz = Math.floorDiv((int) Math.floor(centerZ), blocks) - SIZE / 2;
        if (texture == null) {
            texture = new NativeImageBackedTexture(SIZE, SIZE, true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        }
        originX = ox;
        originZ = oz;
        if (store == null || layer == null) {
            return;
        }
        if (ox == lastBuiltX && oz == lastBuiltZ && scale == builtScale && store == builtStore
                && Objects.equals(layer, builtLayer) && store.revision() == builtRevision) {
            return;
        }
        lastBuiltX = ox;
        lastBuiltZ = oz;
        builtScale = scale;
        builtLayer = layer;
        builtStore = store;
        build(store, layer, scale, ox, oz);
        // Building may request regions from disk; their arrival bumps the revision again.
        builtRevision = store.revision();
        texture.upload();
    }

    private int lastBuiltX = Integer.MIN_VALUE;
    private int lastBuiltZ = Integer.MIN_VALUE;

    private void build(MapStore store, LayerId layer, int scale, int ox, int oz) {
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        int chunkShift = 4 - scale;
        int cellsPerChunk = 1 << chunkShift;
        int mask = cellsPerChunk - 1;
        float slopeFactor = 4.0F / ((1 << scale) + 4);

        ChunkSurface cached = null;
        int cachedX = Integer.MIN_VALUE;
        int cachedZ = Integer.MIN_VALUE;
        byte[] colors = null;

        // Start one row above the map so the first visible row has a northern neighbour, like vanilla.
        for (int pz = -1; pz < SIZE; pz++) {
            int cellZ = oz + pz;
            int chunkZ = cellZ >> chunkShift;
            int localZ = cellZ & mask;
            for (int px = 0; px < SIZE; px++) {
                int cellX = ox + px;
                int chunkX = cellX >> chunkShift;
                if (chunkX != cachedX || chunkZ != cachedZ) {
                    cachedX = chunkX;
                    cachedZ = chunkZ;
                    cached = store.get(layer, chunkX, chunkZ);
                    colors = cached == null ? null : cached.colors(scale);
                }
                int cell = localZ * cellsPerChunk + (cellX & mask);
                int colorId = colors == null ? 0 : colors[cell] & 0xFF;
                boolean explored = colorId != 0;
                float height = explored ? cached.height(scale, cell) : 0.0F;

                if (pz >= 0) {
                    int abgr = 0;
                    if (explored) {
                        int checker = (cellX + cellZ) & 1;
                        MapColor color = MapColor.get(colorId);
                        int brightness;
                        if (color == MapColor.WATER_BLUE) {
                            double f = cached.depth(scale, cell) * 0.1 + checker * 0.2;
                            brightness = f < 0.5 ? 2 : f > 0.9 ? 0 : 1;
                        } else {
                            float north = previousExplored[px] ? previousRow[px] : height;
                            double f = (height - north) * slopeFactor + (checker - 0.5) * 0.4;
                            brightness = f > 0.6 ? 2 : f < -0.6 ? 0 : 1;
                        }
                        abgr = color.getRenderColor(BRIGHTNESS[brightness]);
                    }
                    image.setColor(px, pz, abgr);
                }
                previousRow[px] = height;
                previousExplored[px] = explored;
            }
        }
    }
}
