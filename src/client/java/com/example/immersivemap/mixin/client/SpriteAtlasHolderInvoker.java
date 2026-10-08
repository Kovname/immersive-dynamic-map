package com.example.immersivemap.mixin.client;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasHolder;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Map decoration sprites by id, without registering decoration types. */
@Mixin(SpriteAtlasHolder.class)
public interface SpriteAtlasHolderInvoker {
    @Invoker("getSprite")
    Sprite immersiveMap$getSprite(Identifier id);
}
