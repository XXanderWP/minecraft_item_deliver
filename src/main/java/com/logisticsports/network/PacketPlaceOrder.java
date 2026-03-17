package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPlaceOrder {

    private final BlockPos pos;
    private final int batches;

    public PacketPlaceOrder(BlockPos pos, int batches) {
        this.pos = pos;
        this.batches = batches;
    }

    public static void encode(PacketPlaceOrder msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.batches);
    }

    public static PacketPlaceOrder decode(FriendlyByteBuf buf) {
        return new PacketPlaceOrder(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketPlaceOrder msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.placeOrder(player, msg.batches);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}