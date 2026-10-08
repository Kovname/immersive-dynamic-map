package com.example.immersivemap.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

/** The marker sheet held up close: the same {@link MarkerPanel} as on the map, scaled up and worked with the mouse. */
public final class MarkerScreen extends Screen {
    private final MarkerPanel panel;
    private int scale = 1;
    private int left;
    private int top;

    public MarkerScreen(MarkerPanel panel) {
        super(panel.title());
        this.panel = panel;
    }

    public MarkerPanel panel() {
        return panel;
    }

    /** Whole GUI pixels per panel unit, sized for the tallest sheet so it does not jump between views. */
    private void layout() {
        scale = Math.max(1, Math.min(4, Math.min((width - 24) / MarkerPanel.WIDTH, (height - 24) / MarkerPanel.MAX_HEIGHT)));
        left = (width - MarkerPanel.WIDTH * scale) / 2;
        top = (height - panel.height() * scale) / 2;
    }

    private float panelX(double mouseX) {
        return (float) ((mouseX - left) / scale);
    }

    private float panelY(double mouseY) {
        return (float) ((mouseY - top) / scale);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        layout();
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(left, top, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        panel.render(PanelCanvas.gui(context, textRenderer), panelX(mouseX), panelY(mouseY), true);
        matrices.pop();
        context.draw();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        layout();
        float px = panelX(mouseX);
        float py = panelY(mouseY);
        if (!panel.inside(px, py) || panel.click(px, py)) {
            close();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0) {
            layout();
            panel.scroll(panelX(mouseX), panelY(mouseY), verticalAmount > 0.0 ? 1 : -1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (panel.confirm()) {
                close();
            }
            return true;
        }
        return panel.key(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (panel.isTyping()) {
            panel.type(String.valueOf(chr));
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
