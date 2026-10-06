package com.example.immersivemap;

import com.example.immersivemap.client.ClientNetworking;
import com.example.immersivemap.client.HeldMapFeatureRenderer;
import com.example.immersivemap.client.HeldMapPoses;
import com.example.immersivemap.client.ImmersiveMapClientState;
import com.example.immersivemap.client.MapController;
import com.example.immersivemap.client.MapStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.lwjgl.glfw.GLFW;

public class ImmersiveMapClient implements ClientModInitializer {
    private static final String CATEGORY = "category.immersive_map";

    @Override
    public void onInitializeClient() {
        KeyBinding mapKey = register("toggle", GLFW.GLFW_KEY_M);
        KeyBinding handKey = register("hand", GLFW.GLFW_KEY_UNKNOWN);
        KeyBinding followKey = register("follow", GLFW.GLFW_KEY_N);
        KeyBinding layerUpKey = register("layer_up", GLFW.GLFW_KEY_PAGE_UP);
        KeyBinding layerDownKey = register("layer_down", GLFW.GLFW_KEY_PAGE_DOWN);
        KeyBinding layerAutoKey = register("layer_auto", GLFW.GLFW_KEY_HOME);
        KeyBinding zoomInKey = register("zoom_in", GLFW.GLFW_KEY_EQUAL);
        KeyBinding zoomOutKey = register("zoom_out", GLFW.GLFW_KEY_MINUS);

        ClientNetworking.init();
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                ImmersiveMapClientState.scanner().onChunkLoad(chunk.getPos().x, chunk.getPos().z));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> HeldMapPoses.remove(entity.getId()));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new HeldMapFeatureRenderer(playerRenderer));
            }
        });

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
            while (followKey.wasPressed()) {
                MapController.toggleFollow(client);
            }
            while (layerUpKey.wasPressed()) {
                MapController.changeLayer(client, 1);
            }
            while (layerDownKey.wasPressed()) {
                MapController.changeLayer(client, -1);
            }
            while (layerAutoKey.wasPressed()) {
                MapController.resetLayer(client);
            }
            while (zoomInKey.wasPressed()) {
                MapController.zoom(1);
            }
            while (zoomOutKey.wasPressed()) {
                MapController.zoom(-1);
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

    private static KeyBinding register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.immersive_map." + name, key, CATEGORY));
    }
}
