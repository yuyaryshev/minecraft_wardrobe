package com.yymod.wardrobe.content.menu;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
import com.yymod.wardrobe.content.data.WardrobeMatchMode;
import com.yymod.wardrobe.content.data.WardrobePlayerSettings;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.data.WardrobeSlotMode;
import com.yymod.wardrobe.content.transfer.WardrobeTransfer;
import com.yymod.wardrobe.registry.WardrobeMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class WardrobeMenu extends AbstractContainerMenu {
    public static final int MENU_WIDTH = 404;
    private static final int ICON_SLOT_START = 0;
    private static final int ICON_SLOT_COUNT = WardrobeBlockEntity.SETUP_COUNT;
    private static final int ACTIVE_ICON_SLOT_COUNT = 1;
    private static final int ACTIVE_ICON_SLOT_START = ICON_SLOT_START + ICON_SLOT_COUNT;
    private static final int PLAYER_SLOT_START = ACTIVE_ICON_SLOT_START + ACTIVE_ICON_SLOT_COUNT;
    private static final int LEFT_PADDING = 8;
    private static final int ICON_SLOT_X = LEFT_PADDING;
    private static final int ICON_SLOT_Y_OFFSET = 28;
    private static final int ACTIVE_ICON_X = (MENU_WIDTH - 120) / 2 - 22;
    private static final int PLAYER_SLOT_OFFSET_X = (MENU_WIDTH - 252) / 2;
    private static final int EQUIPMENT_OFFSET_X = 40;

    private final WardrobeBlockEntity blockEntity;
    private final Player player;
    private final int[] slotHighlights = new int[WardrobeSlotConfig.SLOT_COUNT];
    private final WardrobeIconContainer iconContainer;
    private final WardrobeActiveIconContainer activeIconContainer;
    private WardrobeFastTransferMode fastTransferMode;
    private int armorStandScanRange;
    private String lastError = "";
    private final Map<Integer, Integer> slotIdToWardrobeIndex = new HashMap<>();

    public WardrobeMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, resolveBlockEntity(playerInventory, buffer));
    }

    public WardrobeMenu(int id, Inventory playerInventory, WardrobeBlockEntity blockEntity) {
        super(WardrobeMenus.WARDROBE.get(), id);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
        this.iconContainer = new WardrobeIconContainer(blockEntity);
        this.activeIconContainer = new WardrobeActiveIconContainer(blockEntity);
        this.armorStandScanRange = blockEntity.getArmorStandScanRange();

        for (int i = 0; i < ICON_SLOT_COUNT; i++) {
            addSlot(new WardrobeIconSlot(iconContainer, blockEntity, i, ICON_SLOT_X, ICON_SLOT_Y_OFFSET + i * 18, false));
        }

        addSlot(new WardrobeIconSlot(activeIconContainer, blockEntity, 0, ACTIVE_ICON_X, 6, true));

        addCuriosSlots(playerInventory.player);

        addPlayerInventorySlots(playerInventory);

        addIntDataSlot(blockEntity::getActiveSetupIndex, blockEntity::setActiveSetupIndex);
        addIntDataSlot(() -> blockEntity.isSetupMode() ? 1 : 0, value -> blockEntity.setSetupMode(value != 0));
        fastTransferMode = WardrobePlayerSettings.getFastTransferMode(player);
        addIntDataSlot(() -> WardrobePlayerSettings.getFastTransferMode(player).ordinal(), value -> {
            WardrobeFastTransferMode mode = WardrobeFastTransferMode.fromIndex(value);
            fastTransferMode = mode;
            if (!player.level().isClientSide) {
                WardrobePlayerSettings.setFastTransferMode(player, mode);
            }
        });
        addIntDataSlot(() -> blockEntity.isOutputFull() ? 1 : 0, value -> blockEntity.setOutputFull(value != 0));
        addIntDataSlot(blockEntity::getArmorStandScanRange, value -> {
            armorStandScanRange = value;
            if (!player.level().isClientSide) {
                blockEntity.setArmorStandScanRange(value);
            }
        });

        for (int i = 0; i < slotHighlights.length; i++) {
            final int index = i;
            addIntDataSlot(() -> slotHighlights[index], value -> slotHighlights[index] = value);
        }
    }

    private void addIntDataSlot(IntSupplier getter, IntConsumer setter) {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return getter.getAsInt();
            }

            @Override
            public void set(int value) {
                setter.accept(value);
            }
        });
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                addMappedSlot(new Slot(playerInventory, index, PLAYER_SLOT_OFFSET_X + 80 + col * 18, 84 + row * 18), index);
            }
        }

        for (int col = 0; col < 9; col++) {
            addMappedSlot(new Slot(playerInventory, col, PLAYER_SLOT_OFFSET_X + 80 + col * 18, 142), col);
        }

        for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
            int index = 39 - armorSlot;
            addMappedSlot(new Slot(playerInventory, index, PLAYER_SLOT_OFFSET_X + 8 + EQUIPMENT_OFFSET_X, 84 + armorSlot * 18), index);
        }

        addMappedSlot(new Slot(playerInventory, 40, PLAYER_SLOT_OFFSET_X + 8 + EQUIPMENT_OFFSET_X, 154), 40);
    }

    private void addCuriosSlots(Player player) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            int startX = PLAYER_SLOT_OFFSET_X + 80;
            int startY = 84 - 36;
            int wardIndex = WardrobeSlotConfig.CURIOS_SLOT_START;
            for (CurioSlotInfo info : getCurioSlots(handler)) {
                if (wardIndex >= WardrobeSlotConfig.SLOT_COUNT) {
                    break;
                }
                int col = (wardIndex - WardrobeSlotConfig.CURIOS_SLOT_START) % 9;
                int row = (wardIndex - WardrobeSlotConfig.CURIOS_SLOT_START) / 9;
                if (row >= 2) {
                    break;
                }
                int x = startX + col * 18;
                int y = startY + row * 18;
                CurioSlot slot = new CurioSlot(player, info.handler, info.index, info.identifier, x, y, info.renders, false);
                addMappedSlot(slot, wardIndex);
                wardIndex++;
            }
        });
    }

    private List<CurioSlotInfo> getCurioSlots(ICuriosItemHandler handler) {
        Map<String, ICurioStacksHandler> curios = new TreeMap<>(handler.getCurios());
        List<CurioSlotInfo> slots = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
            ICurioStacksHandler stacksHandler = entry.getValue();
            for (int i = 0; i < stacksHandler.getSlots(); i++) {
                slots.add(new CurioSlotInfo(entry.getKey(), stacksHandler.getStacks(), i, stacksHandler.getRenders()));
                if (slots.size() >= WardrobeSlotConfig.CURIOS_SLOT_COUNT) {
                    return slots;
                }
            }
        }
        return slots;
    }

    private void addMappedSlot(Slot slot, int wardrobeIndex) {
        addSlot(slot);
        slotIdToWardrobeIndex.put(slots.size() - 1, wardrobeIndex);
    }

    public int getWardrobeSlotIndex(Slot slot) {
        Integer mapped = slotIdToWardrobeIndex.get(slot.index);
        if (mapped != null) {
            return mapped;
        }
        return -1;
    }

    private record CurioSlotInfo(String identifier,
                                 top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler handler,
                                 int index,
                                 NonNullList<Boolean> renders) {
    }

    @Override
    public void broadcastChanges() {
        Level level = blockEntity.getLevel();
        if (level != null && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int[] highlights = WardrobeTransfer.computeHighlights(blockEntity, serverPlayer);
            System.arraycopy(highlights, 0, slotHighlights, 0, slotHighlights.length);
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }
        return player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5D,
                blockEntity.getBlockPos().getY() + 0.5D,
                blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    public WardrobeBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getHighlight(int slotIndex) {
        return slotHighlights[slotIndex];
    }

    public boolean isSetupMode() {
        return blockEntity.isSetupMode();
    }

    public WardrobeFastTransferMode getFastTransferMode() {
        return fastTransferMode;
    }

    public int getArmorStandScanRange() {
        return armorStandScanRange;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (slotId == ACTIVE_ICON_SLOT_START && !blockEntity.isSetupMode()) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                ItemStack icon = carried.copy();
                icon.setCount(1);
                blockEntity.getActiveSetup().setIcon(icon);
                blockEntity.markUpdated();
            } else if (button == 1) {
                blockEntity.getActiveSetup().setIcon(ItemStack.EMPTY);
                blockEntity.markUpdated();
            }
            return;
        }
        if (slotId >= ICON_SLOT_START && slotId < ICON_SLOT_START + ICON_SLOT_COUNT) {
            if (!blockEntity.isSetupMode()) {
                blockEntity.setActiveSetupIndex(slotId);
                return;
            }
        }

        int playerSlotIndex = toWardrobeSlotIndex(slotId);
        if (playerSlotIndex >= 0) {
            if (blockEntity.isSetupMode()) {
                if (clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                    WardrobeSlotConfig config = blockEntity.getActiveSetup().getSlot(playerSlotIndex);
                    if (config.isBound() && !config.getBoundItem().isEmpty()) {
                        cycleMatchMode(config);
                        blockEntity.markUpdated();
                    }
                    return;
                }
                handleSetupSlotClick(player, playerSlotIndex, slotId, button);
                return;
            } else if (player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    Set<Integer> slots = new HashSet<>();
                    slots.add(playerSlotIndex);
                    blockEntity.transferItems(serverPlayer, slots);
                }
                return;
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    private void handleSetupSlotClick(Player player, int wardrobeSlotIndex, int slotId, int button) {
        WardrobeSlotConfig config = blockEntity.getActiveSetup().getSlot(wardrobeSlotIndex);
        Slot clickedSlot = getSlot(slotId);
        ItemStack slotItem = clickedSlot.getItem();

        if (player.isShiftKeyDown() && button == 1) {
            WardrobeSlotMode next = switch (config.getMode()) {
                case BOTH -> WardrobeSlotMode.UNLOAD;
                case UNLOAD -> WardrobeSlotMode.LOAD;
                case LOAD, NONE -> WardrobeSlotMode.BOTH;
            };
            config.setMode(next);
            blockEntity.markUpdated();
            return;
        }

        if (player.isShiftKeyDown() && button == 0) {
            if (config.isBound() && !config.getBoundItem().isEmpty()) {
                cycleMatchMode(config);
                blockEntity.markUpdated();
            }
            return;
        }

        if (button == 1) {
            if (config.isBound() && !config.isAirBound()) {
                config.clear();
                enforceEquipmentDefaults(wardrobeSlotIndex, config);
                blockEntity.markUpdated();
            } else if (!config.isBound()) {
                config.setAirBound();
                enforceEquipmentDefaults(wardrobeSlotIndex, config);
                blockEntity.markUpdated();
            }
            return;
        }

        if (config.isAirBound()) {
            config.clear();
            enforceEquipmentDefaults(wardrobeSlotIndex, config);
            blockEntity.markUpdated();
            return;
        }

        boolean wasUnbound = !config.isBound()
                || (config.getBoundItem().isEmpty() && config.isEquipmentSlot());
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            ItemStack binding = carried.copy();
            binding.setCount(1);
            config.setBoundItem(binding);
        } else if (!slotItem.isEmpty()) {
            ItemStack binding = slotItem.copy();
            binding.setCount(1);
            config.setBoundItem(binding);
        }
        if (wasUnbound && config.isBound() && !config.getBoundItem().isEmpty()) {
            int maxStack = config.getBoundItem().getMaxStackSize();
            config.setMinCount(maxStack);
            config.setMaxCount(maxStack);
            if (wardrobeSlotIndex >= 36 && !config.getBoundItem().isStackable()) {
                config.setMatchMode(WardrobeMatchMode.EQUIPMENT);
            }
        }
        enforceEquipmentDefaults(wardrobeSlotIndex, config);
        blockEntity.markUpdated();
    }

    private void cycleMatchMode(WardrobeSlotConfig config) {
        ItemStack bound = config.getBoundItem();
        List<String> tags = getItemTagIds(bound);
        boolean hasTags = !tags.isEmpty();
        boolean allowType = !bound.isStackable();
        boolean allowEquipment = !bound.isStackable();

        WardrobeMatchMode current = config.getMatchMode();
        if (current == WardrobeMatchMode.NORMAL) {
            if (allowEquipment) {
                config.setMatchMode(WardrobeMatchMode.EQUIPMENT);
            } else if (hasTags) {
                config.setMatchMode(WardrobeMatchMode.TAG);
                config.setMatchTagId(tags.get(0));
            } else {
                config.setMatchMode(WardrobeMatchMode.NORMAL);
                config.setMatchTagId("");
            }
            return;
        }

        if (current == WardrobeMatchMode.EQUIPMENT) {
            if (hasTags) {
                config.setMatchMode(WardrobeMatchMode.TAG);
                config.setMatchTagId(tags.get(0));
                return;
            }
            if (allowType) {
                config.setMatchMode(WardrobeMatchMode.TYPE);
                config.setMatchTagId("");
                return;
            }
            config.setMatchMode(WardrobeMatchMode.NORMAL);
            config.setMatchTagId("");
            return;
        }

        if (current == WardrobeMatchMode.TAG) {
            if (hasTags) {
                int idx = tags.indexOf(config.getMatchTagId());
                if (idx < 0) {
                    config.setMatchTagId(tags.get(0));
                    return;
                }
                if (idx + 1 < tags.size()) {
                    config.setMatchTagId(tags.get(idx + 1));
                    return;
                }
            }
            if (allowType) {
                config.setMatchMode(WardrobeMatchMode.TYPE);
                config.setMatchTagId("");
            } else {
                config.setMatchMode(WardrobeMatchMode.NORMAL);
                config.setMatchTagId("");
            }
            return;
        }

        config.setMatchMode(WardrobeMatchMode.NORMAL);
        config.setMatchTagId("");
    }

    private List<String> getItemTagIds(ItemStack stack) {
        List<String> tags = new ArrayList<>();
        stack.getTags().forEach(tag -> tags.add(tag.location().toString()));
        return tags;
    }

    private int toWardrobeSlotIndex(int slotId) {
        return slotIdToWardrobeIndex.getOrDefault(slotId, -1);
    }

    private void enforceEquipmentDefaults(int slotIndex, WardrobeSlotConfig config) {
        if (slotIndex < 36) {
            return;
        }
        if (!config.getBoundItem().isEmpty() && config.getBoundItem().isStackable()) {
            config.setEquipmentSlot(false);
            return;
        }
        config.setEquipmentSlot(true);
        if (config.getMode() == WardrobeSlotMode.NONE) {
            config.setMode(WardrobeSlotMode.BOTH);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static WardrobeBlockEntity resolveBlockEntity(Inventory playerInventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof WardrobeBlockEntity wardrobe)) {
            throw new IllegalStateException("Wardrobe block entity not found at " + pos);
        }
        return wardrobe;
    }
}
