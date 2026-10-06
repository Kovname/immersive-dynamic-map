package com.example.immersivemap.client;

import com.example.immersivemap.map.HoldState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

/**
 * A vanilla off-hand style slot next to the hotbar showing that the map is in hand. In the off hand it covers the
 * real off-hand slot; with both hands it sits on the main-hand side and the selected hotbar slot is dimmed.
 */
public final class MapHud {
    private static final Identifier SLOT_LEFT = Identifier.ofVanilla("hud/hotbar_offhand_left");
    private static final Identifier SLOT_RIGHT = Identifier.ofVanilla("hud/hotbar_offhand_right");

    private MapHud() {
    }

    public static void renderSlot(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !MapController.isOpen() || !ClientConfig.get().showHeldSlot) {
            return;
        }
        HoldState hold = MapController.currentHoldState();
        Arm offArm = player.getMainArm().getOpposite();
        Arm side = hold == HoldState.OFF_HAND ? offArm : offArm.getOpposite();
        int center = context.getScaledWindowWidth() / 2;
        int height = context.getScaledWindowHeight();

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 200.0F);
        if (side == Arm.LEFT) {
            context.drawGuiTexture(SLOT_LEFT, center - 91 - 29, height - 23, 29, 24);
            context.drawItem(MapController.mapStack(), center - 91 - 26, height - 19);
        } else {
            context.drawGuiTexture(SLOT_RIGHT, center + 91, height - 23, 29, 24);
            context.drawItem(MapController.mapStack(), center + 91 + 10, height - 19);
        }
        if (hold == HoldState.BOTH_HANDS) {
            int slotX = center - 91 + player.getInventory().selectedSlot * 20;
            context.getMatrices().translate(0.0F, 0.0F, 100.0F);
            context.fill(slotX + 3, height - 19, slotX + 19, height - 3, 0x90000000);
        }
        context.getMatrices().pop();
    }
}
