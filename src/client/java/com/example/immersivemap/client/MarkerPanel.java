package com.example.immersivemap.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.Sprite;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * The marker sheet: a small vanilla map sheet with the icon picker, dye colors and the name written in ink. The same
 * panel is shown up close by {@link MarkerScreen} or drawn on the held map itself by {@link MapUi}, where the map
 * cursor works it. Units are map pixels; the screen scales them up.
 */
public final class MarkerPanel {
    public enum Kind { NEW, VIEW, EDIT, DEATH }

    private enum Target { NONE, FIELD, ICON, COLOR, TITLE, SECONDARY, PRIMARY }

    private record Box(float x0, float y0, float x1, float y1) {
        boolean contains(float x, float y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    public static final int WIDTH = 120;
    public static final int MAX_HEIGHT = 72;
    private static final int SHORT = 46;
    /** The vanilla map background is 128 texels with a 6-texel torn border; one texel per unit. */
    private static final int TEXTURE = 128;
    private static final int BORDER = 6;
    private static final float TEXT = 0.5F;
    private static final float LINE = 0.5F;
    private static final int RIGHT = WIDTH - 8;
    private static final Box FIELD = new Box(8.0F, 8.0F, RIGHT, 17.0F);
    private static final int COLUMNS = 10;
    private static final int CELL = 10;
    private static final int GRID_X = 10;
    private static final int GRID_Y = 19;
    private static final float CAPTION_Y = 41.5F;
    private static final int SWATCH = 6;
    private static final int SWATCH_X = 12;
    private static final int SWATCH_Y = 47;
    private static final float BUTTON_HEIGHT = 8.0F;
    private static final int INK = 0xFF3F2E1C;
    private static final int INK_SOFT = 0xFF7A6446;
    private static final int FADED = 0xFF9A8462;
    private static final int WASH = 0x50FFFFFF;
    private static final int SHADE = 0x28402C14;
    private static final int SELECTION = 0x50402C14;
    private static final int SHADOW = 0x38000000;
    private static final MarkerIcon[] ICONS = MarkerIcon.values();
    /** Vanilla creative inventory order. */
    private static final DyeColor[] DYES = {
            DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK, DyeColor.BROWN, DyeColor.RED,
            DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
            DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK
    };

    private final MapController.Marker marker;
    private final Identifier dimension;
    private final int x;
    private final int z;
    /** The screen keeps the name field focused while editing; on the map it takes a click, so keys keep moving you. */
    private final boolean focusOnEdit;
    private final TextEdit name = new TextEdit(32);
    private Kind kind;
    private MarkerIcon icon;
    private DyeColor color;
    private boolean typing;
    private long caretStart;

    private MarkerPanel(Kind kind, MapController.Marker marker, Identifier dimension, int x, int z, MarkerIcon icon,
                        DyeColor color, String name, boolean focusOnEdit) {
        this.kind = kind;
        this.marker = marker;
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.icon = icon;
        this.color = color;
        this.focusOnEdit = focusOnEdit;
        this.name.set(name);
    }

    /** A new marker at the given block, with the last used icon; typing starts right away. */
    public static MarkerPanel create(Identifier dimension, int x, int z, boolean focusOnEdit) {
        MarkerPanel panel = new MarkerPanel(Kind.NEW, null, dimension, x, z, MapController.lastIcon(),
                MapController.lastColor(), "", focusOnEdit);
        panel.setTyping(true);
        return panel;
    }

    public static MarkerPanel view(MapController.Marker marker, boolean focusOnEdit) {
        return new MarkerPanel(Kind.VIEW, marker, marker.dimension(), marker.x(), marker.z(), marker.icon(), marker.color(),
                marker.name(), focusOnEdit);
    }

    public static MarkerPanel death(Identifier dimension, int x, int z, boolean focusOnEdit) {
        return new MarkerPanel(Kind.DEATH, null, dimension, x, z, MarkerIcon.BANNER, DyeColor.RED, "", focusOnEdit);
    }

    public Kind kind() {
        return kind;
    }

    public Identifier dimension() {
        return dimension;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public MarkerIcon icon() {
        return icon;
    }

    public DyeColor color() {
        return color;
    }

    public boolean isDraft() {
        return kind == Kind.NEW;
    }

    /** The marker shown with this panel's icon and color while they are being changed. */
    public boolean edits(MapController.Marker other) {
        return kind == Kind.EDIT && marker == other;
    }

    public Text title() {
        return Text.translatable(switch (kind) {
            case NEW -> "screen.immersive_map.marker.new";
            case DEATH -> "screen.immersive_map.death";
            default -> "screen.immersive_map.marker";
        });
    }

    public int height() {
        return editing() ? MAX_HEIGHT : SHORT;
    }

    private boolean editing() {
        return kind == Kind.NEW || kind == Kind.EDIT;
    }

    public boolean inside(float px, float py) {
        return px >= 0.0F && py >= 0.0F && px < WIDTH && py < height();
    }

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean on) {
        on &= editing();
        if (on && !typing) {
            caretStart = Util.getMeasuringTimeMs();
        }
        typing = on;
    }

    // ---------------------------------------------------------------- input

    /** Returns {@code true} when the panel is done and should close. */
    public boolean click(float px, float py) {
        switch (targetAt(px, py)) {
            case FIELD -> setTyping(true);
            case ICON -> {
                icon = ICONS[iconAt(px, py)];
                tick();
            }
            case COLOR -> {
                color = DYES[colorAt(px, py)];
                tick();
            }
            case TITLE -> {
                edit();
                setTyping(true);
            }
            case PRIMARY -> {
                press();
                return primaryAction();
            }
            case SECONDARY -> {
                press();
                return secondaryAction();
            }
            case NONE -> {
                if (!focusOnEdit) {
                    setTyping(false);
                }
            }
        }
        return false;
    }

    /** The wheel flips through icons (or dye colors over the swatches) instead of clicking through them. */
    public boolean scroll(float px, float py, int steps) {
        if (!inside(px, py)) {
            return false;
        }
        if (editing() && steps != 0) {
            if (icon.colorable() && colorAt(px, py) >= 0) {
                int index = 0;
                while (DYES[index] != color) {
                    index++;
                }
                color = DYES[Math.floorMod(index - steps, DYES.length)];
            } else {
                icon = icon.cycle(-steps);
            }
            tick();
        }
        return true;
    }

    /** Enter: place or save; returns {@code true} when the panel should close. */
    public boolean confirm() {
        if (kind == Kind.NEW) {
            place();
        } else if (kind == Kind.EDIT) {
            apply();
        }
        return true;
    }

    public boolean key(int key) {
        return typing && name.key(key);
    }

    public void type(String chars) {
        if (typing) {
            name.type(chars);
        }
    }

    private boolean primaryAction() {
        switch (kind) {
            case NEW -> place();
            case EDIT -> apply();
            case VIEW -> {
                edit();
                return false;
            }
            default -> {
            }
        }
        return true;
    }

    private boolean secondaryAction() {
        if (kind == Kind.EDIT || kind == Kind.VIEW) {
            MapController.removeMarker(marker);
            pageSound();
        } else if (kind == Kind.DEATH) {
            MapController.removeDeathMarker();
            pageSound();
        }
        return true;
    }

    private void edit() {
        kind = Kind.EDIT;
        setTyping(focusOnEdit);
    }

    private void place() {
        MapController.addMarker(dimension, x, z, name.text().trim(), icon, color);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.6F, 1.0F);
        }
    }

