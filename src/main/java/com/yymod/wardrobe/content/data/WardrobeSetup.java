package com.yymod.wardrobe.content.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public class WardrobeSetup {
    private String name = "";
    private ItemStack icon = ItemStack.EMPTY;
    private final WardrobeSlotConfig[] slots = new WardrobeSlotConfig[WardrobeSlotConfig.SLOT_COUNT];

    public WardrobeSetup() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new WardrobeSlotConfig();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon == null ? ItemStack.EMPTY : icon;
    }

    public WardrobeSlotConfig getSlot(int index) {
        return slots[index];
    }

    public void save(CompoundTag tag) {
        tag.putString("Name", name);
        if (!icon.isEmpty()) {
            tag.put("Icon", icon.save(new CompoundTag()));
        }
        ListTag slotList = new ListTag();
        for (WardrobeSlotConfig slot : slots) {
            CompoundTag slotTag = new CompoundTag();
            slot.save(slotTag);
            slotList.add(slotTag);
        }
        tag.put("Slots", slotList);
    }

    public void load(CompoundTag tag) {
        name = tag.getString("Name");
        if (tag.contains("Icon")) {
            icon = ItemStack.of(tag.getCompound("Icon"));
        } else {
            icon = ItemStack.EMPTY;
        }
        ListTag slotList = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.length; i++) {
            if (i < slotList.size()) {
                slots[i].load(slotList.getCompound(i));
            } else {
                slots[i].clear();
            }
        }
    }
}
