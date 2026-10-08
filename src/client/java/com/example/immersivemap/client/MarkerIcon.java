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
 * Marker icons, all taken from the vanilla map decorations atlas: vanilla decorations, plus a few in the same 8x8
 * style that this mod adds to that atlas through {@code textures/map/decorations}. Only the banner takes a dye color.
 */
public enum MarkerIcon {
    BANNER("banner", null),
    HOME("home", ImmersiveMapMod.id("home")),
    STAR("star", ImmersiveMapMod.id("star")),
    HEART("heart", ImmersiveMapMod.id("heart")),
    CHEST("chest", ImmersiveMapMod.id("chest")),
    MINE("mine", ImmersiveMapMod.id("mine")),
    FARM("farm", ImmersiveMapMod.id("farm")),
    PORTAL("portal", ImmersiveMapMod.id("portal")),
    DANGER("danger", ImmersiveMapMod.id("danger")),
    CROSS("cross", Identifier.ofVanilla("target_x")),
    POINTER("pointer", Identifier.ofVanilla("target_point")),
    RED("red", Identifier.ofVanilla("red_marker")),
    BLUE("blue", Identifier.ofVanilla("blue_marker")),
    GREEN("green", Identifier.ofVanilla("frame")),
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
        return sprite == null;
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
