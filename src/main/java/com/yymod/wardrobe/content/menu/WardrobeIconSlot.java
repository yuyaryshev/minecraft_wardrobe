package com.yymod.wardrobe.content.menu;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WardrobeIconSlot extends Slot {
    private final WardrobeBlockEntity blockEntity;

    public WardrobeIconSlot(WardrobeIconContainer container, int slot, int x, int y) {
        super(container, slot, x, y);
        this.blockEntity = container == null ? null : container.getBlockEntity();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return blockEntity != null && blockEntity.isSetupMode();
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        return blockEntity != null && blockEntity.isSetupMode();
    }
}
