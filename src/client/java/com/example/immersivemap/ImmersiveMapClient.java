package com.example.immersivemap;

import com.example.immersivemap.client.MapController;
import com.example.immersivemap.client.MapPersistence;
import com.example.immersivemap.client.MapTextureManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class ImmersiveMapClient implements ClientModInitializer {
    
    private static MapTextureManager mapManager;
    private static KeyBinding mapKey;
    private static KeyBinding followKey;
    private static MapPersistence mapPersistence;

    @Override
    public void onInitializeClient() {
        mapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.immersive_map.toggle",
            GLFW.GLFW_KEY_M,
            "category.immersive_map"
        ));
        followKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.immersive_map.follow",
            GLFW.GLFW_KEY_N,
            "category.immersive_map"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (mapKey.wasPressed()) {
                long handle = client.getWindow().getHandle();
                boolean cycleHoldMode = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                MapController.toggle(client, cycleHoldMode);
            }

            while (followKey.wasPressed()) {
                MapController.toggleFollowPlayer(client);
            }

            getPersistence().tick(client, getManager());
            getManager().tickBackground(client.world);
            MapController.tick(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> getPersistence().flush(client, getManager()));
    }
    
    public static MapTextureManager getManager() {
        if (mapManager == null) {
            mapManager = new MapTextureManager();
        }
        return mapManager;
    }

    private static MapPersistence getPersistence() {
        if (mapPersistence == null) {
            mapPersistence = new MapPersistence();
        }
        return mapPersistence;
    }
}
