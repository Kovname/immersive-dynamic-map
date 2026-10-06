package com.example.immersivemap;

import com.example.immersivemap.client.ClientNetworking;
import com.example.immersivemap.client.ImmersiveMapClientState;
import com.example.immersivemap.client.MapController;
import com.example.immersivemap.client.MapStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class ImmersiveMapClient implements ClientModInitializer {
    private static KeyBinding mapKey;
    private static KeyBinding handKey;
    private static KeyBinding recenterKey;

    @Override
    public void onInitializeClient() {
        mapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.immersive_map.toggle", GLFW.GLFW_KEY_M, "category.immersive_map"));
        handKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.immersive_map.hand", GLFW.GLFW_KEY_UNKNOWN, "category.immersive_map"));
        recenterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.immersive_map.follow", GLFW.GLFW_KEY_N, "category.immersive_map"));

        ClientNetworking.init();
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                ImmersiveMapClientState.scanner().onChunkLoad(chunk.getPos().x, chunk.getPos().z));

        // Runs before vanilla input handling, so the swap-hands key moves the map instead of swapping items.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (MapController.isOpen() && client.currentScreen == null) {
                while (client.options.swapHandsKey.wasPressed()) {
                    MapController.switchHands(client);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (mapKey.wasPressed()) {
                if (Screen.hasShiftDown()) {
                    MapController.switchHands(client);
                } else {
                    MapController.toggle(client);
                }
            }
            while (handKey.wasPressed()) {
                MapController.switchHands(client);
            }
            while (recenterKey.wasPressed()) {
                MapController.recenter(client);
            }

            MapController.tick(client);
            MapStore store = ImmersiveMapClientState.store();
            if (store != null) {
                store.tick();
                ImmersiveMapClientState.scanner().tick(client, store);
            }
            ClientNetworking.tick(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ImmersiveMapClientState.onDisconnect());
    }
}
