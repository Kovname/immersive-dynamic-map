package com.example.immersivemap.mixin.client;

import com.example.immersivemap.ImmersiveMapClient;
import com.example.immersivemap.client.MapController;
import com.example.immersivemap.config.MapConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.MapDecorationsAtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private static final RenderLayer MAP_BACKGROUND = RenderLayer.getText(Identifier.ofVanilla("textures/map/map_background.png"));

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    protected abstract void renderArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, Arm arm);

    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, Arm arm);

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void immersiveMap$renderHeldMap(
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider.Immediate vertexConsumers,
            ClientPlayerEntity player,
            int light,
            CallbackInfo ci) {
        if (!MapController.shouldRenderInHands(client) || player.isUsingSpyglass()) {
            return;
        }

        if (MapController.getHoldMode() == MapController.HoldMode.LEFT_HAND) {
            return;
        }

        ImmersiveMapClient.getManager().tickUpdate(
                client.world,
                MapController.getCenterX(),
                MapController.getCenterZ(),
                MapController.getZoomScale());

        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        float lastRenderPitch = MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float lastRenderYaw = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - lastRenderPitch) * 0.1F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - lastRenderYaw) * 0.1F));
        renderMapInBothHands(matrices, vertexConsumers, light, pitch, easedEquipProgress(tickDelta));
        matrices.pop();

        vertexConsumers.draw();
        ci.cancel();
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("RETURN"))
    private void immersiveMap$renderLeftHandOverlay(
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider.Immediate vertexConsumers,
            ClientPlayerEntity player,
            int light,
            CallbackInfo ci) {
        if (!MapController.shouldRenderInHands(client)
                || MapController.getHoldMode() != MapController.HoldMode.LEFT_HAND
                || player.isUsingSpyglass()) {
            return;
        }

        ImmersiveMapClient.getManager().tickUpdate(
                client.world,
                MapController.getCenterX(),
                MapController.getCenterZ(),
                MapController.getZoomScale());

        float lastRenderPitch = MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float lastRenderYaw = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - lastRenderPitch) * 0.04F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - lastRenderYaw) * 0.04F));
        renderMapInLeftHand(matrices, vertexConsumers, light, easedEquipProgress(tickDelta));
        matrices.pop();

        vertexConsumers.draw();
    }

    private void renderMapInBothHands(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float pitch,
            float equipProgress) {
        float angle = getMapAngle(pitch);
        matrices.translate(0.0F, 0.36F * (1.0F - equipProgress) + 0.04F + angle * -0.5F, -0.72F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle * -85.0F + 22.0F * (1.0F - equipProgress)));

        if (client.player != null && !client.player.isInvisible()) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            renderArm(matrices, vertexConsumers, light, Arm.RIGHT);
            renderArm(matrices, vertexConsumers, light, Arm.LEFT);
            matrices.pop();
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        float scale = MathHelper.clamp(config.handMapScale, 0.5F, 2.0F);
        matrices.scale(2.0F * scale, 2.0F * scale, 2.0F * scale);
        renderMapQuad(matrices, vertexConsumers, light);
    }

    private void renderMapInLeftHand(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float equipProgress) {
        matrices.push();
        Arm arm = Arm.LEFT;
        float side = -1.0F;
        float hidden = 1.0F - equipProgress;

        if (client.player != null && !client.player.isInvisible() && client.player.getOffHandStack().isEmpty()) {
            matrices.push();
            matrices.translate(side * 0.04F, -0.14F + hidden * 0.18F, -0.08F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 3.0F));
            renderArmHoldingItem(matrices, vertexConsumers, light, hidden, 0.0F, arm);
            matrices.pop();
        }

        matrices.translate(side * (0.52F + hidden * 0.18F), -0.52F + hidden * 0.52F, -0.9F + hidden * 0.12F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (-18.0F + hidden * 12.0F)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(13.0F + hidden * 22.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * (-5.0F + hidden * 8.0F)));

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        float scale = MathHelper.clamp(config.compactMapScale, 0.45F, 1.5F) * (0.62F + 0.22F * equipProgress);
        matrices.scale(scale, scale, scale);
        renderMapQuad(matrices, vertexConsumers, light);
        matrices.pop();
    }

    private float easedEquipProgress(float tickDelta) {
        float progress = MathHelper.clamp(MapController.getEquipProgress(tickDelta), 0.0F, 1.0F);
        float hidden = 1.0F - progress;
        return 1.0F - hidden * hidden * hidden;
    }

    private float getMapAngle(float pitch) {
        float angle = 1.0F - pitch / 45.0F + 0.1F;
        angle = MathHelper.clamp(angle, 0.0F, 1.0F);
        return -MathHelper.cos(angle * (float) Math.PI) * 0.5F + 0.5F;
    }

    private void renderMapQuad(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.scale(0.38F, 0.38F, 0.38F);
        matrices.translate(-0.5F, -0.5F, 0.0F);
        matrices.scale(0.0078125F, 0.0078125F, 0.0078125F);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer background = vertexConsumers.getBuffer(MAP_BACKGROUND);
        vertex(background, matrix, -7.0F, 135.0F, 0.0F, 0.0F, 1.0F, light);
        vertex(background, matrix, 135.0F, 135.0F, 0.0F, 1.0F, 1.0F, light);
        vertex(background, matrix, 135.0F, -7.0F, 0.0F, 1.0F, 0.0F, light);
        vertex(background, matrix, -7.0F, -7.0F, 0.0F, 0.0F, 0.0F, light);

        VertexConsumer map = vertexConsumers.getBuffer(RenderLayer.getText(ImmersiveMapClient.getManager().getTextureId()));
        vertex(map, matrix, 0.0F, 128.0F, -0.01F, 0.0F, 1.0F, light);
        vertex(map, matrix, 128.0F, 128.0F, -0.01F, 1.0F, 1.0F, light);
        vertex(map, matrix, 128.0F, 0.0F, -0.01F, 1.0F, 0.0F, light);
        vertex(map, matrix, 0.0F, 0.0F, -0.01F, 0.0F, 0.0F, light);

        renderDecorations(matrices, vertexConsumers, light);
    }

    private void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(matrix, x, y, z).color(Colors.WHITE).texture(u, v).light(light);
    }

    private void renderDecorations(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        int order = 0;

        if (client.player != null) {
            renderDecoration(matrices, vertexConsumers, playerDecoration(), order++, light);
        }

        MapConfig config = AutoConfig.getConfigHolder(MapConfig.class).getConfig();
        for (MapController.Marker marker : MapController.getMarkers()) {
            MapDecoration decoration = decorationForWorldPosition(
                    marker.color().decorationType(),
                    marker.x(),
                    marker.z(),
                    180.0F,
                    config.showMarkerNames && !marker.name().isBlank() ? Text.literal(marker.name()) : null,
                    false);
            if (decoration != null) {
                renderDecoration(matrices, vertexConsumers, decoration, order++, light);
            }
        }

        renderCursor(matrices, vertexConsumers, light);
    }

    private MapDecoration playerDecoration() {
        double x = client.player.getX();
        double z = client.player.getZ();
        int blocksPerPixel = MapController.getBlocksPerPixel();
        float relX = (float) ((x - (double) MapController.getCenterX()) / (double) blocksPerPixel);
        float relZ = (float) ((z - (double) MapController.getCenterZ()) / (double) blocksPerPixel);
        RegistryEntry<MapDecorationType> type = MapDecorationTypes.PLAYER;
        byte decorationX = toDecorationCoordinate(relX);
        byte decorationZ = toDecorationCoordinate(relZ);
        byte rotation;

        if (relX >= -63.0F && relZ >= -63.0F && relX <= 63.0F && relZ <= 63.0F) {
            rotation = (byte) ((int) ((client.player.getYaw() + 8.0F) * 16.0F / 360.0F));
        } else {
            type = MapDecorationTypes.PLAYER_OFF_MAP;
            decorationX = toDecorationCoordinate(MathHelper.clamp(relX, -64.0F, 63.5F));
            decorationZ = toDecorationCoordinate(MathHelper.clamp(relZ, -64.0F, 63.5F));
            rotation = 0;
        }

        return new MapDecoration(type, decorationX, decorationZ, rotation, Optional.empty());
    }

    private MapDecoration decorationForWorldPosition(
            RegistryEntry<MapDecorationType> type,
            int worldX,
            int worldZ,
            float rotation,
            Text name,
            boolean clampToEdge) {
        int blocksPerPixel = MapController.getBlocksPerPixel();
        float relX = (float) (worldX - MapController.getCenterX()) / (float) blocksPerPixel;
        float relZ = (float) (worldZ - MapController.getCenterZ()) / (float) blocksPerPixel;
        if (!clampToEdge && (relX < -63.0F || relZ < -63.0F || relX > 63.0F || relZ > 63.0F)) {
            return null;
        }

        if (clampToEdge) {
            relX = MathHelper.clamp(relX, -64.0F, 63.5F);
            relZ = MathHelper.clamp(relZ, -64.0F, 63.5F);
        }

        byte x = toDecorationCoordinate(relX);
        byte z = toDecorationCoordinate(relZ);
        byte rot = (byte) ((int) ((rotation + 8.0F) * 16.0F / 360.0F));
        return new MapDecoration(type, x, z, rot, Optional.ofNullable(name));
    }

    private void renderDecoration(MatrixStack matrices, VertexConsumerProvider vertexConsumers, MapDecoration decoration, int order, int light) {
        MapDecorationsAtlasManager atlas = ((MapRendererAccessor) client.gameRenderer.getMapRenderer()).immersiveMap$getMapDecorationsAtlasManager();
        Sprite sprite = atlas.getSprite(decoration);

        matrices.push();
        matrices.translate((float) decoration.x() / 2.0F + 64.0F, (float) decoration.z() / 2.0F + 64.0F, -0.02F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (decoration.rotation() * 360) / 16.0F));
        matrices.scale(4.0F, 4.0F, 3.0F);
        matrices.translate(-0.125F, 0.125F, 0.0F);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getText(sprite.getAtlasId()));
        buffer.vertex(matrix, -1.0F, 1.0F, (float) order * -0.001F).color(Colors.WHITE).texture(sprite.getMinU(), sprite.getMinV()).light(light);
        buffer.vertex(matrix, 1.0F, 1.0F, (float) order * -0.001F).color(Colors.WHITE).texture(sprite.getMaxU(), sprite.getMinV()).light(light);
        buffer.vertex(matrix, 1.0F, -1.0F, (float) order * -0.001F).color(Colors.WHITE).texture(sprite.getMaxU(), sprite.getMaxV()).light(light);
        buffer.vertex(matrix, -1.0F, -1.0F, (float) order * -0.001F).color(Colors.WHITE).texture(sprite.getMinU(), sprite.getMaxV()).light(light);
        matrices.pop();

        decoration.name().ifPresent(name -> renderDecorationName(matrices, vertexConsumers, name, decoration, light));
    }

    private void renderDecorationName(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Text name, MapDecoration decoration, int light) {
        TextRenderer textRenderer = client.textRenderer;
        float width = textRenderer.getWidth(name);
        float scale = MathHelper.clamp(25.0F / width, 0.0F, 6.0F / 9.0F);

        matrices.push();
        matrices.translate((float) decoration.x() / 2.0F + 64.0F - width * scale / 2.0F, (float) decoration.z() / 2.0F + 70.0F, -0.025F);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(0.0F, 0.0F, -0.1F);
        textRenderer.draw(name, 0.0F, 0.0F, Colors.WHITE, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, Integer.MIN_VALUE, light);
        matrices.pop();
    }

    private byte toDecorationCoordinate(float value) {
        int coordinate = (int) ((double) (value * 2.0F) + 0.5D);
        return (byte) MathHelper.clamp(coordinate, -128, 127);
    }

    private void renderCursor(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        int blocksPerPixel = MapController.getBlocksPerPixel();
        float x = (float) (MapController.getCursorX() - MapController.getCenterX()) / (float) blocksPerPixel + 64.0F;
        float z = (float) (MapController.getCursorZ() - MapController.getCenterZ()) / (float) blocksPerPixel + 64.0F;
        if (x < 0.0F || z < 0.0F || x > 128.0F || z > 128.0F) {
            return;
        }

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getGuiOverlay());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        drawCursorRect(buffer, matrix, x - 3.0F, z - 0.8F, x + 3.0F, z + 0.8F, 0xD0181C38);
        drawCursorRect(buffer, matrix, x - 0.8F, z - 3.0F, x + 0.8F, z + 3.0F, 0xD0181C38);
        drawCursorRect(buffer, matrix, x - 2.25F, z - 0.35F, x + 2.25F, z + 0.35F, 0xFF9EA8FF);
        drawCursorRect(buffer, matrix, x - 0.35F, z - 2.25F, x + 0.35F, z + 2.25F, 0xFF9EA8FF);
        drawCursorRect(buffer, matrix, x - 0.6F, z - 0.6F, x + 0.6F, z + 0.6F, 0xFFFFFFFF);
    }

    private void drawCursorRect(VertexConsumer buffer, Matrix4f matrix, float left, float top, float right, float bottom, int color) {
        colorVertex(buffer, matrix, left, bottom, color);
        colorVertex(buffer, matrix, right, bottom, color);
        colorVertex(buffer, matrix, right, top, color);
        colorVertex(buffer, matrix, left, top, color);
    }

    private void colorVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, int color) {
        buffer.vertex(matrix, x, y, -1.0F)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >> 24 & 255);
    }
}
