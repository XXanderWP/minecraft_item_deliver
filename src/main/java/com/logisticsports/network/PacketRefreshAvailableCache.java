package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRefreshAvailableCache {

    private final BlockPos pos;

    public PacketRefreshAvailableCache(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketRefreshAvailableCache msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketRefreshAvailableCache decode(FriendlyByteBuf buf) {
        return new PacketRefreshAvailableCache(buf.readBlockPos());
    }

    public static void handle(PacketRefreshAvailableCache msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.level().getBlockEntity(msg.pos) instanceof AccessPortBlockEntity be && be.getLevel() != null) {
                long currentTime = be.getLevel().getGameTime();
                if (currentTime - be.lastRefreshTime >= 100) {
                    be.lastRefreshTime = currentTime;
                    be.refreshAvailableCache();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
