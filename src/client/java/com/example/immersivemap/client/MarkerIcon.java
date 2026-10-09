package com.example.immersivemap.client;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.mixin.client.MapRendererAccessor;
import com.example.immersivemap.mixin.client.SpriteAtlasHolderInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

/**
 * Marker icons, all from the vanilla map decorations atlas: vanilla decorations plus icons in the same 8x8 style that
 * this mod adds to that atlas through {@code textures/map/decorations}. The banner and the marker take a dye color.
 */
public enum MarkerIcon {
    BANNER("banner", null),
    /** A gray map pin, tinted with the dye. */
    MARKER("marker", ImmersiveMapMod.id("marker")),
    HOME("home", ImmersiveMapMod.id("home")),
    NETHER_PORTAL("portal", ImmersiveMapMod.id("portal")),
    END_PORTAL("end_portal", ImmersiveMapMod.id("end_portal")),
    XP_FARM("xp_farm", ImmersiveMapMod.id("xp_farm")),
    TRADING("trading", ImmersiveMapMod.id("trading")),
    DIAMONDS("diamonds", ImmersiveMapMod.id("diamonds")),
    FARM("farm", ImmersiveMapMod.id("farm")),
    STORAGE("chest", ImmersiveMapMod.id("chest")),
    SPAWNER("spawner", ImmersiveMapMod.id("spawner")),
    DANGER("danger", ImmersiveMapMod.id("danger")),
    FORTRESS("fortress", ImmersiveMapMod.id("fortress")),
    END_CITY("end_city", ImmersiveMapMod.id("end_city")),
    VILLAGE("village", Identifier.ofVanilla("plains_village")),
    MANSION("mansion", Identifier.ofVanilla("woodland_mansion")),
    MONUMENT("monument", Identifier.ofVanilla("ocean_monument")),
    TEMPLE("temple", Identifier.ofVanilla("jungle_temple")),
    WITCH_HUT("witch_hut", Identifier.ofVanilla("swamp_hut")),
    TRIAL_CHAMBERS("trial_chambers", Identifier.ofVanilla("trial_chambers"));

    public static final Identifier DEATH = Identifier.ofVanilla("red_x");
    private static final MarkerIcon[] VALUES = values();

    private final String id;
    private final Identifier sprite;

    MarkerIcon(String id, Identifier sprite) {
        this.id = id;
        this.sprite = sprite;
    }

    public String id() {
        return id;
    }

    public boolean colorable() {
        return this == BANNER || this == MARKER;
    }

    /** ARGB to draw the sprite with: the dye for the marker, plain white for every other icon. */
    public int tint(DyeColor color) {
        return this == MARKER ? 0xFF000000 | color.getEntityColor() : -1;
    }

    public Identifier spriteId(DyeColor color) {
        return sprite != null ? sprite : Identifier.ofVanilla(color.getName() + "_banner");
    }

    public Sprite sprite(DyeColor color) {
        return sprite(spriteId(color));
    }

    public Text displayName() {
        return Text.translatable("marker.immersive_map.icon." + id);
    }

    public MarkerIcon cycle(int steps) {
        return VALUES[Math.floorMod(ordinal() + steps, VALUES.length)];
    }

    /** Ids of icons that no longer exist load as the banner. */
    public static MarkerIcon byId(String id) {
        for (MarkerIcon icon : VALUES) {
            if (icon.id.equals(id)) {
                return icon;
            }
        }
        return BANNER;
    }

    public static Sprite sprite(Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        return ((SpriteAtlasHolderInvoker) ((MapRendererAccessor) client.gameRenderer.getMapRenderer()).getDecorationsAtlasManager())
                .immersiveMap$getSprite(id);
    }
}
