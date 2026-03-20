package com.logisticsports.network;

import com.logisticsports.menu.AccessPortSettingsMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateFluidRecipe {
    private final FluidStack fluid;

    public PacketUpdateFluidRecipe(FluidStack fluid) {
        this.fluid = fluid;
    }

    public static void encode(PacketUpdateFluidRecipe msg, FriendlyByteBuf buf) {
        buf.writeFluidStack(msg.fluid);
    }

    public static PacketUpdateFluidRecipe decode(FriendlyByteBuf buf) {
        return new PacketUpdateFluidRecipe(buf.readFluidStack());
    }

    public static void handle(PacketUpdateFluidRecipe msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof AccessPortSettingsMenu menu) {
                menu.blockEntity.fluidRecipe = msg.fluid;
                menu.blockEntity.setChanged();
                player.level().sendBlockUpdated(menu.blockEntity.getBlockPos(), menu.blockEntity.getBlockState(), menu.blockEntity.getBlockState(), 3);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
