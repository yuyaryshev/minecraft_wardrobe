package com.yymod.wardrobe.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record WardrobeActionPacket(BlockPos pos, Action action, int index, boolean flag, int delta,
                                   boolean adjustMax, boolean fast, String text) {
    public static WardrobeActionPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        Action action = Action.values()[buffer.readVarInt()];
        int index = buffer.readVarInt();
        boolean flag = buffer.readBoolean();
        int delta = buffer.readVarInt();
        boolean adjustMax = buffer.readBoolean();
        boolean fast = buffer.readBoolean();
        String text = buffer.readUtf(256);
        return new WardrobeActionPacket(pos, action, index, flag, delta, adjustMax, fast, text);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(action.ordinal());
        buffer.writeVarInt(index);
        buffer.writeBoolean(flag);
        buffer.writeVarInt(delta);
        buffer.writeBoolean(adjustMax);
        buffer.writeBoolean(fast);
        buffer.writeUtf(text == null ? "" : text);
    }

    public enum Action {
        TOGGLE_MODE,
        SELECT_SETUP,
        SET_FAST_TRANSFER,
        TRANSFER_ALL,
        OPERATE_SLOT,
        ADJUST_COUNT,
        RENAME_SETUP
    }
}
