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
import net.minecraft.text.Text;
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
 * State of the virtual map: whether it is out, which hand holds it, the equip animation, the view, the cursor,
 * the shown layer and the markers. The real inventory is never modified.
 */
public final class MapController {
    public static final int MAP_SIZE = 128;
    private static final float HALF = MAP_SIZE / 2.0F;
    private static final float ANIMATION_STEP = 0.25F;
    private static final double PICK_RADIUS = 5.0;
    private static final long CURSOR_FADE_NANOS = 400_000_000L;
    /** How long the lock next to the map slot stays up after pinning or unpinning. */
    public static final long LOCK_FLASH_NANOS = 1_200_000_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Marker> MARKERS = new ArrayList<>();

    private static ItemStack mapStack;
    private static boolean open;
    private static HoldState hold = HoldState.BOTH_HANDS;
    /** The hand the animation is currently showing; lags behind {@link #hold} while switching hands. */
    private static HoldState renderedHold = HoldState.BOTH_HANDS;
    private static float progress;
    private static float prevProgress;
    private static int selectedSlot = -1;

    private static boolean follow = true;
    private static double centerX;
    private static double centerZ;
    private static int scale = 1;
    private static boolean viewInitialized;
    /** Offset from the player the view glides back from after following is turned on again. */
    private static double returnX;
    private static double returnZ;
    private static long returnStart;
    private static long returnDuration;
    private static boolean returning;
    private static long lockFlashStart;
    private static boolean lockFlashLocked;

    /** Cursor position in map pixels relative to the map center. */
    private static double cursorX;
    private static double cursorZ;
    private static boolean cursorVisible;
    private static long cursorActive;
    private static long lastFrameNanos;
    private static double scrollAccumulator;

    /** {@code null} = automatic layer (surface, or the explored cave band underground). */
    private static LayerId manualLayer;

