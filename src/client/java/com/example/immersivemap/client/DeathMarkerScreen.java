package com.example.immersivemap.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Opened by clicking the death marker on the map: shows where it is and lets you remove it. */
public final class DeathMarkerScreen extends Screen {
    private final int x;
    private final int z;

    public DeathMarkerScreen(int x, int z) {
        super(Text.translatable("screen.immersive_map.death"));
        this.x = x;
        this.z = z;
    }

    @Override
    protected void init() {
        int panelWidth = 220;
        int left = (width - panelWidth) / 2;
        int y = height / 2 - 16;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.immersive_map.death.delete"), button -> {
            MapController.removeDeathMarker();
            if (client != null && client.player != null) {
                client.player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.5F, 0.8F);
            }
            close();
        }).dimensions(left, y, panelWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(left, y + 26, panelWidth, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Screen#render draws the background itself, so the labels go on top afterwards.
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 52, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("map.immersive_map.coords", x, z),
                width / 2, height / 2 - 36, 0xA0A0A0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
