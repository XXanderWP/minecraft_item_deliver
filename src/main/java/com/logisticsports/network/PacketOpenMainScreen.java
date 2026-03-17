package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class PacketOpenMainScreen {

    private final BlockPos pos;

    public PacketOpenMainScreen(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketOpenMainScreen msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketOpenMainScreen decode(FriendlyByteBuf buf) {
        return new PacketOpenMainScreen(buf.readBlockPos());
    }

    public static void handle(PacketOpenMainScreen msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.refreshAvailableCache();
                NetworkHooks.openScreen(player, port, buf -> buf.writeBlockPos(msg.pos));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}