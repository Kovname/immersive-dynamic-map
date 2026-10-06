package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.LayerId;
import com.example.immersivemap.map.RegionFile;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Explored map chunks of one world (save or server), owned by the client thread. Region files are read, inflated,
 * deflated and written on a background thread so the render thread never touches the disk.
 */
public final class MapStore {
    private static final int SAVE_INTERVAL_TICKS = 20 * 45;

    private record RegionKey(LayerId layer, int x, int z) {
    }

    private record LoadedRegion(RegionKey key, List<ChunkSurface> chunks, int[] xs, int[] zs) {
    }

    private final Path root;
    private final ExecutorService io;
    private final Map<LayerId, Long2ObjectOpenHashMap<ChunkSurface>> layers = new HashMap<>();
    private final Set<RegionKey> requestedRegions = new HashSet<>();
    private final Set<RegionKey> dirtyRegions = new HashSet<>();
    private final ConcurrentLinkedQueue<LoadedRegion> loaded = new ConcurrentLinkedQueue<>();
    /** Chunks changed by this client that the sync server has not seen yet. */
    private final Map<LayerId, LongLinkedOpenHashSet> uploadQueue = new HashMap<>();
    private int revision;
    private int ticks;

    public MapStore(Path root) {
        this.root = root;
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Immersive Map IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    public Path root() {
        return root;
    }

    /** Bumped whenever any chunk changes; the texture rebuilds lazily when it differs. */
    public int revision() {
        return revision;
    }

    public ChunkSurface get(LayerId layer, int chunkX, int chunkZ) {
        requestRegion(layer, chunkX >> RegionFile.SHIFT, chunkZ >> RegionFile.SHIFT);
        Long2ObjectOpenHashMap<ChunkSurface> map = layers.get(layer);
        return map == null ? null : map.get(ChunkPos.toLong(chunkX, chunkZ));
    }

    public ChunkSurface getOrCreate(LayerId layer, int chunkX, int chunkZ) {
        requestRegion(layer, chunkX >> RegionFile.SHIFT, chunkZ >> RegionFile.SHIFT);
        return layers.computeIfAbsent(layer, key -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(ChunkPos.toLong(chunkX, chunkZ), key -> new ChunkSurface());
    }

    /** Call after the scanner modified a chunk returned by {@link #getOrCreate}. */
    public void markChanged(LayerId layer, int chunkX, int chunkZ, boolean upload) {
        ChunkSurface surface = getOrCreate(layer, chunkX, chunkZ);
        surface.timestamp = System.currentTimeMillis();
        dirtyRegions.add(new RegionKey(layer, chunkX >> RegionFile.SHIFT, chunkZ >> RegionFile.SHIFT));
        if (upload) {
            uploadQueue.computeIfAbsent(layer, key -> new LongLinkedOpenHashSet()).add(ChunkPos.toLong(chunkX, chunkZ));
        }
        revision++;
    }

    /** Merges a chunk received from the sync server; never queued for upload again. */
    public void mergeRemote(LayerId layer, int chunkX, int chunkZ, ChunkSurface incoming) {
        ChunkSurface surface = getOrCreate(layer, chunkX, chunkZ);
        if (surface.mergeFrom(incoming)) {
            surface.timestamp = Math.max(surface.timestamp, incoming.timestamp);
            dirtyRegions.add(new RegionKey(layer, chunkX >> RegionFile.SHIFT, chunkZ >> RegionFile.SHIFT));
            revision++;
        }
    }

    public Map<LayerId, LongLinkedOpenHashSet> uploadQueue() {
        return uploadQueue;
    }

    /** Every chunk of this world, used to seed the first upload to a sync server. */
    public void queueEverythingForUpload() {
        for (Map.Entry<LayerId, Long2ObjectOpenHashMap<ChunkSurface>> layer : layers.entrySet()) {
            LongLinkedOpenHashSet queue = uploadQueue.computeIfAbsent(layer.getKey(), key -> new LongLinkedOpenHashSet());
            queue.addAll(layer.getValue().keySet());
        }
    }

    public void tick() {
        LoadedRegion region;
        while ((region = loaded.poll()) != null) {
            Long2ObjectOpenHashMap<ChunkSurface> map = layers.computeIfAbsent(region.key().layer(), key -> new Long2ObjectOpenHashMap<>());
            for (int i = 0; i < region.chunks().size(); i++) {
                long pos = ChunkPos.toLong(region.xs()[i], region.zs()[i]);
                ChunkSurface fromDisk = region.chunks().get(i);
                ChunkSurface current = map.get(pos);
                if (current == null) {
                    map.put(pos, fromDisk);
                } else {
                    // Freshly scanned columns win over the saved copy.
                    fromDisk.mergeFrom(current);
                    map.put(pos, fromDisk);
                }
            }
            revision++;
        }
        if (++ticks % SAVE_INTERVAL_TICKS == 0) {
            save();
        }
    }

    private void requestRegion(LayerId layer, int regionX, int regionZ) {
        RegionKey key = new RegionKey(layer, regionX, regionZ);
        if (!requestedRegions.add(key)) {
            return;
        }
        Path file = regionFile(key);
        io.execute(() -> {
            try {
                List<RegionFile.Entry> entries = RegionFile.read(file);
                if (entries.isEmpty()) {
                    return;
                }
                List<ChunkSurface> chunks = new ArrayList<>(entries.size());
                int[] xs = new int[entries.size()];
                int[] zs = new int[entries.size()];
                for (RegionFile.Entry entry : entries) {
                    try {
                        ChunkSurface surface = ChunkSurface.decompress(entry.data(), entry.timestamp());
                        xs[chunks.size()] = (regionX << RegionFile.SHIFT) + (entry.index() & (RegionFile.SIZE - 1));
                        zs[chunks.size()] = (regionZ << RegionFile.SHIFT) + (entry.index() >> RegionFile.SHIFT);
                        chunks.add(surface);
                    } catch (IOException ignored) {
                        // One corrupt chunk must not drop the rest of the region.
                    }
                }
                loaded.add(new LoadedRegion(key, chunks, xs, zs));
            } catch (IOException exception) {
                ImmersiveMapMod.LOGGER.warn("Could not read map region {}", file, exception);
            }
        });
    }

    private Path regionFile(RegionKey key) {
        return root.resolve(LayerId.dimensionFolder(key.layer().dimension()))
                .resolve(key.layer().folderName())
                .resolve(RegionFile.fileName(key.x(), key.z()));
    }

    public void save() {
        for (RegionKey key : dirtyRegions) {
            Long2ObjectOpenHashMap<ChunkSurface> map = layers.get(key.layer());
            if (map == null) {
                continue;
            }
            // Snapshot on the client thread, compress and write in the background.
            List<ChunkSurface> copies = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int index = 0; index < RegionFile.CHUNKS; index++) {
                int chunkX = (key.x() << RegionFile.SHIFT) + (index & (RegionFile.SIZE - 1));
                int chunkZ = (key.z() << RegionFile.SHIFT) + (index >> RegionFile.SHIFT);
                ChunkSurface surface = map.get(ChunkPos.toLong(chunkX, chunkZ));
                if (surface != null && !surface.isEmpty()) {
                    copies.add(surface.copy());
                    indices.add(index);
                }
            }
            Path file = regionFile(key);
            io.execute(() -> {
                List<RegionFile.Entry> entries = new ArrayList<>(copies.size());
                for (int i = 0; i < copies.size(); i++) {
                    entries.add(new RegionFile.Entry(indices.get(i), copies.get(i).timestamp, copies.get(i).compress()));
                }
                try {
                    RegionFile.write(file, entries);
                } catch (IOException exception) {
                    ImmersiveMapMod.LOGGER.warn("Could not save map region {}", file, exception);
                }
            });
        }
        dirtyRegions.clear();
    }

    public void close() {
        save();
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
