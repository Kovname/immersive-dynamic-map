# Immersive Dynamic Map

Immersive Dynamic Map is a Fabric client-side Minecraft mod that gives the player an always-available handheld map without adding a real inventory item. The map is opened with a hotkey, rendered in first person, and designed to stay close to vanilla Minecraft's held map presentation.

## Features

- Always-available map opened by keybind, not an inventory item.
- Full two-hand map mode with vanilla-style map frame and map decorations.
- Compact left-hand mode that leaves the right hand usable.
- Real-time terrain rendering from already loaded chunks.
- Vanilla-style player and off-map player icons.
- Banner-style markers using vanilla map decoration sprites.
- Marker names, colors, editing, and deletion.
- Pixel-dithered unexplored edges over the vanilla map parchment background.
- Zoom in and out with the mouse wheel while using the interactive map.
- Optional follow-player mode.
- Per-world client-side persistence for view state, markers, and discovered map pixels.
- Mod Menu integration through Cloth Config.

## Controls

Default keybinds:

- `M`: open or close the map.
- `Shift + M`: switch between two-hand map and compact left-hand map.
- `N`: toggle follow-player mode.
- Mouse wheel: zoom the interactive map.
- Hold right mouse button: move the cursor on the interactive map.
- Left click empty map: place a marker.
- Left click marker: cycle marker color.
- Double click marker: edit marker name and color.
- Shift + left click marker: delete marker.
- Hotbar keys `1`-`9`: optionally close the map and switch slots normally.

Keybinds can be changed in Minecraft's Controls screen. Visual and behavior settings are available through Mod Menu.

## Mod Menu Settings

The config screen exposes:

- terrain update interval;
- cached map pixel limit;
- full hand-map scale;
- compact left-hand map scale;
- cursor sensitivity;
- marker name visibility;
- compact-left default mode;
- follow-player default mode;
- close-on-hotbar-change behavior;
- per-world map persistence;
- equip animation speed;
- default map zoom;
- reveal radius;
- pixel edge dither width.

## Requirements

- Minecraft `1.21.1`
- Fabric Loader `0.16.5+`
- Fabric API
- Java `21`
- Cloth Config
- Mod Menu

Cloth Config is bundled into the mod jar by the Gradle build. Mod Menu is declared as a mod dependency for the config screen integration during development.

## Build

```powershell
.\gradlew.bat build
```

The built jar is written to:

```text
build/libs/immersive-dynamic-map-1.0.0.jar
```

## Development Run

```powershell
.\gradlew.bat runClient
```

Development world, logs, and generated runtime files are written under `run/`. This directory is ignored by git.

## Persistence

Map data is stored client-side per world or server dimension under:

```text
run/config/immersive_map/maps/
```

In a normal Minecraft instance this maps to that instance's `config/immersive_map/maps/` directory. Saved data includes map center, cursor position, zoom, follow mode, hold mode, markers, and discovered terrain pixels.

## Project Layout

```text
src/main/java/com/example/immersivemap/
  config/MapConfig.java
  ImmersiveMapMod.java
  LegacyMapItem.java

src/client/java/com/example/immersivemap/
  ImmersiveMapClient.java
  ModMenuIntegration.java
  client/
  mixin/client/

src/main/resources/
  fabric.mod.json
  immersive_map.mixins.json
  immersive_map.client.mixins.json
  assets/immersive_map/
```

## Notes

The legacy `immersive_map:handheld_map` item remains registered only to avoid breaking old saves that may still contain that item id. It is not the active map mechanic and is removed from player inventories by the mod.

## License

MIT
