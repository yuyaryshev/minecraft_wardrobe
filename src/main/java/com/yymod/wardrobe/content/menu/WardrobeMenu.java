package com.yymod.wardrobe.content.menu;

import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.data.WardrobeSlotMode;
import com.yymod.wardrobe.content.transfer.WardrobeTransfer;
import com.yymod.wardrobe.registry.WardrobeMenus;
import net.minecraft.core.BlockPos;
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

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class WardrobeMenu extends AbstractContainerMenu {
    private static final int ICON_SLOT_START = 0;
    private static final int ICON_SLOT_COUNT = 8;
    private static final int PLAYER_SLOT_START = ICON_SLOT_START + ICON_SLOT_COUNT;

    private final WardrobeBlockEntity blockEntity;
    private final Player player;
    private final int[] slotHighlights = new int[WardrobeSlotConfig.SLOT_COUNT];
    private final WardrobeIconContainer iconContainer;
    private String lastError = "";

    public WardrobeMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, resolveBlockEntity(playerInventory, buffer));
    }

    public WardrobeMenu(int id, Inventory playerInventory, WardrobeBlockEntity blockEntity) {
        super(WardrobeMenus.WARDROBE.get(), id);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
        this.iconContainer = new WardrobeIconContainer(blockEntity);

        for (int i = 0; i < ICON_SLOT_COUNT; i++) {
            addSlot(new WardrobeIconSlot(iconContainer, i, 8, 18 + i * 18));
        }

        addPlayerInventorySlots(playerInventory);

        addIntDataSlot(blockEntity::getActiveSetupIndex, blockEntity::setActiveSetupIndex);
        addIntDataSlot(() -> blockEntity.isSetupMode() ? 1 : 0, value -> blockEntity.setSetupMode(value != 0));
        addIntDataSlot(() -> blockEntity.isRightClickEnabled() ? 1 : 0, value -> blockEntity.setRightClickEnabled(value != 0));
        addIntDataSlot(() -> blockEntity.isOutputFull() ? 1 : 0, value -> blockEntity.setOutputFull(value != 0));

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
                addSlot(new Slot(playerInventory, index, 80 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 80 + col * 18, 142));
        }

        for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
            addSlot(new Slot(playerInventory, 39 - armorSlot, 8, 84 + armorSlot * 18));
        }

        addSlot(new Slot(playerInventory, 40, 8, 154));
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

    public boolean isRightClickEnabled() {
        return blockEntity.isRightClickEnabled();
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (slotId >= ICON_SLOT_START && slotId < ICON_SLOT_START + ICON_SLOT_COUNT) {
            if (!blockEntity.isSetupMode()) {
                blockEntity.setActiveSetupIndex(slotId);
                return;
            }
        }

        int playerSlotIndex = toWardrobeSlotIndex(slotId);
        if (playerSlotIndex >= 0) {
            if (blockEntity.isSetupMode()) {
                handleSetupSlotClick(player, playerSlotIndex, slotId, button);
                return;
            } else {
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

        if (button == 1) {
            config.clear();
            blockEntity.markUpdated();
            return;
        }

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
        blockEntity.markUpdated();
    }

    private int toWardrobeSlotIndex(int slotId) {
        if (slotId < PLAYER_SLOT_START) {
            return -1;
        }
        Slot slot = getSlot(slotId);
        if (!(slot.container instanceof Inventory)) {
            return -1;
        }
        int invIndex = slot.getSlotIndex();
        if (invIndex < 9) {
            return invIndex;
        }
        if (invIndex < 36) {
            return invIndex;
        }
        if (invIndex < 40) {
            return 36 + (invIndex - 36);
        }
        if (invIndex == 40) {
            return 40;
        }
        return -1;
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
