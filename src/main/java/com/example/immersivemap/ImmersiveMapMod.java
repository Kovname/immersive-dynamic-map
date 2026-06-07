package com.example.immersivemap;

import com.example.immersivemap.config.MapConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImmersiveMapMod implements ModInitializer {
    public static final String MOD_ID = "immersive_map";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Item LEGACY_HANDHELD_MAP = new LegacyMapItem(new Item.Settings().maxCount(1));
    
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Immersive Dynamic Map");
        AutoConfig.register(MapConfig.class, GsonConfigSerializer::new);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "handheld_map"), LEGACY_HANDHELD_MAP);
    }
}
