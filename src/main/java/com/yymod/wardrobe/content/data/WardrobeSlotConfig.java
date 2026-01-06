package com.yymod.wardrobe.content.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class WardrobeSlotConfig {
    public static final int SLOT_COUNT = 41;

    private ItemStack boundItem = ItemStack.EMPTY;
    private WardrobeSlotMode mode = WardrobeSlotMode.NONE;
    private int minCount = 0;
    private int maxCount = 0;

    public ItemStack getBoundItem() {
        return boundItem;
    }

    public void setBoundItem(ItemStack boundItem) {
        this.boundItem = boundItem;
        if (boundItem.isEmpty()) {
            mode = WardrobeSlotMode.NONE;
            minCount = 0;
            maxCount = 0;
        } else if (mode == WardrobeSlotMode.NONE) {
            mode = WardrobeSlotMode.BOTH;
            int maxStack = boundItem.getMaxStackSize();
            minCount = 0;
            maxCount = maxStack;
        }
    }

    public WardrobeSlotMode getMode() {
        return mode;
    }

    public WardrobeSlotMode getEffectiveMode() {
        if (!isBound()) {
            return WardrobeSlotMode.NONE;
        }
        int maxStack = boundItem.getMaxStackSize();
        if (minCount == 0 && maxCount < maxStack) {
            return WardrobeSlotMode.UNLOAD;
        }
        if (minCount > 0 && maxCount == maxStack) {
            return WardrobeSlotMode.LOAD;
        }
        return mode;
    }

    public void setMode(WardrobeSlotMode mode) {
        this.mode = mode;
    }

    public int getMinCount() {
        return minCount;
    }

    public void setMinCount(int minCount) {
        this.minCount = Math.max(0, minCount);
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = Math.max(0, maxCount);
    }

    public boolean isBound() {
        return !boundItem.isEmpty();
    }

    public void clear() {
        boundItem = ItemStack.EMPTY;
        mode = WardrobeSlotMode.NONE;
        minCount = 0;
        maxCount = 0;
    }

    public void save(CompoundTag tag) {
        if (!boundItem.isEmpty()) {
            tag.put("BoundItem", boundItem.save(new CompoundTag()));
        }
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Min", minCount);
        tag.putInt("Max", maxCount);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("BoundItem")) {
            boundItem = ItemStack.of(tag.getCompound("BoundItem"));
        } else {
            boundItem = ItemStack.EMPTY;
        }
        int modeIndex = tag.getInt("Mode");
        if (modeIndex < 0 || modeIndex >= WardrobeSlotMode.values().length) {
            mode = WardrobeSlotMode.NONE;
        } else {
            mode = WardrobeSlotMode.values()[modeIndex];
        }
        minCount = tag.getInt("Min");
        maxCount = tag.getInt("Max");
    }
}