    private static Identifier deathDimension;
    private static int deathX;
    private static int deathZ;
    /** Removed by the player; stays hidden until a death somewhere else. */
    private static boolean deathHidden;
    private static boolean wasDead;

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
        boolean dead = player.isDead();
        if (dead && !wasDead) {
            recordDeath(client.world.getRegistryKey().getValue(), player.getBlockX(), player.getBlockZ());
        }
        wasDead = dead;
        // The server's last death location also covers instant respawn, where the client never sees a dead player.
        player.getLastDeathPos().ifPresent(pos -> recordDeath(pos.dimension().getValue(), pos.pos().getX(), pos.pos().getZ()));
        if (open && (dead || player.isSpectator())) {
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

    /** The map is out in both hands and fully raised, so map mode can work it. */
    public static boolean isInteractive() {
        return open && hold == HoldState.BOTH_HANDS && renderedHold == HoldState.BOTH_HANDS;
    }

    /** The hand items are put away (or being put away) for the two-handed map. */
    public static boolean blocksHands() {
        return open && hold == HoldState.BOTH_HANDS;
    }

    /**
     * Map mode: right mouse held with the map in both hands. The mouse then moves the map cursor, left click places
     * banners and the wheel zooms; otherwise attacking, breaking and the hotbar wheel stay vanilla.
     */
    public static boolean isMapMode(MinecraftClient client) {
        return blocksHands() && client.currentScreen == null && client.options.useKey.isPressed();
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
        if (!follow || player == null) {
            return centerX;
        }
        return MathHelper.lerp(tickDelta, player.prevX, player.getX()) + returnX * returnRemaining();
    }

    public static double centerZ(float tickDelta) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!follow || player == null) {
            return centerZ;
        }
        return MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) + returnZ * returnRemaining();
    }

    /** 1 right after following was turned back on, easing out to 0 when the view is centered on the player again. */
    private static double returnRemaining() {
        if (!returning) {
            return 0.0;
        }
        double t = (double) (System.nanoTime() - returnStart) / returnDuration;
        if (t >= 1.0) {
            returning = false;
            return 0.0;
        }
        double left = 1.0 - t;
        return left * left * left;
    }

    /**
     * Toggles between following the player and a map pinned in place. Only works with the map out; a lock flashes
     * next to the map slot instead of a chat message, and the view glides back to the player when following again.
     */
    public static void toggleFollow(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !open) {
            return;
        }
        float tickDelta = client.getRenderTickCounter().getTickDelta(true);
        double fromX = centerX(tickDelta);
        double fromZ = centerZ(tickDelta);
        follow = !follow;
        if (follow) {
            returnX = fromX - MathHelper.lerp(tickDelta, player.prevX, player.getX());
            returnZ = fromZ - MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
            double pixels = Math.sqrt(returnX * returnX + returnZ * returnZ) / blocksPerPixel();
            returning = pixels > 0.5;
            returnStart = System.nanoTime();
            returnDuration = (long) (MathHelper.clamp(250.0 + pixels * 2.0, 300.0, 650.0) * 1_000_000L);
            centerX = player.getX();
            centerZ = player.getZ();
            cursorX = 0.0;
            cursorZ = 0.0;
            cursorVisible = false;
        } else {
            // Pin exactly what is on screen, including a return glide still in progress.
            returning = false;
            centerX = fromX;
            centerZ = fromZ;
        }
        stateDirty = true;
        lockFlashStart = System.nanoTime();
        lockFlashLocked = !follow;
        player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.4F, follow ? 1.2F : 0.9F);
    }

    /** Nanoseconds since the last pin/unpin, or a large value when the lock should not be shown. */
    public static long lockFlashAge() {
        return open && lockFlashStart != 0L ? System.nanoTime() - lockFlashStart : Long.MAX_VALUE;
    }

    public static boolean lockFlashLocked() {
        return lockFlashLocked;
    }

    public static boolean isCursorVisible() {
        return cursorVisible && isInteractive();
    }

    /** 1 while the cursor is in use, fading to 0 before it hides after a few idle seconds. */
    public static float cursorAlpha() {
        if (!isCursorVisible()) {
            return 0.0F;
        }
        long fadeStart = (long) (ClientConfig.get().cursorHideSeconds * 1.0E9);
        long idle = System.nanoTime() - cursorActive - fadeStart;
        return idle <= 0L ? 1.0F : MathHelper.clamp(1.0F - (float) idle / CURSOR_FADE_NANOS, 0.0F, 1.0F);
    }

    public static double cursorOffsetX() {
        return cursorX;
    }

    public static double cursorOffsetZ() {
        return cursorZ;
    }

    public static double cursorWorldX(float tickDelta) {
        return centerX(tickDelta) + cursorX * blocksPerPixel();
    }

    public static double cursorWorldZ(float tickDelta) {
        return centerZ(tickDelta) + cursorZ * blocksPerPixel();
    }

    /** Mouse movement while the drag button is held moves the cursor over the map. */
    public static void moveCursor(double mouseDeltaX, double mouseDeltaY) {
        double sensitivity = ClientConfig.get().cursorSensitivity;
        cursorX = MathHelper.clamp(cursorX + mouseDeltaX * sensitivity, -HALF, HALF);
        cursorZ = MathHelper.clamp(cursorZ + mouseDeltaY * sensitivity, -HALF, HALF);
        cursorVisible = true;
        cursorActive = System.nanoTime();
    }

    /**
     * Called every rendered frame: the cursor hides after a few idle seconds, and a cursor resting in the edge band
     * scrolls the map, like an RTS camera.
     */
    public static void frame(MinecraftClient client) {
        long now = System.nanoTime();
        double seconds = lastFrameNanos == 0L ? 0.0 : Math.min((now - lastFrameNanos) / 1.0E9, 0.1);
        lastFrameNanos = now;
        boolean dragging = isInteractive() && isMapMode(client);
        if (dragging) {
            // Map mode shows the cursor right away, where it was left, so you see where a click goes.
            cursorVisible = true;
        }
        if (!isCursorVisible()) {
            return;
        }
        if (dragging || client.currentScreen != null) {
            // Holding the drag button (or editing a banner) keeps the cursor alive even without mouse movement.
            cursorActive = now;
        } else if (cursorAlpha() <= 0.0F) {
            cursorVisible = false;
            return;
        }
        if (!dragging) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        double zone = config.edgePanZone;
        double inner = HALF - zone;
        double dx = edgeStrength(cursorX, inner, zone);
        double dz = edgeStrength(cursorZ, inner, zone);
        if (dx == 0.0 && dz == 0.0) {
            return;
        }
        if (follow) {
            centerX = centerX(1.0F);
            centerZ = centerZ(1.0F);
            follow = false;
            returning = false;
        }
        double step = config.edgePanSpeed * seconds * blocksPerPixel();
        centerX += dx * step;
        centerZ += dz * step;
        stateDirty = true;
    }

    private static double edgeStrength(double cursor, double inner, double zone) {
        if (cursor > inner) {
            return Math.min(1.0, (cursor - inner) / zone);
        }
        if (cursor < -inner) {
            return -Math.min(1.0, (-inner - cursor) / zone);
        }
        return 0.0;
    }

    public static void zoom(double amount) {
        int next = MathHelper.clamp(scale - (int) Math.signum(amount), 0, 4);
        if (next == scale) {
            return;
        }
        if (!follow && cursorVisible && ClientConfig.get().zoomAroundCursor) {
            // Keep the block under the cursor under the cursor.
            double worldX = centerX + cursorX * blocksPerPixel();
            double worldZ = centerZ + cursorZ * blocksPerPixel();
            centerX = worldX - cursorX * (1 << next);
            centerZ = worldZ - cursorZ * (1 << next);
        }
        if (cursorVisible) {
            cursorActive = System.nanoTime();
        }
        scale = next;
        stateDirty = true;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.3F, 0.8F + scale * 0.15F);
        }
    }

    /**
     * Mouse wheel in map mode: zooms, or changes the layer with Shift. Like vanilla, fractional deltas from smooth
     * wheels and touchpads add up to whole steps instead of each tiny event jumping a full step.
     */
    public static void scroll(MinecraftClient client, double amount, boolean layer) {
        if (scrollAccumulator != 0.0 && Math.signum(amount) != Math.signum(scrollAccumulator)) {
            scrollAccumulator = 0.0;
        }
        scrollAccumulator += amount;
        int steps = (int) scrollAccumulator;
        if (steps == 0) {
            return;
        }
        scrollAccumulator -= steps;
        if (layer) {
            changeLayer(client, steps);
        } else {
            zoom(steps);
        }
    }

    // ---------------------------------------------------------------- layers

    public static boolean isLayerManual() {
        return manualLayer != null;
    }

    /** The shown layer: a manually chosen one, the explored cave band underground, or the surface. */
    public static LayerId currentLayer(MinecraftClient client) {
        if (client.world == null) {
            return null;
        }
        Identifier dimension = client.world.getRegistryKey().getValue();
        boolean levels = MapScanner.usesLevels(client.world);
        if (manualLayer != null && manualLayer.dimension().equals(dimension) && !(levels && manualLayer.isSurface())) {
            return manualLayer;
        }
        LayerId cave = ClientConfig.get().autoCaveLayer || levels ? ImmersiveMapClientState.scanner().activeCaveLayer() : null;
        if (cave != null) {
            return cave;
        }
        if (levels && client.player != null) {
            return LayerId.cave(dimension, MathHelper.clamp(LayerId.bandOf(client.player.getBlockY()), LayerId.MIN_BAND, LayerId.MAX_BAND));
        }
        return LayerId.surface(dimension);
    }

    /** {@code direction > 0} goes up towards the surface. */
    public static void changeLayer(MinecraftClient client, int direction) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        Identifier dimension = client.world.getRegistryKey().getValue();
        LayerId current = currentLayer(client);
        int minBand = LayerId.bandOf(client.world.getBottomY());
        int maxBand = LayerId.bandOf(client.world.getTopY() - 1);
        boolean levels = MapScanner.usesLevels(client.world);
        LayerId next;
        if (current.isSurface()) {
            next = direction > 0 ? current : LayerId.cave(dimension, MathHelper.clamp(LayerId.bandOf(player.getBlockY()), minBand, maxBand));
        } else {
            int band = current.band() + Integer.signum(direction);
            next = band > maxBand && !levels ? LayerId.surface(dimension) : LayerId.cave(dimension, MathHelper.clamp(band, minBand, maxBand));
        }
        manualLayer = next;
        player.sendMessage(layerName(client, next, false), true);
        player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.3F, direction > 0 ? 1.3F : 0.8F);
    }

    public static void resetLayer(MinecraftClient client) {
        manualLayer = null;
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("map.immersive_map.layer.auto"), true);
        }
    }

    public static Text layerName(MinecraftClient client, LayerId layer, boolean auto) {
        Text name;
        if (layer.isSurface()) {
            name = Text.translatable("map.immersive_map.layer.surface");
        } else {
            int bottom = layer.band() * LayerId.BAND_HEIGHT;
            boolean levels = client.world != null && MapScanner.usesLevels(client.world);
            name = Text.translatable(levels ? "map.immersive_map.layer.level" : "map.immersive_map.layer.cave",
                    bottom, bottom + LayerId.BAND_HEIGHT - 1);
        }
        return auto ? Text.translatable("map.immersive_map.layer.auto_suffix", name) : name;
    }

    // ---------------------------------------------------------------- markers

    public static List<Marker> markers() {
        return Collections.unmodifiableList(MARKERS);
    }

    public static boolean hasDeathMarker(Identifier dimension) {
        return deathDimension != null && !deathHidden && deathDimension.equals(dimension);
    }

    public static int deathX() {
        return deathX;
    }

    public static int deathZ() {
        return deathZ;
    }

    public static void removeDeathMarker() {
        deathHidden = true;
        stateDirty = true;
    }

    private static void recordDeath(Identifier dimension, int x, int z) {
        if (!dimension.equals(deathDimension) || x != deathX || z != deathZ) {
            deathDimension = dimension;
            deathX = x;
            deathZ = z;
            deathHidden = false;
            stateDirty = true;
        }
    }

    /** The death marker is under the cursor (and closer than any banner). */
    public static boolean isDeathHovered(MinecraftClient client, float tickDelta) {
        if (!isCursorVisible() || client.world == null) {
            return false;
        }
        double x = cursorWorldX(tickDelta);
        double z = cursorWorldZ(tickDelta);
        return deathPickDistance(client.world.getRegistryKey().getValue(), x, z) < markerPickDistance(client.world.getRegistryKey().getValue(), x, z);
    }

    private static double deathPickDistance(Identifier dimension, double x, double z) {
        if (!hasDeathMarker(dimension) || !ClientConfig.get().showDeathMarker) {
            return Double.MAX_VALUE;
        }
        double dx = deathX + 0.5 - x;
        double dz = deathZ + 0.5 - z;
        double pick = PICK_RADIUS * blocksPerPixel();
        double distance = dx * dx + dz * dz;
        return distance <= pick * pick ? distance : Double.MAX_VALUE;
    }

    private static double markerPickDistance(Identifier dimension, double x, double z) {
        Marker marker = nearestMarker(dimension, x, z);
        if (marker == null) {
            return Double.MAX_VALUE;
        }
        double dx = marker.x + 0.5 - x;
        double dz = marker.z + 0.5 - z;
        return dx * dx + dz * dz;
    }

    /** The banner under the cursor, if any. */
    public static Marker hoveredMarker(MinecraftClient client, float tickDelta) {
        if (!isCursorVisible() || client.world == null || isDeathHovered(client, tickDelta)) {
            return null;
        }
        return nearestMarker(client.world.getRegistryKey().getValue(), cursorWorldX(tickDelta), cursorWorldZ(tickDelta));
    }

    private static Marker nearestMarker(Identifier dimension, double x, double z) {
        double pick = PICK_RADIUS * blocksPerPixel();
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
        return nearest;
    }

    /** Left click in map mode: edit the banner or the death marker under the cursor, or place a new banner there. */
    public static void onMapClick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }
        Identifier dimension = client.world.getRegistryKey().getValue();
        // Map mode always shows the cursor; this also covers a click in the same frame right mouse went down.
        cursorVisible = true;
        cursorActive = System.nanoTime();
        double x = cursorWorldX(1.0F);
        double z = cursorWorldZ(1.0F);
        if (deathPickDistance(dimension, x, z) < markerPickDistance(dimension, x, z)) {
            client.setScreen(new DeathMarkerScreen(deathX, deathZ));
            return;
        }
        Marker nearest = nearestMarker(dimension, x, z);
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

    private record SavedState(double centerX, double centerZ, int scale, boolean follow, List<SavedMarker> markers,
                              String deathDimension, int deathX, int deathZ, boolean deathHidden) {
    }

    private record SavedMarker(String dimension, int x, int z, String name, String color) {
    }

    public static void loadState(Path root) {
        statePath = root.resolve("view.json");
        MARKERS.clear();
        viewInitialized = false;
        stateDirty = false;
        manualLayer = null;
        deathDimension = null;
        deathHidden = false;
        cursorX = 0.0;
        cursorZ = 0.0;
        cursorVisible = false;
        returning = false;
        lockFlashStart = 0L;
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
            if (state.deathDimension() != null) {
                deathDimension = Identifier.tryParse(state.deathDimension());
                deathX = state.deathX();
                deathZ = state.deathZ();
                deathHidden = state.deathHidden();
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
        SavedState state = new SavedState(centerX, centerZ, scale, follow, saved,
                deathDimension == null ? null : deathDimension.toString(), deathX, deathZ, deathHidden);
        try {
            Files.createDirectories(statePath.getParent());
            Files.writeString(statePath, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ImmersiveMapMod.LOGGER.warn("Could not write {}", statePath, exception);
        }
    }
}
