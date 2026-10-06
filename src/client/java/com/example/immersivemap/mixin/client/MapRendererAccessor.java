package com.example.immersivemap.mixin.client;

import net.minecraft.client.render.MapRenderer;
import net.minecraft.client.texture.MapDecorationsAtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MapRenderer.class)
public interface MapRendererAccessor {
    @Accessor("mapDecorationsAtlasManager")
    MapDecorationsAtlasManager getDecorationsAtlasManager();
}
