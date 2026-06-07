package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.config.MapConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapPersistence {
    private static final int SAVE_VERSION = 1;
    private static final int SAVE_INTERVAL_TICKS = 200;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private String currentWorldKey;
    private int saveTicks;
    private boolean pendingSave;

    public void tick(MinecraftClient client, MapTextureManager textureManager) {
        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        String worldKey = config.persistMapData ? getWorldKey(client) : null;

        if (!sameKey(currentWorldKey, worldKey)) {
            saveCurrent(client, textureManager);
            currentWorldKey = worldKey;
            pendingSave = false;
            loadCurrent(client, textureManager);
        }

        pendingSave |= MapController.consumeDirty();
        pendingSave |= textureManager.consumeDirty();

        if (worldKey == null || !pendingSave) {
            return;
        }

        saveTicks++;
        if (saveTicks >= SAVE_INTERVAL_TICKS) {
            saveTicks = 0;
            saveCurrent(client, textureManager);
            pendingSave = false;
        }
    }

    public void flush(MinecraftClient client, MapTextureManager textureManager) {
        pendingSave |= MapController.consumeDirty();
        pendingSave |= textureManager.consumeDirty();
        if (pendingSave) {
            saveCurrent(client, textureManager);
            pendingSave = false;
        }
    }

    private void loadCurrent(MinecraftClient client, MapTextureManager textureManager) {
        if (currentWorldKey == null) {
            MapController.loadState(client, null);
            textureManager.loadExploredPixels(Map.of());
            return;
        }

        Path path = getSavePath(client, currentWorldKey);
        if (!Files.isRegularFile(path)) {
            MapController.loadState(client, null);
            textureManager.loadExploredPixels(Map.of());
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            MapController.loadState(client, readController(root.getAsJsonObject("view")));
            textureManager.loadExploredPixels(readPixels(root.getAsJsonArray("pixels")));
        } catch (RuntimeException | IOException ignored) {
            MapController.loadState(client, null);
            textureManager.loadExploredPixels(Map.of());
        }
    }

    private void saveCurrent(MinecraftClient client, MapTextureManager textureManager) {
        if (currentWorldKey == null) {
            return;
        }

        Path path = getSavePath(client, currentWorldKey);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", SAVE_VERSION);
            root.addProperty("world", currentWorldKey);
            root.add("view", writeController(MapController.saveState()));
            root.add("pixels", writePixels(textureManager.snapshotExploredPixels()));

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static JsonObject writeController(MapController.SavedState state) {
        JsonObject view = new JsonObject();
        view.addProperty("centerX", state.centerX());
        view.addProperty("centerZ", state.centerZ());
        view.addProperty("cursorX", state.cursorX());
        view.addProperty("cursorZ", state.cursorZ());
        view.addProperty("zoomScale", state.zoomScale());
        view.addProperty("followPlayer", state.followPlayer());
        view.addProperty("holdMode", state.holdMode());

        JsonArray markers = new JsonArray();
        for (MapController.SavedMarker marker : state.markers()) {
            JsonObject markerJson = new JsonObject();
            markerJson.addProperty("x", marker.x());
            markerJson.addProperty("z", marker.z());
            markerJson.addProperty("name", marker.name());
            markerJson.addProperty("color", marker.color());
            markers.add(markerJson);
        }
        view.add("markers", markers);
        return view;
    }

    private static MapController.SavedState readController(JsonObject view) {
        if (view == null) {
            return null;
        }

        List<MapController.SavedMarker> markers = new ArrayList<>();
        JsonArray markerArray = view.getAsJsonArray("markers");
        if (markerArray != null) {
            for (JsonElement element : markerArray) {
                JsonObject marker = element.getAsJsonObject();
                markers.add(new MapController.SavedMarker(
                        marker.get("x").getAsInt(),
                        marker.get("z").getAsInt(),
                        optionalString(marker, "name"),
                        optionalString(marker, "color")));
            }
        }

        return new MapController.SavedState(
                view.get("centerX").getAsInt(),
                view.get("centerZ").getAsInt(),
                view.get("cursorX").getAsInt(),
                view.get("cursorZ").getAsInt(),
                view.get("zoomScale").getAsInt(),
                view.get("followPlayer").getAsBoolean(),
                optionalString(view, "holdMode"),
                markers);
    }

    private static JsonArray writePixels(Map<Long, Integer> pixels) {
        JsonArray array = new JsonArray();
        for (Map.Entry<Long, Integer> entry : pixels.entrySet()) {
            JsonArray pixel = new JsonArray();
            pixel.add(entry.getKey());
            pixel.add(entry.getValue());
            array.add(pixel);
        }
        return array;
    }

    private static Map<Long, Integer> readPixels(JsonArray array) {
        Map<Long, Integer> pixels = new LinkedHashMap<>();
        if (array == null) {
            return pixels;
        }

        for (JsonElement element : array) {
            JsonArray pixel = element.getAsJsonArray();
            if (pixel.size() >= 2) {
                pixels.put(pixel.get(0).getAsLong(), pixel.get(1).getAsInt());
            }
        }
        return pixels;
    }

    private static String getWorldKey(MinecraftClient client) {
        if (client.world == null) {
            return null;
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            return "singleplayer:" + client.getServer().getSaveProperties().getLevelName() + ":" + dimension;
        }

        ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            return "server:" + server.address + ":" + dimension;
        }

        return "local:" + dimension;
    }

    private static Path getSavePath(MinecraftClient client, String worldKey) {
        return client.runDirectory.toPath()
                .resolve("config")
                .resolve(ImmersiveMapMod.MOD_ID)
                .resolve("maps")
                .resolve(sha256(worldKey) + ".json");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean sameKey(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String optionalString(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
