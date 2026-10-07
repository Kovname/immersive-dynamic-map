package com.example.immersivemap.client;

import com.example.immersivemap.mixin.client.AnimalModelAccessor;
import com.example.immersivemap.mixin.client.LlamaEntityModelAccessor;
import com.example.immersivemap.mixin.client.RabbitEntityModelAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.CompositeEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.LlamaEntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.model.RabbitEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Mob icons built from the mob's own model and texture: the front of its head (the whole model for mobs without
 * one, like slimes or ghasts), flattened onto the map and outlined like the vanilla player head icons.
 */
public final class MobHeads {
    private static final List<ModelPart> NONE = List.of();
    private static final Map<EntityModel<?>, List<ModelPart>> HEADS = new WeakHashMap<>();
    private static final QuadCollector COLLECTOR = new QuadCollector();
    private static final MatrixStack LOCAL = new MatrixStack();
    private static final float OUTLINE = 0.45F;
    /**
     * The text layer without depth writes: icons lie in one plane, so they are simply painted in order (outline, far
     * quads, near quads, far mobs, near mobs) and later map decorations still cover them.
     */
    private static final Function<Identifier, RenderLayer> ICON = Util.memoize(texture -> RenderLayer.of(
            "immersive_map_icon", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, VertexFormat.DrawMode.QUADS, 1536, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.TEXT_PROGRAM)
                    .texture(new RenderPhase.Texture(texture, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)));

    private MobHeads() {
    }

    public static RenderLayer iconLayer(Identifier texture) {
        return ICON.apply(texture);
    }

