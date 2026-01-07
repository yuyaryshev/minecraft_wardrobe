package com.yymod.wardrobe.content.transfer;

import com.yymod.wardrobe.content.block.WardrobeBlock;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeMatchMode;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.data.WardrobeSlotMode;
import com.yymod.wardrobe.content.data.WardrobeSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
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

    private record EquipmentHandlers(IItemHandler left, IItemHandler right) {
    }

    public static WardrobeTransferResult execute(WardrobeBlockEntity wardrobe, ServerPlayer player, @Nullable Set<Integer> onlySlots) {
        wardrobe.clearLastError();
        Level level = wardrobe.getLevel();
        if (level == null) {
            return WardrobeTransferResult.empty();
        }

        List<IItemHandler> inputHandlers = findInputHandlers(wardrobe, level);
        IItemHandler outputHandler = findOutputHandler(wardrobe, level);
        EquipmentHandlers equipmentHandlers = findEquipmentHandlers(wardrobe, level);
        List<IItemHandler> refillHandlers = new ArrayList<>();
        if (outputHandler != null) {
            refillHandlers.add(outputHandler);
        }
        refillHandlers.addAll(inputHandlers);
        WardrobeSetup setup = wardrobe.getActiveSetup();
        int setupIndex = wardrobe.getActiveSetupIndex();
        Map<ItemKey, ItemStack> buffered = new HashMap<>();
        List<ItemStack> equipmentBuffered = new ArrayList<>();

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
            if (!current.isEmpty() && !matchesConfig(current, config)) {
                if (!tryBufferUnload(wardrobe, equipmentHandlers, buffered, equipmentBuffered, current)) {
                    continue;
                }
                setPlayerSlot(player.getInventory(), slotIndex, ItemStack.EMPTY);
                current = ItemStack.EMPTY;
            }

            if (!current.isEmpty() && matchesConfig(current, config)) {
                int maxAllowed = normalizeMax(config, current);
                if (current.getCount() > maxAllowed) {
                    int excess = current.getCount() - maxAllowed;
                    ItemStack excessStack = current.copy();
                    excessStack.setCount(excess);
                    if (tryBufferUnload(wardrobe, equipmentHandlers, buffered, equipmentBuffered, excessStack)) {
                        current.shrink(excess);
                        setPlayerSlot(player.getInventory(), slotIndex, current);
                    }
                }
            }

            current = getPlayerSlot(player.getInventory(), slotIndex);
            WardrobeSlotMode mode = config.getEffectiveMode();
            if (mode.allowsLoad()) {
                int minNeeded = normalizeMin(config, current);
                if (current.isEmpty()) {
                    int missing = minNeeded;
                    if (missing > 0) {
                        ItemStack inserted = loadFromArmorStand(wardrobe, setupIndex, config);
                        if (inserted.isEmpty()) {
                            inserted = loadFromBufferOrInputs(config, refillHandlers, buffered, missing);
                        }
                        if (!inserted.isEmpty()) {
                            current = inserted;
                            setPlayerSlot(player.getInventory(), slotIndex, current);
                        }
                    }
                } else if (matchesConfig(current, config)) {
                    int missing = minNeeded - current.getCount();
                    if (missing > 0) {
                        ItemStack inserted = loadMatchingForExisting(current, config, refillHandlers, buffered, missing);
                        if (!inserted.isEmpty()) {
                            current.grow(inserted.getCount());
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

        for (ItemStack bufferedStack : equipmentBuffered) {
            if (bufferedStack.isEmpty()) {
                continue;
            }
            ItemStack remaining = unloadToEquipment(wardrobe, equipmentHandlers, bufferedStack, wardrobe.getActiveSetupIndex());
            if (!remaining.isEmpty()) {
                wardrobe.setLastError("Not enough equipment space!");
            }
        }

        wardrobe.setOutputFull(outputFull);
        return outputFull ? WardrobeTransferResult.withOutputFull() : WardrobeTransferResult.empty();
    }

    public static void unloadSetup(WardrobeBlockEntity wardrobe, ServerPlayer player, int setupIndex) {
        Level level = wardrobe.getLevel();
        if (level == null) {
            return;
        }
        EquipmentHandlers equipmentHandlers = findEquipmentHandlers(wardrobe, level);
        IItemHandler outputHandler = findOutputHandler(wardrobe, level);
        WardrobeSetup setup = wardrobe.getSetup(Math.max(0, Math.min(WardrobeBlockEntity.SETUP_COUNT - 1, setupIndex)));
        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            WardrobeSlotConfig config = setup.getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            ItemStack current = getPlayerSlot(player.getInventory(), slotIndex);
            if (current.isEmpty()) {
                continue;
            }
            if (!tryUnloadStack(wardrobe, equipmentHandlers, outputHandler, current, setupIndex)) {
                wardrobe.setLastError("Not enough equipment space!");
                continue;
            }
            setPlayerSlot(player.getInventory(), slotIndex, ItemStack.EMPTY);
        }
    }

    public static void unloadAll(WardrobeBlockEntity wardrobe, ServerPlayer player) {
        Level level = wardrobe.getLevel();
        if (level == null) {
            return;
        }
        EquipmentHandlers equipmentHandlers = findEquipmentHandlers(wardrobe, level);
        IItemHandler outputHandler = findOutputHandler(wardrobe, level);
        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            ItemStack current = getPlayerSlot(player.getInventory(), slotIndex);
            if (current.isEmpty()) {
                continue;
            }
            int setupIndex = findEquipmentSetupIndex(wardrobe, current);
            if (!tryUnloadStack(wardrobe, equipmentHandlers, outputHandler, current, setupIndex)) {
                wardrobe.setLastError("Not enough equipment space!");
                continue;
            }
            setPlayerSlot(player.getInventory(), slotIndex, ItemStack.EMPTY);
        }
    }

    public static void equipSetup(WardrobeBlockEntity wardrobe, ServerPlayer player, int setupIndex) {
        Level level = wardrobe.getLevel();
        if (level == null) {
            return;
        }
        WardrobeSetup setup = wardrobe.getSetup(Math.max(0, Math.min(WardrobeBlockEntity.SETUP_COUNT - 1, setupIndex)));
        List<IItemHandler> inputHandlers = findInputHandlers(wardrobe, level);
        IItemHandler outputHandler = findOutputHandler(wardrobe, level);
        List<IItemHandler> refillHandlers = new ArrayList<>();
        if (outputHandler != null) {
            refillHandlers.add(outputHandler);
        }
        refillHandlers.addAll(inputHandlers);

        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            WardrobeSlotConfig config = setup.getSlot(slotIndex);
            if (!config.isBound()) {
                continue;
            }
            WardrobeSlotMode mode = config.getEffectiveMode();
            if (!mode.allowsLoad()) {
                continue;
            }
            ItemStack current = getPlayerSlot(player.getInventory(), slotIndex);
            if (!current.isEmpty() && !matchesConfig(current, config)) {
                continue;
            }
            int minNeeded = normalizeMin(config, current);
            int missing = minNeeded - current.getCount();
            if (missing <= 0) {
                continue;
            }
            ItemStack inserted = ItemStack.EMPTY;
            if (current.isEmpty()) {
                inserted = loadFromArmorStand(wardrobe, setupIndex, config);
            }
            if (inserted.isEmpty()) {
                inserted = loadFromInputsOnly(config, refillHandlers, missing);
            }
            if (!inserted.isEmpty()) {
                if (current.isEmpty()) {
                    current = inserted.copy();
                } else {
                    current.grow(inserted.getCount());
                }
                setPlayerSlot(player.getInventory(), slotIndex, current);
            }
        }
    }

    public static int[] computeHighlights(WardrobeBlockEntity wardrobe, ServerPlayer player) {
        int[] highlights = new int[WardrobeSlotConfig.SLOT_COUNT];
        Level level = wardrobe.getLevel();
        if (level == null) {
            return highlights;
        }

        WardrobeSetup setup = wardrobe.getActiveSetup();
        List<IItemHandler> inputHandlers = findInputHandlers(wardrobe, level);
        IItemHandler outputHandler = findOutputHandler(wardrobe, level);
        List<IItemHandler> refillHandlers = new ArrayList<>();
        if (outputHandler != null) {
            refillHandlers.add(outputHandler);
        }
        refillHandlers.addAll(inputHandlers);

        for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
            WardrobeSlotConfig config = setup.getSlot(slotIndex);
            if (!config.isBound()) {
                highlights[slotIndex] = HIGHLIGHT_NONE;
                continue;
            }

            ItemStack current = getPlayerSlot(player.getInventory(), slotIndex);
            if (!current.isEmpty() && !matchesConfig(current, config)) {
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
            if (mode.allowsLoad() && (current.isEmpty() || matchesConfig(current, config))
                    && current.getCount() < minNeeded) {
                int needed = minNeeded - current.getCount();
                int available = countAvailableForConfig(config, refillHandlers);
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

    private static int countAvailableForConfig(WardrobeSlotConfig config, List<IItemHandler> handlers) {
        ItemStack bound = config.getBoundItem();
        int exact = countExact(bound, handlers);
        if (config.getMatchMode() == WardrobeMatchMode.NORMAL) {
            return exact;
        }
        if (config.getMatchMode() == WardrobeMatchMode.TAG) {
            int tagCount = countByTag(config, handlers);
            return tagCount > 0 ? tagCount : exact;
        }
        if (config.getMatchMode() == WardrobeMatchMode.TYPE) {
            int typeCount = countByType(config, handlers);
            return typeCount > 0 ? typeCount : exact;
        }
        return exact;
    }

    private static int countExact(ItemStack stack, List<IItemHandler> handlers) {
        int count = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack existing = handler.getStackInSlot(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                    count += existing.getCount();
                }
            }
        }
        return count;
    }

    private static int countByTag(WardrobeSlotConfig config, List<IItemHandler> handlers) {
        TagKey<net.minecraft.world.item.Item> tagKey = getMatchTagKey(config);
        if (tagKey == null) {
            return 0;
        }
        int count = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack existing = handler.getStackInSlot(slot);
                if (!existing.isEmpty() && existing.is(tagKey)) {
                    count += existing.getCount();
                }
            }
        }
        return count;
    }

    private static int countByType(WardrobeSlotConfig config, List<IItemHandler> handlers) {
        String typeKey = getTypeKey(config.getBoundItem());
        if (typeKey == null) {
            return 0;
        }
        int count = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack existing = handler.getStackInSlot(slot);
                if (!existing.isEmpty() && typeKey.equals(getTypeKey(existing))) {
                    count += existing.getCount();
                }
            }
        }
        return count;
    }

    private static boolean tryBufferUnload(WardrobeBlockEntity wardrobe, EquipmentHandlers equipmentHandlers,
                                           Map<ItemKey, ItemStack> buffered, List<ItemStack> equipmentBuffered,
                                           ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int setupIndex = findEquipmentSetupIndex(wardrobe, stack);
        if (setupIndex < 0) {
            addToBuffer(buffered, stack);
            return true;
        }
        ItemStack remaining = simulateUnloadToEquipment(wardrobe, equipmentHandlers, stack.copy(), setupIndex);
        if (!remaining.isEmpty()) {
            wardrobe.setLastError("Not enough equipment space!");
            return false;
        }
        equipmentBuffered.add(stack.copy());
        return true;
    }

    private static ItemStack loadFromBufferOrInputs(WardrobeSlotConfig config, List<IItemHandler> inputHandlers,
                                                    Map<ItemKey, ItemStack> buffered, int missing) {
        ItemStack inserted = ItemStack.EMPTY;
        ItemStack bound = config.getBoundItem();

        ItemStack bufferedStack = buffered.get(ItemKey.fromStack(bound));
        if (bufferedStack != null && !bufferedStack.isEmpty()) {
            int toTake = Math.min(missing, bufferedStack.getCount());
            inserted = bound.copy();
            inserted.setCount(toTake);
            bufferedStack.shrink(toTake);
            if (bufferedStack.isEmpty()) {
                buffered.remove(ItemKey.fromStack(bound));
            }
            missing -= toTake;
        }

        if (missing > 0) {
            ItemStack exact = pullFromInputs(bound, missing, inputHandlers);
            if (!exact.isEmpty()) {
                if (inserted.isEmpty()) {
                    inserted = exact.copy();
                } else {
                    inserted.grow(exact.getCount());
                }
                missing -= exact.getCount();
            }
        }

        if (missing > 0) {
            ItemStack alt = pullAltFromInputs(config, missing, inputHandlers);
            if (!alt.isEmpty() && inserted.isEmpty()) {
                inserted = alt.copy();
            }
        }

        return inserted;
    }

    private static ItemStack loadFromInputsOnly(WardrobeSlotConfig config, List<IItemHandler> inputHandlers, int missing) {
        ItemStack bound = config.getBoundItem();
        ItemStack exact = pullFromInputs(bound, missing, inputHandlers);
        if (!exact.isEmpty()) {
            return exact;
        }
        return pullAltFromInputs(config, missing, inputHandlers);
    }

    private static ItemStack loadFromArmorStand(WardrobeBlockEntity wardrobe, int setupIndex, WardrobeSlotConfig config) {
        if (!config.isEquipmentSlot() || config.getBoundItem().isStackable()) {
            return ItemStack.EMPTY;
        }
        ArmorStand stand = getArmorStand(wardrobe, setupIndex);
        if (stand == null) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(config.getBoundItem());
        ItemStack standStack = stand.getItemBySlot(slot);
        if (standStack.isEmpty() || !matchesConfig(standStack, config)) {
            return ItemStack.EMPTY;
        }
        stand.setItemSlot(slot, ItemStack.EMPTY);
        return standStack.copy();
    }

    private static ItemStack loadMatchingForExisting(ItemStack current, WardrobeSlotConfig config,
                                                     List<IItemHandler> inputHandlers, Map<ItemKey, ItemStack> buffered,
                                                     int missing) {
        if (missing <= 0) {
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameTags(current, config.getBoundItem())) {
            ItemStack bufferedStack = buffered.get(ItemKey.fromStack(current));
            if (bufferedStack != null && !bufferedStack.isEmpty()) {
                int toTake = Math.min(missing, bufferedStack.getCount());
                ItemStack taken = current.copy();
                taken.setCount(toTake);
                bufferedStack.shrink(toTake);
                if (bufferedStack.isEmpty()) {
                    buffered.remove(ItemKey.fromStack(current));
                }
                missing -= toTake;
                if (missing <= 0) {
                    return taken;
                }
                ItemStack exact = pullFromInputs(current, missing, inputHandlers);
                if (!exact.isEmpty()) {
                    taken.grow(exact.getCount());
                    return taken;
                }
                return taken;
            }
            return pullFromInputs(current, missing, inputHandlers);
        }

        if (config.getMatchMode() == WardrobeMatchMode.TAG || config.getMatchMode() == WardrobeMatchMode.TYPE) {
            return pullFromInputs(current, missing, inputHandlers);
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack pullAltFromInputs(WardrobeSlotConfig config, int amount, List<IItemHandler> handlers) {
        if (config.getMatchMode() == WardrobeMatchMode.TAG) {
            return pullFromInputsByTag(config, amount, handlers);
        }
        if (config.getMatchMode() == WardrobeMatchMode.TYPE) {
            return pullFromInputsByType(config, amount, handlers);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack pullFromInputsByTag(WardrobeSlotConfig config, int amount, List<IItemHandler> handlers) {
        TagKey<net.minecraft.world.item.Item> tagKey = getMatchTagKey(config);
        if (tagKey == null) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack slotStack = handler.getStackInSlot(slot);
                if (slotStack.isEmpty() || !slotStack.is(tagKey)) {
                    continue;
                }
                int toExtract = Math.min(amount, slotStack.getCount());
                ItemStack extracted = handler.extractItem(slot, toExtract, false);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack pullFromInputsByType(WardrobeSlotConfig config, int amount, List<IItemHandler> handlers) {
        String typeKey = getTypeKey(config.getBoundItem());
        if (typeKey == null) {
            return ItemStack.EMPTY;
        }
        int bestHandler = -1;
        int bestSlot = -1;
        int bestScore = -1;
        for (int h = 0; h < handlers.size(); h++) {
            IItemHandler handler = handlers.get(h);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack slotStack = handler.getStackInSlot(slot);
                if (slotStack.isEmpty() || !typeKey.equals(getTypeKey(slotStack))) {
                    continue;
                }
                int score = slotStack.isDamageableItem()
                        ? slotStack.getMaxDamage() - slotStack.getDamageValue()
                        : Integer.MAX_VALUE;
                if (score > bestScore) {
                    bestScore = score;
                    bestHandler = h;
                    bestSlot = slot;
                }
            }
        }
        if (bestHandler < 0) {
            return ItemStack.EMPTY;
        }
        IItemHandler handler = handlers.get(bestHandler);
        return handler.extractItem(bestSlot, Math.min(amount, 1), false);
    }

    private static boolean matchesConfig(ItemStack stack, WardrobeSlotConfig config) {
        if (ItemStack.isSameItemSameTags(stack, config.getBoundItem())) {
            return true;
        }
        if (config.getMatchMode() == WardrobeMatchMode.TAG) {
            TagKey<net.minecraft.world.item.Item> tagKey = getMatchTagKey(config);
            return tagKey != null && stack.is(tagKey);
        }
        if (config.getMatchMode() == WardrobeMatchMode.TYPE) {
            String typeKey = getTypeKey(config.getBoundItem());
            return typeKey != null && typeKey.equals(getTypeKey(stack));
        }
        return false;
    }

    private static TagKey<net.minecraft.world.item.Item> getMatchTagKey(WardrobeSlotConfig config) {
        String tagId = config.getMatchTagId();
        if (tagId == null || tagId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        if (id == null) {
            return null;
        }
        return TagKey.create(Registries.ITEM, id);
    }

    private static String getTypeKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var item = stack.getItem();
        if (item instanceof SwordItem) {
            return "sword";
        }
        if (item instanceof PickaxeItem) {
            return "pickaxe";
        }
        if (item instanceof AxeItem) {
            return "axe";
        }
        if (item instanceof ShovelItem) {
            return "shovel";
        }
        if (item instanceof HoeItem) {
            return "hoe";
        }
        if (item instanceof ArmorItem armor) {
            return "armor_" + armor.getType().getName();
        }
        if (item instanceof ElytraItem) {
            return "elytra";
        }
        if (item instanceof BowItem) {
            return "bow";
        }
        if (item instanceof CrossbowItem) {
            return "crossbow";
        }
        if (item instanceof ShieldItem) {
            return "shield";
        }
        if (item instanceof TridentItem) {
            return "trident";
        }
        if (item instanceof FishingRodItem) {
            return "fishing_rod";
        }
        if (item instanceof ShearsItem) {
            return "shears";
        }
        if (item instanceof FlintAndSteelItem) {
            return "flint_and_steel";
        }
        if (item instanceof TieredItem) {
            return "tiered_tool";
        }
        return item.getClass().getName();
    }

    private static int findEquipmentSetupIndex(WardrobeBlockEntity wardrobe, ItemStack stack) {
        if (stack.isStackable()) {
            return -1;
        }
        for (int setupIndex = 0; setupIndex < WardrobeBlockEntity.SETUP_COUNT; setupIndex++) {
            WardrobeSetup setup = wardrobe.getSetup(setupIndex);
            for (int slotIndex = 0; slotIndex < WardrobeSlotConfig.SLOT_COUNT; slotIndex++) {
                WardrobeSlotConfig config = setup.getSlot(slotIndex);
                if (!config.isBound()) {
                    continue;
                }
                if (config.getBoundItem().isStackable()) {
                    continue;
                }
                if (!matchesConfig(stack, config)) {
                    continue;
                }
                if (config.isEquipmentSlot() || isEquipmentSlotIndex(slotIndex)) {
                    return setupIndex;
                }
            }
        }
        return -1;
    }

    private static boolean isEquipmentSlotIndex(int slotIndex) {
        return slotIndex >= 36;
    }

    private static EquipmentHandlers findEquipmentHandlers(WardrobeBlockEntity wardrobe, Level level) {
        Direction facing = wardrobe.getBlockState().getValue(WardrobeBlock.FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        IItemHandler leftHandler = getHandlerAt(level, wardrobe.getBlockPos().relative(left), left.getOpposite());
        IItemHandler rightHandler = getHandlerAt(level, wardrobe.getBlockPos().relative(right), right.getOpposite());
        return new EquipmentHandlers(leftHandler, rightHandler);
    }

    private static IItemHandler getHandlerAt(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }

    private static ItemStack unloadToEquipment(WardrobeBlockEntity wardrobe, EquipmentHandlers handlers, ItemStack stack,
                                               int setupIndex) {
        int equipmentIndex = findEquipmentSetupIndex(wardrobe, stack);
        if (equipmentIndex >= 0) {
            setupIndex = equipmentIndex;
        }
        ItemStack remaining = tryInsertArmorStand(wardrobe, setupIndex, stack);
        remaining = insertIntoEquipmentChests(handlers, setupIndex, remaining, false);
        return remaining;
    }

    private static ItemStack simulateUnloadToEquipment(WardrobeBlockEntity wardrobe, EquipmentHandlers handlers,
                                                       ItemStack stack, int setupIndex) {
        ItemStack remaining = tryInsertArmorStandSimulated(wardrobe, setupIndex, stack);
        remaining = insertIntoEquipmentChests(handlers, setupIndex, remaining, true);
        return remaining;
    }

    private static ItemStack tryInsertArmorStandSimulated(WardrobeBlockEntity wardrobe, int setupIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        ArmorStand stand = getArmorStand(wardrobe, setupIndex);
        if (stand == null) {
            return stack;
        }
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (!stand.getItemBySlot(slot).isEmpty()) {
            return stack;
        }
        if (stack.getCount() <= 1) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        remaining.shrink(1);
        return remaining;
    }

    private static ItemStack tryInsertArmorStand(WardrobeBlockEntity wardrobe, int setupIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        ArmorStand stand = getArmorStand(wardrobe, setupIndex);
        if (stand == null) {
            return stack;
        }
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (!stand.getItemBySlot(slot).isEmpty()) {
            return stack;
        }
        ItemStack placed = stack.copy();
        placed.setCount(1);
        stand.setItemSlot(slot, placed);
        ItemStack remaining = stack.copy();
        remaining.shrink(1);
        return remaining;
    }

    private static ArmorStand getArmorStand(WardrobeBlockEntity wardrobe, int setupIndex) {
        WardrobeSetup setup = wardrobe.getSetup(setupIndex);
        if (setup.getArmorStandPos() == null || wardrobe.getLevel() == null) {
            return null;
        }
        List<ArmorStand> stands = wardrobe.getLevel().getEntitiesOfClass(ArmorStand.class,
                new net.minecraft.world.phys.AABB(setup.getArmorStandPos()).inflate(0.5D));
        return stands.isEmpty() ? null : stands.get(0);
    }

    private static ItemStack insertIntoEquipmentChests(EquipmentHandlers handlers, int setupIndex, ItemStack stack,
                                                       boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        IItemHandler primary = null;
        int line = -1;
        if (setupIndex >= 0 && setupIndex <= 2) {
            primary = handlers.left();
            line = setupIndex;
        } else if (setupIndex >= 3 && setupIndex <= 5) {
            primary = handlers.right();
            line = setupIndex - 3;
        }

        ItemStack remaining = stack;
        if (primary != null && line >= 0) {
            remaining = insertIntoLine(primary, line, remaining, simulate);
            if (!remaining.isEmpty() && primary.getSlots() > 27) {
                remaining = insertRange(primary, 27, primary.getSlots(), remaining, simulate);
            }
        }

        if (!remaining.isEmpty() && primary != null) {
            remaining = insertRange(primary, 0, primary.getSlots(), remaining, simulate);
        }
        if (!remaining.isEmpty() && handlers.left() != null && handlers.left() != primary) {
            remaining = insertRange(handlers.left(), 0, handlers.left().getSlots(), remaining, simulate);
        }
        if (!remaining.isEmpty() && handlers.right() != null && handlers.right() != primary) {
            remaining = insertRange(handlers.right(), 0, handlers.right().getSlots(), remaining, simulate);
        }
        return remaining;
    }

    private static ItemStack insertIntoLine(IItemHandler handler, int lineIndex, ItemStack stack, boolean simulate) {
        int start = lineIndex * 9;
        int end = Math.min(handler.getSlots(), start + 9);
        return insertRange(handler, start, end, stack, simulate);
    }

    private static ItemStack insertRange(IItemHandler handler, int start, int end, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int slot = start; slot < end && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private static boolean tryUnloadStack(WardrobeBlockEntity wardrobe, EquipmentHandlers equipmentHandlers,
                                          @Nullable IItemHandler outputHandler, ItemStack stack, int setupIndex) {
        if (stack.isEmpty()) {
            return true;
        }
        if (setupIndex >= 0) {
            ItemStack remaining = simulateUnloadToEquipment(wardrobe, equipmentHandlers, stack.copy(), setupIndex);
            if (!remaining.isEmpty()) {
                return false;
            }
            remaining = unloadToEquipment(wardrobe, equipmentHandlers, stack.copy(), setupIndex);
            return remaining.isEmpty();
        }
        if (outputHandler == null) {
            return false;
        }
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(outputHandler, stack, false);
        return remaining.isEmpty();
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
