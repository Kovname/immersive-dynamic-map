package com.example.immersivemap.client;

import com.example.immersivemap.map.HoldState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Third person: a real sheet of map held in front of the chest with both hands, or in the off hand, matching the
 * arm pose from {@code PlayerEntityModelMixin}.
 */
public final class HeldMapFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final RenderLayer FRONT = RenderLayer.getText(MapDrawer.BACKGROUND_TEXTURE);
    private static final RenderLayer BACK = RenderLayer.getText(Identifier.ofVanilla("textures/map/map_background.png"));
    /** Tilt of the sheet: the far edge is raised by this many radians. */
    private static final float TILT = 0.6F;

    public HeldMapFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        HoldState state = HeldMapPoses.of(player);
        if (!state.isHolding() || player.isInvisible()) {
            return;
        }
        matrices.push();
        getContextModel().body.rotate(matrices);
        float width;
        if (state == HoldState.BOTH_HANDS) {
            matrices.translate(0.0F, 0.47F, -0.5F);
            width = 0.56F;
        } else {
            boolean left = player.getMainArm().getOpposite() == Arm.LEFT;
            matrices.translate(left ? 0.3F : -0.3F, 0.55F, -0.45F);
            matrices.multiply(new org.joml.Quaternionf().rotateY(left ? -0.25F : 0.25F));
            width = 0.42F;
        }
        drawSheet(matrices.peek().getPositionMatrix(), vertexConsumers, width, light);
        matrices.pop();
    }

    private static void drawSheet(Matrix4f matrix, VertexConsumerProvider vertexConsumers, float width, int light) {
        float half = width / 2.0F;
        // Model space: +x = holder's left, -y = up, -z = forward. "Up" of the map points forward and upwards.
        Vector3f right = new Vector3f(-1.0F, 0.0F, 0.0F);
        Vector3f up = new Vector3f(0.0F, -(float) Math.sin(TILT), -(float) Math.cos(TILT));
        Vector3f normal = new Vector3f(0.0F, -(float) Math.cos(TILT), (float) Math.sin(TILT));

        sheet(vertexConsumers.getBuffer(BACK), matrix, right, up, normal, half, -0.004F, 0.0F, 1.0F, light);
        sheet(vertexConsumers.getBuffer(FRONT), matrix, right, up, normal, half, 0.002F, 0.0F, 1.0F, light);
        MapTexture texture = ImmersiveMapClientState.texture();
        sheet(vertexConsumers.getBuffer(RenderLayer.getText(texture.id())), matrix, right, up, normal,
                half * 128.0F / 142.0F, 0.005F, 0.0F, 1.0F, light);
    }

    /** Emits the quad with both windings so it shows regardless of face culling. */
    private static void sheet(VertexConsumer consumer, Matrix4f matrix, Vector3f right, Vector3f up, Vector3f normal,
                              float half, float offset, float min, float max, int light) {
        float[][] corners = {
                {-1, 1, min, min}, {1, 1, max, min}, {1, -1, max, max}, {-1, -1, min, max}
        };
        Vector3f[] positions = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            positions[i] = new Vector3f(right).mul(corners[i][0] * half)
                    .add(new Vector3f(up).mul(corners[i][1] * half))
                    .add(new Vector3f(normal).mul(offset));
        }
        for (int i = 0; i < 4; i++) {
            emit(consumer, matrix, positions[i], corners[i][2], corners[i][3], light);
        }
        for (int i = 3; i >= 0; i--) {
            emit(consumer, matrix, positions[i], corners[i][2], corners[i][3], light);
        }
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, Vector3f pos, float u, float v, int light) {
        consumer.vertex(matrix, pos.x, pos.y, pos.z).color(-1).texture(u, v).light(light);
    }
}
