package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPlaceMultiportOrder {
    private final BlockPos pos;
    private final int batches;
    private final int slotIndex;

    public PacketPlaceMultiportOrder(BlockPos pos, int batches, int slotIndex) {
        this.pos = pos;
        this.batches = batches;
        this.slotIndex = slotIndex;
    }

    public static void encode(PacketPlaceMultiportOrder msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.batches);
        buf.writeInt(msg.slotIndex);
    }

    public static PacketPlaceMultiportOrder decode(FriendlyByteBuf buf) {
        return new PacketPlaceMultiportOrder(buf.readBlockPos(), buf.readInt(), buf.readInt());
    }

    public static void handle(PacketPlaceMultiportOrder msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.placeOrder(player, msg.batches, msg.slotIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
