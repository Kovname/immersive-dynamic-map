package com.example.immersivemap.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Flat quads and text either in the GUI or in the held map's coordinate space, so the marker panel looks the same on
 * screen and on the map. Each primitive sits a step closer than the previous one, so later ones cover earlier ones in
 * both spaces whatever order the render layers are flushed in.
 */
public final class PanelCanvas {
    private final MatrixStack matrices;
    private final VertexConsumerProvider consumers;
    private final TextRenderer font;
    private final int light;
    private final float step;
    private float z;

    private PanelCanvas(MatrixStack matrices, VertexConsumerProvider consumers, TextRenderer font, int light, float z, float step) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.font = font;
        this.light = light;
        this.z = z;
        this.step = step;
    }

    public static PanelCanvas gui(DrawContext context, TextRenderer font) {
        return new PanelCanvas(context.getMatrices(), context.getVertexConsumers(), font,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, 0.0F, 0.02F);
    }

    /** In map space smaller z is closer; steps below 0.001 would z-fight on the held map. */
    public static PanelCanvas map(MatrixStack matrices, VertexConsumerProvider consumers, TextRenderer font, int light, float z) {
        return new PanelCanvas(matrices, consumers, font, light, z, -0.001F);
    }

    public MatrixStack matrices() {
        return matrices;
    }

    private float next() {
        z += step;
        return z;
    }

    public void fill(float x0, float y0, float x1, float y1, int argb) {
        fillGradient(x0, y0, x1, y1, argb, argb);
    }

    public void fillGradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        float layer = next();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getTextBackground());
        consumer.vertex(matrix, x0, y1, layer).color(bottom).light(light);
        consumer.vertex(matrix, x1, y1, layer).color(bottom).light(light);
        consumer.vertex(matrix, x1, y0, layer).color(top).light(light);
        consumer.vertex(matrix, x0, y0, layer).color(top).light(light);
    }

    /** A rectangle outline {@code t} thick, inside the given bounds. */
    public void frame(float x0, float y0, float x1, float y1, float t, int argb) {
        float layer = next();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getTextBackground());
        quad(consumer, matrix, x0, y0, x1, y0 + t, layer, argb);
        quad(consumer, matrix, x0, y1 - t, x1, y1, layer, argb);
        quad(consumer, matrix, x0, y0 + t, x0 + t, y1 - t, layer, argb);
        quad(consumer, matrix, x1 - t, y0 + t, x1, y1 - t, layer, argb);
    }

    private void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float layer, int argb) {
        consumer.vertex(matrix, x0, y1, layer).color(argb).light(light);
        consumer.vertex(matrix, x1, y1, layer).color(argb).light(light);
        consumer.vertex(matrix, x1, y0, layer).color(argb).light(light);
        consumer.vertex(matrix, x0, y0, layer).color(argb).light(light);
    }

    public void texture(Identifier texture, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        float layer = next();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getText(texture));
        consumer.vertex(matrix, x0, y1, layer).color(argb).texture(u0, v1).light(light);
        consumer.vertex(matrix, x1, y1, layer).color(argb).texture(u1, v1).light(light);
        consumer.vertex(matrix, x1, y0, layer).color(argb).texture(u1, v0).light(light);
        consumer.vertex(matrix, x0, y0, layer).color(argb).texture(u0, v0).light(light);
    }

    public void sprite(Sprite sprite, float x, float y, float size, int argb) {
        texture(sprite.getAtlasId(), x, y, x + size, y + size, sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV(), argb);
    }

    public void text(Text text, float x, float y, float scale, int argb) {
        float layer = next();
        matrices.push();
        matrices.translate(x, y, layer);
        matrices.scale(scale, scale, 1.0F);
        font.draw(text, 0.0F, 0.0F, argb, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }

    public float width(Text text, float scale) {
        return font.getWidth(text) * scale;
    }

    public float width(String text, float scale) {
        return font.getWidth(text) * scale;
    }
}
