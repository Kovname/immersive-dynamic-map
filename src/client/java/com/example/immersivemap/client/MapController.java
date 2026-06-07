package com.example.immersivemap.client;

import com.example.immersivemap.config.MapConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MapController {
    private static final int MAP_EDGE_PAN_THRESHOLD = 48;
    private static final int MARKER_PICK_RADIUS = 4;
    private static final double FOLLOW_CURSOR_RETURN_SPEED = 0.18D;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final List<Marker> MARKERS = new ArrayList<>();

    private static boolean active;
    private static boolean followPlayer;
    private static int centerX;
    private static int centerZ;
    private static int cursorX;
    private static int cursorZ;
    private static int zoomScale;
    private static double cursorRemainderX;
    private static double cursorRemainderZ;
    private static float equipProgress;
    private static float previousEquipProgress;
    private static HoldMode holdMode = HoldMode.BOTH_HANDS;
    private static Marker lastClickedMarker;
    private static long lastMarkerClickMs;
    private static World lastWorld;
    private static boolean hasStoredView;
    private static boolean dirty;

    private MapController() {
    }

    public static void toggle(MinecraftClient client, boolean cycleHoldMode) {
        if (cycleHoldMode) {
            holdMode = holdMode.next();
            return;
        }

        if (active) {
            close();
            return;
        }

        if (client.player == null || client.world == null) {
            return;
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        active = true;
        lastWorld = client.world;
        followPlayer = hasStoredView ? followPlayer : config.followPlayerByDefault;
        holdMode = hasStoredView ? holdMode : config.defaultCompactLeftHand ? HoldMode.LEFT_HAND : HoldMode.BOTH_HANDS;
        zoomScale = hasStoredView ? zoomScale : MathHelper.clamp(config.defaultMapScale, 0, 4);
        equipProgress = 0.0F;
        previousEquipProgress = 0.0F;

        if (client.player.isUsingItem() && client.interactionManager != null) {
            client.interactionManager.stopUsingItem(client.player);
        }

        if (!hasStoredView) {
            BlockPos pos = client.player.getBlockPos();
            centerX = pos.getX();
            centerZ = pos.getZ();
            cursorX = centerX;
            cursorZ = centerZ;
            cursorRemainderX = 0.0D;
            cursorRemainderZ = 0.0D;
        }

        dirty = true;
    }

    public static void tick(MinecraftClient client) {
        previousEquipProgress = equipProgress;
        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        float animationSpeed = MathHelper.clamp(config.equipAnimationSpeed, 0.05F, 1.0F);
        equipProgress = active ? Math.min(1.0F, equipProgress + animationSpeed) : 0.0F;

        if (!active) {
            return;
        }

        if (client.player == null || client.world == null) {
            active = false;
            return;
        }

        if (lastWorld != client.world) {
            lastWorld = client.world;
            BlockPos pos = client.player.getBlockPos();
            centerX = pos.getX();
            centerZ = pos.getZ();
            cursorX = centerX;
            cursorZ = centerZ;
            cursorRemainderX = 0.0D;
            cursorRemainderZ = 0.0D;
            hasStoredView = false;
        }

        if (followPlayer) {
            centerX = client.player.getBlockX();
            centerZ = client.player.getBlockZ();
            if (!client.options.useKey.isPressed()) {
                returnCursorToPlayer(client);
            }
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean shouldRenderInHands(MinecraftClient client) {
        return active
                && client.player != null
                && client.world != null
                && client.currentScreen == null
                && client.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    public static HoldMode getHoldMode() {
        return holdMode;
    }

    public static float getEquipProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousEquipProgress, equipProgress);
    }

    public static boolean isInteractive() {
        return active && holdMode == HoldMode.BOTH_HANDS;
    }

    public static boolean isCompactLeftHandActive() {
        return active && holdMode == HoldMode.LEFT_HAND;
    }

    public static void close() {
        if (active) {
            active = false;
            dirty = true;
        }
    }

    public static boolean shouldAutoCloseOnHotbarChange() {
        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        return isInteractive() && config.autoCloseOnHotbarChange;
    }

    public static void toggleFollowPlayer(MinecraftClient client) {
        followPlayer = !followPlayer;
        if (followPlayer && client.player != null) {
            centerX = client.player.getBlockX();
            centerZ = client.player.getBlockZ();
        }
        dirty = true;
    }

    public static int getCenterX() {
        return centerX;
    }

    public static int getCenterZ() {
        return centerZ;
    }

    public static int getCursorX() {
        return cursorX;
    }

    public static int getCursorZ() {
        return cursorZ;
    }

    public static int getZoomScale() {
        return zoomScale;
    }

    public static int getBlocksPerPixel() {
        return 1 << zoomScale;
    }

    public static List<Marker> getMarkers() {
        return Collections.unmodifiableList(MARKERS);
    }

    public static void moveCursor(double deltaX, double deltaY) {
        if (!active) {
            return;
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        double sensitivity = MathHelper.clamp(config.cursorSensitivity, 0.01D, 0.35D);
        int blocksPerPixel = getBlocksPerPixel();
        cursorRemainderX += deltaX * sensitivity * blocksPerPixel;
        cursorRemainderZ += deltaY * sensitivity * blocksPerPixel;
        int stepX = wholeStep(cursorRemainderX);
        int stepZ = wholeStep(cursorRemainderZ);

        if (stepX == 0 && stepZ == 0) {
            return;
        }

        cursorRemainderX -= stepX;
        cursorRemainderZ -= stepZ;

        cursorX += stepX;
        cursorZ += stepZ;
        keepCursorVisible();
        dirty = true;
    }

    public static void changeZoom(double amount) {
        if (!active || amount == 0.0D) {
            return;
        }

        int direction = amount > 0.0D ? -1 : 1;
        zoomScale = MathHelper.clamp(zoomScale + direction, 0, 4);
        keepCursorVisible();
        dirty = true;
    }

    public static void handlePrimaryAction(MinecraftClient client) {
        if (!active) {
            return;
        }

        Marker marker = getHoveredMarker();
        if (marker == null) {
            MARKERS.add(new Marker(cursorX, cursorZ, "", MarkerColor.RED));
            lastClickedMarker = null;
            lastMarkerClickMs = 0L;
            dirty = true;
            return;
        }

        if (Screen.hasShiftDown()) {
            removeMarker(marker);
            return;
        }

        long now = Util.getMeasuringTimeMs();
        if (marker == lastClickedMarker && now - lastMarkerClickMs <= DOUBLE_CLICK_MS) {
            client.setScreen(new MarkerEditScreen(marker));
            lastClickedMarker = null;
            lastMarkerClickMs = 0L;
            return;
        }

        marker.setColor(marker.color().next());
        lastClickedMarker = marker;
        lastMarkerClickMs = now;
        dirty = true;
    }

    public static void removeMarker(Marker marker) {
        MARKERS.remove(marker);
        if (lastClickedMarker == marker) {
            lastClickedMarker = null;
            lastMarkerClickMs = 0L;
        }
        dirty = true;
    }

    public static Marker getHoveredMarker() {
        int radiusBlocks = MARKER_PICK_RADIUS * getBlocksPerPixel();
        for (Marker marker : MARKERS) {
            if (Math.abs(marker.x() - cursorX) <= radiusBlocks
                    && Math.abs(marker.z() - cursorZ) <= radiusBlocks) {
                return marker;
            }
        }

        return null;
    }

    public static SavedState saveState() {
        List<SavedMarker> markers = new ArrayList<>(MARKERS.size());
        for (Marker marker : MARKERS) {
            markers.add(new SavedMarker(marker.x(), marker.z(), marker.name(), marker.color().id()));
        }

        return new SavedState(centerX, centerZ, cursorX, cursorZ, zoomScale, followPlayer, holdMode.name(), markers);
    }

    public static void loadState(MinecraftClient client, SavedState state) {
        MARKERS.clear();
        lastClickedMarker = null;
        lastMarkerClickMs = 0L;
        lastWorld = client.world;
        cursorRemainderX = 0.0D;
        cursorRemainderZ = 0.0D;

        if (state == null) {
            MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
            followPlayer = config.followPlayerByDefault;
            holdMode = config.defaultCompactLeftHand ? HoldMode.LEFT_HAND : HoldMode.BOTH_HANDS;
            zoomScale = MathHelper.clamp(config.defaultMapScale, 0, 4);
            hasStoredView = false;
            if (client.player != null) {
                BlockPos pos = client.player.getBlockPos();
                centerX = pos.getX();
                centerZ = pos.getZ();
                cursorX = centerX;
                cursorZ = centerZ;
            }
            dirty = false;
            return;
        }

        centerX = state.centerX();
        centerZ = state.centerZ();
        cursorX = state.cursorX();
        cursorZ = state.cursorZ();
        zoomScale = MathHelper.clamp(state.zoomScale(), 0, 4);
        followPlayer = state.followPlayer();
        holdMode = HoldMode.fromName(state.holdMode());
        for (SavedMarker marker : state.markers()) {
            MARKERS.add(new Marker(marker.x(), marker.z(), marker.name(), MarkerColor.byId(marker.color())));
        }
        hasStoredView = true;
        dirty = false;
    }

    public static boolean consumeDirty() {
        boolean changed = dirty;
        dirty = false;
        return changed;
    }

    private static int wholeStep(double value) {
        if (Math.abs(value) < 1.0D) {
            return 0;
        }

        return (int) value;
    }

    private static void keepCursorVisible() {
        int threshold = MAP_EDGE_PAN_THRESHOLD * getBlocksPerPixel();
        int offsetX = cursorX - centerX;
        int offsetZ = cursorZ - centerZ;

        if (offsetX > threshold) {
            centerX = cursorX - threshold;
        } else if (offsetX < -threshold) {
            centerX = cursorX + threshold;
        }

        if (offsetZ > threshold) {
            centerZ = cursorZ - threshold;
        } else if (offsetZ < -threshold) {
            centerZ = cursorZ + threshold;
        }
    }

    private static void returnCursorToPlayer(MinecraftClient client) {
        int targetX = client.player.getBlockX();
        int targetZ = client.player.getBlockZ();
        int nextX = approach(cursorX, targetX, FOLLOW_CURSOR_RETURN_SPEED);
        int nextZ = approach(cursorZ, targetZ, FOLLOW_CURSOR_RETURN_SPEED);

        if (nextX == cursorX && nextZ == cursorZ) {
            cursorX = targetX;
            cursorZ = targetZ;
            cursorRemainderX = 0.0D;
            cursorRemainderZ = 0.0D;
            return;
        }

        cursorX = nextX;
        cursorZ = nextZ;
        cursorRemainderX = 0.0D;
        cursorRemainderZ = 0.0D;
    }

    private static int approach(int value, int target, double speed) {
        int delta = target - value;
        if (delta == 0) {
            return value;
        }

        int step = (int) Math.round((double) delta * speed);
        if (step == 0) {
            step = delta > 0 ? 1 : -1;
        }

        return value + step;
    }

    public enum HoldMode {
        BOTH_HANDS,
        LEFT_HAND;

        private HoldMode next() {
            return this == BOTH_HANDS ? LEFT_HAND : BOTH_HANDS;
        }

        private static HoldMode fromName(String name) {
            for (HoldMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }

            return BOTH_HANDS;
        }
    }

    public static final class Marker {
        private final int x;
        private final int z;
        private String name;
        private MarkerColor color;

        private Marker(int x, int z, String name, MarkerColor color) {
            this.x = x;
            this.z = z;
            this.name = name;
            this.color = color;
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
            dirty = true;
        }

        public MarkerColor color() {
            return color;
        }

        public void setColor(MarkerColor color) {
            this.color = color == null ? MarkerColor.RED : color;
            dirty = true;
        }
    }

    public record SavedState(
            int centerX,
            int centerZ,
            int cursorX,
            int cursorZ,
            int zoomScale,
            boolean followPlayer,
            String holdMode,
            List<SavedMarker> markers) {
    }

    public record SavedMarker(int x, int z, String name, String color) {
    }
}
