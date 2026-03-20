package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateGTCCircuit {

    private final BlockPos pos;
    private final int circuit;

    public PacketUpdateGTCCircuit(BlockPos pos, int circuit) {
        this.pos = pos;
        this.circuit = circuit;
    }

    public static void encode(PacketUpdateGTCCircuit msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.circuit);
    }

    public static PacketUpdateGTCCircuit decode(FriendlyByteBuf buf) {
        return new PacketUpdateGTCCircuit(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketUpdateGTCCircuit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                port.gtcCircuit = msg.circuit;
                port.setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
