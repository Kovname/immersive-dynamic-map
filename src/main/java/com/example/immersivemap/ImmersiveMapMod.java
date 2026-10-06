package com.example.immersivemap;

import com.example.immersivemap.network.ModPayloads;
import com.example.immersivemap.server.ServerMapService;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Nothing is registered in game registries, so vanilla clients can join a server
 * running this mod and modded clients can join vanilla servers.
 */
public class ImmersiveMapMod implements ModInitializer {
    public static final String MOD_ID = "immersive_map";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Bumped whenever a payload layout changes; peers with another version are ignored. */
    public static final int PROTOCOL_VERSION = 1;

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModPayloads.register();
        ServerMapService.init();
    }
}
