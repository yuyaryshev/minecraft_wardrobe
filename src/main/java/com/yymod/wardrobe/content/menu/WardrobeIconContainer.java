package com.yymod.wardrobe.content.menu;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class WardrobeIconContainer implements Container {
    private final WardrobeBlockEntity blockEntity;

    public WardrobeIconContainer(WardrobeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    public WardrobeBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public int getContainerSize() {
        return WardrobeBlockEntity.SETUP_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return blockEntity.getSetup(index).getIcon();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack existing = getItem(index);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = existing.split(count);
        blockEntity.getSetup(index).setIcon(existing);
        blockEntity.markUpdated();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack existing = getItem(index);
        blockEntity.getSetup(index).setIcon(ItemStack.EMPTY);
        blockEntity.markUpdated();
        return existing;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        blockEntity.getSetup(index).setIcon(stack);
        blockEntity.markUpdated();
    }

    @Override
    public void setChanged() {
        blockEntity.markUpdated();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, ItemStack.EMPTY);
        }
    }
}
