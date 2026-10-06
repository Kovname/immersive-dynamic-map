package com.example.immersivemap.server;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.ChunkSurface;
import com.example.immersivemap.map.HoldState;
import com.example.immersivemap.network.ClientHelloC2S;
import com.example.immersivemap.network.HoldStateC2S;
import com.example.immersivemap.network.MapChunksPayload;
import com.example.immersivemap.network.PlayerHoldStateS2C;
import com.example.immersivemap.network.PlayerPositionsS2C;
import com.example.immersivemap.network.ServerHelloS2C;
import com.example.immersivemap.network.SyncRequestC2S;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server half of the mod. Everything here runs on the server thread. */
public final class ServerMapService {
    private static final ServerMapService INSTANCE = new ServerMapService();
    private static final int SAVE_INTERVAL_TICKS = 20 * 60 * 5;

    private final Map<UUID, HoldState> holdStates = new HashMap<>();
    private final Set<UUID> moddedPlayers = new HashSet<>();
    private final Map<UUID, SyncSession> syncSessions = new HashMap<>();
    private final Set<Identifier> dimensions = new HashSet<>();
    private ServerConfig config = new ServerConfig();
    private SharedMapStore sharedMap;
    private ItemStack mapStack = ItemStack.EMPTY;
    private int ticks;

    private static final class SyncSession {
        final LinkedHashSet<SyncKey> queue = new LinkedHashSet<>();
        int uploadTokens;
        boolean upToDate;
    }

    private ServerMapService() {
    }

    public static ServerMapService get() {
        return INSTANCE;
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> INSTANCE.onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> INSTANCE.onDisconnect(handler.player));
        EntityTrackingEvents.START_TRACKING.register(INSTANCE::onStartTracking);

        ServerPlayNetworking.registerGlobalReceiver(ClientHelloC2S.ID, (payload, context) -> INSTANCE.onClientHello(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(HoldStateC2S.ID, (payload, context) -> INSTANCE.onHoldState(context.player(), payload.state()));
        ServerPlayNetworking.registerGlobalReceiver(SyncRequestC2S.ID, (payload, context) -> INSTANCE.onSyncRequest(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(MapChunksPayload.ID, (payload, context) -> INSTANCE.onUpload(context.player(), payload));
    }

    private void onServerStarted(MinecraftServer server) {
        config = ServerConfig.load();
        mapStack = new ItemStack(Items.FILLED_MAP);
        dimensions.clear();
        for (ServerWorld world : server.getWorlds()) {
            dimensions.add(world.getRegistryKey().getValue());
        }
        if (config.enableMapSync) {
            sharedMap = new SharedMapStore(server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(ImmersiveMapMod.MOD_ID));
            sharedMap.load();
        }
    }

    private void onServerStopping() {
        if (sharedMap != null) {
            sharedMap.close();
            sharedMap = null;
        }
        holdStates.clear();
        moddedPlayers.clear();
        syncSessions.clear();
    }

    private void onDisconnect(ServerPlayerEntity player) {
        holdStates.remove(player.getUuid());
        moddedPlayers.remove(player.getUuid());
        syncSessions.remove(player.getUuid());
    }

    private void onClientHello(ServerPlayerEntity player, ClientHelloC2S hello) {
        if (hello.protocol() != ImmersiveMapMod.PROTOCOL_VERSION) {
            ImmersiveMapMod.LOGGER.info("{} uses map protocol {} (server: {}), map features disabled for them",
                    player.getName().getString(), hello.protocol(), ImmersiveMapMod.PROTOCOL_VERSION);
            return;
        }
        moddedPlayers.add(player.getUuid());
        ServerPlayNetworking.send(player, new ServerHelloS2C(
                ImmersiveMapMod.PROTOCOL_VERSION,
                config.sharePlayerPositions,
                config.showHeldMapToOthers,
                sharedMap != null));
    }

    private void onTick(MinecraftServer server) {
        ticks++;
        if (config.sharePlayerPositions && !moddedPlayers.isEmpty() && ticks % config.positionUpdateIntervalTicks == 0) {
            broadcastPositions(server);
        }
        if (sharedMap != null) {
            if (!syncSessions.isEmpty()) {
                tickSync(server);
            }
            if (ticks % SAVE_INTERVAL_TICKS == 0) {
                sharedMap.saveDirty();
            }
        }
    }

    // ---------------------------------------------------------------- player positions

    private void broadcastPositions(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }
            List<PlayerPositionsS2C.Entry> visible = new ArrayList<>(players.size());
            for (ServerPlayerEntity player : players) {
                if (!isHiddenFromMaps(player)) {
                    visible.add(new PlayerPositionsS2C.Entry(player.getUuid(), player.getX(), player.getZ(), player.getYaw()));
                }
            }
            for (ServerPlayerEntity receiver : players) {
                if (!moddedPlayers.contains(receiver.getUuid())) {
                    continue;
                }
                List<PlayerPositionsS2C.Entry> others = new ArrayList<>(visible.size());
                for (PlayerPositionsS2C.Entry entry : visible) {
                    if (!entry.id().equals(receiver.getUuid())) {
                        others.add(entry);
                    }
                }
                ServerPlayNetworking.send(receiver, new PlayerPositionsS2C(others));
            }
        }
    }

    private boolean isHiddenFromMaps(ServerPlayerEntity player) {
        if (player.isSpectator() || player.isInvisible()) {
            return true;
        }
        if (config.hideSneakingPlayers && player.isSneaking()) {
            return true;
        }
        ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
        return head.isOf(Items.CARVED_PUMPKIN) || head.isIn(ItemTags.SKULLS);
    }

    // ---------------------------------------------------------------- held map

    private void onHoldState(ServerPlayerEntity player, HoldState requested) {
        HoldState state = player.isSpectator() ? HoldState.NONE : requested;
        HoldState previous = holdStates.getOrDefault(player.getUuid(), HoldState.NONE);
        if (state == previous) {
            return;
        }
        if (state == HoldState.NONE) {
            holdStates.remove(player.getUuid());
        } else {
            holdStates.put(player.getUuid(), state);
        }
        if (!config.showHeldMapToOthers) {
            return;
        }

        PlayerHoldStateS2C payload = new PlayerHoldStateS2C(player.getId(), state);
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer != player && ServerPlayNetworking.canSend(viewer, PlayerHoldStateS2C.ID)) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
        sendHandEquipment(player);
        if (!previous.isHolding()) {
            player.getWorld().playSound(player, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6F, 1.1F);
        }
    }

