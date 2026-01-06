package com.yymod.wardrobe.content.block.item;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WardrobeBlockItem extends BlockItem {
    public WardrobeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        CompoundTag tag = stack.getTagElement(BlockItem.BLOCK_ENTITY_TAG);
        if (tag == null) {
            return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof WardrobeBlockEntity wardrobe)) {
            return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        }

        wardrobe.loadFromItem(tag);
        return true;
    }
}
