package com.example.immersivemap.client;

import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.LayerId;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Reads loaded chunks into {@link ChunkSurface}s with the same column rules as {@code FilledMapItem#updateColors}
 * (heightmap, transparent blocks skipped, water depth, visible fluids). Runs on the client thread under a time
 * budget: new chunks first, then a slow rescan ring around the player so block edits show up.
 * Caves and dimensions with a ceiling are mapped by {@link CaveMapper} from what the player sees.
 */
public final class MapScanner {
    private static final long RESCAN_NEAR_MS = 2_000L;
    private static final long RESCAN_FAR_MS = 20_000L;

    private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private final Long2LongOpenHashMap lastScan = new Long2LongOpenHashMap();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final BlockPos.Mutable below = new BlockPos.Mutable();
    private final CaveMapper caves = new CaveMapper();
    private ClientWorld world;
    private int ringIndex;

    public void reset() {
        pending.clear();
        lastScan.clear();
        world = null;
        caves.reset();
    }

    public void onChunkLoad(int chunkX, int chunkZ) {
        pending.add(ChunkPos.toLong(chunkX, chunkZ));
    }

    /** The cave layer the player is exploring right now, or {@code null} on the surface. */
    public LayerId activeCaveLayer() {
        return caves.activeLayer();
    }

    /**
     * The vanilla map of a dimension with a ceiling is only a dirt/stone noise; with cave maps on, such dimensions
     * are mapped level by level instead and have no surface layer.
     */
    public static boolean usesLevels(ClientWorld world) {
        return world.getDimension().hasCeiling() && ClientConfig.get().smartCaveLayers;
    }

    public void tick(MinecraftClient client, MapStore store) {
        ClientWorld currentWorld = client.world;
        ClientPlayerEntity player = client.player;
        if (currentWorld == null || player == null || store == null) {
            return;
        }
        if (currentWorld != world) {
            reset();
            world = currentWorld;
        }

        Identifier dimension = world.getRegistryKey().getValue();
        LayerId surface = LayerId.surface(dimension);
        long budget = (long) (ClientConfig.get().scanBudgetMs * 1_000_000L);
        long deadline = System.nanoTime() + budget;
        long now = System.currentTimeMillis();

        if (usesLevels(world)) {
            pending.clear();
            caves.tick(client, store, deadline);
            return;
        }

        while (!pending.isEmpty() && System.nanoTime() < deadline) {
            long chunk = pending.removeFirstLong();
            scanSurface(store, surface, ChunkPos.getPackedX(chunk), ChunkPos.getPackedZ(chunk), now);
        }

        // Rescan around the player: a spiral of render distance, nearer chunks more often.
        int radius = Math.max(2, client.options.getClampedViewDistance());
        int side = radius * 2 + 1;
        int total = side * side;
        ChunkPos center = player.getChunkPos();
        int visited = 0;
        while (System.nanoTime() < deadline && visited++ < total) {
            ringIndex = (ringIndex + 1) % total;
            int dx = ringIndex % side - radius;
            int dz = ringIndex / side - radius;
            int chunkX = center.x + dx;
            int chunkZ = center.z + dz;
            long key = ChunkPos.toLong(chunkX, chunkZ);
            long interval = Math.max(Math.abs(dx), Math.abs(dz)) <= 2 ? RESCAN_NEAR_MS : RESCAN_FAR_MS;
            if (now - lastScan.getOrDefault(key, 0L) >= interval) {
                scanSurface(store, surface, chunkX, chunkZ, now);
            }
        }

        caves.tick(client, store, System.nanoTime() + budget);
    }

    private void scanSurface(MapStore store, LayerId layer, int chunkX, int chunkZ, long now) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        lastScan.put(ChunkPos.toLong(chunkX, chunkZ), now);
        ChunkSurface surface = store.getOrCreate(layer, chunkX, chunkZ);
        boolean changed = false;
        boolean ceiling = world.getDimension().hasCeiling();
        int bottom = world.getBottomY();
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int height;
                int depth = 0;
                BlockState state;
                if (ceiling) {
                    // Vanilla draws a dirt/stone noise for dimensions with a ceiling.
                    int noise = worldX + worldZ * 231871;
                    noise = noise * noise * 31287121 + noise * 11;
                    state = (noise >> 20 & 1) == 0 ? Blocks.DIRT.getDefaultState() : Blocks.STONE.getDefaultState();
                    height = 100;
                    pos.set(worldX, height, worldZ);
                } else {
                    height = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z) + 1;
                    if (height <= bottom + 1) {
                        // An empty column (the End void): vanilla paints bedrock here, a light wash reads better.
                        changed |= surface.set(ChunkSurface.index(x, z), ChunkSurface.VOID_COLOR, bottom, 0);
                        continue;
                    } else {
                        do {
                            pos.set(worldX, --height, worldZ);
                            state = chunk.getBlockState(pos);
                        } while (state.getMapColor(world, pos) == MapColor.CLEAR && height > bottom);
                        if (height > bottom && !state.getFluidState().isEmpty()) {
                            depth = waterDepth(chunk, worldX, height, worldZ, bottom);
                            state = visibleFluid(state, pos);
                        }
                    }
                }
                int color = state.getMapColor(world, pos).id;
                if (color != 0) {
                    changed |= surface.set(ChunkSurface.index(x, z), color, height, depth);
                }
            }
        }
        if (changed) {
            store.markChanged(layer, chunkX, chunkZ, true);
        }
    }

    private int waterDepth(WorldChunk chunk, int x, int y, int z, int bottom) {
        int depth = 0;
        int cursor = y - 1;
        BlockState state;
        do {
            below.set(x, cursor--, z);
            state = chunk.getBlockState(below);
            depth++;
        } while (cursor > bottom && !state.getFluidState().isEmpty());
        return depth;
    }

    private BlockState visibleFluid(BlockState state, BlockPos at) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isSideSolidFullSquare(world, at, Direction.UP) ? fluid.getBlockState() : state;
    }
}
