package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
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
 * Pinning or unpinning the map briefly shows a padlock on the outer side of that slot.
 */
public final class MapHud {
    private static final Identifier SLOT_LEFT = Identifier.ofVanilla("hud/hotbar_offhand_left");
    private static final Identifier SLOT_RIGHT = Identifier.ofVanilla("hud/hotbar_offhand_right");
    /**
     * Padlock frames from shut to open, LOCK_WIDTH x LOCK_HEIGHT each. Opening, the shackle pops up and turns on its
     * long leg like the vanilla unlocked button; the first row swings right, the second left, always away from the slot.
     */
    private static final Identifier LOCK = ImmersiveMapMod.id("hud/map_lock");
    private static final int LOCK_FRAMES = 6;
    private static final int LOCK_WIDTH = 17;
    private static final int LOCK_HEIGHT = 19;
    private static final int LOCK_GAP = 2;
    private static final float LOCK_HOLD = 0.12F;
    private static final float LOCK_TURN = 0.24F;

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
     * The padlock fades in next to {@code edge} (the outer edge of the map slot), swings shut when the map is pinned
     * or pops open when it follows you again, then fades out.
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
        // The previous state stays up for a moment so the change reads.
        float motion = MathHelper.clamp((seconds - LOCK_HOLD) / LOCK_TURN, 0.0F, 1.0F);
        float open = MapController.lockFlashLocked() ? 1.0F - motion : motion;
        int frame = Math.min(LOCK_FRAMES - 1, (int) (open * LOCK_FRAMES));
        boolean left = side == Arm.LEFT;
        int x = left ? edge - LOCK_GAP - LOCK_WIDTH : edge + LOCK_GAP;
        int y = height - 21;
        RenderSystem.enableBlend();
        context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        context.drawGuiTexture(LOCK, LOCK_WIDTH * LOCK_FRAMES, LOCK_HEIGHT * 2, frame * LOCK_WIDTH, left ? LOCK_HEIGHT : 0,
                x, y, LOCK_WIDTH, LOCK_HEIGHT);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
