package com.yymod.wardrobe.content.data;

public enum WardrobeMatchMode {
    NORMAL,
    EQUIPMENT,
    TAG,
    TYPE;

    public static WardrobeMatchMode fromIndex(int index) {
        WardrobeMatchMode[] values = values();
        if (index < 0 || index >= values.length) {
            return NORMAL;
        }
        return values[index];
    }
}
