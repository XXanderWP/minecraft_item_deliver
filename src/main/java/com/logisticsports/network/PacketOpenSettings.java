package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class PacketOpenSettings {

    private final BlockPos pos;

    public PacketOpenSettings(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketOpenSettings msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketOpenSettings decode(FriendlyByteBuf buf) {
        return new PacketOpenSettings(buf.readBlockPos());
    }

    public static void handle(PacketOpenSettings msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                    @Override
                    public net.minecraft.network.chat.Component getDisplayName() {
                        return net.minecraft.network.chat.Component.literal("Настройки");
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                            int id, net.minecraft.world.entity.player.Inventory inv,
                            net.minecraft.world.entity.player.Player p) {
                        return new com.logisticsports.menu.AccessPortSettingsMenu(id, inv, port);
                    }
                }, buf -> buf.writeBlockPos(msg.pos));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}