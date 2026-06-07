package com.example.immersivemap.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "immersive_map")
public class MapConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int terrainUpdateIntervalTicks = 2;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 65536, max = 1048576)
    public int cachedMapPixels = 262144;

    @ConfigEntry.Gui.Tooltip
    public float handMapScale = 1.0f;

    @ConfigEntry.Gui.Tooltip
    public float compactMapScale = 0.85f;

    @ConfigEntry.Gui.Tooltip
    public float cursorSensitivity = 0.18f;

    @ConfigEntry.Gui.Tooltip
    public boolean showMarkerNames = true;

    @ConfigEntry.Gui.Tooltip
    public boolean defaultCompactLeftHand = false;

    @ConfigEntry.Gui.Tooltip
    public boolean followPlayerByDefault = false;

    @ConfigEntry.Gui.Tooltip
    public boolean autoCloseOnHotbarChange = true;

    @ConfigEntry.Gui.Tooltip
    public boolean persistMapData = true;

    @ConfigEntry.Gui.Tooltip
    public float equipAnimationSpeed = 0.22f;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
    public int defaultMapScale = 0;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 12, max = 96)
    public int surfaceRevealRadiusPixels = 64;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 8)
    public int edgeDitherPixels = 4;
}
