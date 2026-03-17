package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdatePackageMode {

    private final BlockPos pos;
    private final boolean packageMode;

    public PacketUpdatePackageMode(BlockPos pos, boolean packageMode) {
        this.pos = pos;
        this.packageMode = packageMode;
    }

    public static void encode(PacketUpdatePackageMode msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.packageMode);
    }

    public static PacketUpdatePackageMode decode(FriendlyByteBuf buf) {
        return new PacketUpdatePackageMode(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PacketUpdatePackageMode msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.packageMode = msg.packageMode;
                port.setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}