package com.example.immersivemap.map;

/** How a player is currently holding the virtual map. */
public enum HoldState {
    NONE,
    BOTH_HANDS,
    OFF_HAND;

    private static final HoldState[] VALUES = values();

    public static HoldState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public boolean isHolding() {
        return this != NONE;
    }
}