    private void apply() {
        marker.setIcon(icon);
        marker.setColor(color);
        marker.setName(name.text());
        MapController.rememberStyle(icon, color);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.4F, 1.2F);
        }
    }

    private static void tick() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F));
    }

    private static void press() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.25F));
    }

    private static void pageSound() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.6F, 0.8F);
        }
    }

    // ---------------------------------------------------------------- layout

    private Target targetAt(float px, float py) {
        if (!inside(px, py)) {
            return Target.NONE;
        }
        if (primary().contains(px, py)) {
            return Target.PRIMARY;
        }
        if (secondary().contains(px, py)) {
            return Target.SECONDARY;
        }
        if (editing()) {
            if (FIELD.contains(px, py)) {
                return Target.FIELD;
            }
            if (iconAt(px, py) >= 0) {
                return Target.ICON;
            }
            if (icon.colorable() && colorAt(px, py) >= 0) {
                return Target.COLOR;
            }
        } else if (kind == Kind.VIEW && px >= 27.0F && px < RIGHT && py >= 8.0F && py < 21.5F) {
            return Target.TITLE;
        }
        return Target.NONE;
    }

    private static int iconAt(float px, float py) {
        if (px < GRID_X || py < GRID_Y) {
            return -1;
        }
        int column = (int) ((px - GRID_X) / CELL);
        int row = (int) ((py - GRID_Y) / CELL);
        int index = row * COLUMNS + column;
        return column < COLUMNS && index < ICONS.length ? index : -1;
    }

    private static int colorAt(float px, float py) {
        if (px < SWATCH_X || py < SWATCH_Y || py >= SWATCH_Y + SWATCH) {
            return -1;
        }
        int index = (int) ((px - SWATCH_X) / SWATCH);
        return index < DYES.length ? index : -1;
    }

    private float buttonsY() {
        return editing() ? 57.0F : 31.0F;
    }

    private Text primaryLabel() {
        return Text.translatable(switch (kind) {
            case NEW -> "screen.immersive_map.marker.place";
            case EDIT -> "screen.immersive_map.marker.done";
            case VIEW -> "screen.immersive_map.marker.edit";
            case DEATH -> "screen.immersive_map.marker.close";
        });
    }

    private Text secondaryLabel() {
        return Text.translatable(switch (kind) {
            case NEW -> "screen.immersive_map.marker.cancel";
            case EDIT, VIEW -> "screen.immersive_map.marker.delete";
            case DEATH -> "screen.immersive_map.death.delete";
        });
    }

    private static float buttonWidth(Text label) {
        return Math.max(22.0F, MinecraftClient.getInstance().textRenderer.getWidth(label) * TEXT + 7.0F);
    }

    private Box primary() {
        float width = buttonWidth(primaryLabel());
        float y = buttonsY();
        return new Box(RIGHT - width, y, RIGHT, y + BUTTON_HEIGHT);
    }

    private Box secondary() {
        Box primary = primary();
        float width = buttonWidth(secondaryLabel());
        return new Box(primary.x0() - 3.0F - width, primary.y0(), primary.x0() - 3.0F, primary.y1());
    }

    // ---------------------------------------------------------------- drawing

    /** {@code px}/{@code py}: the pointer in panel units, used only when {@code pointer} is set. */
    public void render(PanelCanvas c, float px, float py, boolean pointer) {
        Target target = pointer ? targetAt(px, py) : Target.NONE;
        int height = height();
        c.fill(2.0F, 2.5F, WIDTH + 1.5F, height + 1.5F, SHADOW);
        sheet(c, height);
        if (editing()) {
            renderEditor(c, target, target == Target.ICON ? iconAt(px, py) : target == Target.COLOR ? colorAt(px, py) : -1);
        } else {
            renderCard(c, target);
        }
        button(c, secondaryLabel(), secondary(), target == Target.SECONDARY);
        button(c, primaryLabel(), primary(), target == Target.PRIMARY);
    }

    private void renderEditor(PanelCanvas c, Target target, int hovered) {
        if (target == Target.FIELD) {
            c.fill(FIELD.x0(), FIELD.y0(), FIELD.x1(), FIELD.y1(), WASH);
        }
        c.fill(FIELD.x0() + 1.0F, 16.0F, FIELD.x1() - 1.0F, 16.0F + LINE, typing ? INK : FADED);
        renderName(c, FIELD.x0() + 2.0F, 10.5F, FIELD.x1() - FIELD.x0() - 4.0F);

        for (int i = 0; i < ICONS.length; i++) {
            float cx = GRID_X + (i % COLUMNS) * CELL;
            float cy = GRID_Y + (i / COLUMNS) * CELL;
            if (ICONS[i] == icon) {
                c.fill(cx, cy, cx + CELL, cy + CELL, SHADE);
                c.frame(cx, cy, cx + CELL, cy + CELL, LINE, INK);
            } else if (target == Target.ICON && hovered == i) {
                c.fill(cx, cy, cx + CELL, cy + CELL, WASH);
            }
            c.sprite(ICONS[i].sprite(color), cx + 1.0F, cy + 1.0F, 8.0F, -1);
        }

        Text caption;
        int captionColor = INK;
        if (target == Target.ICON) {
            caption = ICONS[hovered].displayName();
        } else if (target == Target.COLOR) {
            caption = colorName(DYES[hovered]);
        } else {
            caption = icon.colorable()
                    ? Text.translatable("screen.immersive_map.marker.colored", icon.displayName(), colorName(color))
                    : icon.displayName();
            captionColor = INK_SOFT;
        }
        c.text(caption, (WIDTH - c.width(caption, TEXT)) / 2.0F, CAPTION_Y, TEXT, captionColor);

        if (icon.colorable()) {
            for (int i = 0; i < DYES.length; i++) {
                float sx = SWATCH_X + i * SWATCH;
                boolean selected = DYES[i] == color;
                if (target == Target.COLOR && hovered == i && !selected) {
                    c.fill(sx, SWATCH_Y, sx + SWATCH, SWATCH_Y + SWATCH, WASH);
                }
                c.fill(sx + 1.0F, SWATCH_Y + 1.0F, sx + SWATCH - 1.0F, SWATCH_Y + SWATCH - 1.0F, 0xFF000000 | DYES[i].getEntityColor());
                c.frame(sx + 0.5F, SWATCH_Y + 0.5F, sx + SWATCH - 0.5F, SWATCH_Y + SWATCH - 0.5F, LINE, selected ? INK : INK_SOFT);
                if (selected) {
                    c.frame(sx, SWATCH_Y, sx + SWATCH, SWATCH_Y + SWATCH, LINE, INK);
                }
            }
        }

        c.text(Text.translatable("map.immersive_map.coords", x, z), 9.0F, buttonsY() + 2.25F, TEXT, FADED);
    }

    /** The name in ink with a blinking caret, scrolled so the caret stays in view. */
    private void renderName(PanelCanvas c, float tx, float ty, float maxWidth) {
        String text = name.text();
        if (text.isEmpty()) {
            c.text(Text.translatable("screen.immersive_map.marker.name_hint"), tx, ty, TEXT, FADED);
        }
        int caret = name.caret();
        int start = 0;
        while (start < caret && c.width(text.substring(start, caret), TEXT) > maxWidth) {
            start++;
        }
        String shown = text.substring(start);
        while (!shown.isEmpty() && c.width(shown, TEXT) > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        if (name.selectedAll()) {
            c.fill(tx - 0.5F, ty - 1.0F, tx + c.width(shown, TEXT) + 0.5F, ty + 4.5F, SELECTION);
        }
        if (!shown.isEmpty()) {
            c.text(Text.literal(shown), tx, ty, TEXT, INK);
        }
        if (typing && (Util.getMeasuringTimeMs() - caretStart) / 300L % 2L == 0L) {
            float cx = tx + c.width(text.substring(start, caret), TEXT);
            c.fill(cx, ty - 0.75F, cx + LINE, ty + 4.25F, INK);
        }
    }

    private void renderCard(PanelCanvas c, Target target) {
        Sprite sprite = kind == Kind.DEATH ? MarkerIcon.sprite(MarkerIcon.DEATH) : icon.sprite(color);
        c.sprite(sprite, 9.0F, 9.0F, 16.0F, -1);
        Text title = kind == Kind.DEATH ? Text.translatable("map.immersive_map.death")
                : name.text().isEmpty() ? icon.displayName() : Text.literal(name.text());
        if (target == Target.TITLE) {
            c.fill(27.0F, 8.0F, RIGHT, 21.5F, WASH);
        }
        float maxWidth = RIGHT - 30.0F;
        c.text(fit(c, title, maxWidth), 29.0F, 10.0F, TEXT, INK);
        c.text(fit(c, where(x, z), maxWidth), 29.0F, 16.5F, TEXT, FADED);
    }

    private static void button(PanelCanvas c, Text label, Box box, boolean hovered) {
        if (hovered) {
            c.fill(box.x0(), box.y0(), box.x1(), box.y1(), SHADE);
        }
        c.frame(box.x0(), box.y0(), box.x1(), box.y1(), LINE, hovered ? INK : INK_SOFT);
        c.text(label, (box.x0() + box.x1() - c.width(label, TEXT)) / 2.0F, box.y0() + 2.25F, TEXT, INK);
    }

    /** Nine-slice of the vanilla map background, so the sheet has the same paper and torn edge as the map. */
    private static void sheet(PanelCanvas c, int height) {
        int b = BORDER;
        int t = TEXTURE;
        int innerWidth = WIDTH - 2 * b;
        int innerHeight = height - 2 * b;
        region(c, 0, 0, b, b, 0, 0);
        region(c, WIDTH - b, 0, b, b, t - b, 0);
        region(c, 0, height - b, b, b, 0, t - b);
        region(c, WIDTH - b, height - b, b, b, t - b, t - b);
        region(c, b, 0, innerWidth, b, b, 0);
        region(c, b, height - b, innerWidth, b, b, t - b);
        region(c, 0, b, b, innerHeight, 0, b);
        region(c, WIDTH - b, b, b, innerHeight, t - b, b);
        region(c, b, b, innerWidth, innerHeight, b, b);
    }

    private static void region(PanelCanvas c, float x, float y, float width, float height, float u, float v) {
        c.texture(MapDrawer.BACKGROUND_TEXTURE, x, y, x + width, y + height,
                u / TEXTURE, v / TEXTURE, (u + width) / TEXTURE, (v + height) / TEXTURE, -1);
    }

    private static Text fit(PanelCanvas c, Text text, float maxWidth) {
        if (c.width(text, TEXT) <= maxWidth) {
            return text;
        }
        String value = text.getString();
        while (!value.isEmpty() && c.width(value + "…", TEXT) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return Text.literal(value + "…");
    }

    public static Text colorName(DyeColor color) {
        return Text.translatable("color.minecraft." + color.getName());
    }

    /** "X 10  Z -20 · 35 blocks" from the player. */
    public static Text where(int x, int z) {
        Text coords = Text.translatable("map.immersive_map.coords", x, z);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return coords;
        }
        double dx = x + 0.5 - player.getX();
        double dz = z + 0.5 - player.getZ();
        return Text.translatable("map.immersive_map.where", coords, MathHelper.floor(Math.sqrt(dx * dx + dz * dz)));
    }
}
