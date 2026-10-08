package com.example.immersivemap.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.StringHelper;
import org.lwjgl.glfw.GLFW;

/** One line of text being typed: caret, word jumps, select all and the clipboard, like a vanilla text field. */
public final class TextEdit {
    private final int maxLength;
    private String text = "";
    private int caret;
    private boolean selectedAll;

    public TextEdit(int maxLength) {
        this.maxLength = maxLength;
    }

    public String text() {
        return text;
    }

    public int caret() {
        return caret;
    }

    public boolean selectedAll() {
        return selectedAll && !text.isEmpty();
    }

    public void set(String value) {
        String clean = value == null ? "" : StringHelper.stripInvalidChars(value);
        text = clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
        caret = text.length();
        selectedAll = false;
    }

    public void type(String chars) {
        String clean = StringHelper.stripInvalidChars(chars);
        if (clean.isEmpty()) {
            return;
        }
        if (selectedAll()) {
            text = "";
            caret = 0;
        }
        selectedAll = false;
        int room = maxLength - text.length();
        if (room <= 0) {
            return;
        }
        if (clean.length() > room) {
            clean = clean.substring(0, room);
        }
        text = text.substring(0, caret) + clean + text.substring(caret);
        caret += clean.length();
    }

    /** Editing and clipboard keys; returns {@code false} for keys that do not edit text. */
    public boolean key(int key) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (Screen.isSelectAll(key)) {
            selectedAll = true;
            caret = text.length();
            return true;
        }
        if (Screen.isPaste(key)) {
            type(client.keyboard.getClipboard());
            return true;
        }
        if (Screen.isCopy(key) || Screen.isCut(key)) {
            client.keyboard.setClipboard(text);
            if (Screen.isCut(key)) {
                set("");
            }
            return true;
        }
        boolean word = Screen.hasControlDown();
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectedAll()) {
                    set("");
                } else if (caret > 0) {
                    int from = word ? wordStart(caret) : caret - 1;
                    text = text.substring(0, from) + text.substring(caret);
                    caret = from;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selectedAll()) {
                    set("");
                } else if (caret < text.length()) {
                    text = text.substring(0, caret) + text.substring(word ? wordEnd(caret) : caret + 1);
                }
            }
            case GLFW.GLFW_KEY_LEFT -> caret = word ? wordStart(caret) : Math.max(0, caret - 1);
            case GLFW.GLFW_KEY_RIGHT -> caret = word ? wordEnd(caret) : Math.min(text.length(), caret + 1);
            case GLFW.GLFW_KEY_HOME -> caret = 0;
            case GLFW.GLFW_KEY_END -> caret = text.length();
            default -> {
                return false;
            }
        }
        selectedAll = false;
        return true;
    }

    private int wordStart(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }

    private int wordEnd(int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        return i;
    }
}
