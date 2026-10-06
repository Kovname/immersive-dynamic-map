package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import com.example.immersivemap.map.HoldState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Third person view of the local player. Other players' maps arrive as equipment from a server running the mod, so
 * they need nothing here.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack immersiveMap$mainHand(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack real = original.call(entity);
        if (entity != MinecraftClient.getInstance().player) {
            return real;
        }
        return MapController.currentHoldState() == HoldState.BOTH_HANDS ? MapController.mapStack() : real;
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getOffHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack immersiveMap$offHand(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack real = original.call(entity);
        if (entity != MinecraftClient.getInstance().player) {
            return real;
        }
        return switch (MapController.currentHoldState()) {
            case BOTH_HANDS -> ItemStack.EMPTY;
            case OFF_HAND -> MapController.mapStack();
            case NONE -> real;
        };
    }
}
