package com.example.immersivemap.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AnimalModel.class)
public interface AnimalModelAccessor {
    @Invoker("getHeadParts")
    Iterable<ModelPart> immersiveMap$headParts();

    @Invoker("getBodyParts")
    Iterable<ModelPart> immersiveMap$bodyParts();
}
