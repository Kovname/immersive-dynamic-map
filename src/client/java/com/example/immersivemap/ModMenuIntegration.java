package com.example.immersivemap;

import com.example.immersivemap.client.ClientConfig;
import com.example.immersivemap.client.ClientNetworking;
import com.example.immersivemap.client.CaveMapper;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ClientConfig c = ClientConfig.get();
            ClientConfig d = new ClientConfig();
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.immersive_map.title"))
                    .setSavingRunnable(() -> {
                        c.save();
                        ClientNetworking.updateSync();
                    });
            ConfigEntryBuilder e = builder.entryBuilder();

            ConfigCategory holding = builder.getOrCreateCategory(Text.translatable("config.immersive_map.holding"));
            bool(holding, e, "startInOffHand", c.startInOffHand, d.startInOffHand, v -> c.startInOffHand = v);
            bool(holding, e, "hotbarMovesMapToOffHand", c.hotbarMovesMapToOffHand, d.hotbarMovesMapToOffHand, v -> c.hotbarMovesMapToOffHand = v);
            bool(holding, e, "showHeldSlot", c.showHeldSlot, d.showHeldSlot, v -> c.showHeldSlot = v);
            bool(holding, e, "thirdPersonPose", c.thirdPersonPose, d.thirdPersonPose, v -> c.thirdPersonPose = v);

            ConfigCategory view = builder.getOrCreateCategory(Text.translatable("config.immersive_map.view"));
            bool(view, e, "followPlayerByDefault", c.followPlayerByDefault, d.followPlayerByDefault, v -> c.followPlayerByDefault = v);
            view.addEntry(e.startIntSlider(option("defaultScale"), c.defaultScale, 0, 4)
                    .setDefaultValue(d.defaultScale).setTextGetter(v -> Text.literal("1:" + (1 << v)))
                    .setSaveConsumer(v -> c.defaultScale = v).build());
            bool(view, e, "zoomAroundCursor", c.zoomAroundCursor, d.zoomAroundCursor, v -> c.zoomAroundCursor = v);
            bool(view, e, "worldLighting", c.worldLighting, d.worldLighting, v -> c.worldLighting = v);
            view.addEntry(e.startFloatField(option("cursorSensitivity"), c.cursorSensitivity)
                    .setMin(0.05F).setMax(2.0F).setDefaultValue(d.cursorSensitivity)
                    .setSaveConsumer(v -> c.cursorSensitivity = v).build());
            view.addEntry(e.startFloatField(option("cursorHideSeconds"), c.cursorHideSeconds)
                    .setMin(1.0F).setMax(15.0F).setDefaultValue(d.cursorHideSeconds).setTooltip(tooltip("cursorHideSeconds"))
                    .setSaveConsumer(v -> c.cursorHideSeconds = v).build());
            view.addEntry(e.startIntSlider(option("edgePanZone"), c.edgePanZone, 2, 40)
                    .setDefaultValue(d.edgePanZone).setTooltip(tooltip("edgePanZone"))
                    .setSaveConsumer(v -> c.edgePanZone = v).build());
            view.addEntry(e.startIntSlider(option("edgePanSpeed"), c.edgePanSpeed, 10, 400)
                    .setDefaultValue(d.edgePanSpeed).setSaveConsumer(v -> c.edgePanSpeed = v).build());

            ConfigCategory contents = builder.getOrCreateCategory(Text.translatable("config.immersive_map.contents"));
            contents.addEntry(e.startEnumSelector(option("playerMarker"), ClientConfig.PlayerMarker.class, c.playerMarker)
                    .setDefaultValue(d.playerMarker)
                    .setEnumNameProvider(v -> Text.translatable("config.immersive_map.playerMarker." + v.name().toLowerCase()))
                    .setSaveConsumer(v -> c.playerMarker = v).build());
            bool(contents, e, "smoothArrowRotation", c.smoothArrowRotation, d.smoothArrowRotation, v -> c.smoothArrowRotation = v);
            bool(contents, e, "showOtherPlayers", c.showOtherPlayers, d.showOtherPlayers, v -> c.showOtherPlayers = v);
            bool(contents, e, "showMobs", c.showMobs, d.showMobs, v -> c.showMobs = v);
            contents.addEntry(e.startEnumSelector(option("mobFilter"), ClientConfig.MobFilter.class, c.mobFilter)
                    .setDefaultValue(d.mobFilter)
                    .setEnumNameProvider(v -> Text.translatable("config.immersive_map.mobFilter." + v.name().toLowerCase()))
                    .setSaveConsumer(v -> c.mobFilter = v).build());
            contents.addEntry(e.startIntSlider(option("maxMobIcons"), c.maxMobIcons, 1, 256)
                    .setDefaultValue(d.maxMobIcons).setSaveConsumer(v -> c.maxMobIcons = v).build());
            bool(contents, e, "showMarkerNames", c.showMarkerNames, d.showMarkerNames, v -> c.showMarkerNames = v);
            bool(contents, e, "showDeathMarker", c.showDeathMarker, d.showDeathMarker, v -> c.showDeathMarker = v);
            bool(contents, e, "showChunkGrid", c.showChunkGrid, d.showChunkGrid, v -> c.showChunkGrid = v);
            bool(contents, e, "showCoordinates", c.showCoordinates, d.showCoordinates, v -> c.showCoordinates = v);
            bool(contents, e, "showScale", c.showScale, d.showScale, v -> c.showScale = v);

            ConfigCategory caves = builder.getOrCreateCategory(Text.translatable("config.immersive_map.caves"));
            bool(caves, e, "smartCaveLayers", c.smartCaveLayers, d.smartCaveLayers, v -> c.smartCaveLayers = v);
            bool(caves, e, "autoCaveLayer", c.autoCaveLayer, d.autoCaveLayer, v -> c.autoCaveLayer = v);
            caves.addEntry(e.startIntSlider(option("caveViewDistance"), c.caveViewDistance, CaveMapper.MIN_DISTANCE, CaveMapper.MAX_DISTANCE)
                    .setDefaultValue(d.caveViewDistance).setTooltip(tooltip("caveViewDistance"))
                    .setSaveConsumer(v -> c.caveViewDistance = v).build());

            ConfigCategory performance = builder.getOrCreateCategory(Text.translatable("config.immersive_map.performance"));
            performance.addEntry(e.startFloatField(option("scanBudgetMs"), c.scanBudgetMs)
                    .setMin(0.25F).setMax(8.0F).setDefaultValue(d.scanBudgetMs).setTooltip(tooltip("scanBudgetMs"))
                    .setSaveConsumer(v -> c.scanBudgetMs = v).build());

            ConfigCategory experimental = builder.getOrCreateCategory(Text.translatable("config.immersive_map.experimental"));
            bool(experimental, e, "mapSync", c.mapSync, d.mapSync, v -> c.mapSync = v);
            bool(experimental, e, "showCursorBiome", c.showCursorBiome, d.showCursorBiome, v -> c.showCursorBiome = v);
            return builder.build();
        };
    }

    private static void bool(ConfigCategory category, ConfigEntryBuilder e, String key, boolean value, boolean def, Consumer<Boolean> save) {
        AbstractConfigListEntry<Boolean> entry = e.startBooleanToggle(option(key), value)
                .setDefaultValue(def).setTooltip(tooltip(key)).setSaveConsumer(save).build();
        category.addEntry(entry);
    }

    private static Text option(String key) {
        return Text.translatable("config.immersive_map.option." + key);
    }

    private static Text tooltip(String key) {
        return Text.translatable("config.immersive_map.option." + key + ".tooltip");
    }
}
