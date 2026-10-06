package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holding right click drags the two-handed map instead of turning the camera; the wheel zooms. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$dragMap(double timeDelta, CallbackInfo ci) {
        if (!MapController.isInteractive() || client.currentScreen != null || !client.options.useKey.isPressed()) {
            return;
        }
        MapController.pan(cursorDeltaX, cursorDeltaY);
        cursorDeltaX = 0.0D;
        cursorDeltaY = 0.0D;
        ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$zoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!MapController.isInteractive() || client.currentScreen != null || window != client.getWindow().getHandle() || vertical == 0.0D) {
            return;
        }
        MapController.zoom(vertical);
        ci.cancel();
    }
}
