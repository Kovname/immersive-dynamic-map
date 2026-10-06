package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.HoldState;
import com.example.immersivemap.map.LayerId;
import com.example.immersivemap.network.ClientHelloC2S;
import com.example.immersivemap.network.HoldStateC2S;
import com.example.immersivemap.network.MapChunksPayload;
import com.example.immersivemap.network.PlayerHoldStateS2C;
import com.example.immersivemap.network.PlayerPositionsS2C;
import com.example.immersivemap.network.ServerHelloS2C;
import com.example.immersivemap.network.SyncRequestC2S;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to a server running the mod: handshake, held map state, far player positions and experimental map sync. */
public final class ClientNetworking {
    private static final int UPLOAD_INTERVAL_TICKS = 10;
    private static final int UPLOAD_BATCH = 96;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Immersive Map Sync");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static ServerHelloS2C server;
    private static boolean syncActive;
    private static long watermark;
    private static int ticks;

    private ClientNetworking() {
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            server = null;
            syncActive = false;
            HeldMapPoses.clear();
            ImmersiveMapClientState.onJoin(client);
            if (ClientPlayNetworking.canSend(ClientHelloC2S.ID)) {
                ClientPlayNetworking.send(new ClientHelloC2S(ImmersiveMapMod.PROTOCOL_VERSION));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            saveWatermark();
            server = null;
            syncActive = false;
            ImmersiveMapClientState.onDisconnect();
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerHelloS2C.ID, (payload, context) -> {
            if (payload.protocol() != ImmersiveMapMod.PROTOCOL_VERSION) {
                return;
            }
            server = payload;
            sendHoldState(MapController.currentHoldState());
            updateSync();
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerHoldStateS2C.ID,
                (payload, context) -> HeldMapPoses.set(payload.entityId(), payload.state()));
        ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsS2C.ID,
                (payload, context) -> ImmersiveMapClientState.players().accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(MapChunksPayload.ID, (payload, context) -> receiveChunks(context.client(), payload));
    }

    public static boolean isServerModded() {
        return server != null;
    }

    public static void sendHoldState(HoldState state) {
        if (server != null && ClientPlayNetworking.canSend(HoldStateC2S.ID)) {
            ClientPlayNetworking.send(new HoldStateC2S(state));
        }
    }

    /** Starts or stops sync after a handshake or when the option changes. */
    public static void updateSync() {
        MapStore store = ImmersiveMapClientState.store();
        boolean wanted = server != null && server.mapSync() && ClientConfig.get().mapSync && store != null;
        if (wanted == syncActive || server == null || !ClientPlayNetworking.canSend(SyncRequestC2S.ID)) {
            return;
        }
        syncActive = wanted;
        if (wanted) {
            watermark = loadWatermark(store.root());
            if (watermark == 0L) {
                store.queueEverythingForUpload();
            }
            ClientPlayNetworking.send(new SyncRequestC2S(true, watermark));
        } else {
            ClientPlayNetworking.send(new SyncRequestC2S(false, 0L));
        }
    }

    public static void tick(MinecraftClient client) {
        MapStore store = ImmersiveMapClientState.store();
        if (!syncActive || store == null || ++ticks % UPLOAD_INTERVAL_TICKS != 0) {
            return;
        }
        List<LayerId> layers = new ArrayList<>();
        List<long[]> positions = new ArrayList<>();
        List<ChunkSurface> copies = new ArrayList<>();
        for (Map.Entry<LayerId, LongLinkedOpenHashSet> entry : store.uploadQueue().entrySet()) {
            LongLinkedOpenHashSet queue = entry.getValue();
            while (!queue.isEmpty() && copies.size() < UPLOAD_BATCH) {
                long pos = queue.removeFirstLong();
                int x = ChunkPos.getPackedX(pos);
                int z = ChunkPos.getPackedZ(pos);
                ChunkSurface surface = store.get(entry.getKey(), x, z);
                if (surface != null && !surface.isEmpty()) {
                    layers.add(entry.getKey());
                    positions.add(new long[]{x, z});
                    copies.add(surface.copy());
                }
            }
            if (copies.size() >= UPLOAD_BATCH) {
                break;
            }
        }
        if (copies.isEmpty()) {
            return;
        }
        // Deflate off the client thread, send from it.
        WORKER.execute(() -> {
            List<MapChunksPayload.Entry> entries = new ArrayList<>(copies.size());
            for (int i = 0; i < copies.size(); i++) {
                byte[] data = copies.get(i).compress();
                if (data.length <= MapChunksPayload.MAX_DATA_BYTES) {
                    entries.add(new MapChunksPayload.Entry(layers.get(i), (int) positions.get(i)[0], (int) positions.get(i)[1],
                            copies.get(i).timestamp, data));
                }
            }
            client.execute(() -> {
                if (syncActive && client.getNetworkHandler() != null && ClientPlayNetworking.canSend(MapChunksPayload.ID)) {
                    ClientPlayNetworking.send(new MapChunksPayload(entries, 0L));
                }
            });
        });
    }

    private static void receiveChunks(MinecraftClient client, MapChunksPayload payload) {
        List<MapChunksPayload.Entry> entries = payload.entries();
        long batchWatermark = payload.watermark();
        WORKER.execute(() -> {
            List<ChunkSurface> surfaces = new ArrayList<>(entries.size());
            for (MapChunksPayload.Entry entry : entries) {
                try {
                    surfaces.add(ChunkSurface.decompress(entry.data(), entry.timestamp()));
                } catch (IOException exception) {
                    surfaces.add(null);
                }
            }
            client.execute(() -> {
                MapStore store = ImmersiveMapClientState.store();
                if (store == null) {
                    return;
                }
                for (int i = 0; i < entries.size(); i++) {
                    ChunkSurface surface = surfaces.get(i);
                    MapChunksPayload.Entry entry = entries.get(i);
                    if (surface != null && entry.layer().isValid()) {
                        store.mergeRemote(entry.layer(), entry.chunkX(), entry.chunkZ(), surface);
                    }
                }
                if (batchWatermark > watermark) {
                    watermark = batchWatermark;
                }
            });
        });
    }

    private static long loadWatermark(Path root) {
        try {
            Path file = root.resolve("sync_watermark.txt");
            return Files.isRegularFile(file) ? Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).trim()) : 0L;
        } catch (IOException | NumberFormatException exception) {
            return 0L;
        }
    }

    private static void saveWatermark() {
        MapStore store = ImmersiveMapClientState.store();
        if (store == null || watermark == 0L) {
            return;
        }
        try {
            Files.createDirectories(store.root());
            Files.writeString(store.root().resolve("sync_watermark.txt"), Long.toString(watermark), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ImmersiveMapMod.LOGGER.warn("Could not save map sync state", exception);
        }
    }
}
