package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    private static final int PLAYER_OFFHAND_SLOT_ID = 45;
    private static final int OFFHAND_SWAP_BUTTON = 40;

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$protectVirtualOffhandSlot(
            int syncId,
            int slotId,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci) {
        if (!MapController.isCompactLeftHandActive()) {
            return;
        }

        if (slotId == PLAYER_OFFHAND_SLOT_ID || actionType == SlotActionType.SWAP && button == OFFHAND_SWAP_BUTTON) {
            ci.cancel();
        }
    }
}
