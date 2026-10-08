package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Map mode (right mouse held with the map in both hands): the mouse moves the map cursor instead of the camera, the
 * wheel zooms and Shift+wheel changes the layer. Otherwise the mouse is vanilla, and the wheel switches hotbar slots.
 */
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
        if (!MapController.isInteractive() || !MapController.isMapMode(client)) {
            return;
        }
        MapController.moveCursor(cursorDeltaX, cursorDeltaY);
        cursorDeltaX = 0.0D;
        cursorDeltaY = 0.0D;
        ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$zoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        // All of map mode, including the raise animation, so the wheel never falls through to the hotbar.
        if (window != client.getWindow().getHandle() || !MapController.isMapMode(client)) {
            return;
        }
        ci.cancel();
        // Same as vanilla: a horizontal-only scroll (tilt wheels, Shift+wheel on some systems) counts reversed.
        double raw = vertical != 0.0D ? vertical : -horizontal;
        if (raw == 0.0D) {
            return;
        }
        double amount = (client.options.getDiscreteMouseScroll().getValue() ? Math.signum(raw) : raw)
                * client.options.getMouseWheelSensitivity().getValue();
        MapController.scroll(client, amount, Screen.hasShiftDown());
    }
}
