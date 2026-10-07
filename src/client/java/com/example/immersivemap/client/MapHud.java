package com.example.immersivemap.client;

import com.example.immersivemap.map.HoldState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * A vanilla off-hand style slot next to the hotbar showing that the map is in hand. In the off hand it covers the
 * real off-hand slot; with both hands it sits on the main-hand side and the selected hotbar slot is dimmed.
 * Pinning or unpinning the map briefly shows the cartography table lock on the outer side of that slot.
 */
public final class MapHud {
    private static final Identifier SLOT_LEFT = Identifier.ofVanilla("hud/hotbar_offhand_left");
    private static final Identifier SLOT_RIGHT = Identifier.ofVanilla("hud/hotbar_offhand_right");
    private static final Identifier LOCK = Identifier.ofVanilla("container/cartography_table/locked");
    private static final int LOCK_WIDTH = 10;
    private static final int LOCK_HEIGHT = 14;
    /** The top six rows of the lock sprite are the shackle; it is lifted this far while the map follows you. */
    private static final int SHACKLE_ROWS = 6;
    private static final int SHACKLE_LIFT = 2;

    private MapHud() {
    }

    public static void renderSlot(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !MapController.isOpen()) {
            return;
        }
        boolean showSlot = ClientConfig.get().showHeldSlot;
        HoldState hold = MapController.currentHoldState();
        Arm offArm = player.getMainArm().getOpposite();
        Arm side = hold == HoldState.OFF_HAND ? offArm : offArm.getOpposite();
        int center = context.getScaledWindowWidth() / 2;
        int height = context.getScaledWindowHeight();

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 200.0F);
        if (showSlot) {
            if (side == Arm.LEFT) {
                context.drawGuiTexture(SLOT_LEFT, center - 91 - 29, height - 23, 29, 24);
                context.drawItem(MapController.mapStack(), center - 91 - 26, height - 19);
            } else {
                context.drawGuiTexture(SLOT_RIGHT, center + 91, height - 23, 29, 24);
                context.drawItem(MapController.mapStack(), center + 91 + 10, height - 19);
            }
        }
        renderLock(context, side, center + (side == Arm.LEFT ? -91 : 91) + (showSlot ? (side == Arm.LEFT ? -29 : 29) : 0), height);
        if (showSlot && hold == HoldState.BOTH_HANDS) {
            int slotX = center - 91 + player.getInventory().selectedSlot * 20;
            context.getMatrices().translate(0.0F, 0.0F, 100.0F);
            context.fill(slotX + 3, height - 19, slotX + 19, height - 3, 0x90000000);
        }
        context.getMatrices().pop();
    }

    /**
     * The lock fades in next to {@code edge} (the outer edge of the map slot), its shackle snapping shut when the map
     * is pinned or springing open when it follows you again, then fades out.
     */
    private static void renderLock(DrawContext context, Arm side, int edge, int height) {
        long age = MapController.lockFlashAge();
        if (age >= MapController.LOCK_FLASH_NANOS) {
            return;
        }
        float seconds = age / 1.0E9F;
        float total = MapController.LOCK_FLASH_NANOS / 1.0E9F;
        float alpha = Math.min(1.0F, seconds / 0.08F) * MathHelper.clamp((total - seconds) / 0.35F, 0.0F, 1.0F);
        if (alpha <= 0.0F) {
            return;
        }
        float motion = MathHelper.clamp(seconds / 0.15F, 0.0F, 1.0F);
        int lift = Math.round((MapController.lockFlashLocked() ? 1.0F - motion : motion) * SHACKLE_LIFT);
        int x = side == Arm.LEFT ? edge - 3 - LOCK_WIDTH : edge + 3;
        int y = height - 5 - LOCK_HEIGHT;
        RenderSystem.enableBlend();
        context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        context.drawGuiTexture(LOCK, LOCK_WIDTH, LOCK_HEIGHT, 0, SHACKLE_ROWS, x, y + SHACKLE_ROWS, LOCK_WIDTH, LOCK_HEIGHT - SHACKLE_ROWS);
        context.drawGuiTexture(LOCK, LOCK_WIDTH, LOCK_HEIGHT, 0, 0, x, y - lift, LOCK_WIDTH, SHACKLE_ROWS);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
