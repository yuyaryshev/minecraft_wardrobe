package com.yymod.wardrobe.content.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class WardrobePlayerSettings {
    private static final String KEY_FAST_TRANSFER = "yy_wardrobe_fast_transfer";

    private WardrobePlayerSettings() {
    }

    public static WardrobeFastTransferMode getFastTransferMode(Player player) {
        CompoundTag tag = player.getPersistentData();
        return WardrobeFastTransferMode.fromIndex(tag.getInt(KEY_FAST_TRANSFER));
    }

    public static void setFastTransferMode(Player player, WardrobeFastTransferMode mode) {
        CompoundTag tag = player.getPersistentData();
        tag.putInt(KEY_FAST_TRANSFER, mode == null ? 0 : mode.ordinal());
    }
}
