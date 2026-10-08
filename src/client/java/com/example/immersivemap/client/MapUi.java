package com.example.immersivemap.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Experimental: the marker panel drawn on the held map itself and worked with the map cursor in map mode, so making
 * a marker never leaves the game view. While the name field is focused the keyboard types into it, like the chat
 * (see {@code KeyboardMixin}); Enter places or saves, Escape closes, and a click outside the sheet puts it away.
 */
public final class MapUi {
    private static final float MARGIN = 2.0F;
    private static MarkerPanel panel;
    /** The sheet lies on the half of the map away from its marker, so the marker stays in view. */
    private static boolean bottom;

    private MapUi() {
    }

    public static MarkerPanel panel() {
        return panel;
    }

    public static boolean isOpen() {
        return panel != null;
    }

    public static boolean isTyping() {
        return panel != null && panel.isTyping();
    }

    public static void open(MarkerPanel next, double anchorMapZ) {
        panel = next;
        bottom = anchorMapZ < MapController.MAP_SIZE / 2.0;
        if (panel.isTyping()) {
            releaseKeys();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.5F, 1.2F);
        }
    }

    public static void close() {
        if (panel != null) {
            MapController.holdEdgePan();
        }
        panel = null;
    }

    private static float left() {
        return (MapController.MAP_SIZE - MarkerPanel.WIDTH) / 2.0F;
    }

    private static float top() {
        return bottom ? MapController.MAP_SIZE - panel.height() - MARGIN : MARGIN;
    }

    public static boolean covers(double mapX, double mapZ) {
        return panel != null && panel.inside((float) (mapX - left()), (float) (mapZ - top()));
    }

    /** A map-mode click while the sheet is out; it always takes the click, closing when it lands outside. */
    public static boolean click(double mapX, double mapZ) {
        if (panel == null) {
            return false;
        }
        float px = (float) (mapX - left());
        float pz = (float) (mapZ - top());
        if (!panel.inside(px, pz) || panel.click(px, pz)) {
            close();
        } else if (panel.isTyping()) {
            releaseKeys();
        }
        return true;
    }

    public static boolean scroll(double mapX, double mapZ, int steps) {
        return panel != null && panel.scroll((float) (mapX - left()), (float) (mapZ - top()), steps);
    }

    /** Returns {@code true} when the key event belongs to the sheet and must not reach key bindings. */
    public static boolean handleKey(int key, int action) {
        if (panel == null) {
            return false;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (panel.isTyping()) {
            // Function keys keep working: screenshots, hiding the HUD, fullscreen.
            if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
                return false;
            }
            if (action != GLFW.GLFW_RELEASE) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    close();
                } else if (enter) {
                    confirm();
                } else {
                    panel.key(key);
                }
            }
            return true;
        }
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (action == GLFW.GLFW_PRESS && enter) {
            confirm();
            return true;
        }
        return false;
    }

    private static void confirm() {
        if (panel.confirm()) {
            close();
        }
    }

    public static void type(String chars) {
        if (panel != null) {
            panel.type(chars);
        }
    }

    public static void tick(MinecraftClient client) {
        if (panel != null && (!ClientConfig.get().markerPanelOnMap || !MapController.blocksHands() || client.world == null
                || !panel.dimension().equals(client.world.getRegistryKey().getValue()))) {
            close();
        }
    }

    public static void render(PanelCanvas canvas, double cursorMapX, double cursorMapZ, boolean cursor) {
        if (panel == null) {
            return;
        }
        MatrixStack matrices = canvas.matrices();
        matrices.push();
        matrices.translate(left(), top(), 0.0F);
        panel.render(canvas, (float) (cursorMapX - left()), (float) (cursorMapZ - top()), cursor);
        matrices.pop();
    }

    /** Typing must not leave a movement key held; mouse buttons stay, so map mode survives the click. */
    private static void releaseKeys() {
        for (KeyBinding binding : MinecraftClient.getInstance().options.allKeys) {
            if (KeyBindingHelper.getBoundKeyOf(binding).getCategory() != InputUtil.Type.MOUSE) {
                binding.setPressed(false);
            }
        }
    }
}
