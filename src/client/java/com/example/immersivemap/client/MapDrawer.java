package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.LayerId;
import com.example.immersivemap.mixin.client.MapRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.MapDecorationsAtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the virtual map in the vanilla held-map coordinate space (0..128 with the parchment from -7 to 135), using
 * the vanilla background, render layers, decoration atlas, decoration transform and banner label style.
 */
public final class MapDrawer {
    public static final Identifier BACKGROUND_TEXTURE = Identifier.ofVanilla("textures/map/map_background_checkerboard.png");
    private static final RenderLayer BACKGROUND = RenderLayer.getText(BACKGROUND_TEXTURE);
    private static final RenderLayer CURSOR = RenderLayer.getText(ImmersiveMapMod.id("textures/map/cursor.png"));
    private static final Identifier EGG = Identifier.ofVanilla("item/spawn_egg");
    private static final Identifier EGG_OVERLAY = Identifier.ofVanilla("item/spawn_egg_overlay");
    private static final float SIZE = MapController.MAP_SIZE;
    private static final float HEAD_SIZE = 7.0F;
    private static final float MOB_SIZE = 5.5F;
    private static final float BABY_MOB_SIZE = 4.0F;
    private static final float MOB_LAYER = -0.015F;
    private static final int MOB_OUTLINE = 0x2B2118;
    private static final int HOSTILE_OUTLINE = 0x5E1A12;
    /** Faded sepia, darker than the parchment border but softer than black ink. */
    private static final int INK = 0xFF5C4733;
    private static final float TEXT_SCALE = 0.45F;
    /** Baselines inside the opaque part of the parchment border, above and below the map. */
    private static final float TOP_TEXT = -4.45F;
    private static final float BOTTOM_TEXT = 128.45F;
    /** The left thumb of the two-handed pose covers the first few pixels of the bottom border. */
    private static final float BOTTOM_LEFT_TEXT = 8.0F;
    /** Vanilla switches to the small off-limits dot this many blocks away from the map. */
    private static final double OFF_MAP_RANGE = 320.0;

    private MapDrawer() {
    }

    private record View(int originX, int originZ, int blocks) {
        float x(double worldX) {
            return (float) (worldX / blocks - originX);
        }

        float z(double worldZ) {
            return (float) (worldZ / blocks - originZ);
        }

        boolean inside(float x, float z) {
            return x >= 0 && z >= 0 && x <= SIZE && z <= SIZE;
        }
    }

    /** Matrices must already hold the vanilla first-person map transform. */
    public static void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int worldLight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        int light = config.worldLighting ? worldLight : LightmapTextureManager.MAX_LIGHT_COORDINATE;
        float tickDelta = client.getRenderTickCounter().getTickDelta(true);
        MapController.frame(client);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        quad(vertexConsumers.getBuffer(BACKGROUND), matrix, -7.0F, -7.0F, 135.0F, 135.0F, 0.0F, -1, light);

        int scale = MapController.scale();
        int blocks = 1 << scale;
        double centerX = MapController.centerX(tickDelta);
        double centerZ = MapController.centerZ(tickDelta);
        LayerId layer = MapController.currentLayer(client);
        MapTexture texture = ImmersiveMapClientState.texture();
        texture.update(ImmersiveMapClientState.store(), layer, scale, centerX, centerZ);
        quad(vertexConsumers.getBuffer(RenderLayer.getText(texture.id())), matrix, 0.0F, 0.0F, 128.0F, 128.0F, -0.01F, -1, light);

        View view = new View(texture.originX(), texture.originZ(), blocks);
        if (config.showChunkGrid && scale <= 2) {
            drawChunkGrid(vertexConsumers, matrix, view, light);
        }

        MapDecorationsAtlasManager atlas =
                ((MapRendererAccessor) client.gameRenderer.getMapRenderer()).getDecorationsAtlasManager();
        int[] depth = {0};
        Identifier dimension = client.world.getRegistryKey().getValue();

        if (config.showMobs) {
            drawMobs(client, matrices, vertexConsumers, view, layer, config, tickDelta, light);
        }

