package com.example.immersivemap.client;

import com.example.immersivemap.map.HoldState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** Who is holding a map right now, for third-person poses: the local player and players reported by the server. */
public final class HeldMapPoses {
    private static final Int2ObjectOpenHashMap<HoldState> REMOTE = new Int2ObjectOpenHashMap<>();
    private static boolean firstPersonArm;

    private HeldMapPoses() {
    }

    public static HoldState of(Entity entity) {
        if (firstPersonArm || !(entity instanceof PlayerEntity) || !ClientConfig.get().thirdPersonPose) {
            return HoldState.NONE;
        }
        if (entity == MinecraftClient.getInstance().player) {
            return MapController.currentHoldState();
        }
        return REMOTE.getOrDefault(entity.getId(), HoldState.NONE);
    }

    /** Set while vanilla draws a first-person arm, which poses the player model like the vanilla hands expect. */
    public static void setRenderingFirstPersonArm(boolean rendering) {
        firstPersonArm = rendering;
    }

    public static void set(int entityId, HoldState state) {
        if (state.isHolding()) {
            REMOTE.put(entityId, state);
        } else {
            REMOTE.remove(entityId);
        }
    }

    public static void remove(int entityId) {
        REMOTE.remove(entityId);
    }

    public static void clear() {
        REMOTE.clear();
    }
}
