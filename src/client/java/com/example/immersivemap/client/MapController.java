package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.HoldState;
import com.example.immersivemap.map.LayerId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State of the virtual map: whether it is out, which hand holds it, the equip animation, the view and the markers.
 * The real inventory is never modified; the held item is only hidden while the map is out.
 */
public final class MapController {
    public static final int MAP_SIZE = 128;
    private static final float ANIMATION_STEP = 0.25F;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Marker> MARKERS = new ArrayList<>();

    private static ItemStack mapStack;
    private static boolean open;
    private static HoldState hold = HoldState.BOTH_HANDS;
    /** The hand the animation is currently showing; lags behind {@link #hold} while switching hands. */
    private static HoldState renderedHold = HoldState.BOTH_HANDS;
    private static float progress;
    private static float prevProgress;
    private static boolean follow = true;
    private static double centerX;
    private static double centerZ;
    private static int scale = 1;
    private static boolean viewInitialized;
    private static int selectedSlot = -1;
    private static Path statePath;
    private static boolean stateDirty;

    private MapController() {
    }

    public static ItemStack mapStack() {
        if (mapStack == null) {
            mapStack = new ItemStack(Items.FILLED_MAP);
        }
        return mapStack;
    }

    // ---------------------------------------------------------------- open / hands

    public static void toggle(MinecraftClient client) {
        if (open) {
            close(client);
        } else {
            open(client, ClientConfig.get().startInOffHand ? HoldState.OFF_HAND : HoldState.BOTH_HANDS);
        }
    }

