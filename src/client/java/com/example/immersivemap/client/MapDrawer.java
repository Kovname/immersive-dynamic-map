package com.example.immersivemap.client;

import com.example.immersivemap.map.LayerId;
import com.example.immersivemap.mixin.client.MapRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.MapDecorationsAtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the virtual map in the vanilla held-map coordinate space (0..128 with the parchment from -7 to 135),
 * using the vanilla background, render layers, decoration atlas and name label style.
 */
public final class MapDrawer {
    private static final RenderLayer BACKGROUND =
            RenderLayer.getText(Identifier.ofVanilla("textures/map/map_background_checkerboard.png"));
    private static final float SIZE = MapController.MAP_SIZE;
    private static final float HEAD_SIZE = 7.0F;
    private static final int INK = 0xFF4A3B2A;

    private MapDrawer() {
    }

    /** Matrices must already hold the vanilla first-person map transform. */
    public static void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        float tickDelta = client.getRenderTickCounter().getTickDelta(true);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer background = vertexConsumers.getBuffer(BACKGROUND);
        background.vertex(matrix, -7.0F, 135.0F, 0.0F).color(-1).texture(0.0F, 1.0F).light(light);
        background.vertex(matrix, 135.0F, 135.0F, 0.0F).color(-1).texture(1.0F, 1.0F).light(light);
        background.vertex(matrix, 135.0F, -7.0F, 0.0F).color(-1).texture(1.0F, 0.0F).light(light);
        background.vertex(matrix, -7.0F, -7.0F, 0.0F).color(-1).texture(0.0F, 0.0F).light(light);

        int scale = MapController.scale();
        int blocks = 1 << scale;
        double centerX = MapController.centerX(tickDelta);
        double centerZ = MapController.centerZ(tickDelta);
        LayerId layer = MapController.currentLayer(client);
        MapTexture texture = ImmersiveMapClientState.texture();
        texture.update(ImmersiveMapClientState.store(), layer, scale, centerX, centerZ);

        VertexConsumer map = vertexConsumers.getBuffer(RenderLayer.getText(texture.id()));
        map.vertex(matrix, 0.0F, 128.0F, -0.01F).color(-1).texture(0.0F, 1.0F).light(light);
        map.vertex(matrix, 128.0F, 128.0F, -0.01F).color(-1).texture(1.0F, 1.0F).light(light);
        map.vertex(matrix, 128.0F, 0.0F, -0.01F).color(-1).texture(1.0F, 0.0F).light(light);
        map.vertex(matrix, 0.0F, 0.0F, -0.01F).color(-1).texture(0.0F, 0.0F).light(light);

        View view = new View(texture.originX(), texture.originZ(), blocks);
        MapDecorationsAtlasManager atlas =
                ((MapRendererAccessor) client.gameRenderer.getMapRenderer()).getDecorationsAtlasManager();
        ClientConfig config = ClientConfig.get();
        int[] depth = {0};

        // Banners
        Identifier dimension = client.world.getRegistryKey().getValue();
        for (MapController.Marker marker : MapController.markers()) {
            if (!marker.dimension().equals(dimension)) {
                continue;
            }
            float x = view.x(marker.x() + 0.5);
            float z = view.z(marker.z() + 0.5);
            if (x < 0 || z < 0 || x > SIZE || z > SIZE) {
                continue;
            }
            drawDecoration(matrices, vertexConsumers, atlas, marker.color().decorationType(), x, z, 0.0F, 1.0F, depth, light);
            if (config.showMarkerNames && !marker.name().isEmpty()) {
                drawName(matrices, vertexConsumers, client.textRenderer, Text.literal(marker.name()), x, z, light);
            }
        }

        // Cursor for marker placement while the map is dragged away from the player.
        if (MapController.isInteractive() && !MapController.isFollowing()) {
            drawDecoration(matrices, vertexConsumers, atlas, MapDecorationTypes.TARGET_POINT,
                    view.x(centerX), view.z(centerZ), 0.0F, 1.0F, depth, light);
        }

