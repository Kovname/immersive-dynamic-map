package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.config.MapConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapTextureManager implements AutoCloseable {
    public static final int MAP_SIZE = 128;

    private static final int COLOR_EMPTY = 0x00000000;
    private static final int BACKGROUND_CHUNKS_PER_TICK = 8;

    private final int[] terrainColors = new int[MAP_SIZE * MAP_SIZE];
    private final boolean[] viewExplored = new boolean[MAP_SIZE * MAP_SIZE];
    private final LinkedHashMap<Long, Integer> exploredPixels = new LinkedHashMap<>(4096, 0.75F, true);

    private NativeImage image;
    private NativeImageBackedTexture texture;
    private Identifier id;
    private int terrainUpdateTicks;
    private int lastCenterX = Integer.MIN_VALUE;
    private int lastCenterZ = Integer.MIN_VALUE;
    private int lastScale = Integer.MIN_VALUE;
    private int backgroundChunkCursor;
    private boolean dirty;

    public MapTextureManager() {
        initialize();
    }

    public Identifier getTextureId() {
        initialize();
        return id;
    }

    public void tickUpdate(World world, int centerX, int centerZ, int scale) {
        initialize();
        if (world == null) {
            return;
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        int updateInterval = MathHelper.clamp(config.terrainUpdateIntervalTicks, 1, 3);
        boolean viewChanged = centerX != lastCenterX || centerZ != lastCenterZ || scale != lastScale;
        terrainUpdateTicks++;

        if (viewChanged || terrainUpdateTicks >= updateInterval) {
            terrainUpdateTicks = 0;
            lastCenterX = centerX;
            lastCenterZ = centerZ;
            lastScale = scale;
            recordAroundPlayer(world, scale);
            composeView(centerX, centerZ, scale);
            trimCache(Math.max(MAP_SIZE * MAP_SIZE, config.cachedMapPixels));

            for (int z = 0; z < MAP_SIZE; z++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    image.setColor(x, z, terrainColors[index(x, z)]);
                }
            }

            texture.upload();
        }
    }

    public void tickBackground(World world) {
        if (world == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world != world) {
            return;
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        terrainUpdateTicks++;
        int updateInterval = MathHelper.clamp(config.terrainUpdateIntervalTicks, 1, 3);
        if (terrainUpdateTicks < updateInterval) {
            return;
        }

        terrainUpdateTicks = 0;
        int viewDistance = MathHelper.clamp(client.options.getClampedViewDistance(), 2, 32);
        int diameter = viewDistance * 2 + 1;
        int totalChunks = diameter * diameter;
        int playerChunkX = ChunkSectionPos.getSectionCoord(client.player.getBlockX());
        int playerChunkZ = ChunkSectionPos.getSectionCoord(client.player.getBlockZ());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        Map<Long, WorldChunk> chunkCache = new LinkedHashMap<>();

        for (int processed = 0; processed < BACKGROUND_CHUNKS_PER_TICK && processed < totalChunks; processed++) {
            int cursor = Math.floorMod(backgroundChunkCursor++, totalChunks);
            int chunkX = playerChunkX + cursor % diameter - viewDistance;
            int chunkZ = playerChunkZ + cursor / diameter - viewDistance;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }

            recordLoadedChunk(world, pos, chunkCache, chunkX, chunkZ);
        }

        trimCache(Math.max(MAP_SIZE * MAP_SIZE, config.cachedMapPixels));
    }

    private void initialize() {
        if (image != null && texture != null) {
            return;
        }

        image = new NativeImage(MAP_SIZE, MAP_SIZE, false);
        texture = new NativeImageBackedTexture(image);
        id = Identifier.of(ImmersiveMapMod.MOD_ID, "dynamic_map");
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
    }

    private void recordAroundPlayer(World world, int scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world != world) {
            return;
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        int blocksPerPixel = 1 << scale;
        int radiusPixels = getRevealRadius(config.surfaceRevealRadiusPixels, scale);
        int playerCellX = Math.floorDiv(client.player.getBlockX(), blocksPerPixel);
        int playerCellZ = Math.floorDiv(client.player.getBlockZ(), blocksPerPixel);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        Map<Long, WorldChunk> chunkCache = new LinkedHashMap<>();

        for (int distance = 0; distance <= radiusPixels; distance++) {
            int radiusSquared = distance * distance;
            int innerSquared = distance == 0 ? -1 : (distance - 1) * (distance - 1);
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    int squared = dx * dx + dz * dz;
                    if (squared > radiusSquared || squared <= innerSquared) {
                        continue;
                    }

                    recordCell(world, pos, chunkCache, playerCellX + dx, playerCellZ + dz, scale, blocksPerPixel);
                }
            }
        }
    }

    private void recordCell(
            World world,
            BlockPos.Mutable pos,
            Map<Long, WorldChunk> chunkCache,
            int cellX,
            int cellZ,
            int scale,
            int blocksPerPixel) {
        long key = packKey(cellX, cellZ, scale);
        if (exploredPixels.containsKey(key)) {
            return;
        }

        int worldX = cellX * blocksPerPixel;
        int worldZ = cellZ * blocksPerPixel;
        Integer color = samplePixel(world, pos, chunkCache, worldX, worldZ, blocksPerPixel);
        if (color != null) {
            exploredPixels.put(key, color);
            dirty = true;
        }
    }

    private void recordLoadedChunk(
            World world,
            BlockPos.Mutable pos,
            Map<Long, WorldChunk> chunkCache,
            int chunkX,
            int chunkZ) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        for (int scale = 0; scale <= 4; scale++) {
            int blocksPerPixel = 1 << scale;
            int cellsPerChunk = Math.max(1, 16 / blocksPerPixel);
            for (int x = 0; x < cellsPerChunk; x++) {
                for (int z = 0; z < cellsPerChunk; z++) {
                    int worldX = startX + x * blocksPerPixel;
                    int worldZ = startZ + z * blocksPerPixel;
                    recordCell(
                            world,
                            pos,
                            chunkCache,
                            Math.floorDiv(worldX, blocksPerPixel),
                            Math.floorDiv(worldZ, blocksPerPixel),
                            scale,
                            blocksPerPixel);
                }
            }
        }
    }

    public Map<Long, Integer> snapshotExploredPixels() {
        return new LinkedHashMap<>(exploredPixels);
    }

    public void loadExploredPixels(Map<Long, Integer> pixels) {
        exploredPixels.clear();
        if (pixels != null) {
            exploredPixels.putAll(pixels);
        }
        lastCenterX = Integer.MIN_VALUE;
        lastCenterZ = Integer.MIN_VALUE;
        lastScale = Integer.MIN_VALUE;
        dirty = false;
    }

    public boolean consumeDirty() {
        boolean changed = dirty;
        dirty = false;
        return changed;
    }

    private void composeView(int centerX, int centerZ, int scale) {
        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        int edgeDitherPixels = MathHelper.clamp(config.edgeDitherPixels, 0, 8);
        int blocksPerPixel = 1 << scale;
        int centerCellX = Math.floorDiv(centerX, blocksPerPixel);
        int centerCellZ = Math.floorDiv(centerZ, blocksPerPixel);

        for (int x = 0; x < MAP_SIZE; x++) {
            for (int z = 0; z < MAP_SIZE; z++) {
                int cellX = centerCellX + x - MAP_SIZE / 2;
                int cellZ = centerCellZ + z - MAP_SIZE / 2;
                int i = index(x, z);
                Integer color = exploredPixels.get(packKey(cellX, cellZ, scale));
                viewExplored[i] = color != null;
                terrainColors[i] = color == null ? COLOR_EMPTY : color;
            }
        }

        if (edgeDitherPixels > 0) {
            applyVanillaEdgeDither(edgeDitherPixels);
        }
    }

    private void applyVanillaEdgeDither(int edgeDitherPixels) {
        for (int z = 0; z < MAP_SIZE; z++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int i = index(x, z);
                if (viewExplored[i] && shouldRevealParchmentAtEdge(x, z, edgeDitherPixels)) {
                    terrainColors[i] = COLOR_EMPTY;
                }
            }
        }
    }

    private Integer samplePixel(World world, BlockPos.Mutable pos, Map<Long, WorldChunk> chunkCache, int startX, int startZ, int blocksPerPixel) {
        Map<MapColor, Integer> colors = new HashMap<>();
        double averageHeight = 0.0D;
        int waterDepth = 0;
        int sampleCount = 0;

        int samplesPerAxis = getSamplesPerAxis(blocksPerPixel);
        for (int sampleX = 0; sampleX < samplesPerAxis; sampleX++) {
            for (int sampleZ = 0; sampleZ < samplesPerAxis; sampleZ++) {
                int worldX = startX + (sampleX * blocksPerPixel + blocksPerPixel / 2) / samplesPerAxis;
                int worldZ = startZ + (sampleZ * blocksPerPixel + blocksPerPixel / 2) / samplesPerAxis;
                WorldChunk chunk = getLoadedChunk(world, worldX, worldZ, chunkCache);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }

                Sample sample = sampleSurface(world, chunk, pos, worldX, worldZ);
                MapColor mapColor = sample.state().getMapColor(world, pos);
                if (mapColor == null || mapColor == MapColor.CLEAR) {
                    continue;
                }

                sampleCount++;
                colors.merge(mapColor, 1, Integer::sum);
                averageHeight += sample.height();
                if (sample.water()) {
                    waterDepth += sample.waterDepth();
                }
            }
        }

        if (sampleCount == 0 || colors.isEmpty()) {
            return null;
        }

        MapColor dominant = colors.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(MapColor.CLEAR);
        MapColor.Brightness brightness = getSampleBrightness(dominant, averageHeight / sampleCount, waterDepth / Math.max(1, sampleCount), startX, startZ, blocksPerPixel);
        return dominant.getRenderColor(brightness);
    }

    private Sample sampleSurface(World world, WorldChunk chunk, BlockPos.Mutable pos, int worldX, int worldZ) {
        int surfaceY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, worldX, worldZ);
        if (surfaceY <= world.getBottomY()) {
            pos.set(worldX, world.getBottomY(), worldZ);
            return new Sample(Blocks.BEDROCK.getDefaultState(), surfaceY, false, 0);
        }

        int y = surfaceY + 1;
        BlockState state;
        do {
            pos.set(worldX, --y, worldZ);
            state = chunk.getBlockState(pos);
        } while (state.getMapColor(world, pos) == MapColor.CLEAR && y > world.getBottomY());

        FluidState fluidState = state.getFluidState();
        boolean isWater = fluidState.isOf(Fluids.WATER);
        if (!fluidState.isEmpty() && !state.isSideSolidFullSquare(world, pos, net.minecraft.util.math.Direction.UP)) {
            state = fluidState.getBlockState();
        }

        int depth = 0;
        if (isWater) {
            for (int depthY = y - 1; depthY >= world.getBottomY() && depth < 32; depthY--) {
                pos.set(worldX, depthY, worldZ);
                if (!chunk.getBlockState(pos).getFluidState().isOf(Fluids.WATER)) {
                    break;
                }
                depth++;
            }
            pos.set(worldX, y, worldZ);
        }

        return new Sample(state, y, isWater, depth);
    }

    private MapColor.Brightness getSampleBrightness(MapColor color, double height, int waterDepth, int worldX, int worldZ, int blocksPerPixel) {
        if (color == MapColor.WATER_BLUE) {
            double depth = (double) waterDepth * 0.1D + (double) (worldX + worldZ & 1) * 0.2D;
            if (depth < 0.5D) {
                return MapColor.Brightness.HIGH;
            }
            if (depth > 0.9D) {
                return MapColor.Brightness.LOW;
            }
            return MapColor.Brightness.NORMAL;
        }

        double shade = ((height % 8.0D) - 4.0D) / (double) (blocksPerPixel + 4) + ((double) (worldX + worldZ & 1) - 0.5D) * 0.2D;
        if (shade > 0.45D) {
            return MapColor.Brightness.HIGH;
        }
        if (shade < -0.45D) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    private WorldChunk getLoadedChunk(World world, int worldX, int worldZ, Map<Long, WorldChunk> chunkCache) {
        int chunkX = ChunkSectionPos.getSectionCoord(worldX);
        int chunkZ = ChunkSectionPos.getSectionCoord(worldZ);
        long key = pack(chunkX, chunkZ);

        if (chunkCache.containsKey(key)) {
            return chunkCache.get(key);
        }

        WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
        chunkCache.put(key, chunk);
        return chunk;
    }

    private void trimCache(int maxSize) {
        Iterator<Long> iterator = exploredPixels.keySet().iterator();
        while (exploredPixels.size() > maxSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static int index(int x, int z) {
        return z * MAP_SIZE + x;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private boolean shouldRevealParchmentAtEdge(int x, int z, int maxDistance) {
        int distance = distanceToUnexploredViewPixel(x, z, maxDistance);
        if (distance > maxDistance) {
            return false;
        }

        if (distance <= 1) {
            return ((x + z) & 1) == 0;
        }

        int threshold = switch (distance) {
            case 2 -> 4;
            case 3 -> 2;
            default -> 1;
        };
        return bayer4(x, z) < threshold;
    }

    private int distanceToUnexploredViewPixel(int x, int z, int maxDistance) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                if (!isExploredViewPixel(x + dx, z - distance) || !isExploredViewPixel(x + dx, z + distance)) {
                    return distance;
                }
            }

            for (int dz = -distance + 1; dz < distance; dz++) {
                if (!isExploredViewPixel(x - distance, z + dz) || !isExploredViewPixel(x + distance, z + dz)) {
                    return distance;
                }
            }
        }

        return maxDistance + 1;
    }

    private boolean isExploredViewPixel(int x, int z) {
        return x < 0 || z < 0 || x >= MAP_SIZE || z >= MAP_SIZE || viewExplored[index(x, z)];
    }

    private static int bayer4(int x, int z) {
        int localX = x & 3;
        int localZ = z & 3;
        return switch (localZ) {
            case 0 -> switch (localX) {
                case 0 -> 0;
                case 1 -> 8;
                case 2 -> 2;
                default -> 10;
            };
            case 1 -> switch (localX) {
                case 0 -> 12;
                case 1 -> 4;
                case 2 -> 14;
                default -> 6;
            };
            case 2 -> switch (localX) {
                case 0 -> 3;
                case 1 -> 11;
                case 2 -> 1;
                default -> 9;
            };
            default -> switch (localX) {
                case 0 -> 15;
                case 1 -> 7;
                case 2 -> 13;
                default -> 5;
            };
        };
    }

    private static int getSamplesPerAxis(int blocksPerPixel) {
        if (blocksPerPixel >= 8) {
            return 3;
        }

        if (blocksPerPixel >= 4) {
            return 4;
        }

        return blocksPerPixel;
    }

    private static int getRevealRadius(int configuredRadius, int scale) {
        if (scale >= 3) {
            return MathHelper.clamp(configuredRadius / 2, 24, 48);
        }

        return MathHelper.clamp(configuredRadius, 24, 72);
    }

    private static long packKey(int x, int z, int scale) {
        return ((long) scale & 7L) << 60
                | ((long) x & 0x3FFFFFFFL) << 30
                | ((long) z & 0x3FFFFFFFL);
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        image = null;
    }

    private record Sample(BlockState state, double height, boolean water, int waterDepth) {
    }

}
