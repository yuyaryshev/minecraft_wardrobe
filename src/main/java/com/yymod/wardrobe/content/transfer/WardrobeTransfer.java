package com.yymod.wardrobe.content.transfer;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.data.WardrobeSlotMode;
import com.yymod.wardrobe.content.data.WardrobeSetup;
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
        Map<ItemKey, ItemStack> buffered = new HashMap<>();

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
                addToBuffer(buffered, current);
                setPlayerSlot(player.getInventory(), slotIndex, ItemStack.EMPTY);
                current = ItemStack.EMPTY;
            }

            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, config.getBoundItem())) {
                int maxAllowed = normalizeMax(config, current);
                if (current.getCount() > maxAllowed) {
                    int excess = current.getCount() - maxAllowed;
                    ItemStack excessStack = current.copy();
                    excessStack.setCount(excess);
                    addToBuffer(buffered, excessStack);
                    current.shrink(excess);
                    setPlayerSlot(player.getInventory(), slotIndex, current);
                }
            }

            current = getPlayerSlot(player.getInventory(), slotIndex);
            WardrobeSlotMode mode = config.getEffectiveMode();
            if (mode.allowsLoad()) {
                int minNeeded = normalizeMin(config, current);
                if (current.isEmpty() || ItemStack.isSameItemSameTags(current, config.getBoundItem())) {
                    int missing = minNeeded - current.getCount();
                    if (missing > 0) {
                        ItemKey key = ItemKey.fromStack(config.getBoundItem());
                        ItemStack bufferedStack = buffered.get(key);
                        if (bufferedStack != null && !bufferedStack.isEmpty()) {
                            int toTake = Math.min(missing, bufferedStack.getCount());
                            if (current.isEmpty()) {
                                current = config.getBoundItem().copy();
                                current.setCount(0);
                            }
                            current.grow(toTake);
                            bufferedStack.shrink(toTake);
                            missing -= toTake;
                            if (bufferedStack.isEmpty()) {
                                buffered.remove(key);
                            }
                            setPlayerSlot(player.getInventory(), slotIndex, current);
                        }
                    }
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

        if (!buffered.isEmpty()) {
            for (ItemStack bufferedStack : buffered.values()) {
                if (bufferedStack.isEmpty()) {
                    continue;
                }
                ItemStack remaining = unloadToPartialInputs(bufferedStack, inputHandlers);
                if (!remaining.isEmpty() && outputHandler != null) {
                    remaining = ItemHandlerHelper.insertItemStacked(outputHandler, remaining, false);
                }
                if (!remaining.isEmpty()) {
                    outputFull = true;
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
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, config.getBoundItem())) {
                highlights[slotIndex] = HIGHLIGHT_UNLOAD;
                continue;
            }

            int maxAllowed = normalizeMax(config, current.isEmpty() ? config.getBoundItem() : current);
            if (!current.isEmpty() && current.getCount() > maxAllowed) {
                highlights[slotIndex] = HIGHLIGHT_UNLOAD;
                continue;
            }

            int minNeeded = normalizeMin(config, current);
            WardrobeSlotMode mode = config.getEffectiveMode();
            if (mode.allowsLoad() && (current.isEmpty() || ItemStack.isSameItemSameTags(current, config.getBoundItem()))
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
        ItemStack remaining = unloadToPartialInputs(stack.copy(), inputHandlers);
        if (!remaining.isEmpty() && outputHandler != null) {
            remaining = ItemHandlerHelper.insertItemStacked(outputHandler, remaining, false);
        }
        return remaining;
    }

    private static ItemStack unloadToPartialInputs(ItemStack stack, List<IItemHandler> inputHandlers) {
        ItemStack remaining = stack;
        int maxStack = stack.getMaxStackSize();
        for (IItemHandler handler : inputHandlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack existing = handler.getStackInSlot(slot);
                if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remaining)) {
                    continue;
                }
                if (existing.getCount() >= maxStack) {
                    continue;
                }
                remaining = handler.insertItem(slot, remaining, false);
                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return remaining;
    }

    private static void addToBuffer(Map<ItemKey, ItemStack> buffered, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemKey key = ItemKey.fromStack(stack);
        ItemStack existing = buffered.get(key);
        if (existing == null) {
            buffered.put(key, stack.copy());
        } else {
            existing.grow(stack.getCount());
        }
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
        if (!current.isStackable()) {
            return Math.min(Math.max(max, 1), 1);
        }
        return Math.max(0, max);
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
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            BlockPos inventoryPos = pos.relative(direction);
            BlockEntity blockEntity = level.getBlockEntity(inventoryPos);
            if (blockEntity != null) {
                Direction side = direction.getOpposite();
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
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
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
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
