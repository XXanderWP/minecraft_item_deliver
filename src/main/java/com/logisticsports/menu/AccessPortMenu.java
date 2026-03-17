package com.logisticsports.menu;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AccessPortMenu extends AbstractContainerMenu {

    public final AccessPortBlockEntity blockEntity;

    public AccessPortMenu(int containerId, Inventory playerInventory, AccessPortBlockEntity blockEntity) {
        super(ModRegistry.ACCESS_PORT_MENU.get(), containerId);
        this.blockEntity = blockEntity;
    }

    public static AccessPortMenu create(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof AccessPortBlockEntity port) {
            return new AccessPortMenu(containerId, playerInventory, port);
        }
        throw new IllegalStateException("No AccessPortBlockEntity at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}