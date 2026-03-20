package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateRecipient {

    private final BlockPos pos;
    private final String recipient;

    public PacketUpdateRecipient(BlockPos pos, String recipient) {
        this.pos = pos;
        this.recipient = recipient;
    }

    public static void encode(PacketUpdateRecipient msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.recipient);
    }

    public static PacketUpdateRecipient decode(FriendlyByteBuf buf) {
        return new PacketUpdateRecipient(buf.readBlockPos(), buf.readUtf());
    }

    public static void handle(PacketUpdateRecipient msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.recipient = msg.recipient;
                port.setChanged();
                player.level().sendBlockUpdated(msg.pos, be.getBlockState(), be.getBlockState(), 3);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}