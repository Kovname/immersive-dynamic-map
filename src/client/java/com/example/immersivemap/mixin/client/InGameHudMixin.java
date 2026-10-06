package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void immersiveMap$renderMapSlot(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MapHud.renderSlot(context);
    }
}
