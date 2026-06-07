package com.example.immersivemap.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class MarkerEditScreen extends Screen {
    private final MapController.Marker marker;
    private TextFieldWidget nameField;
    private ButtonWidget colorButton;
    private boolean deleted;

    public MarkerEditScreen(MapController.Marker marker) {
        super(Text.translatable("screen.immersive_map.marker"));
        this.marker = marker;
    }

    @Override
    protected void init() {
        int panelWidth = 220;
        int x = (width - panelWidth) / 2;
        int y = height / 2 - 42;

        nameField = new TextFieldWidget(textRenderer, x, y, panelWidth, 20, Text.translatable("screen.immersive_map.marker.name"));
        nameField.setMaxLength(32);
        nameField.setText(marker.name());
        nameField.setFocused(true);
        addDrawableChild(nameField);
        setFocused(nameField);

        colorButton = ButtonWidget.builder(colorText(), button -> {
            marker.setColor(marker.color().next());
            button.setMessage(colorText());
        }).dimensions(x, y + 26, panelWidth, 20).build();
        addDrawableChild(colorButton);

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.immersive_map.marker.delete"), button -> {
            deleted = true;
            MapController.removeMarker(marker);
            super.close();
        }).dimensions(x, y + 52, panelWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(x, y + 78, panelWidth, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 64, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (!deleted) {
            marker.setName(nameField.getText());
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Text colorText() {
        return Text.translatable("screen.immersive_map.marker.color", Text.translatable("marker.immersive_map.color." + marker.color().id()));
    }
}
