package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;

/** Per-connection objects: the map store of the current world, the scanner, remote players and the texture. */
public final class ImmersiveMapClientState {
    private static final MapScanner SCANNER = new MapScanner();
    private static final PlayerTracker PLAYERS = new PlayerTracker();
    private static MapStore store;
    private static MapTexture texture;

    private ImmersiveMapClientState() {
    }

    public static MapScanner scanner() {
        return SCANNER;
    }

    public static PlayerTracker players() {
        return PLAYERS;
    }

    public static MapStore store() {
        return store;
    }

    public static MapTexture texture() {
        if (texture == null) {
            texture = new MapTexture();
        }
        return texture;
    }

    public static void onJoin(MinecraftClient client) {
        onDisconnect();
        Path root = client.runDirectory.toPath().resolve(ImmersiveMapMod.MOD_ID).resolve("maps").resolve(worldId(client));
        store = new MapStore(root);
        MapController.loadState(root);
    }

    public static void onDisconnect() {
        MapController.saveState();
        MapController.forceClose();
        SCANNER.reset();
        PLAYERS.clear();
        if (store != null) {
            store.close();
            store = null;
        }
        if (texture != null) {
            texture.invalidate();
        }
    }

    private static String worldId(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server != null) {
            Path name = server.getSavePath(WorldSavePath.ROOT).normalize().getFileName();
            return sanitize("sp_" + (name == null ? "world" : name.toString()));
        }
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null) {
            return sanitize((info.isRealm() ? "realms_" : "mp_") + info.address);
        }
        return "mp_unknown";
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
