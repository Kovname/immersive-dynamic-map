package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    public GameOptions options;

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$handleMapInputs(CallbackInfo ci) {
        if (MapController.shouldAutoCloseOnHotbarChange()) {
            for (int slot = 0; slot < options.hotbarKeys.length; slot++) {
                if (options.hotbarKeys[slot].isPressed()) {
                    MapController.close();
                    return;
                }
            }
        }

        if (MapController.isCompactLeftHandActive()) {
            while (options.swapHandsKey.wasPressed()) {
            }
        }

        if (!MapController.isInteractive()) {
            return;
        }

        for (int slot = 0; slot < options.hotbarKeys.length; slot++) {
            while (options.hotbarKeys[slot].wasPressed()) {
            }
        }

        while (options.attackKey.wasPressed()) {
            MapController.handlePrimaryAction((MinecraftClient) (Object) this);
        }

        while (options.useKey.wasPressed()) {
        }

        while (options.pickItemKey.wasPressed()) {
        }

        while (options.swapHandsKey.wasPressed()) {
        }

        while (options.dropKey.wasPressed()) {
        }

        ci.cancel();
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$cancelAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MapController.isInteractive()) {
            MapController.handlePrimaryAction((MinecraftClient) (Object) this);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$cancelItemUse(CallbackInfo ci) {
        if (MapController.isInteractive()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$cancelBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (MapController.isInteractive()) {
            ci.cancel();
        }
    }
}
