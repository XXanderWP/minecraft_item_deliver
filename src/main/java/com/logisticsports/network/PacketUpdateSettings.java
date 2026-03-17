package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateSettings {

    private final BlockPos pos;
    private final boolean requireAll;

    public PacketUpdateSettings(BlockPos pos, boolean requireAll) {
        this.pos = pos;
        this.requireAll = requireAll;
    }

    public static void encode(PacketUpdateSettings msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.requireAll);
    }

    public static PacketUpdateSettings decode(FriendlyByteBuf buf) {
        return new PacketUpdateSettings(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PacketUpdateSettings msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.requireAll = msg.requireAll;
                port.setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}