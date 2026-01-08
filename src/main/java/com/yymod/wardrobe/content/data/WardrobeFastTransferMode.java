package com.yymod.wardrobe.content.data;

public enum WardrobeFastTransferMode {
    NONE,
    RIGHT_CLICK,
    SHIFT_CLICK,
    LEFT_CLICK,
    SHIFT_LEFT_CLICK;

    public static WardrobeFastTransferMode fromIndex(int index) {
        WardrobeFastTransferMode[] values = values();
        if (index < 0 || index >= values.length) {
            return NONE;
        }
        return values[index];
    }
}
