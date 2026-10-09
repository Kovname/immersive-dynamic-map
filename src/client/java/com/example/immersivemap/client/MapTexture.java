package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.LayerId;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 128x128 texture with exactly the pixels a vanilla filled map would show for this view: the most common map color
 * of each cell, shaded by the height difference to the cell north of it or by water depth, with the vanilla checker
 * dither. Unexplored pixels stay transparent so the parchment shows through, and the explored area frays out into
 * the parchment in a checker like the edge of a vanilla map. Rebuilt only when the view or the underlying chunks
 * change. Cave walls use the darkest vanilla shade without dither, explored void is a faint tint.
 *
 * <p>Where the shown layer is unexplored, the nearest explored neighbouring layers show through, fainter the further
 * away they are (see {@link Blend}).
 */
public final class MapTexture {
    private static final int SIZE = MapController.MAP_SIZE;
    /** Cells read around the map, so shading and the edge fray near the border see their neighbours. */
    private static final int MARGIN = 2;
    private static final int SPAN = SIZE + 2 * MARGIN;
    /** Indexed by vanilla brightness + 1, so layers below can go one shade darker. */
    private static final MapColor.Brightness[] SHADES = {
            MapColor.Brightness.LOWEST, MapColor.Brightness.LOW, MapColor.Brightness.NORMAL, MapColor.Brightness.HIGH
    };
    /** ABGR: a light ink wash over the parchment. */
    private static final int VOID_ABGR = 0x2A000000;
    private static final int[] NEIGHBOURS = {-SPAN - 1, -SPAN, -SPAN + 1, -1, 1, SPAN - 1, SPAN, SPAN + 1};
    /** Each blended layer further away is this much fainter than the previous one. */
    private static final float BLEND_FALLOFF = 0.6F;

    /**
     * Neighbouring layers drawn faded where the shown layer is unexplored: up to {@code above} bands up and
     * {@code below} bands down, the nearest at {@code strength} opacity. Under ground the surface map counts as the
     * band its height falls in ({@code surface} is off in dimensions mapped level by level); on the surface map the
     * bands from {@code referenceBand} (the player's) down show through.
     */
    public record Blend(int above, int below, float strength, int referenceBand, boolean surface) {
        public static final Blend NONE = new Blend(0, 0, 0.0F, 0, false);

        boolean active() {
            return strength > 0.0F && (above > 0 || below > 0);
        }
    }

    /** Map inputs of one layer for the map and its margin. */
    private static final class Grid {
        final byte[] raw = new byte[SPAN * SPAN];
        final float[] height = new float[SPAN * SPAN];
        final byte[] depth = new byte[SPAN * SPAN];
        boolean empty;

        void fill(MapStore store, LayerId layer, int scale, int ox, int oz) {
            int chunkShift = 4 - scale;
            int cellsPerChunk = 1 << chunkShift;
            int mask = cellsPerChunk - 1;
            ChunkSurface cached = null;
            int cachedX = Integer.MIN_VALUE;
            int cachedZ = Integer.MIN_VALUE;
            byte[] colors = null;
            empty = true;
            for (int gz = 0; gz < SPAN; gz++) {
                int cellZ = oz + gz - MARGIN;
                int chunkZ = cellZ >> chunkShift;
                int localZ = cellZ & mask;
                for (int gx = 0; gx < SPAN; gx++) {
                    int cellX = ox + gx - MARGIN;
                    int chunkX = cellX >> chunkShift;
                    if (chunkX != cachedX || chunkZ != cachedZ) {
                        cachedX = chunkX;
                        cachedZ = chunkZ;
                        cached = store.get(layer, chunkX, chunkZ);
                        colors = cached == null ? null : cached.colors(scale);
                    }
                    int cell = localZ * cellsPerChunk + (cellX & mask);
                    int i = gz * SPAN + gx;
                    int r = colors == null ? 0 : colors[cell] & 0x7F;
                    raw[i] = (byte) r;
                    if (r != 0) {
                        empty = false;
                        height[i] = cached.height(scale, cell);
                        depth[i] = (byte) cached.depth(scale, cell);
                    }
                }
            }
        }
    }

    private final Identifier id;
    private final Grid current = new Grid();
    /** Explored floor cells touching unexplored ones, for the edge fray. */
    private final boolean[] rim = new boolean[SPAN * SPAN];
    private final Grid surface = new Grid();
    private final List<Grid> neighbours = new ArrayList<>();
    /** Per entry of {@link #neighbours}: distance in bands, negative for layers below. Nearest first. */
    private final int[] neighbourDistance = new int[16];
    private final int[] alphaByDistance = new int[16];
    private NativeImageBackedTexture texture;

