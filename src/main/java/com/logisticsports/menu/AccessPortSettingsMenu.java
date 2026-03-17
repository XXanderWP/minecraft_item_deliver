package com.logisticsports.menu;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.network.ModNetwork;
import com.logisticsports.network.PacketUpdateRecipeSlot;
import com.logisticsports.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class AccessPortSettingsMenu extends AbstractContainerMenu {

    public final AccessPortBlockEntity blockEntity;
    public final ItemStackHandler recipeHandler;
    public final ItemStackHandler indicatorHandler;

    public AccessPortSettingsMenu(int containerId, Inventory playerInventory, AccessPortBlockEntity blockEntity) {
        super(ModRegistry.ACCESS_PORT_SETTINGS_MENU.get(), containerId);
        int BG_HEIGHT = 253;
        int BG_WIDTH = 200;
        int invTop = BG_HEIGHT - 18 * 4 - 8;
        int invOffsetY = 1;
        this.blockEntity = blockEntity;

        this.recipeHandler = new ItemStackHandler(9);
        for (int i = 0; i < 9; i++) {
            recipeHandler.setStackInSlot(i, blockEntity.recipe.get(i));
        }

        this.indicatorHandler = new ItemStackHandler(1);
        indicatorHandler.setStackInSlot(0, blockEntity.indicator);

        // 9 ghost слотов рецепта в ряд
        for (int i = 0; i < 9; i++) {
            final int index = i;
            addSlot(new SlotItemHandler(recipeHandler, i, 8 + i * 18, 30 + invOffsetY) {
                @Override
                public boolean mayPickup(Player player) { return false; }
                @Override
                public boolean mayPlace(ItemStack stack) { return true; }
            });
        }

        // Ghost слот индикатора
        addSlot(new SlotItemHandler(indicatorHandler, 0, BG_WIDTH - 26 + 1, 3 + invOffsetY) {
            @Override
            public boolean mayPickup(Player player) { return false; }
            @Override
            public boolean mayPlace(ItemStack stack) { return true; }
        });



// Инвентарь игрока
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invTop + row * 18 + invOffsetY));
            }
        }

// Хотбар
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, invTop + 3 * 18 + 4 + invOffsetY));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= slots.size()) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        Slot slot = slots.get(slotId);
        boolean isRecipeSlot = slotId >= 0 && slotId < 9;
        boolean isIndicatorSlot = slotId == 9;

        if (isRecipeSlot || isIndicatorSlot) {
            ItemStack carried = getCarried();
            ItemStack current = slot.getItem();

            if (clickType == ClickType.PICKUP) {
                if (button == 0) {
                    // ЛКМ
                    if (!carried.isEmpty()) {
                        // Ставим или заменяем предмет
                        ItemStack copy = carried.copy();
                        copy.setCount(1);
                        slot.set(copy);
                        syncSlot(slotId, copy);
                    }
                    // ЛКМ без предмета в руке — ничего не делаем
                } else if (button == 1) {
                    // ПКМ — убираем предмет
                    slot.set(ItemStack.EMPTY);
                    syncSlot(slotId, ItemStack.EMPTY);
                }
            }
            return;
        }

        // Обычные слоты инвентаря — стандартная логика
        super.clicked(slotId, button, clickType, player);
    }

    public void scrollSlot(int slotId, boolean up) {
        if (slotId < 0 || slotId > 9) return; // было slotId >= 9, теперь > 9
        Slot slot = slots.get(slotId);
        ItemStack current = slot.getItem();
        if (current.isEmpty()) return;

        int delta = up ? 1 : -1;
        int newCount = current.getCount() + delta;

        if (newCount <= 0) {
            slot.set(ItemStack.EMPTY);
            syncSlot(slotId, ItemStack.EMPTY);
        } else {
            int max = current.getMaxStackSize();
            if (newCount > max) newCount = max;
            ItemStack updated = current.copy();
            updated.setCount(newCount);
            slot.set(updated);
            syncSlot(slotId, updated);
        }
    }

    private void syncSlot(int slotId, ItemStack stack) {
        if (slotId < 9) {
            blockEntity.recipe.set(slotId, stack);
        } else if (slotId == 9) {
            blockEntity.indicator = stack;
        }
        blockEntity.setChanged();
        ModNetwork.CHANNEL.sendToServer(new PacketUpdateRecipeSlot(slotId, stack));
    }

    public static AccessPortSettingsMenu create(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof AccessPortBlockEntity port) {
            return new AccessPortSettingsMenu(containerId, playerInventory, port);
        }
        throw new IllegalStateException("No AccessPortSettingsMenu at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // Публичная обёртка для JEI
    public void syncSlotPublic(int slotId, ItemStack stack) {
        syncSlot(slotId, stack);
    }
}