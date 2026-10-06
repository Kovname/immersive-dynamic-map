package com.example.immersivemap.server;

import com.example.immersivemap.ImmersiveMapMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server options in {@code config/immersive_map_server.json}. Plain Gson so dedicated servers do not depend on
 * client-only config libraries.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Send every player's position to modded clients so heads show up beyond entity tracking range. */
    public boolean sharePlayerPositions = true;
    public int positionUpdateIntervalTicks = 10;
    /** Like the vanilla locator bar: sneaking players are left off other players' maps. */
    public boolean hideSneakingPlayers = true;
    /** Show the held map in other players' hands, also for clients without the mod. */
    public boolean showHeldMapToOthers = true;
    /** Experimental: merge explored map chunks of every player who opted in and share them. */
    public boolean enableMapSync = true;
    public int syncDownloadBytesPerTick = 49152;
    public int maxUploadChunksPerSecond = 256;

    public static ServerConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("immersive_map_server.json");
        ServerConfig config = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ServerConfig.class);
            } catch (IOException | JsonParseException exception) {
                ImmersiveMapMod.LOGGER.warn("Could not read {}, using defaults", path, exception);
            }
        }
        if (config == null) {
            config = new ServerConfig();
        }
        config.sanitize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ImmersiveMapMod.LOGGER.warn("Could not write {}", path, exception);
        }
        return config;
    }

    private void sanitize() {
        positionUpdateIntervalTicks = MathHelper.clamp(positionUpdateIntervalTicks, 1, 100);
        syncDownloadBytesPerTick = MathHelper.clamp(syncDownloadBytesPerTick, 4096, 1 << 20);
        maxUploadChunksPerSecond = MathHelper.clamp(maxUploadChunksPerSecond, 16, 4096);
    }
}
