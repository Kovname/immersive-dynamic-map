package com.example.immersivemap.mixin;

import com.example.immersivemap.server.ServerMapService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Players who start tracking someone with the map out see it right away. */
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {
    @WrapOperation(
            method = "sendPackets",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack immersiveMap$showHeldMap(LivingEntity entity, EquipmentSlot slot, Operation<ItemStack> original) {
        return ServerMapService.get().equippedForViewers(entity, slot, original.call(entity, slot));
    }
}
