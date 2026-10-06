package com.example.immersivemap;

import com.example.immersivemap.client.ClientConfig;
import com.example.immersivemap.client.ClientNetworking;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ClientConfig config = ClientConfig.get();
            ClientConfig defaults = new ClientConfig();
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.immersive_map.title"))
                    .setSavingRunnable(() -> {
                        config.save();
                        ClientNetworking.updateSync();
                    });
            ConfigEntryBuilder entries = builder.entryBuilder();

            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("config.immersive_map.general"));
            general.addEntry(entries.startBooleanToggle(option("followPlayerByDefault"), config.followPlayerByDefault)
                    .setDefaultValue(defaults.followPlayerByDefault).setSaveConsumer(v -> config.followPlayerByDefault = v).build());
            general.addEntry(entries.startIntSlider(option("defaultScale"), config.defaultScale, 0, 4)
                    .setDefaultValue(defaults.defaultScale).setSaveConsumer(v -> config.defaultScale = v).build());
            general.addEntry(entries.startBooleanToggle(option("startInOffHand"), config.startInOffHand)
                    .setDefaultValue(defaults.startInOffHand).setSaveConsumer(v -> config.startInOffHand = v).build());
            general.addEntry(entries.startBooleanToggle(option("hotbarMovesMapToOffHand"), config.hotbarMovesMapToOffHand)
                    .setDefaultValue(defaults.hotbarMovesMapToOffHand).setSaveConsumer(v -> config.hotbarMovesMapToOffHand = v).build());
            general.addEntry(entries.startBooleanToggle(option("showPlayerHeads"), config.showPlayerHeads)
                    .setDefaultValue(defaults.showPlayerHeads).setSaveConsumer(v -> config.showPlayerHeads = v).build());
            general.addEntry(entries.startBooleanToggle(option("showMarkerNames"), config.showMarkerNames)
                    .setDefaultValue(defaults.showMarkerNames).setSaveConsumer(v -> config.showMarkerNames = v).build());
            general.addEntry(entries.startBooleanToggle(option("showCoordinates"), config.showCoordinates)
                    .setDefaultValue(defaults.showCoordinates).setSaveConsumer(v -> config.showCoordinates = v).build());
            general.addEntry(entries.startBooleanToggle(option("showHeldSlot"), config.showHeldSlot)
                    .setDefaultValue(defaults.showHeldSlot).setSaveConsumer(v -> config.showHeldSlot = v).build());
            general.addEntry(entries.startFloatField(option("cursorSensitivity"), config.cursorSensitivity)
                    .setMin(0.05F).setMax(2.0F).setDefaultValue(defaults.cursorSensitivity)
                    .setSaveConsumer(v -> config.cursorSensitivity = v).build());
            general.addEntry(entries.startFloatField(option("scanBudgetMs"), config.scanBudgetMs)
                    .setMin(0.25F).setMax(8.0F).setDefaultValue(defaults.scanBudgetMs)
                    .setSaveConsumer(v -> config.scanBudgetMs = v).build());

            ConfigCategory experimental = builder.getOrCreateCategory(Text.translatable("config.immersive_map.experimental"));
            experimental.addEntry(entries.startBooleanToggle(option("smartCaveLayers"), config.smartCaveLayers)
                    .setDefaultValue(defaults.smartCaveLayers).setTooltip(tooltip("smartCaveLayers"))
                    .setSaveConsumer(v -> config.smartCaveLayers = v).build());
            experimental.addEntry(entries.startIntSlider(option("caveRevealRadius"), config.caveRevealRadius, 4, 32)
                    .setDefaultValue(defaults.caveRevealRadius).setSaveConsumer(v -> config.caveRevealRadius = v).build());
            experimental.addEntry(entries.startBooleanToggle(option("mapSync"), config.mapSync)
                    .setDefaultValue(defaults.mapSync).setTooltip(tooltip("mapSync"))
                    .setSaveConsumer(v -> config.mapSync = v).build());
            return builder.build();
        };
    }

    private static Text option(String key) {
        return Text.translatable("config.immersive_map.option." + key);
    }

    private static Text tooltip(String key) {
        return Text.translatable("config.immersive_map.option." + key + ".tooltip");
    }
}