        if (config.showDeathMarker && MapController.hasDeathMarker(dimension)) {
            float x = view.x(MapController.deathX() + 0.5);
            float z = view.z(MapController.deathZ() + 0.5);
            if (view.inside(x, z)) {
                drawDecoration(matrices, vertexConsumers, atlas, MapDecorationTypes.RED_X, x, z, 0.0F, depth, light);
                if (MapController.isDeathHovered(client, tickDelta)) {
                    drawName(matrices, vertexConsumers, client.textRenderer, Text.translatable("map.immersive_map.death"), x, z, light);
                }
            }
        }

        MapController.Marker hovered = MapController.hoveredMarker(client, tickDelta);
        for (MapController.Marker marker : MapController.markers()) {
            if (!marker.dimension().equals(dimension)) {
                continue;
            }
            float x = view.x(marker.x() + 0.5);
            float z = view.z(marker.z() + 0.5);
            if (!view.inside(x, z)) {
                continue;
            }
            drawDecoration(matrices, vertexConsumers, atlas, marker.color().decorationType(), x, z, 0.0F, depth, light);
            if (!marker.name().isEmpty() && (config.showMarkerNames || marker == hovered)) {
                drawName(matrices, vertexConsumers, client.textRenderer, Text.literal(marker.name()), x, z, light);
            }
        }

