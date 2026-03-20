package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.menu.AccessPortMenu;
import com.logisticsports.menu.AccessPortSettingsMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketUpdateRecipeSlot {

    private final int slot;
    private final ItemStack stack;

    public PacketUpdateRecipeSlot(int slot, ItemStack stack) {
        this.slot = slot;
        this.stack = stack;
    }

    public static void encode(PacketUpdateRecipeSlot msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slot);
        buf.writeItem(msg.stack);
    }

    public static PacketUpdateRecipeSlot decode(FriendlyByteBuf buf) {
        return new PacketUpdateRecipeSlot(buf.readInt(), buf.readItem());
    }

    public static void handle(PacketUpdateRecipeSlot msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof AccessPortSettingsMenu menu) {
                int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
                if (msg.slot < activeRecipeSlots) {
                    menu.blockEntity.recipe.set(msg.slot, msg.stack);
                } else if (msg.slot == activeRecipeSlots) {
                    menu.blockEntity.indicator = msg.stack;
                }
                menu.blockEntity.setChanged();
                menu.blockEntity.syncToClient();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}