package com.yymod.wardrobe.network;

import net.minecraft.network.FriendlyByteBuf;

public record WardrobeErrorPacket(String message) {
    public static WardrobeErrorPacket decode(FriendlyByteBuf buffer) {
        return new WardrobeErrorPacket(buffer.readUtf(256));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(message == null ? "" : message);
    }
}
