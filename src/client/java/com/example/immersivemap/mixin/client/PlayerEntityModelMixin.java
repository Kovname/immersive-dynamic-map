package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.HeldMapPoses;
import com.example.immersivemap.map.HoldState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arms raised to hold the map in front of the chest, like reading it. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void immersiveMap$holdMapPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                          float headYaw, float headPitch, CallbackInfo ci) {
        HoldState state = HeldMapPoses.of(entity);
        if (!state.isHolding()) {
            return;
        }
        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        float look = MathHelper.clamp(model.head.pitch, -0.6F, 0.6F) * 0.35F;
        if (state == HoldState.BOTH_HANDS) {
            pose(model.rightArm, -0.95F + look, -0.38F);
            pose(model.leftArm, -0.95F + look, 0.38F);
        } else if (entity.getMainArm().getOpposite() == Arm.LEFT) {
            pose(model.leftArm, -0.85F + look, 0.12F);
        } else {
            pose(model.rightArm, -0.85F + look, -0.12F);
        }
        model.leftSleeve.copyTransform(model.leftArm);
        model.rightSleeve.copyTransform(model.rightArm);
    }

    private static void pose(ModelPart arm, float pitch, float yaw) {
        arm.pitch = pitch;
        arm.yaw = yaw;
        arm.roll = 0.0F;
    }
}
