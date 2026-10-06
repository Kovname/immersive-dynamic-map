# Immersive Dynamic Map

A Fabric mod for Minecraft 1.21.1 that gives you a map you can always take out, drawn and held exactly like a vanilla filled map, but it covers the whole world you have explored.

## Features

- **Looks vanilla.** Map colors, slope shading, water depth and the checker dither use the same rules as `FilledMapItem`. All five vanilla zoom levels (1:1 to 1:16) aggregate colors the same way vanilla does. The map uses the vanilla parchment, decoration atlas and banner label style.
- **Held like a real map.** `M` takes the map out: your current item is lowered and put away, then the map comes up in the vanilla two-handed pose. `Shift+M` or the swap-hands key (`F`) moves it to the off hand (vanilla one-handed pose) so your main hand stays usable. Number keys while holding it with both hands also move it to the off hand.
- **Held map slot.** A vanilla off-hand style slot next to the hotbar shows the map is in your hands; the stowed hotbar slot is dimmed.
- **Other players see it.** With the mod on the server, other players see the map in your hands (vanilla clients too, since it is sent as normal equipment). Nothing is added to your inventory.
- **Player heads.** Players are drawn as their skin heads with a facing pointer. Your own head has a white frame. Players outside the map are clamped to its edge.
- **Far players (server).** With the mod on the server, heads appear even when players are far outside tracking range. Sneaking, invisible, spectating and pumpkin/skull-wearing players are hidden, like the locator bar.
- **Navigation.** Hold right mouse to drag the map, use the wheel to zoom and press `N` to recenter and follow. Left click places a banner at the map center; left click a banner to rename, recolor or delete it. Coordinates and scale are written on the parchment border.
- **Background drawing.** Loaded chunks are read under a per-tick time budget, nearby chunks are rescanned so block edits show up, and region files are compressed and saved on a background thread.
- **Experimental: smart cave layers.** Underground, the map switches to a 16-block cave layer showing only cave floors you have actually been near.
- **Experimental: shared map.** With the mod on the server, explored chunks of every player who enables it are merged on the server and streamed to everyone, rate limited in both directions.

## Controls

| Key | Action |
| --- | --- |
| `M` | Take out / put away the map |
| `Shift+M`, `F` (swap hands) | Move the map between both hands and the off hand |
| `N` | Recenter the map on yourself and follow |
| Right mouse (hold) + move | Drag the map (both hands) |
| Mouse wheel | Zoom (both hands) |
| Left click | Place a banner / edit the banner under the center |

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