    private int originX = Integer.MIN_VALUE;
    private int originZ;
    private int builtScale = -1;
    private LayerId builtLayer;
    private Blend builtBlend;
    private int builtRevision = -1;
    private MapStore builtStore;
    private int lastBuiltX = Integer.MIN_VALUE;
    private int lastBuiltZ = Integer.MIN_VALUE;

    public MapTexture(String name) {
        this.id = ImmersiveMapMod.id(name);
    }

    public Identifier id() {
        ensureTexture();
        return id;
    }

    private void ensureTexture() {
        if (texture == null) {
            texture = new NativeImageBackedTexture(SIZE, SIZE, true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        }
    }

    public void invalidate() {
        builtRevision = -1;
        builtStore = null;
        builtLayer = null;
    }

    /** The layer of the last build, or {@code null}. */
    public LayerId layer() {
        return builtLayer;
    }

    /** Top-left map cell of the last build, in cells of the built scale. */
    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    /** Prepares the texture for a view centered on the given block position. */
    public void update(MapStore store, LayerId layer, Blend blend, int scale, double centerX, double centerZ) {
        int blocks = 1 << scale;
        int ox = Math.floorDiv((int) Math.floor(centerX), blocks) - SIZE / 2;
        int oz = Math.floorDiv((int) Math.floor(centerZ), blocks) - SIZE / 2;
        ensureTexture();
        originX = ox;
        originZ = oz;
        if (store == null || layer == null) {
            return;
        }
        if (ox == lastBuiltX && oz == lastBuiltZ && scale == builtScale && store == builtStore
                && Objects.equals(layer, builtLayer) && blend.equals(builtBlend) && store.revision() == builtRevision) {
            return;
        }
        lastBuiltX = ox;
        lastBuiltZ = oz;
        builtScale = scale;
        builtLayer = layer;
        builtBlend = blend;
        builtStore = store;
        build(store, layer, blend, scale, ox, oz);
        // Building may request regions from disk; their arrival bumps the revision again.
        builtRevision = store.revision();
        texture.upload();
    }

    private void build(MapStore store, LayerId layer, Blend blend, int scale, int ox, int oz) {
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        float slopeFactor = 4.0F / ((1 << scale) + 4);
        for (int distance = 1; distance < alphaByDistance.length; distance++) {
            alphaByDistance[distance] = Math.round(255.0F * blend.strength() * (float) Math.pow(BLEND_FALLOFF, distance - 1));
        }
        current.fill(store, layer, scale, ox, oz);
        findRim();
        int count = blend.active() ? fillNeighbours(store, layer, blend, scale, ox, oz) : 0;
        boolean withSurface = blend.active() && blend.surface() && !layer.isSurface()
                && fillSurface(store, layer, scale, ox, oz);

        for (int pz = 0; pz < SIZE; pz++) {
            int cellZ = oz + pz;
            for (int px = 0; px < SIZE; px++) {
                int cellX = ox + px;
                int i = (pz + MARGIN) * SPAN + px + MARGIN;
                int abgr = 0;
                if (current.raw[i] != 0 && !(((cellX + cellZ) & 1) == 0 && frays(i))) {
                    abgr = color(current, i, cellX, cellZ, slopeFactor, 0);
                } else if (count > 0 || withSurface) {
                    abgr = blended(layer, blend, i, cellX, cellZ, slopeFactor, count, withSurface);
                }
                image.setColor(px, pz, abgr);
            }
        }
    }

    /** Fills {@link #neighbours} with the explored bands around {@code layer}, nearest first; returns their count. */
    private int fillNeighbours(MapStore store, LayerId layer, Blend blend, int scale, int ox, int oz) {
        int count = 0;
        int reach = Math.max(blend.above(), blend.below());
        for (int distance = 1; distance <= reach; distance++) {
            for (int side = 1; side >= -1; side -= 2) {
                if (distance > (side > 0 ? blend.above() : blend.below())) {
                    continue;
                }
                int band;
                if (layer.isSurface()) {
                    // Nothing lies above the surface; below it, start at the player's own band.
                    if (side > 0) {
                        continue;
                    }
                    band = blend.referenceBand() - distance + 1;
                } else {
                    band = layer.band() + side * distance;
                }
                if (band < LayerId.MIN_BAND || band > LayerId.MAX_BAND) {
                    continue;
                }
                if (count == neighbours.size()) {
                    neighbours.add(new Grid());
                }
                Grid grid = neighbours.get(count);
                grid.fill(store, LayerId.cave(layer.dimension(), band), scale, ox, oz);
                if (!grid.empty) {
                    neighbourDistance[count] = side * distance;
                    count++;
                }
            }
        }
        return count;
    }

    private boolean fillSurface(MapStore store, LayerId layer, int scale, int ox, int oz) {
        surface.fill(store, LayerId.surface(layer.dimension()), scale, ox, oz);
        return !surface.empty;
    }

    /** The nearest explored neighbouring layer at this cell, faded by its distance; 0 if there is none. */
    private int blended(LayerId layer, Blend blend, int i, int cellX, int cellZ, float slopeFactor, int count,
                        boolean withSurface) {
        Grid best = null;
        int bestDistance = 0;
        for (int k = 0; k < count; k++) {
            if (neighbours.get(k).raw[i] != 0) {
                best = neighbours.get(k);
                bestDistance = neighbourDistance[k];
                break;
            }
        }
        if (withSurface && explored(surface.raw[i])) {
            int bands = LayerId.bandOf(Math.round(surface.height[i])) - layer.band();
            int distance = Math.max(1, Math.abs(bands));
            if (distance <= (bands < 0 ? blend.below() : blend.above())
                    && (best == null || distance < Math.abs(bestDistance))) {
                best = surface;
                bestDistance = bands < 0 ? -distance : distance;
            }
        }
        if (best == null) {
            return 0;
        }
        int alpha = alphaByDistance[Math.abs(bestDistance)];
        int abgr = color(best, i, cellX, cellZ, slopeFactor, bestDistance < 0 ? -1 : 0);
        int baseAlpha = abgr >>> 24;
        return (baseAlpha * alpha / 255) << 24 | abgr & 0xFFFFFF;
    }

    private void findRim() {
        for (int gz = 1; gz < SPAN - 1; gz++) {
            for (int gx = 1; gx < SPAN - 1; gx++) {
                int i = gz * SPAN + gx;
                boolean edge = false;
                if (current.raw[i] != 0 && !ChunkSurface.isWall(current.raw[i])) {
                    for (int offset : NEIGHBOURS) {
                        if (current.raw[i + offset] == 0) {
                            edge = true;
                            break;
                        }
                    }
                }
                rim[i] = edge;
            }
        }
    }

    /**
     * Whether an explored floor cell is within two cells of the unexplored area, like the two-cell checker ring at the
     * edge of what a vanilla map has drawn. Walls never fray, and the distance does not reach through them, so cave
     * floors along a wall stay whole.
     */
    private boolean frays(int i) {
        if (ChunkSurface.isWall(current.raw[i])) {
            return false;
        }
        if (rim[i]) {
            return true;
        }
        for (int offset : NEIGHBOURS) {
            if (rim[i + offset]) {
                return true;
            }
        }
        return false;
    }

    private static boolean explored(int raw) {
        return raw != 0 && raw != ChunkSurface.VOID_COLOR;
    }

    /** Vanilla color of an explored cell; {@code shade} -1 draws it one vanilla shade darker. */
    private static int color(Grid grid, int i, int cellX, int cellZ, float slopeFactor, int shade) {
        int raw = grid.raw[i];
        if (raw == ChunkSurface.VOID_COLOR) {
            return VOID_ABGR;
        }
        if (ChunkSurface.isWall(raw)) {
            return wallColor(MapColor.get(raw & ChunkSurface.COLOR_MASK));
        }
        int checker = (cellX + cellZ) & 1;
        MapColor color = MapColor.get(raw);
        int brightness;
        if (color == MapColor.WATER_BLUE) {
            double f = (grid.depth[i] & 0xFF) * 0.1 + checker * 0.2;
            brightness = f < 0.5 ? 2 : f > 0.9 ? 0 : 1;
        } else {
            float height = grid.height[i];
            int north = i - SPAN;
            float northHeight = explored(grid.raw[north]) ? grid.height[north] : height;
            double f = (height - northHeight) * slopeFactor + (checker - 0.5) * 0.4;
            brightness = f > 0.6 ? 2 : f < -0.6 ? 0 : 1;
        }
        return color.getRenderColor(SHADES[brightness + 1 + shade]);
    }

    /** The darkest vanilla shade, darkened further so walls outline caves clearly next to shaded floors. */
    private static int wallColor(MapColor color) {
        int abgr = color.getRenderColor(MapColor.Brightness.LOWEST);
        int r = (abgr & 0xFF) * 3 / 5;
        int g = (abgr >> 8 & 0xFF) * 3 / 5;
        int b = (abgr >> 16 & 0xFF) * 3 / 5;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }
}
