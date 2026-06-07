package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin<T extends LivingEntity, M extends EntityModel<T> & ModelWithArms> extends FeatureRenderer<T, M> {
    private static final ItemStack IMMERSIVE_MAP_STACK = new ItemStack(Items.FILLED_MAP);

    protected HeldItemFeatureRendererMixin(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Shadow
    protected abstract void renderItem(
            LivingEntity entity,
            ItemStack stack,
            ModelTransformationMode transformationMode,
            Arm arm,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light);

    @Inject(method = "render", at = @At("TAIL"))
    private void immersiveMap$renderVirtualHeldMap(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            T livingEntity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != livingEntity || !MapController.isRendering() || MapController.getEquipProgress(1.0F) <= 0.0F) {
            return;
        }

        if (MapController.getHoldMode() == MapController.HoldMode.LEFT_HAND) {
            if (!livingEntity.getOffHandStack().isEmpty()
                    || livingEntity.getMainHandStack().isOf(Items.FILLED_MAP)
                    || livingEntity.getMainHandStack().isOf(Items.MAP)) {
                return;
            }

            renderVirtualMap(matrices, vertexConsumers, light, livingEntity, Arm.LEFT);
            return;
        }

        if (livingEntity.getMainHandStack().isEmpty() && livingEntity.getOffHandStack().isEmpty()) {
            renderVirtualMap(matrices, vertexConsumers, light, livingEntity, livingEntity.getMainArm());
        }
    }

    private void renderVirtualMap(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T livingEntity, Arm arm) {
        matrices.push();
        if (getContextModel().child) {
            matrices.translate(0.0F, 0.75F, 0.0F);
            matrices.scale(0.5F, 0.5F, 0.5F);
        }

        ModelTransformationMode mode = arm == Arm.RIGHT
                ? ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                : ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
        renderItem(livingEntity, IMMERSIVE_MAP_STACK, mode, arm, matrices, vertexConsumers, light);
        matrices.pop();
    }
}
