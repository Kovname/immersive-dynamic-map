package com.example.immersivemap.server;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.LayerId;
import com.example.immersivemap.map.RegionFile;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Server-side union of every synced player's explored map, kept compressed in memory. */
final class SharedMapStore {
    static final class Entry {
        final long timestamp;
        final byte[] data;

        Entry(long timestamp, byte[] data) {
            this.timestamp = timestamp;
            this.data = data;
        }
    }

    private record RegionKey(LayerId layer, int x, int z) {
    }

    private final Path root;
    private final Map<LayerId, Long2ObjectOpenHashMap<Entry>> layers = new HashMap<>();
    private final Set<RegionKey> dirty = new HashSet<>();
    private final List<Future<?>> pendingWrites = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ImmersiveMap-SharedMap-IO");
        thread.setDaemon(true);
        return thread;
    });
    private long latestTimestamp;

    SharedMapStore(Path root) {
        this.root = root;
    }

    void load() {
        if (!Files.isDirectory(root)) {
            return;
        }
        int chunks = 0;
        try (DirectoryStream<Path> dimensions = Files.newDirectoryStream(root)) {
            for (Path dimension : dimensions) {
                if (!Files.isDirectory(dimension)) {
                    continue;
                }
                try (DirectoryStream<Path> layerDirs = Files.newDirectoryStream(dimension)) {
                    for (Path layerDir : layerDirs) {
                        LayerId layer = LayerId.fromFolders(dimension.getFileName().toString(), layerDir.getFileName().toString());
                        if (layer == null || !Files.isDirectory(layerDir)) {
                            continue;
                        }
                        chunks += loadLayer(layer, layerDir);
                    }
                }
            }
        } catch (IOException exception) {
            ImmersiveMapMod.LOGGER.error("Could not load the shared map", exception);
        }
        ImmersiveMapMod.LOGGER.info("Loaded {} shared map chunks", chunks);
    }

    private int loadLayer(LayerId layer, Path layerDir) throws IOException {
        int loaded = 0;
        Long2ObjectOpenHashMap<Entry> map = layers.computeIfAbsent(layer, key -> new Long2ObjectOpenHashMap<>());
        try (DirectoryStream<Path> files = Files.newDirectoryStream(layerDir, "r.*.dat")) {
            for (Path file : files) {
                String[] parts = file.getFileName().toString().split("\\.");
                if (parts.length != 4) {
                    continue;
                }
                int regionX;
                int regionZ;
                try {
                    regionX = Integer.parseInt(parts[1]);
                    regionZ = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                try {
                    for (RegionFile.Entry entry : RegionFile.read(file)) {
                        int chunkX = (regionX << RegionFile.SHIFT) + (entry.index() & (RegionFile.SIZE - 1));
                        int chunkZ = (regionZ << RegionFile.SHIFT) + (entry.index() >> RegionFile.SHIFT);
                        map.put(ChunkPos.toLong(chunkX, chunkZ), new Entry(entry.timestamp(), entry.data()));
                        latestTimestamp = Math.max(latestTimestamp, entry.timestamp());
                        loaded++;
                    }
                } catch (IOException exception) {
                    ImmersiveMapMod.LOGGER.warn("Skipping unreadable shared map region {}", file, exception);
                }
            }
        }
        return loaded;
    }

    Entry get(LayerId layer, long chunk) {
        Long2ObjectOpenHashMap<Entry> map = layers.get(layer);
        return map == null ? null : map.get(chunk);
    }

    long latestTimestamp() {
        return latestTimestamp;
    }

    /** Returns the new entry when the merge added anything, otherwise {@code null}. */
    Entry merge(LayerId layer, int chunkX, int chunkZ, ChunkSurface incoming, long now) {
        Long2ObjectOpenHashMap<Entry> map = layers.computeIfAbsent(layer, key -> new Long2ObjectOpenHashMap<>());
        long key = ChunkPos.toLong(chunkX, chunkZ);
        Entry existing = map.get(key);
        ChunkSurface merged;
        if (existing != null) {
            try {
                merged = ChunkSurface.decompress(existing.data, existing.timestamp);
            } catch (IOException exception) {
                merged = new ChunkSurface();
            }
            if (!merged.mergeFrom(incoming)) {
                return null;
            }
        } else {
            if (incoming.isEmpty()) {
                return null;
            }
            merged = incoming;
        }

        // Strictly increasing, so a client watermark never skips entries stored in the same millisecond.
        long timestamp = Math.max(now, latestTimestamp + 1L);
        Entry entry = new Entry(timestamp, merged.compress());
        map.put(key, entry);
        latestTimestamp = timestamp;
        dirty.add(new RegionKey(layer, chunkX >> RegionFile.SHIFT, chunkZ >> RegionFile.SHIFT));
        return entry;
    }

    void collectSince(long since, List<SyncKey> out) {
        for (Map.Entry<LayerId, Long2ObjectOpenHashMap<Entry>> layer : layers.entrySet()) {
            for (Long2ObjectMap.Entry<Entry> entry : layer.getValue().long2ObjectEntrySet()) {
                if (entry.getValue().timestamp > since) {
                    out.add(new SyncKey(layer.getKey(), entry.getLongKey()));
                }
            }
        }
    }

    void saveDirty() {
        pendingWrites.removeIf(Future::isDone);
        for (RegionKey region : dirty) {
            Long2ObjectOpenHashMap<Entry> map = layers.get(region.layer());
            List<RegionFile.Entry> entries = new ArrayList<>();
            for (int index = 0; index < RegionFile.CHUNKS; index++) {
                int chunkX = (region.x() << RegionFile.SHIFT) + (index & (RegionFile.SIZE - 1));
                int chunkZ = (region.z() << RegionFile.SHIFT) + (index >> RegionFile.SHIFT);
                Entry entry = map.get(ChunkPos.toLong(chunkX, chunkZ));
                if (entry != null) {
                    entries.add(new RegionFile.Entry(index, entry.timestamp, entry.data));
                }
            }
            Path file = root.resolve(LayerId.dimensionFolder(region.layer().dimension()))
                    .resolve(region.layer().folderName())
                    .resolve(RegionFile.fileName(region.x(), region.z()));
            pendingWrites.add(io.submit(() -> {
                try {
                    RegionFile.write(file, entries);
                } catch (IOException exception) {
                    ImmersiveMapMod.LOGGER.error("Could not save shared map region {}", file, exception);
                }
            }));
        }
        dirty.clear();
    }

    void close() {
        saveDirty();
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                ImmersiveMapMod.LOGGER.warn("Timed out while saving the shared map");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
