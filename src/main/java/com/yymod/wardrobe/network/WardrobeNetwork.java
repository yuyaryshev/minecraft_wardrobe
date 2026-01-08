package com.yymod.wardrobe.network;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.client.WardrobeClientHandler;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
import com.yymod.wardrobe.content.data.WardrobePlayerSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class WardrobeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(YYWardrobe.MOD_ID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int messageId = 0;
    private static final Map<UUID, PasteBuffer> PASTE_BUFFERS = new ConcurrentHashMap<>();

    public static void init() {
        CHANNEL.registerMessage(messageId++, WardrobeActionPacket.class, WardrobeActionPacket::encode,
                WardrobeActionPacket::decode, WardrobeNetwork::handleAction);
        CHANNEL.registerMessage(messageId++, WardrobeErrorPacket.class, WardrobeErrorPacket::encode,
                WardrobeErrorPacket::decode, WardrobeNetwork::handleError);
    }

    public static void sendToServer(WardrobeActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendError(ServerPlayer player, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WardrobeErrorPacket(message));
    }

    private static void handleAction(WardrobeActionPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Player sender = context.getSender();
            if (!(sender instanceof ServerPlayer serverPlayer)) {
                return;
            }
            Level level = serverPlayer.level();
            BlockEntity blockEntity = level.getBlockEntity(packet.pos());
            if (!(blockEntity instanceof WardrobeBlockEntity wardrobe)) {
                return;
            }
            switch (packet.action()) {
                case TOGGLE_MODE -> wardrobe.setSetupMode(packet.flag());
                case SELECT_SETUP -> wardrobe.setActiveSetupIndex(packet.index());
                case SET_FAST_TRANSFER -> WardrobePlayerSettings.setFastTransferMode(serverPlayer,
                        WardrobeFastTransferMode.fromIndex(packet.index()));
                case SET_SCAN_RANGE -> wardrobe.setArmorStandScanRange(packet.index());
                case TRANSFER_ALL -> wardrobe.transferItems(serverPlayer, null);
                case OPERATE_SLOT -> {
                    Set<Integer> slots = new HashSet<>();
                    slots.add(packet.index());
                    wardrobe.transferItems(serverPlayer, slots);
                }
                case ADJUST_COUNT -> wardrobe.adjustSlotCount(packet.index(), packet.adjustMax(), packet.delta(), packet.fast());
                case RENAME_SETUP -> wardrobe.renameSetup(packet.index(), packet.text());
                case PASTE_SETUP -> handlePasteSetup(packet, serverPlayer, wardrobe);
                case UNLOAD_SETUP -> {
                    wardrobe.setActiveSetupIndex(packet.index());
                    wardrobe.equipSetup(serverPlayer, packet.index());
                    wardrobe.transferItems(serverPlayer, null);
                }
                case UNLOAD_ALL -> {
                    wardrobe.unloadAll(serverPlayer);
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleError(WardrobeErrorPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WardrobeClientHandler.handleError(packet.message()));
        });
        context.setPacketHandled(true);
    }

    private static void handlePasteSetup(WardrobeActionPacket packet, ServerPlayer player, WardrobeBlockEntity wardrobe) {
        if (!packet.flag()) {
            wardrobe.applySetupJson(packet.text(), player);
            return;
        }
        int chunkIndex = packet.index();
        int totalChunks = packet.delta();
        if (totalChunks <= 0) {
            wardrobe.applySetupJson(packet.text(), player);
            return;
        }
        PasteBuffer buffer = PASTE_BUFFERS.computeIfAbsent(player.getUUID(), id -> new PasteBuffer(totalChunks));
        if (buffer.totalChunks != totalChunks) {
            buffer = new PasteBuffer(totalChunks);
            PASTE_BUFFERS.put(player.getUUID(), buffer);
        }
        buffer.add(chunkIndex, packet.text());
        if (buffer.isComplete()) {
            PASTE_BUFFERS.remove(player.getUUID());
            wardrobe.applySetupJson(buffer.join(), player);
        }
    }

    private static final class PasteBuffer {
        private final int totalChunks;
        private final String[] chunks;
        private int received;

        private PasteBuffer(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new String[totalChunks];
        }

        private void add(int index, String text) {
            if (index < 0 || index >= totalChunks) {
                return;
            }
            if (chunks[index] == null) {
                received++;
            }
            chunks[index] = text == null ? "" : text;
        }

        private boolean isComplete() {
            return received >= totalChunks;
        }

        private String join() {
            StringBuilder builder = new StringBuilder();
            for (String chunk : chunks) {
                if (chunk != null) {
                    builder.append(chunk);
                }
            }
            return builder.toString();
        }
    }
}
