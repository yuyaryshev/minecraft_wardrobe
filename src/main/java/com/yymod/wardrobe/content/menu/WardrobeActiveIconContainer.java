package com.yymod.wardrobe.content.menu;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class WardrobeActiveIconContainer implements Container {
    private final WardrobeBlockEntity blockEntity;

    public WardrobeActiveIconContainer(WardrobeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    public WardrobeBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getItem(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return blockEntity.getActiveSetup().getIcon();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack existing = getItem(index);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = existing.split(count);
        blockEntity.getActiveSetup().setIcon(existing);
        blockEntity.markUpdated();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack existing = getItem(index);
        blockEntity.getActiveSetup().setIcon(ItemStack.EMPTY);
        blockEntity.markUpdated();
        return existing;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        blockEntity.getActiveSetup().setIcon(stack);
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
        setItem(0, ItemStack.EMPTY);
    }
}