    private void onStartTracking(Entity tracked, ServerPlayerEntity viewer) {
        if (!config.showHeldMapToOthers || !(tracked instanceof ServerPlayerEntity holder)) {
            return;
        }
        HoldState state = holdStates.getOrDefault(holder.getUuid(), HoldState.NONE);
        if (state.isHolding() && ServerPlayNetworking.canSend(viewer, PlayerHoldStateS2C.ID)) {
            ServerPlayNetworking.send(viewer, new PlayerHoldStateS2C(holder.getId(), state));
        }
    }

    private void sendHandEquipment(ServerPlayerEntity player) {
        List<Pair<EquipmentSlot, ItemStack>> equipment = List.of(
                Pair.of(EquipmentSlot.MAINHAND, equippedForViewers(player, EquipmentSlot.MAINHAND, player.getMainHandStack()).copy()),
                Pair.of(EquipmentSlot.OFFHAND, equippedForViewers(player, EquipmentSlot.OFFHAND, player.getOffHandStack()).copy()));
        player.getServerWorld().getChunkManager().sendToOtherNearbyPlayers(player, new EntityEquipmentUpdateS2CPacket(player.getId(), equipment));
    }

    /** What other players should see in a hand slot; the real inventory is never touched. */
    public ItemStack equippedForViewers(LivingEntity entity, EquipmentSlot slot, ItemStack real) {
        if (!config.showHeldMapToOthers || !(entity instanceof ServerPlayerEntity) || holdStates.isEmpty()) {
            return real;
        }
        HoldState state = holdStates.getOrDefault(entity.getUuid(), HoldState.NONE);
        return switch (state) {
            case BOTH_HANDS -> slot == EquipmentSlot.MAINHAND ? mapStack : slot == EquipmentSlot.OFFHAND ? ItemStack.EMPTY : real;
            case OFF_HAND -> slot == EquipmentSlot.OFFHAND ? mapStack : real;
            case NONE -> real;
        };
    }

