package com.yymod.wardrobe.content.event;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.block.WardrobeBlock;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
import com.yymod.wardrobe.content.data.WardrobePlayerSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

@Mod.EventBusSubscriber(modid = YYWardrobe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WardrobeInteractEvents {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof WardrobeBlock)) {
            return;
        }
        WardrobeBlockEntity wardrobe = getWardrobe(level, event.getPos(), state);
        if (wardrobe == null) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        boolean shiftDown = player.isShiftKeyDown();
        WardrobeFastTransferMode mode = WardrobePlayerSettings.getFastTransferMode(player);
        boolean shouldTransfer = switch (mode) {
            case RIGHT_CLICK -> !shiftDown;
            case SHIFT_CLICK -> shiftDown;
            default -> false;
        };

        if (wardrobe.isOperationalMode() && shouldTransfer) {
            wardrobe.transferItems(serverPlayer, null);
        } else {
            NetworkHooks.openScreen(serverPlayer, wardrobe, wardrobe.getBlockPos());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof WardrobeBlock)) {
            return;
        }
        WardrobeBlockEntity wardrobe = getWardrobe(level, event.getPos(), state);
        if (wardrobe == null || !wardrobe.isOperationalMode()) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Direction face = event.getFace();
        if (face == null || face != wardrobe.getBlockState().getValue(WardrobeBlock.FACING)) {
            return;
        }

        boolean shiftDown = player.isShiftKeyDown();
        WardrobeFastTransferMode mode = WardrobePlayerSettings.getFastTransferMode(player);
        boolean shouldTransfer = switch (mode) {
            case LEFT_CLICK -> !shiftDown;
            case SHIFT_LEFT_CLICK -> shiftDown;
            default -> false;
        };
        if (!shouldTransfer) {
            return;
        }

        wardrobe.transferItems(serverPlayer, null);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    private static WardrobeBlockEntity getWardrobe(Level level, BlockPos pos, BlockState state) {
        BlockPos basePos = state.getValue(WardrobeBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockEntity blockEntity = level.getBlockEntity(basePos);
        if (blockEntity instanceof WardrobeBlockEntity wardrobe) {
            return wardrobe;
        }
        return null;
    }
}
