package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With the map in both hands, attacking and breaking stay vanilla (with the item in the selected slot) unless right
 * mouse is held: in map mode left click works the map instead. Right click never uses the put-away hand item.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    /** A left click went to the map: keeping the button down after letting go of right mouse must not start breaking. */
    @Unique
    private boolean immersiveMap$mapClickHeld;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$clickMap(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (MapController.isMapMode(client)) {
            if (MapController.isInteractive()) {
                MapController.onMapClick(client);
            }
            immersiveMap$mapClickHeld = true;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$blockItemUse(CallbackInfo ci) {
        if (MapController.blocksHands()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$blockBreaking(boolean breaking, CallbackInfo ci) {
        if (!breaking) {
            immersiveMap$mapClickHeld = false;
        }
        if (immersiveMap$mapClickHeld || MapController.isMapMode((MinecraftClient) (Object) this)) {
            if (interactionManager != null) {
                interactionManager.cancelBlockBreaking();
            }
            ci.cancel();
        }
    }
}
