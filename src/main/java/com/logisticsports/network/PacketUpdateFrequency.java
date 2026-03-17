package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateFrequency {

    private final BlockPos pos;
    private final int frequency;

    public PacketUpdateFrequency(BlockPos pos, int frequency) {
        this.pos = pos;
        this.frequency = frequency;
    }

    public static void encode(PacketUpdateFrequency msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.frequency);
    }

    public static PacketUpdateFrequency decode(FriendlyByteBuf buf) {
        return new PacketUpdateFrequency(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketUpdateFrequency msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.frequency = msg.frequency;
                port.setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}