        // Other players are always heads: loaded entities first, then far players reported by the server.
        if (config.showOtherPlayers) {
            Set<UUID> drawn = new HashSet<>();
            drawn.add(client.player.getUuid());
            for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
                if (other == client.player || other.isSpectator() || other.isInvisibleTo(client.player)) {
                    continue;
                }
                drawn.add(other.getUuid());
                drawHead(matrices, vertexConsumers, view, MathHelper.lerp(tickDelta, other.prevX, other.getX()),
                        MathHelper.lerp(tickDelta, other.prevZ, other.getZ()), 0.0F, other.getSkinTextures().texture(),
                        false, depth, light);
            }
            long now = System.currentTimeMillis();
            for (PlayerTracker.Remote remote : ImmersiveMapClientState.players().all()) {
                if (drawn.add(remote.id)) {
                    drawHead(matrices, vertexConsumers, view, remote.x(now), remote.z(now), 0.0F,
                            skinOf(client, remote.id), false, depth, light);
                }
            }
        }

        drawSelf(client, matrices, vertexConsumers, atlas, view, config, tickDelta, depth, light);

        float cursorAlpha = MapController.cursorAlpha();
        if (cursorAlpha > 0.0F) {
            drawCursor(vertexConsumers, matrices, view.x(MapController.cursorWorldX(tickDelta)),
                    view.z(MapController.cursorWorldZ(tickDelta)), cursorAlpha, light);
        }

        drawMargins(client, matrices, vertexConsumers, layer, scale, config, tickDelta, light);
    }

    // ---------------------------------------------------------------- players

    private static void drawSelf(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 MapDecorationsAtlasManager atlas, View view, ClientConfig config, float tickDelta,
                                 int[] depth, int light) {
        double worldX = MathHelper.lerp(tickDelta, client.player.prevX, client.player.getX());
        double worldZ = MathHelper.lerp(tickDelta, client.player.prevZ, client.player.getZ());
        float yaw = client.player.getYaw(tickDelta);
        float x = view.x(worldX);
        float z = view.z(worldZ);
        if (!view.inside(x, z)) {
            // Vanilla: the arrow becomes a dot on the border pointing back to you.
            float clampedX = MathHelper.clamp(x, 0.0F, SIZE);
            float clampedZ = MathHelper.clamp(z, 0.0F, SIZE);
            double away = Math.max(Math.abs(x - clampedX), Math.abs(z - clampedZ)) * view.blocks();
            drawDecoration(matrices, vertexConsumers, atlas,
                    away < OFF_MAP_RANGE ? MapDecorationTypes.PLAYER_OFF_MAP : MapDecorationTypes.PLAYER_OFF_LIMITS,
                    clampedX, clampedZ, 0.0F, depth, light);
            return;
        }
        if (config.playerMarker == ClientConfig.PlayerMarker.HEAD) {
            drawHead(matrices, vertexConsumers, view, worldX, worldZ, yaw, client.player.getSkinTextures().texture(),
                    true, depth, light);
            return;
        }
        float rotation;
        if (config.smoothArrowRotation) {
            rotation = yaw;
        } else {
            // Same 16-step rounding as MapState#addDecoration.
            double r = yaw < 0.0F ? yaw - 8.0 : yaw + 8.0;
            rotation = (byte) (r * 16.0 / 360.0) * 360.0F / 16.0F;
        }
        drawDecoration(matrices, vertexConsumers, atlas, MapDecorationTypes.PLAYER, x, z, rotation, depth, light);
    }

    private static Identifier skinOf(MinecraftClient client, UUID id) {
        PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(id);
        return entry != null ? entry.getSkinTextures().texture() : DefaultSkinHelper.getSkinTextures(id).texture();
    }

    /** A player's face with hat layer in a thin frame; the local player's head also gets a facing pointer. */
    private static void drawHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, View view, double worldX,
                                 double worldZ, float yaw, Identifier skin, boolean self, int[] depth, int light) {
        float x = view.x(worldX);
        float z = view.z(worldZ);
        boolean inside = view.inside(x, z);
        float size = inside ? HEAD_SIZE : HEAD_SIZE * 0.65F;
        float half = size / 2.0F;
        float px = MathHelper.clamp(x, half, SIZE - half);
        float pz = MathHelper.clamp(z, half, SIZE - half);
        int border = self ? 0xFFFFFFFF : 0xFF2B2118;

        matrices.push();
        matrices.translate(inside ? x : px, inside ? z : pz, -0.02F - depth[0]++ * 0.001F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getTextBackground());
        if (self) {
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
        float outline = half + (inside ? 0.8F : 0.6F);
        solidQuad(solid, matrix, -outline, -outline, outline, outline, -0.0003F, inside ? border : 0xC0000000 | border & 0xFFFFFF, light);
        VertexConsumer face = vertexConsumers.getBuffer(RenderLayer.getText(skin));
        skinQuad(face, matrix, half, -0.0006F, 8, 8, light);
        skinQuad(face, matrix, half + 0.3F, -0.0009F, 40, 8, light);
        matrices.pop();
    }

    // ---------------------------------------------------------------- mobs

    /**
     * Loaded mobs as heads of their own models (spawn eggs for the odd mob without one), nearest on top. Only mobs
     * on the shown level are drawn: none from caves on the surface map, and on cave maps only those near your height.
     */
    private static void drawMobs(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                 View view, LayerId layer, ClientConfig config, float tickDelta, int light) {
        boolean cave = layer != null && !layer.isSurface();
        double referenceY = cave && MapController.isLayerManual()
                ? layer.band() * LayerId.BAND_HEIGHT + LayerId.BAND_HEIGHT / 2.0
                : client.player.getY();
        boolean skyLight = client.world.getDimension().hasSkyLight();
        List<MobEntity> mobs = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof MobEntity mob) || !mob.isAlive() || mob.isInvisibleTo(client.player)) {
                continue;
            }
            boolean hostile = entity instanceof Monster;
            if (config.mobFilter == ClientConfig.MobFilter.HOSTILE && !hostile
                    || config.mobFilter == ClientConfig.MobFilter.PASSIVE && hostile) {
                continue;
            }
            if (!view.inside(view.x(entity.getX()), view.z(entity.getZ()))) {
                continue;
            }
            double dy = Math.abs(entity.getY() - referenceY);
            if (cave ? dy > 20.0 : dy > 8.0 && skyLight
                    && client.world.getLightLevel(LightType.SKY, BlockPos.ofFloored(entity.getEyePos())) == 0) {
                continue;
            }
            mobs.add(mob);
        }
        mobs.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        int count = Math.min(mobs.size(), config.maxMobIcons);
        // Farthest first: everything shares one depth, so later (nearer) mobs end up on top.
        for (int i = count - 1; i >= 0; i--) {
            MobEntity mob = mobs.get(i);
            float x = view.x(MathHelper.lerp(tickDelta, mob.prevX, mob.getX()));
            float z = view.z(MathHelper.lerp(tickDelta, mob.prevZ, mob.getZ()));
            double dy = Math.abs(mob.getY() - referenceY);
            int alpha = cave && dy > 8.0 ? (int) MathHelper.clampedLerp(255.0, 110.0, (dy - 8.0) / 12.0) : 255;
            float size = mob.isBaby() ? BABY_MOB_SIZE : MOB_SIZE;
            int outline = mob instanceof Monster ? HOSTILE_OUTLINE : MOB_OUTLINE;
            if (!MobHeads.draw(matrices, vertexConsumers, mob, x, z, size, MOB_LAYER, outline, alpha, light)) {
                drawEgg(client, matrices, vertexConsumers, mob, x, z, alpha, light);
            }
        }
    }

    private static void drawEgg(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                MobEntity mob, float x, float z, int alpha, int light) {
        SpawnEggItem egg = SpawnEggItem.forEntity(mob.getType());
        if (egg == null) {
            return;
        }
        var sprites = client.getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        Sprite base = sprites.apply(EGG);
        Sprite overlay = sprites.apply(EGG_OVERLAY);
        VertexConsumer consumer = vertexConsumers.getBuffer(MobHeads.iconLayer(base.getAtlasId()));
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        spriteQuad(consumer, matrix, base, x - 2.5F, z - 2.5F, x + 2.5F, z + 2.5F, MOB_LAYER, alpha << 24 | egg.getColor(0) & 0xFFFFFF, light);
        spriteQuad(consumer, matrix, overlay, x - 2.5F, z - 2.5F, x + 2.5F, z + 2.5F, MOB_LAYER, alpha << 24 | egg.getColor(1) & 0xFFFFFF, light);
    }

    // ---------------------------------------------------------------- overlays

    private static void drawChunkGrid(VertexConsumerProvider vertexConsumers, Matrix4f matrix, View view, int light) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getTextBackground());
        float step = 16.0F / view.blocks();
        float startX = (float) (Math.ceil(view.originX() * (double) view.blocks() / 16.0) * 16.0 / view.blocks() - view.originX());
        float startZ = (float) (Math.ceil(view.originZ() * (double) view.blocks() / 16.0) * 16.0 / view.blocks() - view.originZ());
        float w = 0.15F;
        int color = 0x38000000;
        for (float x = startX; x <= SIZE; x += step) {
            solidQuad(consumer, matrix, x - w, 0.0F, x + w, SIZE, -0.012F, color, light);
        }
        for (float z = startZ; z <= SIZE; z += step) {
            solidQuad(consumer, matrix, 0.0F, z - w, SIZE, z + w, -0.012F, color, light);
        }
    }

    /** The hotspot (top-left texel) sits on the cursor position. */
    private static void drawCursor(VertexConsumerProvider vertexConsumers, MatrixStack matrices, float x, float z, float alpha, int light) {
        float texel = 0.75F;
        int color = MathHelper.clamp((int) (alpha * 255.0F), 0, 255) << 24 | 0xFFFFFF;
        quad(vertexConsumers.getBuffer(CURSOR), matrices.peek().getPositionMatrix(),
                x - texel * 0.5F, z - texel * 0.5F, x + texel * 15.5F, z + texel * 15.5F, -0.08F, color, light);
    }

    /**
     * Ink on the parchment border: layer and scale above the map; coordinates (yours, or the cursor's while it is
     * out) bottom left and the biome bottom right.
     */
    private static void drawMargins(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                    LayerId layer, int scale, ClientConfig config, float tickDelta, int light) {
        TextRenderer text = client.textRenderer;
        boolean cursor = MapController.isCursorVisible();
        double worldX = cursor ? MapController.cursorWorldX(tickDelta) : MathHelper.lerp(tickDelta, client.player.prevX, client.player.getX());
        double worldZ = cursor ? MapController.cursorWorldZ(tickDelta) : MathHelper.lerp(tickDelta, client.player.prevZ, client.player.getZ());

        if (config.showCoordinates) {
            drawInk(matrices, vertexConsumers, text,
                    Text.translatable("map.immersive_map.coords", MathHelper.floor(worldX), MathHelper.floor(worldZ)),
                    BOTTOM_LEFT_TEXT, BOTTOM_TEXT, TEXT_SCALE, light);
        }
        if (config.showCursorBiome) {
            BlockPos pos = BlockPos.ofFloored(worldX, client.player.getY(), worldZ);
            if (client.world.isChunkLoaded(pos)) {
                RegistryEntry<Biome> biome = client.world.getBiome(pos);
                Text name = biome.getKey()
                        .map(key -> (Text) Text.translatable(key.getValue().toTranslationKey("biome")))
                        .orElse(Text.empty());
                drawInk(matrices, vertexConsumers, text, name, 127.5F - text.getWidth(name) * TEXT_SCALE, BOTTOM_TEXT, TEXT_SCALE, light);
            }
        }
        if (layer != null && (!layer.isSurface() || MapController.isLayerManual())) {
            drawInk(matrices, vertexConsumers, text, MapController.layerName(client, layer, !MapController.isLayerManual()),
                    0.5F, TOP_TEXT, TEXT_SCALE, light);
        }
        if (config.showScale) {
            Text label = Text.literal("1:" + (1 << scale));
            drawInk(matrices, vertexConsumers, text, label, 127.5F - text.getWidth(label) * TEXT_SCALE, TOP_TEXT, TEXT_SCALE, light);
        }
    }

    // ---------------------------------------------------------------- primitives

    /** Same transform as {@code MapRenderer.MapTexture#draw} uses for decorations. */
    private static void drawDecoration(MatrixStack matrices, VertexConsumerProvider vertexConsumers, MapDecorationsAtlasManager atlas,
                                       RegistryEntry<MapDecorationType> type, float x, float z, float rotation,
                                       int[] depth, int light) {
        Sprite sprite = atlas.getSprite(new MapDecoration(type, (byte) 0, (byte) 0, (byte) 0, Optional.empty()));
        matrices.push();
        matrices.translate(x, z, -0.02F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        matrices.scale(4.0F, 4.0F, 3.0F);
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

    private static void drawInk(MatrixStack matrices, VertexConsumerProvider vertexConsumers, TextRenderer textRenderer,
                                Text text, float x, float y, float scale, int light) {
        matrices.push();
        matrices.translate(x, y, -0.03F);
        matrices.scale(scale, scale, 1.0F);
        textRenderer.draw(text, 0.0F, 0.0F, INK, false, matrices.peek().getPositionMatrix(), vertexConsumers,
                TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }

    public static void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int color, int light) {
        consumer.vertex(matrix, x0, y1, z).color(color).texture(0.0F, 1.0F).light(light);
        consumer.vertex(matrix, x1, y1, z).color(color).texture(1.0F, 1.0F).light(light);
        consumer.vertex(matrix, x1, y0, z).color(color).texture(1.0F, 0.0F).light(light);
        consumer.vertex(matrix, x0, y0, z).color(color).texture(0.0F, 0.0F).light(light);
    }

    private static void solidQuad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int color, int light) {
        consumer.vertex(matrix, x0, y1, z).color(color).light(light);
        consumer.vertex(matrix, x1, y1, z).color(color).light(light);
        consumer.vertex(matrix, x1, y0, z).color(color).light(light);
        consumer.vertex(matrix, x0, y0, z).color(color).light(light);
    }

    private static void spriteQuad(VertexConsumer consumer, Matrix4f matrix, Sprite sprite, float x0, float y0, float x1, float y1,
                                   float z, int color, int light) {
        consumer.vertex(matrix, x0, y1, z).color(color).texture(sprite.getMinU(), sprite.getMaxV()).light(light);
        consumer.vertex(matrix, x1, y1, z).color(color).texture(sprite.getMaxU(), sprite.getMaxV()).light(light);
        consumer.vertex(matrix, x1, y0, z).color(color).texture(sprite.getMaxU(), sprite.getMinV()).light(light);
        consumer.vertex(matrix, x0, y0, z).color(color).texture(sprite.getMinU(), sprite.getMinV()).light(light);
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
}
