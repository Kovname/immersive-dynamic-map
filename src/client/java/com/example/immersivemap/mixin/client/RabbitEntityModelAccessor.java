package com.example.immersivemap.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.RabbitEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RabbitEntityModel.class)
public interface RabbitEntityModelAccessor {
    @Accessor("head")
    ModelPart immersiveMap$head();

    @Accessor("rightEar")
    ModelPart immersiveMap$rightEar();

    @Accessor("leftEar")
    ModelPart immersiveMap$leftEar();

    @Accessor("nose")
    ModelPart immersiveMap$nose();
}
