package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With the map in both hands the mouse buttons work the map: left click places banners, right drag pans. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$clickMap(CallbackInfoReturnable<Boolean> cir) {
        if (MapController.blocksHands()) {
            if (MapController.isInteractive()) {
                MapController.onMapClick((MinecraftClient) (Object) this);
            }
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
        if (MapController.blocksHands()) {
            if (interactionManager != null) {
                interactionManager.cancelBlockBreaking();
            }
            ci.cancel();
        }
    }
}
