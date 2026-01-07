package com.yymod.wardrobe.network;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import com.yymod.wardrobe.client.WardrobeClientHandler;
import com.yymod.wardrobe.content.data.WardrobeFastTransferMode;
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
import java.util.Set;
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
                case SET_FAST_TRANSFER -> wardrobe.setFastTransferMode(WardrobeFastTransferMode.fromIndex(packet.index()));
                case TRANSFER_ALL -> wardrobe.transferItems(serverPlayer, null);
                case OPERATE_SLOT -> {
                    Set<Integer> slots = new HashSet<>();
                    slots.add(packet.index());
                    wardrobe.transferItems(serverPlayer, slots);
                }
                case ADJUST_COUNT -> wardrobe.adjustSlotCount(packet.index(), packet.adjustMax(), packet.delta(), packet.fast());
                case RENAME_SETUP -> wardrobe.renameSetup(packet.index(), packet.text());
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
}
