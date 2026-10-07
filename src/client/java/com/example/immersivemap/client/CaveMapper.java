package com.example.immersivemap.client;

import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.LayerId;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Maps caves, and every level of dimensions with a ceiling such as the Nether, from what the player can actually
 * see. Underground the map is a slice at the player's height: for each column around the player the first open
 * space at head or feet level is followed down to its floor, however deep. A floor is only recorded when a line of
 * sight from the eyes reaches the air above it, and rock at eye level is only recorded as a wall when its face is in
 * sight, so caves behind walls stay unexplored.
 *
 * <p>Data goes to 16-block bands ({@link LayerId#cave}); the band follows the player with a little hysteresis. Work is
 * spread over ticks under a time budget, nearest columns first, and restarts from the new position as the player
 * moves, so the revealed radius grows while standing still.
 */
public final class CaveMapper {
    public static final int MIN_DISTANCE = 16;
    public static final int MAX_DISTANCE = 96;
    private static final int NO_BAND = Integer.MIN_VALUE;
    private static final int SWITCH_TICKS = 10;
    private static final int BAND_HYSTERESIS = 2;
    /** Open space between feet - 3 and feet + 2 counts as the player's level. */
    private static final int WINDOW_UP = 2;
    private static final int WINDOW_DOWN = 3;
    /** Deeper drops are left unexplored. */
    private static final int MAX_DROP = 128;
    private static final int NONE = Integer.MIN_VALUE;
    private static final int REFRESH_TICKS = 20;
    private static final double RESTART_DISTANCE_SQ = 6.0 * 6.0;
    private static final int CHUNK_RADIUS = MAX_DISTANCE / 16 + 2;
    private static final int CHUNK_SPAN = CHUNK_RADIUS * 2 + 1;
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.getDefaultState();

    /** Column offsets packed as {@code (dx + 128) << 8 | (dz + 128)}, nearest first. */
    private static final int[] OFFSETS;
    /** {@code COUNT_WITHIN[r]} is the number of offsets at most {@code r} blocks away. */
    private static final int[] COUNT_WITHIN = new int[MAX_DISTANCE + 1];

    static {
        List<int[]> offsets = new ArrayList<>();
        int maxSq = MAX_DISTANCE * MAX_DISTANCE;
        for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx++) {
            for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz++) {
                int sq = dx * dx + dz * dz;
                if (sq <= maxSq) {
                    offsets.add(new int[]{sq, dx, dz});
                }
            }
        }
        offsets.sort((a, b) -> Integer.compare(a[0], b[0]));
        OFFSETS = new int[offsets.size()];
        int radius = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            int[] offset = offsets.get(i);
            while (offset[0] > radius * radius) {
                COUNT_WITHIN[radius++] = i;
            }
            OFFSETS[i] = (offset[1] + 128) << 8 | (offset[2] + 128);
        }
        while (radius <= MAX_DISTANCE) {
            COUNT_WITHIN[radius++] = OFFSETS.length;
        }
    }

    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final WorldChunk[] chunks = new WorldChunk[CHUNK_SPAN * CHUNK_SPAN];
    private final boolean[] chunkLooked = new boolean[CHUNK_SPAN * CHUNK_SPAN];
    private final ChunkSurface[] surfaces = new ChunkSurface[CHUNK_SPAN * CHUNK_SPAN];
    private final int[] runTops = new int[2];
    private final int[] runFloors = new int[2];

    private ClientWorld world;
    private MapStore store;
    private int bottomY;
    private int topY;
    private int bottomSection;

    private int undergroundTicks;
    private boolean underground;
    private int band = NO_BAND;
    private LayerId layer;

    private boolean passActive;
    private LayerId passLayer;
    private int passIndex;
    private int passEnd;
    private double passX;
    private double passZ;
    private double eyeX;
    private double eyeY;
    private double eyeZ;
    private int eyeBlockX;
    private int eyeBlockY;
    private int eyeBlockZ;
    private int feetY;
    private int originX;
    private int originZ;
    private int chunkBaseX;
    private int chunkBaseZ;
    private int ticksSincePass;
    private boolean hasLastPass;
    private double lastPassX;
    private double lastPassY;
    private double lastPassZ;

    public void reset() {
        world = null;
        store = null;
        underground = false;
        undergroundTicks = 0;
        band = NO_BAND;
        layer = null;
        passActive = false;
        passLayer = null;
        hasLastPass = false;
        dirty.clear();
        clearCaches();
    }

    /** The band being explored right now, or {@code null} above ground. */
    public LayerId activeLayer() {
        return layer;
    }

    public void tick(MinecraftClient client, MapStore mapStore, long deadline) {
        ClientWorld current = client.world;
        ClientPlayerEntity player = client.player;
        if (current == null || player == null || mapStore == null) {
            return;
        }
        if (current != world || mapStore != store) {
            reset();
            world = current;
            store = mapStore;
            bottomY = world.getBottomY();
            topY = world.getTopY();
            bottomSection = world.getBottomSectionCoord();
        }
        if (!ClientConfig.get().smartCaveLayers || player.isSpectator()) {
            underground = false;
            undergroundTicks = 0;
            band = NO_BAND;
            layer = null;
            passActive = false;
            return;
        }
        updateLevel(player);
        if (layer == null) {
            passActive = false;
            return;
        }

        ticksSincePass++;
        if (passActive) {
            double dx = player.getX() - passX;
            double dz = player.getZ() - passZ;
            if (!layer.equals(passLayer) || dx * dx + dz * dz > RESTART_DISTANCE_SQ || Math.abs(player.getBlockY() - feetY) >= 2) {
                startPass(player);
            }
        } else if (!hasLastPass || !layer.equals(passLayer) || ticksSincePass >= REFRESH_TICKS
                || player.squaredDistanceTo(lastPassX, lastPassY, lastPassZ) > 1.0) {
            startPass(player);
        }
        if (!passActive) {
            return;
        }

        clearCaches();
        while (passIndex < passEnd && System.nanoTime() < deadline) {
            int packed = OFFSETS[passIndex++];
            processColumn(originX + (packed >> 8) - 128, originZ + (packed & 0xFF) - 128);
        }
        if (passIndex >= passEnd) {
            passActive = false;
            ticksSincePass = 0;
            hasLastPass = true;
            lastPassX = player.getX();
            lastPassY = player.getY();
            lastPassZ = player.getZ();
        }
        LongIterator iterator = dirty.iterator();
        while (iterator.hasNext()) {
            long chunk = iterator.nextLong();
            store.markChanged(passLayer, ChunkPos.getPackedX(chunk), ChunkPos.getPackedZ(chunk), true);
        }
        dirty.clear();
        clearCaches();
    }

    /** Underground (with a short delay against flicker at cave mouths) and in dimensions with a ceiling. */
    private void updateLevel(ClientPlayerEntity player) {
        boolean ceiling = world.getDimension().hasCeiling();
        boolean now = ceiling;
        if (!ceiling) {
            BlockPos eye = BlockPos.ofFloored(player.getEyePos());
            boolean covered = eye.getY() < world.getTopY(Heightmap.Type.MOTION_BLOCKING, eye.getX(), eye.getZ()) - 1;
            boolean dark = !world.getDimension().hasSkyLight() || world.getLightLevel(LightType.SKY, eye) < 8;
            now = covered && dark;
        }
        undergroundTicks = now ? Math.min(undergroundTicks + 1, SWITCH_TICKS) : Math.max(undergroundTicks - 1, 0);
        if (ceiling || undergroundTicks >= SWITCH_TICKS) {
            underground = true;
        } else if (undergroundTicks == 0) {
            underground = false;
        }
        if (!underground) {
            band = NO_BAND;
            layer = null;
            return;
        }
        int feet = player.getBlockY();
        int bandBottom = band * LayerId.BAND_HEIGHT;
        if (band == NO_BAND || feet < bandBottom - BAND_HYSTERESIS || feet > bandBottom + LayerId.BAND_HEIGHT - 1 + BAND_HYSTERESIS) {
            band = MathHelper.clamp(LayerId.bandOf(feet), LayerId.MIN_BAND, LayerId.MAX_BAND);
        }
        Identifier dimension = world.getRegistryKey().getValue();
        if (layer == null || layer.band() != band || !layer.dimension().equals(dimension)) {
            layer = LayerId.cave(dimension, band);
        }
    }

    private void startPass(ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        eyeBlockX = MathHelper.floor(eyeX);
        eyeBlockY = MathHelper.floor(eyeY);
        eyeBlockZ = MathHelper.floor(eyeZ);
        feetY = player.getBlockY();
        originX = player.getBlockX();
        originZ = player.getBlockZ();
        passX = player.getX();
        passZ = player.getZ();
        chunkBaseX = (originX >> 4) - CHUNK_RADIUS;
        chunkBaseZ = (originZ >> 4) - CHUNK_RADIUS;
        passLayer = layer;
        passIndex = 0;
        passEnd = COUNT_WITHIN[MathHelper.clamp(ClientConfig.get().caveViewDistance, MIN_DISTANCE, MAX_DISTANCE)];
        passActive = true;
    }

    // ---------------------------------------------------------------- columns

    private void processColumn(int x, int z) {
        WorldChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) {
            return;
        }
        int top = Math.min(feetY + WINDOW_UP, topY - 1);
        int low = Math.max(feetY - WINDOW_DOWN, bottomY);
        if (top < low) {
            return;
        }
        int limit = Math.max(bottomY, feetY - MAX_DROP);

        // Up to two open runs starting at the player's level, each followed down to its floor.
        int runs = 0;
        int y = top;
        while (y >= low && runs < 2) {
            if (!isOpen(chunk, x, y, z)) {
                y--;
                continue;
            }
            int floor = findFloor(chunk, x, y, z, limit);
            runTops[runs] = y;
            runFloors[runs] = floor;
            runs++;
            if (floor == NONE) {
                break;
            }
            y = floor - 1;
        }
        if (runs == 0) {
            recordWallIfSeen(chunk, x, z, low, top);
            return;
        }

        // Prefer the run the player is standing in, then the one closest to the feet.
        int first = 0;
        if (runs == 2 && !containsFeet(0) && (containsFeet(1) || standDistance(1) < standDistance(0))) {
            first = 1;
        }
        for (int k = 0; k < runs; k++) {
            int run = (first + k) % runs;
            int floor = runFloors[run];
            if (floor == NONE) {
                continue;
            }
            int above = floor + 1;
            int eyeLevel = MathHelper.clamp(eyeBlockY, above, runTops[run]);
            if (canSee(x, above, z) || eyeLevel != above && canSee(x, eyeLevel, z)) {
                recordFloor(chunk, x, floor, z);
                return;
            }
        }
    }

    private boolean containsFeet(int run) {
        return runFloors[run] != NONE && runFloors[run] < feetY && runTops[run] >= feetY;
    }

    private int standDistance(int run) {
        return runFloors[run] == NONE ? Integer.MAX_VALUE : Math.abs(runFloors[run] + 1 - feetY);
    }

    /** The first non-clear block below an open cell, or {@link #NONE} when it is deeper than {@code limit}. */
    private int findFloor(WorldChunk chunk, int x, int openY, int z, int limit) {
        ChunkSection[] sections = chunk.getSectionArray();
        int y = openY;
        while (true) {
            int below = y - 1;
            if (below < limit) {
                return NONE;
            }
            ChunkSection section = sections[(below >> 4) - bottomSection];
            if (section.isEmpty()) {
                // A whole section of air: jump straight to its bottom.
                y = below & ~15;
                continue;
            }
            BlockState state = section.getBlockState(x & 15, below & 15, z & 15);
            pos.set(x, below, z);
            if (state.getMapColor(world, pos) != MapColor.CLEAR) {
                return below;
            }
            y = below;
        }
    }

    private void recordFloor(WorldChunk chunk, int x, int floor, int z) {
        BlockState state = stateIn(chunk, x, floor, z);
        pos.set(x, floor, z);
        int depth = 0;
        if (!state.getFluidState().isEmpty()) {
            depth = waterDepth(chunk, x, floor, z);
            state = visibleFluid(state, pos);
        }
        int color = state.getMapColor(world, pos).id;
        if (color == 0) {
            return;
        }
        ChunkSurface surface = surface(x >> 4, z >> 4);
        if (surface.set(ChunkSurface.index(x & 15, z & 15), color, floor, Math.min(depth, 127))) {
            dirty.add(ChunkPos.toLong(x >> 4, z >> 4));
        }
    }

    /**
     * Solid at the player's level: a wall, if its face can be seen at eye height, at the top of the slice (where a
     * rising floor leaves the slice and hides the eye-level cell) or at feet height.
     */
    private void recordWallIfSeen(WorldChunk chunk, int x, int z, int low, int top) {
        int eye = MathHelper.clamp(eyeBlockY, low, top);
        int feet = MathHelper.clamp(feetY, low, top);
        int target;
        if (canSee(x, eye, z)) {
            target = eye;
        } else if (top != eye && canSee(x, top, z)) {
            target = top;
        } else if (feet != eye && feet != top && canSee(x, feet, z)) {
            target = feet;
        } else {
            return;
        }
        BlockState state = stateIn(chunk, x, target, z);
        pos.set(x, target, z);
        int color = state.getMapColor(world, pos).id;
        if (color == 0) {
            color = MapColor.STONE_GRAY.id;
        }
        ChunkSurface surface = surface(x >> 4, z >> 4);
        int index = ChunkSurface.index(x & 15, z & 15);
        int existing = surface.colors[index] & 0x7F;
        if (existing != 0 && !ChunkSurface.isWall(existing) && existing != ChunkSurface.VOID_COLOR) {
            // A floor recorded at this level is gone (filled in); floors at other heights win over walls.
            int stand = surface.heights[index] + 1;
            if (stand < low || stand > top) {
                return;
            }
        }
        if (surface.set(index, color | ChunkSurface.WALL_FLAG, top, 0)) {
            dirty.add(ChunkPos.toLong(x >> 4, z >> 4));
        }
    }

    // ---------------------------------------------------------------- line of sight

    /**
     * Walks the voxels from the eyes to the point of the target cell nearest to them; any opaque full cube on the way
     * blocks the view.
     */
    private boolean canSee(int tx, int ty, int tz) {
        int x = eyeBlockX;
        int y = eyeBlockY;
        int z = eyeBlockZ;
        int steps = Math.abs(tx - x) + Math.abs(ty - y) + Math.abs(tz - z);
        if (steps == 0) {
            return true;
        }
        double dx = MathHelper.clamp(eyeX, tx + 0.05, tx + 0.95) - eyeX;
        double dy = MathHelper.clamp(eyeY, ty + 0.05, ty + 0.95) - eyeY;
        double dz = MathHelper.clamp(eyeZ, tz + 0.05, tz + 0.95) - eyeZ;
        int stepX = tx > x ? 1 : tx < x ? -1 : 0;
        int stepY = ty > y ? 1 : ty < y ? -1 : 0;
        int stepZ = tz > z ? 1 : tz < z ? -1 : 0;
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double maxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - eyeX : eyeX - x) * deltaX;
        double maxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - eyeY : eyeY - y) * deltaY;
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - eyeZ : eyeZ - z) * deltaZ;
        for (int i = 0; i < steps; i++) {
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
            if (x == tx && y == ty && z == tz) {
                return true;
            }
            if (isOpaque(x, y, z)) {
                return false;
            }
        }
        return false;
    }

    private boolean isOpaque(int x, int y, int z) {
        if (y >= topY) {
            return false;
        }
        if (y < bottomY) {
            return true;
        }
        WorldChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) {
            return true;
        }
        BlockState state = stateIn(chunk, x, y, z);
        pos.set(x, y, z);
        return state.isOpaqueFullCube(world, pos);
    }

    // ---------------------------------------------------------------- blocks

    private boolean isOpen(WorldChunk chunk, int x, int y, int z) {
        BlockState state = stateIn(chunk, x, y, z);
        pos.set(x, y, z);
        return state.getMapColor(world, pos) == MapColor.CLEAR;
    }

    private BlockState stateIn(WorldChunk chunk, int x, int y, int z) {
        if (y >= topY) {
            return AIR;
        }
        if (y < bottomY) {
            return BEDROCK;
        }
        ChunkSection section = chunk.getSectionArray()[(y >> 4) - bottomSection];
        return section.isEmpty() ? AIR : section.getBlockState(x & 15, y & 15, z & 15);
    }

    private int waterDepth(WorldChunk chunk, int x, int y, int z) {
        int depth = 0;
        int cursor = y - 1;
        BlockState state;
        do {
            state = stateIn(chunk, x, cursor--, z);
            depth++;
        } while (cursor > bottomY && depth < 127 && !state.getFluidState().isEmpty());
        return depth;
    }

    private BlockState visibleFluid(BlockState state, BlockPos at) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isSideSolidFullSquare(world, at, Direction.UP) ? fluid.getBlockState() : state;
    }

    private WorldChunk chunk(int chunkX, int chunkZ) {
        int ix = chunkX - chunkBaseX;
        int iz = chunkZ - chunkBaseZ;
        if (ix < 0 || iz < 0 || ix >= CHUNK_SPAN || iz >= CHUNK_SPAN) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
            return chunk == null || chunk.isEmpty() ? null : chunk;
        }
        int i = ix * CHUNK_SPAN + iz;
        if (!chunkLooked[i]) {
            chunkLooked[i] = true;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
            chunks[i] = chunk == null || chunk.isEmpty() ? null : chunk;
        }
        return chunks[i];
    }

    private ChunkSurface surface(int chunkX, int chunkZ) {
        int ix = chunkX - chunkBaseX;
        int iz = chunkZ - chunkBaseZ;
        if (ix < 0 || iz < 0 || ix >= CHUNK_SPAN || iz >= CHUNK_SPAN) {
            return store.getOrCreate(passLayer, chunkX, chunkZ);
        }
        int i = ix * CHUNK_SPAN + iz;
        if (surfaces[i] == null) {
            surfaces[i] = store.getOrCreate(passLayer, chunkX, chunkZ);
        }
        return surfaces[i];
    }

    private void clearCaches() {
        Arrays.fill(chunks, null);
        Arrays.fill(chunkLooked, false);
        Arrays.fill(surfaces, null);
    }
}
