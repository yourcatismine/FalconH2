package com.falconcore.anticheat.check;

public enum CheckCategory {
    MOVEMENT("Movement"),
    COMBAT("Combat"),
    INTERACTION("Interaction"),
    WORLD("World"),
    OTHER("Other");

    private final String displayName;

    CheckCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
