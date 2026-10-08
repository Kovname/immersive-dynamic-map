package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapUi;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Typing into the marker panel drawn on the map: while its name field is focused, keys and characters go to the
 * panel instead of key bindings, like the chat; Enter and Escape also confirm or close the panel when it is open.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$panelKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window == client.getWindow().getHandle() && client.currentScreen == null && MapUi.handleKey(key, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$panelChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (window == client.getWindow().getHandle() && client.currentScreen == null && MapUi.isTyping()) {
            MapUi.type(Character.toString(codePoint));
            ci.cancel();
        }
    }
}
