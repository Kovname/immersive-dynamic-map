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

    public enum PlayerMarker { ARROW, HEAD }

    public enum MobFilter { ALL, HOSTILE, PASSIVE }

    // Holding
    public boolean startInOffHand = false;
    public boolean hotbarMovesMapToOffHand = true;
    public boolean showHeldSlot = true;
    public boolean thirdPersonPose = true;

    // View
    public boolean followPlayerByDefault = true;
    /** Vanilla map scale 0..4. */
    public int defaultScale = 1;
    public boolean zoomAroundCursor = true;
    public boolean worldLighting = true;

    // Cursor / navigation
    public float cursorSensitivity = 0.3F;
    /** Width of the edge band in map pixels where the cursor scrolls the map. */
    public int edgePanZone = 12;
    /** Map pixels per second at the very edge. */
    public int edgePanSpeed = 96;

    // Map contents
    public PlayerMarker playerMarker = PlayerMarker.ARROW;
    public boolean smoothArrowRotation = false;
    public boolean showOtherPlayers = true;
    public boolean showMobs = false;
    public MobFilter mobFilter = MobFilter.ALL;
    public int maxMobIcons = 48;
    public boolean showMarkerNames = true;
    public boolean showDeathMarker = true;
    public boolean showChunkGrid = false;
    public boolean showCoordinates = true;
    public boolean showScale = true;

    // Performance
    /** Milliseconds per client tick spent reading chunks into the map. */
    public float scanBudgetMs = 1.5F;

    // Experimental
    public boolean smartCaveLayers = true;
    public boolean autoCaveLayer = true;
    public int caveRevealRadius = 14;
    public boolean mapSync = false;
    public boolean showCursorBiome = false;

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
        config.save();
        return config;
    }

    public void sanitize() {
        defaultScale = MathHelper.clamp(defaultScale, 0, 4);
        cursorSensitivity = MathHelper.clamp(cursorSensitivity, 0.05F, 2.0F);
        edgePanZone = MathHelper.clamp(edgePanZone, 2, 40);
        edgePanSpeed = MathHelper.clamp(edgePanSpeed, 10, 400);
        maxMobIcons = MathHelper.clamp(maxMobIcons, 1, 256);
        scanBudgetMs = MathHelper.clamp(scanBudgetMs, 0.25F, 8.0F);
        caveRevealRadius = MathHelper.clamp(caveRevealRadius, 4, 32);
        if (playerMarker == null) {
            playerMarker = PlayerMarker.ARROW;
        }
        if (mobFilter == null) {
            mobFilter = MobFilter.ALL;
        }
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
