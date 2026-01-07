package com.yymod.wardrobe.content.menu;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WardrobeIconSlot extends Slot {
    private final WardrobeBlockEntity blockEntity;
    private final boolean allowWhenOperational;

    public WardrobeIconSlot(Container container, WardrobeBlockEntity blockEntity, int slot, int x, int y,
                            boolean allowWhenOperational) {
        super(container, slot, x, y);
        this.blockEntity = blockEntity;
        this.allowWhenOperational = allowWhenOperational;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return blockEntity != null && (blockEntity.isSetupMode() || allowWhenOperational);
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        return blockEntity != null && (blockEntity.isSetupMode() || allowWhenOperational);
    }
}