    /** Draws the icon centered at ({@code x}, {@code z}); returns {@code false} when the mob has no usable model. */
    public static boolean draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, LivingEntity mob, float x, float z,
                               float size, float layer, int outlineColor, int alpha, int light) {
        EntityRenderer<? super LivingEntity> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(mob);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return false;
        }
        List<ModelPart> parts = HEADS.computeIfAbsent(living.getModel(), MobHeads::headParts);
        if (parts.isEmpty()) {
            return false;
        }
        Identifier texture;
        try {
            texture = renderer.getTexture(mob);
            collect(parts, light);
        } catch (RuntimeException exception) {
            HEADS.put(living.getModel(), NONE);
            return false;
        }
        QuadCollector quads = COLLECTOR;
        if (quads.kept == 0) {
            return false;
        }
        float width = quads.maxX - quads.minX;
        float height = quads.maxY - quads.minY;
        if (width <= 0.0F || height <= 0.0F) {
            return false;
        }
        float scale = size / Math.max(width, height);
        float centerX = (quads.minX + quads.maxX) / 2.0F;
        float centerY = (quads.minY + quads.maxY) / 2.0F;
        VertexConsumer consumer = vertexConsumers.getBuffer(iconLayer(texture));
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int outline = alpha << 24 | outlineColor & 0xFFFFFF;
        quads.emit(consumer, matrix, x - OUTLINE, z, scale, centerX, centerY, layer, outline, light);
        quads.emit(consumer, matrix, x + OUTLINE, z, scale, centerX, centerY, layer, outline, light);
        quads.emit(consumer, matrix, x, z - OUTLINE, scale, centerX, centerY, layer, outline, light);
        quads.emit(consumer, matrix, x, z + OUTLINE, scale, centerX, centerY, layer, outline, light);
        quads.emit(consumer, matrix, x, z, scale, centerX, centerY, layer, alpha << 24 | 0xFFFFFF, light);
        return true;
    }

    /** Head parts of the shared renderer model, in their default pose; the model is left exactly as it was. */
    private static void collect(List<ModelPart> parts, int light) {
        List<ModelPart> all = new ArrayList<>();
        for (ModelPart part : parts) {
            part.traverse().forEach(all::add);
        }
        float[] saved = new float[all.size() * 9];
        for (int i = 0; i < all.size(); i++) {
            ModelPart part = all.get(i);
            int o = i * 9;
            saved[o] = part.pivotX;
            saved[o + 1] = part.pivotY;
            saved[o + 2] = part.pivotZ;
            saved[o + 3] = part.pitch;
            saved[o + 4] = part.yaw;
            saved[o + 5] = part.roll;
            saved[o + 6] = part.xScale;
            saved[o + 7] = part.yScale;
            saved[o + 8] = part.zScale;
            part.resetTransform();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
        COLLECTOR.reset();
        try {
            for (ModelPart part : parts) {
                part.render(LOCAL, COLLECTOR, light, OverlayTexture.DEFAULT_UV, -1);
            }
        } finally {
            for (int i = 0; i < all.size(); i++) {
                ModelPart part = all.get(i);
                int o = i * 9;
                part.pivotX = saved[o];
                part.pivotY = saved[o + 1];
                part.pivotZ = saved[o + 2];
                part.pitch = saved[o + 3];
                part.yaw = saved[o + 4];
                part.roll = saved[o + 5];
                part.xScale = saved[o + 6];
                part.yScale = saved[o + 7];
                part.zScale = saved[o + 8];
            }
        }
        COLLECTOR.finish();
    }

    private static List<ModelPart> headParts(EntityModel<?> model) {
        List<ModelPart> parts = new ArrayList<>();
        try {
            if (model instanceof ModelWithHead withHead) {
                parts.add(withHead.getHead());
                if (model instanceof BipedEntityModel<?> biped) {
                    parts.add(biped.hat);
                }
            } else if (model instanceof AnimalModel<?> animal) {
                AnimalModelAccessor accessor = (AnimalModelAccessor) animal;
                accessor.immersiveMap$headParts().forEach(parts::add);
                if (parts.isEmpty()) {
                    List<ModelPart> body = new ArrayList<>();
                    accessor.immersiveMap$bodyParts().forEach(body::add);
                    ModelPart head = findChild(body, "head");
                    if (head != null) {
                        parts.add(head);
                    } else {
                        parts.addAll(body);
                    }
                }
            } else if (model instanceof SinglePartEntityModel<?> single) {
                parts.add(single.getChild("head").orElse(single.getPart()));
            } else if (model instanceof LlamaEntityModel<?> llama) {
                parts.add(((LlamaEntityModelAccessor) llama).immersiveMap$head());
            } else if (model instanceof RabbitEntityModel<?> rabbit) {
                RabbitEntityModelAccessor accessor = (RabbitEntityModelAccessor) rabbit;
                parts.add(accessor.immersiveMap$head());
                parts.add(accessor.immersiveMap$rightEar());
                parts.add(accessor.immersiveMap$leftEar());
                parts.add(accessor.immersiveMap$nose());
            } else if (model instanceof CompositeEntityModel<?> composite) {
                composite.getParts().forEach(parts::add);
            }
        } catch (RuntimeException exception) {
            return NONE;
        }
        parts.removeIf(part -> part == null);
        return parts.isEmpty() ? NONE : List.copyOf(parts);
    }

    private static ModelPart findChild(List<ModelPart> roots, String name) {
        for (ModelPart root : roots) {
            ModelPart found = root.traverse().filter(part -> part.hasChild(name)).findFirst().map(part -> part.getChild(name)).orElse(null);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Receives the model's quads in model space (y down, the face looks towards -z), keeps the ones facing the
     * viewer and draws them flat, far ones first, so ears, snouts and hats cover the face like in 3D.
     */
    private static final class QuadCollector implements VertexConsumer {
        private float[] data = new float[64 * 4 * 5];
        private float[] depth = new float[64];
        private boolean[] front = new boolean[64];
        private int[] order = new int[64];
        private int vertices;
        private int kept;
        private float minX;
        private float minY;
        private float maxX;
        private float maxY;

        void reset() {
            vertices = 0;
            kept = 0;
        }

        @Override
        public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
            int quad = vertices >> 2;
            if (quad >= front.length) {
                int size = front.length * 2;
                data = Arrays.copyOf(data, size * 4 * 5);
                depth = Arrays.copyOf(depth, size);
                front = Arrays.copyOf(front, size);
                order = Arrays.copyOf(order, size);
            }
            int o = vertices * 5;
            data[o] = x;
            data[o + 1] = y;
            data[o + 2] = z;
            data[o + 3] = u;
            data[o + 4] = v;
            if ((vertices & 3) == 0) {
                front[quad] = normalZ < -0.25F;
            }
            vertices++;
        }

        void finish() {
            int quads = vertices >> 2;
            minX = Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;
            kept = 0;
            for (int q = 0; q < quads; q++) {
                if (!front[q]) {
                    continue;
                }
                float z = 0.0F;
                float area = 0.0F;
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * 5;
                    int n = (q * 4 + (k + 1) % 4) * 5;
                    minX = Math.min(minX, data[o]);
                    maxX = Math.max(maxX, data[o]);
                    minY = Math.min(minY, data[o + 1]);
                    maxY = Math.max(maxY, data[o + 1]);
                    z += data[o + 2];
                    area += data[o] * data[n + 1] - data[n] * data[o + 1];
                }
                if (area > 0.0F) {
                    // Same winding as the vanilla map quads, which the culling text layer expects.
                    reverse(q);
                }
                depth[q] = z;
                // Insertion sort by depth, farthest (largest z) first.
                int i = kept++;
                while (i > 0 && depth[order[i - 1]] < z) {
                    order[i] = order[i - 1];
                    i--;
                }
                order[i] = q;
            }
        }

        private void reverse(int quad) {
            int a = quad * 4 * 5 + 5;
            int b = quad * 4 * 5 + 15;
            for (int k = 0; k < 5; k++) {
                float swap = data[a + k];
                data[a + k] = data[b + k];
                data[b + k] = swap;
            }
        }

        void emit(VertexConsumer consumer, Matrix4f matrix, float x, float z, float scale, float centerX, float centerY,
                  float layer, int color, int light) {
            for (int i = 0; i < kept; i++) {
                int base = order[i] * 4 * 5;
                for (int k = 0; k < 4; k++) {
                    int o = base + k * 5;
                    consumer.vertex(matrix, x + (data[o] - centerX) * scale, z + (data[o + 1] - centerY) * scale, layer)
                            .color(color).texture(data[o + 3], data[o + 4]).light(light);
                }
            }
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }
}