    public static void open(MinecraftClient client, HoldState state) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || player.isSpectator()) {
            return;
        }
        if (!viewInitialized) {
            ClientConfig config = ClientConfig.get();
            follow = config.followPlayerByDefault;
            scale = config.defaultScale;
            centerX = player.getX();
            centerZ = player.getZ();
            viewInitialized = true;
        }
        if (player.isUsingItem() && client.interactionManager != null) {
            client.interactionManager.stopUsingItem(player);
        }
        if (!open && progress <= 0.0F) {
            renderedHold = state;
        }
        open = true;
        hold = state;
        selectedSlot = player.getInventory().selectedSlot;
        player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.6F, 1.1F);
        ClientNetworking.sendHoldState(currentHoldState());
    }

    public static void close(MinecraftClient client) {
        if (!open) {
            return;
        }
        open = false;
        if (client.player != null) {
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.4F, 0.9F);
        }
        ClientNetworking.sendHoldState(HoldState.NONE);
    }

    /** Moves the map between both hands and the off hand, taking it out into the off hand if it is put away. */
    public static void switchHands(MinecraftClient client) {
        if (!open) {
            open(client, HoldState.OFF_HAND);
            return;
        }
        hold = hold == HoldState.BOTH_HANDS ? HoldState.OFF_HAND : HoldState.BOTH_HANDS;
        if (client.player != null) {
            selectedSlot = client.player.getInventory().selectedSlot;
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.4F, 1.3F);
        }
        ClientNetworking.sendHoldState(currentHoldState());
    }

    public static void forceClose() {
        open = false;
        progress = 0.0F;
        prevProgress = 0.0F;
    }

    public static HoldState currentHoldState() {
        return open ? hold : HoldState.NONE;
    }

    public static void tick(MinecraftClient client) {
        prevProgress = progress;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            forceClose();
            return;
        }
        if (open && (player.isDead() || player.isSpectator())) {
            close(client);
        }

        if (open && renderedHold == hold) {
            progress = Math.min(1.0F, progress + ANIMATION_STEP);
        } else {
            progress = Math.max(0.0F, progress - ANIMATION_STEP);
            if (progress <= 0.0F && open) {
                renderedHold = hold;
            }
        }

        if (open) {
            int slot = player.getInventory().selectedSlot;
            if (hold == HoldState.BOTH_HANDS && selectedSlot >= 0 && slot != selectedSlot) {
                if (ClientConfig.get().hotbarMovesMapToOffHand) {
                    switchHands(client);
                } else {
                    close(client);
                }
            }
            selectedSlot = slot;
        }
        if (follow) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
        if (stateDirty && client.world.getTime() % 100 == 0) {
            saveState();
        }
    }

    public static boolean isOpen() {
        return open;
    }

    /** The map is out in both hands: mouse and buttons drive the map instead of the world. */
    public static boolean isInteractive() {
        return open && hold == HoldState.BOTH_HANDS && renderedHold == HoldState.BOTH_HANDS;
    }

    /** The hand items are put away (or being put away) for the two-handed map. */
    public static boolean blocksHands() {
        return open && hold == HoldState.BOTH_HANDS;
    }

    public static boolean isVisible() {
        return progress > 0.0F || prevProgress > 0.0F;
    }

    public static HoldState renderedHold() {
        return renderedHold;
    }

    /** 0 = hands show their items, 1 = map fully raised. */
    public static float animation(float tickDelta) {
        float t = MathHelper.lerp(tickDelta, prevProgress, progress);
        return t * t * (3.0F - 2.0F * t);
    }

    public static boolean shouldRenderFirstPerson(MinecraftClient client) {
        return isVisible() && client.player != null && client.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    // ---------------------------------------------------------------- view / navigation

    public static boolean isFollowing() {
        return follow;
    }

    public static int scale() {
        return scale;
    }

    public static int blocksPerPixel() {
        return 1 << scale;
    }

    public static double centerX(float tickDelta) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return follow && player != null ? MathHelper.lerp(tickDelta, player.prevX, player.getX()) : centerX;
    }

    public static double centerZ(float tickDelta) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return follow && player != null ? MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) : centerZ;
    }

    /** Drag the map: content follows the mouse like a sheet of paper. */
    public static void pan(double mouseDeltaX, double mouseDeltaY) {
        double factor = ClientConfig.get().cursorSensitivity * blocksPerPixel();
        follow = false;
        centerX -= mouseDeltaX * factor;
        centerZ -= mouseDeltaY * factor;
        stateDirty = true;
    }

    public static void zoom(double amount) {
        int next = MathHelper.clamp(scale - (int) Math.signum(amount), 0, 4);
        if (next != scale) {
            scale = next;
            stateDirty = true;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.3F, 0.8F + scale * 0.15F);
            }
        }
    }

    public static void recenter(MinecraftClient client) {
        if (client.player != null) {
            centerX = client.player.getX();
            centerZ = client.player.getZ();
        }
        follow = true;
        stateDirty = true;
    }

    /** The layer shown right now: an explored cave band underground (experimental) or the vanilla surface. */
    public static LayerId currentLayer(MinecraftClient client) {
        if (client.world == null) {
            return null;
        }
        LayerId cave = ImmersiveMapClientState.scanner().activeCaveLayer();
        return cave != null ? cave : LayerId.surface(client.world.getRegistryKey().getValue());
    }

    // ---------------------------------------------------------------- markers

    public static List<Marker> markers() {
        return Collections.unmodifiableList(MARKERS);
    }

    /** Left click on the open map: edit the banner under the map center or place a new one there. */
    public static void onMapClick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }
        Identifier dimension = client.world.getRegistryKey().getValue();
        double x = centerX(1.0F);
        double z = centerZ(1.0F);
        double pick = 5.0 * blocksPerPixel();
        Marker nearest = null;
        double best = pick * pick;
        for (Marker marker : MARKERS) {
            if (!marker.dimension.equals(dimension)) {
                continue;
            }
            double dx = marker.x + 0.5 - x;
            double dz = marker.z + 0.5 - z;
            double distance = dx * dx + dz * dz;
            if (distance <= best) {
                best = distance;
                nearest = marker;
            }
        }
        if (nearest != null) {
            client.setScreen(new MarkerEditScreen(nearest));
            return;
        }
        MarkerColor color = MARKERS.isEmpty() ? MarkerColor.RED : MARKERS.get(MARKERS.size() - 1).color;
        MARKERS.add(new Marker(dimension, MathHelper.floor(x), MathHelper.floor(z), "", color));
        client.player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.6F, 1.0F);
        stateDirty = true;
    }

    public static void removeMarker(Marker marker) {
        MARKERS.remove(marker);
        stateDirty = true;
    }

    public static final class Marker {
        private final Identifier dimension;
        private final int x;
        private final int z;
        private String name;
        private MarkerColor color;

        private Marker(Identifier dimension, int x, int z, String name, MarkerColor color) {
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.name = name == null ? "" : name;
            this.color = color == null ? MarkerColor.RED : color;
        }

        public Identifier dimension() {
            return dimension;
        }

        public int x() {
            return x;
        }

        public int z() {
            return z;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name.trim();
            stateDirty = true;
        }

        public MarkerColor color() {
            return color;
        }

        public void setColor(MarkerColor color) {
            this.color = color == null ? MarkerColor.RED : color;
            stateDirty = true;
        }
    }

    // ---------------------------------------------------------------- persistence

    private record SavedState(double centerX, double centerZ, int scale, boolean follow, List<SavedMarker> markers) {
    }

    private record SavedMarker(String dimension, int x, int z, String name, String color) {
    }

    public static void loadState(Path root) {
        statePath = root.resolve("view.json");
        MARKERS.clear();
        viewInitialized = false;
        stateDirty = false;
        forceClose();
        if (!Files.isRegularFile(statePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            SavedState state = GSON.fromJson(reader, SavedState.class);
            if (state == null) {
                return;
            }
            centerX = state.centerX();
            centerZ = state.centerZ();
            scale = MathHelper.clamp(state.scale(), 0, 4);
            follow = state.follow();
            viewInitialized = true;
            if (state.markers() != null) {
                for (SavedMarker saved : state.markers()) {
                    Identifier dimension = saved.dimension() == null ? null : Identifier.tryParse(saved.dimension());
                    if (dimension != null) {
                        MARKERS.add(new Marker(dimension, saved.x(), saved.z(), saved.name(), MarkerColor.byId(saved.color())));
                    }
                }
            }
        } catch (IOException | JsonParseException exception) {
            ImmersiveMapMod.LOGGER.warn("Could not read {}", statePath, exception);
        }
    }

    public static void saveState() {
        if (statePath == null) {
            return;
        }
        stateDirty = false;
        List<SavedMarker> saved = new ArrayList<>(MARKERS.size());
        for (Marker marker : MARKERS) {
            saved.add(new SavedMarker(marker.dimension.toString(), marker.x, marker.z, marker.name, marker.color.id()));
        }
        SavedState state = new SavedState(centerX, centerZ, scale, follow, saved);
        try {
            Files.createDirectories(statePath.getParent());
            Files.writeString(statePath, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ImmersiveMapMod.LOGGER.warn("Could not write {}", statePath, exception);
        }
    }
}