    public List<Pair<EquipmentSlot, ItemStack>> rewriteEquipment(LivingEntity entity, List<Pair<EquipmentSlot, ItemStack>> equipment) {
        if (holdStates.isEmpty() || !(entity instanceof ServerPlayerEntity)) {
            return equipment;
        }
        List<Pair<EquipmentSlot, ItemStack>> rewritten = new ArrayList<>(equipment.size());
        for (Pair<EquipmentSlot, ItemStack> pair : equipment) {
            ItemStack shown = equippedForViewers(entity, pair.getFirst(), pair.getSecond());
            rewritten.add(shown == pair.getSecond() ? pair : Pair.of(pair.getFirst(), shown.copy()));
        }
        return rewritten;
    }

    // ---------------------------------------------------------------- shared map sync

    private void onSyncRequest(ServerPlayerEntity player, SyncRequestC2S request) {
        if (!request.enabled() || sharedMap == null || !moddedPlayers.contains(player.getUuid())) {
            syncSessions.remove(player.getUuid());
            return;
        }
        SyncSession session = new SyncSession();
        session.uploadTokens = config.maxUploadChunksPerSecond;
        List<SyncKey> keys = new ArrayList<>();
        sharedMap.collectSince(request.since(), keys);
        Identifier dimension = player.getWorld().getRegistryKey().getValue();
        ChunkPos center = player.getChunkPos();
        keys.sort(Comparator.<SyncKey>comparingInt(key -> key.layer().dimension().equals(dimension) ? 0 : 1)
                .thenComparingLong(key -> {
                    long dx = ChunkPos.getPackedX(key.chunk()) - center.x;
                    long dz = ChunkPos.getPackedZ(key.chunk()) - center.z;
                    return dx * dx + dz * dz;
                }));
        session.queue.addAll(keys);
        syncSessions.put(player.getUuid(), session);
    }

    private void onUpload(ServerPlayerEntity player, MapChunksPayload payload) {
        SyncSession session = syncSessions.get(player.getUuid());
        if (session == null || sharedMap == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (MapChunksPayload.Entry entry : payload.entries()) {
            if (session.uploadTokens <= 0) {
                break;
            }
            session.uploadTokens--;
            if (!entry.layer().isValid() || !dimensions.contains(entry.layer().dimension())) {
                continue;
            }
            ChunkSurface incoming;
            try {
                incoming = ChunkSurface.decompress(entry.data(), now);
            } catch (IOException exception) {
                continue;
            }
            if (sharedMap.merge(entry.layer(), entry.chunkX(), entry.chunkZ(), incoming, now) == null) {
                continue;
            }
            SyncKey key = new SyncKey(entry.layer(), ChunkPos.toLong(entry.chunkX(), entry.chunkZ()));
            for (Map.Entry<UUID, SyncSession> other : syncSessions.entrySet()) {
                if (!other.getKey().equals(player.getUuid())) {
                    other.getValue().queue.add(key);
                }
            }
        }
    }

    private void tickSync(MinecraftServer server) {
        int refill = Math.max(1, config.maxUploadChunksPerSecond / 20);
        for (Map.Entry<UUID, SyncSession> sessionEntry : syncSessions.entrySet()) {
            SyncSession session = sessionEntry.getValue();
            session.uploadTokens = Math.min(config.maxUploadChunksPerSecond, session.uploadTokens + refill);
            if (session.queue.isEmpty() && session.upToDate) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(sessionEntry.getKey());
            if (player == null) {
                continue;
            }

            List<MapChunksPayload.Entry> entries = new ArrayList<>();
            int budget = config.syncDownloadBytesPerTick;
            Iterator<SyncKey> iterator = session.queue.iterator();
            while (iterator.hasNext() && budget > 0 && entries.size() < MapChunksPayload.MAX_ENTRIES) {
                SyncKey key = iterator.next();
                iterator.remove();
                SharedMapStore.Entry stored = sharedMap.get(key.layer(), key.chunk());
                if (stored == null) {
                    continue;
                }
                MapChunksPayload.Entry entry = new MapChunksPayload.Entry(key.layer(),
                        ChunkPos.getPackedX(key.chunk()), ChunkPos.getPackedZ(key.chunk()), stored.timestamp, stored.data);
                entries.add(entry);
                budget -= entry.estimatedSize();
            }
            session.upToDate = session.queue.isEmpty();
            ServerPlayNetworking.send(player, new MapChunksPayload(entries, session.upToDate ? sharedMap.latestTimestamp() : -1L));
        }
    }
}
