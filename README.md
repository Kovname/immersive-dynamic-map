# Immersive Dynamic Map

A Fabric mod for Minecraft 1.21.1 that gives you a map you can always take out, drawn and held exactly like a vanilla filled map, but it covers the whole world you have explored.

## Features

- **Looks vanilla.** Map colors, slope shading, water depth and the checker dither use the same rules as `FilledMapItem`. All five vanilla zoom levels (1:1 to 1:16) aggregate colors the same way vanilla does. The map uses the vanilla parchment, decoration atlas and banner label style.
- **Your marker is the vanilla arrow.** It turns in 16 steps like vanilla (smooth rotation is optional). When you leave the shown area it becomes the vanilla white dot on the border, or the small dot when you are far away. You can switch your marker to your head in the settings.
- **Other players are always heads**, clamped to the border when they are outside the map. With the mod on the server, far-away players show up too.
- **Held like a real map.** `M` lowers your item and raises the map in the vanilla two-handed pose. `Shift+M`, the swap-hands key (`F`) or the number keys move it to the off hand (vanilla one-handed pose), so your main hand stays usable.
- **Visible to others.** Players holding the map raise their arms and hold a real map sheet, with both hands or in the off hand. Other modded players see this through the server; vanilla clients see a map item in the hand. An off-hand style slot next to the hotbar shows the map is in your hands.
- **Map mode on right mouse.** With the map in both hands, hold right mouse to work the map: the mouse moves a map cursor (it fades out after a few idle seconds), left click opens the marker sheet, the wheel zooms around the cursor and resting the cursor near an edge scrolls the map (Dota-style). Without right mouse the mouse is vanilla: you attack and break blocks with the item in the selected slot, and the wheel switches hotbar slots (moving the map to the off hand). With the map out, `N` switches between following you and pinning the map in place: a small padlock flashes next to the map slot, swinging shut or popping open on its long leg like the vanilla unlocked button, and the map glides back to you when it follows again. `+`/`-` zoom at any time.
- **Markers.** In map mode, left click opens the marker sheet: a small vanilla map sheet with the name written in ink, 20 icons in the vanilla map decoration style (the banner in all 16 dyes, dungeon, home, Nether portal, End portal, XP farm, trading, diamonds, farm, storage, spawner, danger, Nether fortress, End city, and the vanilla village, woodland mansion, ocean monument, jungle temple, witch hut and trial chambers icons). Pick an icon with one click or flip through them with the wheel; the new marker pulses on the map until it is placed. Clicking a marker shows its card with Edit and Delete; while editing, the marker on the map previews the change. Hovering a marker shows its name, coordinates and distance in a vanilla tooltip. Clicking the death X lets you remove it.
- **Marker sheet on the map (experimental).** The same sheet can be drawn right on the held map and worked with the map cursor, so making a marker never leaves the game view. While the name is focused the keyboard types into it like the chat (movement keys are released and wait), Enter places or saves, Esc closes, and a click outside the sheet puts it away.
- **Caves and the Nether.** Underground, and on every level of the Nether, the map is a slice at your height (like the cave modes of popular map mods) that only shows what you have actually seen: a floor is drawn when there is a line of sight to it, and rock at eye level becomes a wall (the darkest vanilla shade) when its face is in sight. Pits show their floor however deep, nearby areas fill first, and the revealed radius grows while you stand still. Data is kept in 16-block levels that follow you with a little hysteresis. The Nether has no useless noise surface any more, and the End void is a light wash instead of vanilla's grey bedrock. `Page Up`/`Page Down`, or `Shift+wheel` in map mode, pick a level manually, and `Home` returns to automatic.
- **Mobs.** Optional mob icons are the front of each mob's head, made from its own model and texture (filterable; hostile mobs get a dark red outline). The surface map skips mobs in caves, and cave maps show only mobs near your height.
- **Extras.** A vanilla red X at your last death, a chunk grid, coordinates (bottom left), biome (bottom right), layer and scale in soft ink on the parchment border, and a world-lit or always-bright map.
- **Background drawing.** Loaded chunks are read under a per-tick time budget, nearby chunks are rescanned so block edits show up, and region files are compressed and saved on a background thread.
- **Experimental.** The shared map merges explored chunks of every player through the server. A biome-under-cursor label is also available.

## Controls

| Key | Action |
| --- | --- |
| `M` | Take out / put away the map |
| `Shift+M`, `F` (swap hands) | Move the map between both hands and the off hand |
| `N` (map out) | Follow you / pin the map in place |
| Right mouse (hold) + move | Map mode: move the cursor; at the edge the map scrolls |
| Wheel in map mode, `+` / `-` | Zoom |
| `Shift` + wheel in map mode, `Page Up` / `Page Down` | Change layer |
| `Home` | Automatic layer |
| Left click in map mode | Marker sheet for a new marker / card of the marker or death mark under the cursor |
| Wheel over the marker sheet | Flip through icons (or banner dyes over the swatches) |

Outside map mode left click attacks and breaks blocks and the wheel switches hotbar slots, as in vanilla. All keys can be rebound in Controls.

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
