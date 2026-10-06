package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.HeldMapPoses;
import com.example.immersivemap.map.HoldState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hides the hand items of players holding the map; the map sheet is drawn by HeldMapFeatureRenderer. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack immersiveMap$mainHand(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack real = original.call(entity);
        // The map itself is drawn by HeldMapFeatureRenderer.
        return HeldMapPoses.of(entity) == HoldState.BOTH_HANDS ? ItemStack.EMPTY : real;
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getOffHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack immersiveMap$offHand(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack real = original.call(entity);
        return HeldMapPoses.of(entity).isHolding() ? ItemStack.EMPTY : real;
    }
}
