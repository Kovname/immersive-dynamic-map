package com.example.immersivemap.client;

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

/** Client options in {@code config/immersive_map_client.json}. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("immersive_map_client.json");
    private static ClientConfig instance;

    public boolean followPlayerByDefault = true;
    /** Vanilla map scale 0..4. */
    public int defaultScale = 1;
    public boolean startInOffHand = false;
    public boolean showPlayerHeads = true;
    public boolean showMarkerNames = true;
    public boolean showCoordinates = true;
    public boolean showHeldSlot = true;
    /** Hotbar number keys move the map to the off hand instead of closing it. */
    public boolean hotbarMovesMapToOffHand = true;
    public float cursorSensitivity = 0.35F;
    /** Milliseconds per client tick spent reading chunks into the map. */
    public float scanBudgetMs = 1.5F;
    /** Experimental: map explored caves on separate layers. */
    public boolean smartCaveLayers = false;
    public int caveRevealRadius = 14;
    /** Experimental: exchange explored chunks with every player through a server running the mod. */
    public boolean mapSync = false;

    public static ClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ClientConfig load() {
        ClientConfig config = null;
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ClientConfig.class);
            } catch (IOException | JsonParseException exception) {
                ImmersiveMapMod.LOGGER.warn("Could not read {}, using defaults", PATH, exception);
            }
        }
        if (config == null) {
            config = new ClientConfig();
        }
        config.sanitize();
        config.save();
        return config;
    }

    public void sanitize() {
        defaultScale = MathHelper.clamp(defaultScale, 0, 4);
        cursorSensitivity = MathHelper.clamp(cursorSensitivity, 0.05F, 2.0F);
        scanBudgetMs = MathHelper.clamp(scanBudgetMs, 0.25F, 8.0F);
        caveRevealRadius = MathHelper.clamp(caveRevealRadius, 4, 32);
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ImmersiveMapMod.LOGGER.warn("Could not write {}", PATH, exception);
        }
    }
}
