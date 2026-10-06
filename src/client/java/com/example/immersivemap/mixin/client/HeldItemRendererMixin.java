package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import com.example.immersivemap.client.MapDrawer;
import com.example.immersivemap.map.HoldState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person: the held item is lowered, then the map is raised with the vanilla two-hand or one-hand map pose.
 * The vanilla map drawing is swapped for the dynamic map only while our call is on the stack.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private boolean immersiveMap$drawingVirtual;

    @Unique
    private boolean immersiveMap$reentry;

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                                  float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                                  VertexConsumerProvider vertexConsumers, int light);

    @Shadow
    protected abstract void renderMapInBothHands(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                 float pitch, float equipProgress, float swingProgress);

    @Shadow
    protected abstract void renderMapInOneHand(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                               float equipProgress, Arm arm, float swingProgress, ItemStack stack);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$renderVirtualMap(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                               float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (immersiveMap$reentry || player != client.player || !MapController.shouldRenderFirstPerson(client)) {
            return;
        }
        HoldState hold = MapController.renderedHold();
        Hand mapHand = hold == HoldState.BOTH_HANDS ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (hold == HoldState.OFF_HAND && hand != Hand.OFF_HAND) {
            return;
        }
        ci.cancel();
        float t = MapController.animation(tickDelta);
        if (t < 0.5F) {
            // First half of the animation: put the real item away.
            immersiveMap$reentry = true;
            try {
                renderFirstPersonItem(player, tickDelta, pitch, hand, swingProgress, item,
                        Math.max(equipProgress, t * 2.0F), matrices, vertexConsumers, light);
            } finally {
                immersiveMap$reentry = false;
            }
            return;
        }
        if (hand != mapHand) {
            return;
        }
        float lowered = 1.0F - (t - 0.5F) * 2.0F;
        matrices.push();
        immersiveMap$drawingVirtual = true;
        try {
            if (hold == HoldState.BOTH_HANDS) {
                renderMapInBothHands(matrices, vertexConsumers, light, pitch, lowered, swingProgress);
            } else {
                renderMapInOneHand(matrices, vertexConsumers, light, lowered, player.getMainArm().getOpposite(),
                        swingProgress, MapController.mapStack());
            }
        } finally {
            immersiveMap$drawingVirtual = false;
            matrices.pop();
        }
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$drawDynamicMap(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                             ItemStack stack, CallbackInfo ci) {
        if (!immersiveMap$drawingVirtual) {
            return;
        }
        ci.cancel();
        // Same transform as vanilla renderFirstPersonMap.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.scale(0.38F, 0.38F, 0.38F);
        matrices.translate(-0.5F, -0.5F, 0.0F);
        matrices.scale(0.0078125F, 0.0078125F, 0.0078125F);
        MapDrawer.draw(matrices, vertexConsumers, light);
    }
}
