package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.HeldMapPoses;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person arms go through the player model too; the third-person map pose must not reach them. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "renderArm", at = @At("HEAD"))
    private void immersiveMap$enterFirstPersonArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                  AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve,
                                                  CallbackInfo ci) {
        HeldMapPoses.setRenderingFirstPersonArm(true);
    }

    @Inject(method = "renderArm", at = @At("RETURN"))
    private void immersiveMap$leaveFirstPersonArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                  AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve,
                                                  CallbackInfo ci) {
        HeldMapPoses.setRenderingFirstPersonArm(false);
    }
}