        // Players: loaded entities first, then far players reported by the server. Local player on top.
        Set<UUID> drawn = new HashSet<>();
        drawn.add(client.player.getUuid());
        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
            if (other == client.player || other.isSpectator() || other.isInvisibleTo(client.player)) {
                continue;
            }
            drawn.add(other.getUuid());
            double x = MathHelper.lerp(tickDelta, other.prevX, other.getX());
            double z = MathHelper.lerp(tickDelta, other.prevZ, other.getZ());
            drawPlayer(matrices, vertexConsumers, atlas, view, x, z, other.getYaw(tickDelta),
                    other.getSkinTextures().texture(), false, config.showPlayerHeads, depth, light);
        }
        long now = System.currentTimeMillis();
        for (PlayerTracker.Remote remote : ImmersiveMapClientState.players().all()) {
            if (!drawn.add(remote.id)) {
                continue;
            }
            drawPlayer(matrices, vertexConsumers, atlas, view, remote.x(now), remote.z(now), remote.yaw(),
                    skinOf(client, remote.id), false, config.showPlayerHeads, depth, light);
        }
        double selfX = MathHelper.lerp(tickDelta, client.player.prevX, client.player.getX());
        double selfZ = MathHelper.lerp(tickDelta, client.player.prevZ, client.player.getZ());
        drawPlayer(matrices, vertexConsumers, atlas, view, selfX, selfZ, client.player.getYaw(tickDelta),
                client.player.getSkinTextures().texture(), true, config.showPlayerHeads, depth, light);

        drawMargins(matrices, vertexConsumers, client.textRenderer, layer, scale, centerX, centerZ, config, light);
    }

    private record View(int originX, int originZ, int blocks) {
        float x(double worldX) {
            return (float) (worldX / blocks - originX);
        }

        float z(double worldZ) {
            return (float) (worldZ / blocks - originZ);
        }
    }

    private static Identifier skinOf(MinecraftClient client, UUID id) {
        PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(id);
        return entry != null ? entry.getSkinTextures().texture() : DefaultSkinHelper.getSkinTextures(id).texture();
    }

    private static void drawPlayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, MapDecorationsAtlasManager atlas,
                                   View view, double worldX, double worldZ, float yaw, Identifier skin, boolean self,
                                   boolean heads, int[] depth, int light) {
        float x = view.x(worldX);
        float z = view.z(worldZ);
        boolean inside = x >= 0 && z >= 0 && x <= SIZE && z <= SIZE;
        float clampedX = MathHelper.clamp(x, 0.0F, SIZE);
        float clampedZ = MathHelper.clamp(z, 0.0F, SIZE);

        if (!heads) {
            if (inside) {
                float rotation = Math.round(yaw / 22.5F) * 22.5F;
                drawDecoration(matrices, vertexConsumers, atlas, MapDecorationTypes.PLAYER, x, z, rotation, 1.0F, depth, light);
            } else {
                float distance = Math.max(Math.abs(x - clampedX), Math.abs(z - clampedZ)) * view.blocks();
                drawDecoration(matrices, vertexConsumers, atlas,
                        distance < 320 ? MapDecorationTypes.PLAYER_OFF_MAP : MapDecorationTypes.PLAYER_OFF_LIMITS,
                        clampedX, clampedZ, 0.0F, 1.0F, depth, light);
            }
            return;
        }

        float size = inside ? HEAD_SIZE : HEAD_SIZE * 0.6F;
        int border = self ? 0xFFFFFFFF : 0xFF2B2118;
        float half = size / 2.0F;
        float px = inside ? x : MathHelper.clamp(x, half, SIZE - half);
        float pz = inside ? z : MathHelper.clamp(z, half, SIZE - half);
        float base = -0.02F - depth[0]++ * 0.001F;

        matrices.push();
        matrices.translate(px, pz, base);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getTextBackground());
        if (inside) {
            // Facing pointer in the border colour, like the tip of the vanilla arrow.
            double radians = Math.toRadians(yaw);
            float dx = (float) -Math.sin(radians);
            float dz = (float) Math.cos(radians);
            float tip = half + 3.0F;
            float back = half - 0.5F;
            float wing = 2.2F;
            solid.vertex(matrix, dx * tip, dz * tip, 0.0F).color(border).light(light);
            solid.vertex(matrix, dx * back - dz * wing, dz * back + dx * wing, 0.0F).color(border).light(light);
            solid.vertex(matrix, dx * back + dz * wing, dz * back - dx * wing, 0.0F).color(border).light(light);
            solid.vertex(matrix, dx * tip, dz * tip, 0.0F).color(border).light(light);
        }
        float outline = half + 1.0F;
        quad(solid, matrix, outline, -0.0003F, border, light);

        VertexConsumer face = vertexConsumers.getBuffer(RenderLayer.getText(skin));
        skinQuad(face, matrix, half, -0.0006F, 8, 8, light);
        skinQuad(face, matrix, half + 0.35F, -0.0009F, 40, 8, light);
        matrices.pop();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float half, float z, int color, int light) {
        consumer.vertex(matrix, -half, half, z).color(color).light(light);
        consumer.vertex(matrix, half, half, z).color(color).light(light);
        consumer.vertex(matrix, half, -half, z).color(color).light(light);
        consumer.vertex(matrix, -half, -half, z).color(color).light(light);
    }

    private static void skinQuad(VertexConsumer consumer, Matrix4f matrix, float half, float z, int u, int v, int light) {
        float u0 = u / 64.0F;
        float u1 = (u + 8) / 64.0F;
        float v0 = v / 64.0F;
        float v1 = (v + 8) / 64.0F;
        consumer.vertex(matrix, -half, half, z).color(-1).texture(u0, v1).light(light);
        consumer.vertex(matrix, half, half, z).color(-1).texture(u1, v1).light(light);
        consumer.vertex(matrix, half, -half, z).color(-1).texture(u1, v0).light(light);
        consumer.vertex(matrix, -half, -half, z).color(-1).texture(u0, v0).light(light);
    }

    /** Same transform as {@code MapRenderer.MapTexture#draw} uses for decorations. */
    private static void drawDecoration(MatrixStack matrices, VertexConsumerProvider vertexConsumers, MapDecorationsAtlasManager atlas,
                                       RegistryEntry<MapDecorationType> type, float x, float z, float rotation, float size,
                                       int[] depth, int light) {
        Sprite sprite = atlas.getSprite(new MapDecoration(type, (byte) 0, (byte) 0, (byte) 0, Optional.empty()));
        matrices.push();
        matrices.translate(x, z, -0.02F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        matrices.scale(4.0F * size, 4.0F * size, 3.0F);
        matrices.translate(-0.125F, 0.125F, 0.0F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float layer = depth[0]++ * -0.001F;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getText(sprite.getAtlasId()));
        consumer.vertex(matrix, -1.0F, 1.0F, layer).color(-1).texture(sprite.getMinU(), sprite.getMinV()).light(light);
        consumer.vertex(matrix, 1.0F, 1.0F, layer).color(-1).texture(sprite.getMaxU(), sprite.getMinV()).light(light);
        consumer.vertex(matrix, 1.0F, -1.0F, layer).color(-1).texture(sprite.getMaxU(), sprite.getMaxV()).light(light);
        consumer.vertex(matrix, -1.0F, -1.0F, layer).color(-1).texture(sprite.getMinU(), sprite.getMaxV()).light(light);
        matrices.pop();
    }

    /** Vanilla banner name label: scaled to at most 25 map pixels, on a translucent black plate. */
    private static void drawName(MatrixStack matrices, VertexConsumerProvider vertexConsumers, TextRenderer textRenderer,
                                 Text text, float x, float z, int light) {
        float width = textRenderer.getWidth(text);
        float scale = MathHelper.clamp(25.0F / width, 0.0F, 6.0F / 9.0F);
        matrices.push();
        matrices.translate(x - width * scale / 2.0F, z + 4.0F, -0.025F);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(0.0F, 0.0F, -0.1F);
        textRenderer.draw(text, 0.0F, 0.0F, -1, false, matrices.peek().getPositionMatrix(), vertexConsumers,
                TextRenderer.TextLayerType.NORMAL, Integer.MIN_VALUE, light);
        matrices.pop();
    }

    /** Coordinates, scale and cave layer are written in ink on the parchment border. */
    private static void drawMargins(MatrixStack matrices, VertexConsumerProvider vertexConsumers, TextRenderer textRenderer,
                                    LayerId layer, int scale, double centerX, double centerZ, ClientConfig config, int light) {
        float textScale = 0.5F;
        if (config.showCoordinates) {
            Text coords = Text.literal(MathHelper.floor(centerX) + ", " + MathHelper.floor(centerZ));
            drawInk(matrices, vertexConsumers, textRenderer, coords, 1.0F, 129.6F, textScale, light);
            Text ratio = Text.literal("1:" + (1 << scale));
            drawInk(matrices, vertexConsumers, textRenderer, ratio, 127.0F - textRenderer.getWidth(ratio) * textScale, 129.6F, textScale, light);
        }
        if (layer != null && !layer.isSurface()) {
            int bottom = layer.band() * LayerId.BAND_HEIGHT;
            Text label = Text.translatable("map.immersive_map.cave_layer", bottom, bottom + LayerId.BAND_HEIGHT - 1);
            drawInk(matrices, vertexConsumers, textRenderer, label, 1.0F, -5.6F, textScale, light);
        }
    }

    private static void drawInk(MatrixStack matrices, VertexConsumerProvider vertexConsumers, TextRenderer textRenderer,
                                Text text, float x, float y, float scale, int light) {
        matrices.push();
        matrices.translate(x, y, -0.03F);
        matrices.scale(scale, scale, 1.0F);
        textRenderer.draw(text, 0.0F, 0.0F, INK, false, matrices.peek().getPositionMatrix(), vertexConsumers,
                TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }
}
