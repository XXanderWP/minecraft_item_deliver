package com.logisticsports.network;

import com.logisticsports.menu.AccessPortSettingsMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateMultiportMode {
    private final boolean isMultiport;

    public PacketUpdateMultiportMode(boolean isMultiport) {
        this.isMultiport = isMultiport;
    }

    public static void encode(PacketUpdateMultiportMode msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isMultiport);
    }

    public static PacketUpdateMultiportMode decode(FriendlyByteBuf buf) {
        return new PacketUpdateMultiportMode(buf.readBoolean());
    }

    public static void handle(PacketUpdateMultiportMode msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof AccessPortSettingsMenu menu) {
                menu.blockEntity.isMultiport = msg.isMultiport;
                menu.blockEntity.setChanged();
                menu.blockEntity.syncToClient();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
