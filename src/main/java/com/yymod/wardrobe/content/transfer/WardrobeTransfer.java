package com.yymod.wardrobe.content.transfer;

import com.yymod.wardrobe.content.block.WardrobeBlock;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.data.WardrobeSlotMode;
import com.yymod.wardrobe.content.data.WardrobeSetup;
import com.yymod.wardrobe.registry.WardrobeBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WardrobeTransfer {
    private static final int CONNECTION_LIMIT = 16;
    public static final int HIGHLIGHT_NONE = 0;
    public static final int HIGHLIGHT_LOAD_OK = 1;
    public static final int HIGHLIGHT_LOAD_MISSING = 2;
    public static final int HIGHLIGHT_UNLOAD = 3;

    private WardrobeTransfer() {
    }

    public static WardrobeTransferResult execute(WardrobeBlockEntity wardrobe, ServerPlayer player, @Nullable Set<Integer> onlySlots) {
        wardrobe.clearLastError();
        Level level = wardrobe.getLevel();
        if (level == null) {
            return WardrobeTransferResult.empty();
        }

        List<IItemHandler> inputHandlers = findInputHandlers(wardrobe, level);
        IItemHandler outputHandler = findOutputHandler(wardrobe, level);
        WardrobeSetup setup = wardrobe.getActiveSetup();

        boolean outputFull = false;

        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            if (onlySlots != null && !onlySlots.contains(slotIndex)) {
                continue;
            }
            WardrobeSlotConfig config = setup.getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }

            ItemStack current = getPlayerSlot(player.getInventory(), slotIndex);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, config.getBoundItem())) {
                if (config.getMode().allowsUnload()) {
                    ItemStack remainder = unloadStack(wardrobe, config, current, inputHandlers, outputHandler);
                    setPlayerSlot(player.getInventory(), slotIndex, remainder);
                    if (!remainder.isEmpty()) {
                        outputFull = true;
                    } else {
                        current = ItemStack.EMPTY;
                    }
                }
            }

            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, config.getBoundItem())
                    && config.getMode().allowsUnload()) {
                int maxAllowed = normalizeMax(config, current);
                if (current.getCount() > maxAllowed) {
                    int excess = current.getCount() - maxAllowed;
                    ItemStack excessStack = current.copy();
                    excessStack.setCount(excess);
                    ItemStack remainder = unloadStack(wardrobe, config, excessStack, inputHandlers, outputHandler);
                    int removed = excess - remainder.getCount();
                    current.shrink(removed);
                    setPlayerSlot(player.getInventory(), slotIndex, current);
                    if (!remainder.isEmpty()) {
                        outputFull = true;
                    }
                }
            }

            current = getPlayerSlot(player.getInventory(), slotIndex);
            if (config.getMode().allowsLoad()) {
                int minNeeded = normalizeMin(config, current);
                if (current.isEmpty() || ItemStack.isSameItemSameTags(current, config.getBoundItem())) {
                    int missing = minNeeded - current.getCount();
                    if (missing > 0) {
                        ItemStack toInsert = pullFromInputs(config.getBoundItem(), missing, inputHandlers);
                        if (!toInsert.isEmpty()) {
                            if (current.isEmpty()) {
                                current = config.getBoundItem().copy();
                                current.setCount(0);
                            }
                            current.grow(toInsert.getCount());
                            setPlayerSlot(player.getInventory(), slotIndex, current);
                        }
                    }
                }
            }
        }

        wardrobe.setOutputFull(outputFull);
        return outputFull ? WardrobeTransferResult.withOutputFull() : WardrobeTransferResult.empty();
    }

    public static int[] computeHighlights(WardrobeBlockEntity wardrobe, ServerPlayer player) {
        int[] highlights = new int[WardrobeSlotConfig.SLOT_COUNT];
        Level level = wardrobe.getLevel();
        if (level == null) {
            return highlights;
        }

        WardrobeSetup setup = wardrobe.getActiveSetup();
        List<IItemHandler> inputHandlers = findInputHandlers(wardrobe, level);
        Map<ItemKey, Integer> inputCounts = countItems(inputHandlers);

        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            WardrobeSlotConfig config = setup.getSlot(slotIndex);
            if (!config.isBound()) {
                highlights[slotIndex] = HIGHLIGHT_NONE;
                continue;
            }

            ItemStack current = getPlayerSlot(player.getInventory(), slotIndex);
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, config.getBoundItem())
                    && config.getMode().allowsUnload()) {
                highlights[slotIndex] = HIGHLIGHT_UNLOAD;
                continue;
            }

            int maxAllowed = normalizeMax(config, current.isEmpty() ? config.getBoundItem() : current);
            if (!current.isEmpty() && config.getMode().allowsUnload() && current.getCount() > maxAllowed) {
                highlights[slotIndex] = HIGHLIGHT_UNLOAD;
                continue;
            }

            int minNeeded = normalizeMin(config, current);
            if (config.getMode().allowsLoad() && (current.isEmpty() || ItemStack.isSameItemSameTags(current, config.getBoundItem()))
                    && current.getCount() < minNeeded) {
                int needed = minNeeded - current.getCount();
                int available = inputCounts.getOrDefault(ItemKey.fromStack(config.getBoundItem()), 0);
                highlights[slotIndex] = available >= needed ? HIGHLIGHT_LOAD_OK : HIGHLIGHT_LOAD_MISSING;
                continue;
            }

            highlights[slotIndex] = HIGHLIGHT_NONE;
        }

        return highlights;
    }

    public static void notifyPlayerError(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static ItemStack unloadStack(WardrobeBlockEntity wardrobe, WardrobeSlotConfig config, ItemStack stack,
                                         List<IItemHandler> inputHandlers, @Nullable IItemHandler outputHandler) {
        boolean preferInput = shouldPreferInput(wardrobe, stack);
        ItemStack remaining = stack.copy();

        if (preferInput) {
            remaining = unloadToPreferredInput(remaining, inputHandlers);
        }

        if (!remaining.isEmpty() && outputHandler != null) {
            remaining = ItemHandlerHelper.insertItemStacked(outputHandler, remaining, false);
        }

        return remaining;
    }

    private static ItemStack unloadToPreferredInput(ItemStack stack, List<IItemHandler> inputHandlers) {
        ItemStack remaining = stack;
        int twoStacks = stack.getMaxStackSize() * 2;
        for (IItemHandler handler : inputHandlers) {
            int currentCount = countItem(handler, stack);
            if (currentCount > 0 && currentCount < twoStacks) {
                remaining = ItemHandlerHelper.insertItemStacked(handler, remaining, false);
                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return remaining;
    }

    private static boolean shouldPreferInput(WardrobeBlockEntity wardrobe, ItemStack stack) {
        for (int i = 0; i < WardrobeBlockEntity.SETUP_COUNT; i++) {
            WardrobeSetup setup = wardrobe.getSetup(i);
            for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
                WardrobeSlotConfig config = setup.getSlot(slotIndex);
                if (config.isBound() && config.getMode().allowsLoad()
                        && ItemStack.isSameItemSameTags(config.getBoundItem(), stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ItemStack pullFromInputs(ItemStack template, int amount, List<IItemHandler> inputHandlers) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack collected = ItemStack.EMPTY;
        int remaining = amount;

        for (IItemHandler handler : inputHandlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack slotStack = handler.getStackInSlot(slot);
                if (slotStack.isEmpty() || !ItemStack.isSameItemSameTags(slotStack, template)) {
                    continue;
                }
                int toExtract = Math.min(remaining, slotStack.getCount());
                ItemStack extracted = handler.extractItem(slot, toExtract, false);
                if (!extracted.isEmpty()) {
                    if (collected.isEmpty()) {
                        collected = extracted.copy();
                    } else {
                        collected.grow(extracted.getCount());
                    }
                    remaining -= extracted.getCount();
                    if (remaining <= 0) {
                        return collected;
                    }
                }
            }
        }

        return collected;
    }

    private static int normalizeMax(WardrobeSlotConfig config, ItemStack current) {
        int max = Math.max(config.getMaxCount(), config.getMinCount());
        if (max <= 0) {
            max = current.getMaxStackSize();
        }
        if (!current.isStackable()) {
            max = 1;
        }
        return max;
    }

    private static int normalizeMin(WardrobeSlotConfig config, ItemStack current) {
        int max = normalizeMax(config, current.isEmpty() ? config.getBoundItem() : current);
        int min = Math.min(config.getMinCount(), max);
        if (!current.isStackable()) {
            min = Math.min(min, 1);
        }
        return Math.max(0, min);
    }

    private static ItemStack getPlayerSlot(Inventory inventory, int index) {
        if (index < 9) {
            return inventory.items.get(index);
        }
        if (index < 36) {
            return inventory.items.get(index);
        }
        if (index < 40) {
            return inventory.armor.get(index - 36);
        }
        return inventory.offhand.get(0);
    }

    private static void setPlayerSlot(Inventory inventory, int index, ItemStack stack) {
        if (index < 9) {
            inventory.items.set(index, stack);
            return;
        }
        if (index < 36) {
            inventory.items.set(index, stack);
            return;
        }
        if (index < 40) {
            inventory.armor.set(index - 36, stack);
            return;
        }
        inventory.offhand.set(0, stack);
    }

    private static List<IItemHandler> findInputHandlers(WardrobeBlockEntity wardrobe, Level level) {
        List<IItemHandler> handlers = new ArrayList<>();
        BlockPos pos = wardrobe.getBlockPos();
        Direction back = level.getBlockState(pos).getValue(WardrobeBlock.FACING).getOpposite();
        for (int i = 1; i <= CONNECTION_LIMIT; i++) {
            BlockPos connectionPos = pos.relative(back, i);
            if (!level.getBlockState(connectionPos).is(WardrobeBlocks.WARDROBE_CONNECTION.get())) {
                break;
            }
            BlockPos inventoryPos = connectionPos.relative(back);
            BlockEntity blockEntity = level.getBlockEntity(inventoryPos);
            if (blockEntity != null) {
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                        .ifPresent(handlers::add);
            }
        }
        return handlers;
    }

    @Nullable
    private static IItemHandler findOutputHandler(WardrobeBlockEntity wardrobe, Level level) {
        BlockPos outputPos = wardrobe.getBlockPos().below();
        BlockEntity blockEntity = level.getBlockEntity(outputPos);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    private static Map<ItemKey, Integer> countItems(List<IItemHandler> handlers) {
        Map<ItemKey, Integer> counts = new HashMap<>();
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemKey key = ItemKey.fromStack(stack);
                counts.merge(key, stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static int countItem(IItemHandler handler, ItemStack stack) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack existing = handler.getStackInSlot(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                count += existing.getCount();
            }
        }
        return count;
    }
}
