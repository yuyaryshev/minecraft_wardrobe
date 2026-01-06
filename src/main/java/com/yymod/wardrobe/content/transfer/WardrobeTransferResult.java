package com.yymod.wardrobe.content.transfer;

public record WardrobeTransferResult(boolean outputFull, String errorMessage) {
    public static WardrobeTransferResult empty() {
        return new WardrobeTransferResult(false, "");
    }

    public static WardrobeTransferResult withOutputFull() {
        return new WardrobeTransferResult(true, "");
    }

    public static WardrobeTransferResult error(String message) {
        return new WardrobeTransferResult(false, message);
    }
}
