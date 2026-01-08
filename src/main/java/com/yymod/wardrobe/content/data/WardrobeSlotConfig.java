package com.yymod.wardrobe.content.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class WardrobeSlotConfig {
    public static final int PLAYER_SLOT_COUNT = 41;
    public static final int CURIOS_SLOT_COUNT = 18;
    public static final int SLOT_COUNT = PLAYER_SLOT_COUNT + CURIOS_SLOT_COUNT;
    public static final int CURIOS_SLOT_START = PLAYER_SLOT_COUNT;

    private ItemStack boundItem = ItemStack.EMPTY;
    private WardrobeSlotMode mode = WardrobeSlotMode.NONE;
    private WardrobeMatchMode matchMode = WardrobeMatchMode.NORMAL;
    private String matchTagId = "";
    private boolean equipmentSlot = false;
    private int minCount = 0;
    private int maxCount = 0;

    public ItemStack getBoundItem() {
        return boundItem;
    }

    public void setBoundItem(ItemStack boundItem) {
        this.boundItem = boundItem;
        if (boundItem.isEmpty()) {
            mode = WardrobeSlotMode.NONE;
            matchMode = WardrobeMatchMode.NORMAL;
            matchTagId = "";
            minCount = 0;
            maxCount = 0;
        } else if (mode == WardrobeSlotMode.NONE) {
            mode = WardrobeSlotMode.BOTH;
            int maxStack = boundItem.getMaxStackSize();
            minCount = 0;
            maxCount = maxStack;
            matchMode = WardrobeMatchMode.NORMAL;
            matchTagId = "";
        }
    }

    public WardrobeSlotMode getMode() {
        return mode;
    }

    public WardrobeSlotMode getEffectiveMode() {
        if (!isBound()) {
            return WardrobeSlotMode.NONE;
        }
        int maxStack = boundItem.isEmpty() ? 64 : boundItem.getMaxStackSize();
        if (minCount == 0 && maxCount < maxStack) {
            return WardrobeSlotMode.UNLOAD;
        }
        if (minCount > 0 && maxCount == maxStack) {
            return WardrobeSlotMode.LOAD;
        }
        if (equipmentSlot) {
            return WardrobeSlotMode.LOAD;
        }
        return mode;
    }

    public void setMode(WardrobeSlotMode mode) {
        this.mode = mode;
    }

    public WardrobeMatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(WardrobeMatchMode matchMode) {
        this.matchMode = matchMode == null ? WardrobeMatchMode.NORMAL : matchMode;
        if (this.matchMode == WardrobeMatchMode.EQUIPMENT && !boundItem.isEmpty() && boundItem.isStackable()) {
            this.matchMode = WardrobeMatchMode.NORMAL;
            equipmentSlot = false;
            return;
        }
        if (this.matchMode == WardrobeMatchMode.EQUIPMENT) {
            equipmentSlot = true;
        } else if (equipmentSlot) {
            equipmentSlot = false;
        }
    }

    public String getMatchTagId() {
        return matchTagId;
    }

    public void setMatchTagId(String matchTagId) {
        this.matchTagId = matchTagId == null ? "" : matchTagId;
    }

    public boolean isEquipmentSlot() {
        if (!boundItem.isEmpty() && boundItem.isStackable()) {
            return false;
        }
        return equipmentSlot || matchMode == WardrobeMatchMode.EQUIPMENT;
    }

    public void setEquipmentSlot(boolean equipmentSlot) {
        if (!boundItem.isEmpty() && boundItem.isStackable()) {
            this.equipmentSlot = false;
            if (matchMode == WardrobeMatchMode.EQUIPMENT) {
                matchMode = WardrobeMatchMode.NORMAL;
            }
            return;
        }
        this.equipmentSlot = equipmentSlot;
        if (equipmentSlot) {
            matchMode = WardrobeMatchMode.EQUIPMENT;
        }
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
        return !boundItem.isEmpty() || isAirBound() || isEquipmentSlot();
    }

    public boolean isAirBound() {
        return boundItem.isEmpty() && mode == WardrobeSlotMode.UNLOAD && minCount == 0 && maxCount == 0;
    }

    public void setAirBound() {
        boundItem = ItemStack.EMPTY;
        mode = WardrobeSlotMode.UNLOAD;
        minCount = 0;
        maxCount = 0;
    }

    public void clear() {
        boundItem = ItemStack.EMPTY;
        mode = WardrobeSlotMode.NONE;
        matchMode = WardrobeMatchMode.NORMAL;
        matchTagId = "";
        equipmentSlot = false;
        minCount = 0;
        maxCount = 0;
    }

    public void save(CompoundTag tag) {
        if (!boundItem.isEmpty()) {
            tag.put("BoundItem", boundItem.save(new CompoundTag()));
        }
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("MatchMode", matchMode.ordinal());
        if (!matchTagId.isEmpty()) {
            tag.putString("MatchTag", matchTagId);
        }
        tag.putBoolean("EquipmentSlot", equipmentSlot);
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
        int matchIndex = tag.getInt("MatchMode");
        matchMode = WardrobeMatchMode.fromIndex(matchIndex);
        matchTagId = tag.getString("MatchTag");
        equipmentSlot = tag.getBoolean("EquipmentSlot");
        if (matchMode == WardrobeMatchMode.NORMAL && equipmentSlot) {
            matchMode = WardrobeMatchMode.EQUIPMENT;
        }
        minCount = tag.getInt("Min");
        maxCount = tag.getInt("Max");
    }
}
