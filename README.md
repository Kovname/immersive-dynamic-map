# Immersive Dynamic Map

A Fabric mod for Minecraft 1.21.1 that gives you a map you can always take out, drawn and held exactly like a vanilla filled map, but it covers the whole world you have explored.

## Features

- **Looks vanilla.** Map colors, slope shading, water depth and the checker dither use the same rules as `FilledMapItem`. All five vanilla zoom levels (1:1 to 1:16) aggregate colors the same way vanilla does. The map uses the vanilla parchment, decoration atlas and banner label style.
- **Your marker is the vanilla arrow.** It turns in 16 steps like vanilla (smooth rotation is optional). When you leave the shown area it becomes the vanilla white dot on the border, or the small dot when you are far away. You can switch your marker to your head in the settings.
- **Other players are always heads**, clamped to the border when they are outside the map. With the mod on the server, far-away players show up too.
- **Held like a real map.** `M` lowers your item and raises the map in the vanilla two-handed pose. `Shift+M`, the swap-hands key (`F`) or the number keys move it to the off hand (vanilla one-handed pose), so your main hand stays usable.
- **Visible to others.** Players holding the map raise their arms and hold a real map sheet, with both hands or in the off hand. Other modded players see this through the server; vanilla clients see a map item in the hand. An off-hand style slot next to the hotbar shows the map is in your hands.
- **RTS-style navigation.** Hold right mouse to move a map cursor. Resting it near an edge scrolls the map (Dota-style). `N` switches between following you and pinning the map in place (◇ next to the scale means pinned). The wheel or `+`/`-` zooms around the cursor.
- **Banners.** Left click places a banner under the cursor, or opens the banner you are pointing at to rename, recolor or delete it.
- **Layers.** Underground the map switches to the cave layer you are in. `Page Up`/`Page Down` or `Shift+wheel` pick a layer manually, and `Home` returns to automatic.
- **Extras.** Optional mob icons (spawn egg sprites, filterable), a vanilla red X at your last death, a chunk grid, coordinates and scale on the parchment border, and a world-lit or always-bright map.
- **Background drawing.** Loaded chunks are read under a per-tick time budget, nearby chunks are rescanned so block edits show up, and region files are compressed and saved on a background thread.
- **Experimental.** Smart cave layers show only cave floors you actually explored. The shared map merges explored chunks of every player through the server. A biome-under-cursor label is also available.

## Controls

| Key | Action |
| --- | --- |
| `M` | Take out / put away the map |
| `Shift+M`, `F` (swap hands) | Move the map between both hands and the off hand |
| `N` | Follow you / pin the map in place |
| Right mouse (hold) + move | Move the cursor; at the edge the map scrolls |
| Mouse wheel, `+` / `-` | Zoom |
| `Shift` + wheel, `Page Up` / `Page Down` | Change layer |
| `Home` | Automatic layer |
| Left click | Place a banner / edit the banner under the cursor |

All keys can be rebound in Controls.

## Server

Install the same jar on a Fabric server. It adds nothing to the game registries, so vanilla clients can still join. Settings are in `config/immersive_map_server.json`:

- `sharePlayerPositions`, `positionUpdateIntervalTicks`, `hideSneakingPlayers`
- `showHeldMapToOthers`
- `enableMapSync`, `syncDownloadBytesPerTick`, `maxUploadChunksPerSecond`

The shared map is stored in `<world>/data/immersive_map/`.

## Client settings

You can change client settings in Mod Menu (Cloth Config is bundled) or in `config/immersive_map_client.json`. Explored map data is saved per world/server in `.minecraft/immersive_map/maps/`.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. Requires Java 21, Fabric Loader 0.16.5+ and Fabric API.
