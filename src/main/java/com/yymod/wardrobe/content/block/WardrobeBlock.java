package com.yymod.wardrobe.content.block;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import java.util.Collections;
import java.util.List;

public class WardrobeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public WardrobeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            level.setBlock(pos.above(),
                    state.setValue(HALF, DoubleBlockHalf.UPPER),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == Direction.UP && half == DoubleBlockHalf.LOWER && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.DOWN && half == DoubleBlockHalf.UPPER && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);
        if (otherState.is(this)) {
            if (half == DoubleBlockHalf.UPPER) {
                level.destroyBlock(otherPos, !player.isCreative());
            } else {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockEntity blockEntity = level.getBlockEntity(basePos);
        if (!(blockEntity instanceof WardrobeBlockEntity wardrobe)) {
            return InteractionResult.CONSUME;
        }

        if (wardrobe.isOperationalMode()) {
            WardrobeFastTransferMode mode = wardrobe.getFastTransferMode();
            boolean shouldTransfer = switch (mode) {
                case RIGHT_CLICK -> !player.isShiftKeyDown();
                case SHIFT_CLICK -> player.isShiftKeyDown();
                case NONE -> false;
            };
            if (shouldTransfer) {
                wardrobe.transferItems((ServerPlayer) player, null);
                return InteractionResult.CONSUME;
            }
        }

        NetworkHooks.openScreen((ServerPlayer) player, wardrobe, basePos);
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return Collections.emptyList();
        }
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof WardrobeBlockEntity wardrobe) {
            ItemStack stack = new ItemStack(this);
            wardrobe.saveToItem(stack);
            return List.of(stack);
        }
        return super.getDrops(state, builder);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new WardrobeBlockEntity(pos, state) : null;
    }
}
