package com.example.immersivemap.mixin;

import com.example.immersivemap.server.ServerMapService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Keeps showing the held map to other players when the real hand items change while it is out. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @WrapOperation(
            method = "sendEquipmentChanges(Ljava/util/Map;)V",
            at = @At(value = "NEW", target = "net/minecraft/network/packet/s2c/play/EntityEquipmentUpdateS2CPacket"))
    private EntityEquipmentUpdateS2CPacket immersiveMap$showHeldMap(
            int entityId,
            List<Pair<EquipmentSlot, ItemStack>> equipment,
            Operation<EntityEquipmentUpdateS2CPacket> original) {
        return original.call(entityId, ServerMapService.get().rewriteEquipment((LivingEntity) (Object) this, equipment));
    }
}
