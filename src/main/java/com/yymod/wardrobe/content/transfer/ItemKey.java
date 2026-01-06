package com.yymod.wardrobe.content.transfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record ItemKey(Item item, CompoundTag tag) {
    public static ItemKey fromStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new ItemKey(stack.getItem(), tag == null ? null : tag.copy());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemKey other)) {
            return false;
        }
        return item == other.item && Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, tag);
    }
}
