package com.yymod.wardrobe.content.block.entity;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.data.WardrobeSetup;
import com.yymod.wardrobe.content.menu.WardrobeMenu;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
import com.yymod.wardrobe.content.data.WardrobeSlotConfig;
import com.yymod.wardrobe.content.data.WardrobeSlotMode;
import com.yymod.wardrobe.content.transfer.WardrobeTransfer;
import com.yymod.wardrobe.content.transfer.WardrobeTransferResult;
import com.yymod.wardrobe.network.WardrobeNetwork;
import com.yymod.wardrobe.registry.WardrobeBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WardrobeBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SETUP_COUNT = 8;

    private final List<WardrobeSetup> setups = new ArrayList<>();
    private int activeSetup = 0;
    private boolean setupMode = true;
    private WardrobeFastTransferMode fastTransferMode = WardrobeFastTransferMode.RIGHT_CLICK;
    private boolean outputFull = false;
    private String lastError = "";

    public WardrobeBlockEntity(BlockPos pos, BlockState state) {
        super(WardrobeBlockEntities.WARDROBE.get(), pos, state);
        for (int i = 0; i < SETUP_COUNT; i++) {
            WardrobeSetup setup = new WardrobeSetup();
            setup.setName("Setup " + (i + 1));
            setups.add(setup);
        }
    }

    public WardrobeSetup getSetup(int index) {
        return setups.get(index);
    }

    public WardrobeSetup getActiveSetup() {
        return setups.get(activeSetup);
    }

    public int getActiveSetupIndex() {
        return activeSetup;
    }

    public void setActiveSetupIndex(int activeSetup) {
        this.activeSetup = Math.max(0, Math.min(SETUP_COUNT - 1, activeSetup));
        markUpdated();
    }

    public boolean isSetupMode() {
        return setupMode;
    }

    public boolean isOperationalMode() {
        return !setupMode;
    }

    public void setSetupMode(boolean setupMode) {
        this.setupMode = setupMode;
        markUpdated();
    }

    public WardrobeFastTransferMode getFastTransferMode() {
        return fastTransferMode;
    }

    public void setFastTransferMode(WardrobeFastTransferMode mode) {
        this.fastTransferMode = mode == null ? WardrobeFastTransferMode.NONE : mode;
        markUpdated();
    }

    public boolean isOutputFull() {
        return outputFull;
    }

    public void setOutputFull(boolean outputFull) {
        this.outputFull = outputFull;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
        markUpdated();
    }

    public void clearLastError() {
        this.lastError = "";
        markUpdated();
    }

    public WardrobeTransferResult transferItems(ServerPlayer player, @Nullable Set<Integer> onlySlots) {
        if (level == null || player instanceof FakePlayer) {
            return WardrobeTransferResult.empty();
        }
        try {
            WardrobeTransferResult result = WardrobeTransfer.execute(this, player, onlySlots);
            outputFull = result.outputFull();
            markUpdated();
            return result;
        } catch (Exception ex) {
            String message = "Wardrobe error: " + ex.getMessage();
            YYWardrobe.LOGGER.error(message, ex);
            setLastError(message);
            WardrobeTransfer.notifyPlayerError(player, message);
            WardrobeNetwork.sendError(player, message);
            return WardrobeTransferResult.error(message);
        }
    }

    public void adjustSlotCount(int slotIndex, boolean adjustMax, int delta, boolean fast) {
        WardrobeSlotConfig config = getActiveSetup().getSlot(slotIndex);
        if (!config.isBound()) {
            return;
        }
        ItemStack bound = config.getBoundItem();
        int maxStack = bound.isStackable() ? bound.getMaxStackSize() : 1;
        int current = adjustMax ? config.getMaxCount() : config.getMinCount();
        int updated;
        if (fast) {
            int half = Math.max(1, maxStack / 2);
            int[] steps = new int[]{0, half, maxStack};
            int idx = 0;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == current) {
                    idx = i;
                    break;
                }
            }
            int next = delta >= 0 ? Math.min(steps.length - 1, idx + 1) : Math.max(0, idx - 1);
            updated = steps[next];
        } else {
            updated = Math.max(0, Math.min(maxStack, current + delta));
        }

        if (adjustMax) {
            config.setMaxCount(updated);
            if (config.getMinCount() > updated) {
                config.setMinCount(updated);
            }
        } else {
            config.setMinCount(updated);
            if (config.getMaxCount() < updated) {
                config.setMaxCount(updated);
            }
        }
        if (config.getMode() == WardrobeSlotMode.NONE) {
            config.setMode(WardrobeSlotMode.BOTH);
        }
        markUpdated();
    }

    public void renameSetup(int setupIndex, String name) {
        if (setupIndex < 0 || setupIndex >= setups.size()) {
            return;
        }
        setups.get(setupIndex).setName(name);
        markUpdated();
    }

    public void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.yy_wardrobe.wardrobe");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new WardrobeMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("ActiveSetup", activeSetup);
        tag.putBoolean("SetupMode", setupMode);
        tag.putInt("FastTransferMode", fastTransferMode.ordinal());
        tag.putString("LastError", lastError);

        ListTag setupList = new ListTag();
        for (WardrobeSetup setup : setups) {
            CompoundTag setupTag = new CompoundTag();
            setup.save(setupTag);
            setupList.add(setupTag);
        }
        tag.put("Setups", setupList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        activeSetup = tag.getInt("ActiveSetup");
        setupMode = tag.getBoolean("SetupMode");
        if (tag.contains("FastTransferMode")) {
            fastTransferMode = WardrobeFastTransferMode.fromIndex(tag.getInt("FastTransferMode"));
        } else {
            fastTransferMode = tag.getBoolean("EnableRightClick")
                    ? WardrobeFastTransferMode.RIGHT_CLICK
                    : WardrobeFastTransferMode.NONE;
        }
        lastError = tag.getString("LastError");

        ListTag setupList = tag.getList("Setups", Tag.TAG_COMPOUND);
        for (int i = 0; i < setups.size(); i++) {
            if (i < setupList.size()) {
                setups.get(i).load(setupList.getCompound(i));
            }
        }
    }

    public void saveToItem(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        stack.addTagElement("BlockEntityTag", tag);
    }

    public void loadFromItem(CompoundTag tag) {
        load(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
