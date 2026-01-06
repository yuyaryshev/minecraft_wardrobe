package com.yymod.wardrobe.content.data;

public enum WardrobeSlotMode {
    NONE,
    BOTH,
    UNLOAD,
    LOAD;

    public boolean allowsUnload() {
        return this == BOTH || this == UNLOAD;
    }

    public boolean allowsLoad() {
        return this == BOTH || this == LOAD;
    }
}
