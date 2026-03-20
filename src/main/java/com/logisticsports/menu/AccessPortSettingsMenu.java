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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import com.logisticsports.network.PacketUpdateFluidRecipe;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import java.util.List;
import java.util.ArrayList;

public class AccessPortSettingsMenu extends AbstractContainerMenu {

    public final AccessPortBlockEntity blockEntity;
    public final ItemStackHandler recipeHandler;
    public final ItemStackHandler indicatorHandler;

    public List<String> availableRecipients = new ArrayList<>();

    public AccessPortSettingsMenu(int containerId, Inventory playerInventory, AccessPortBlockEntity blockEntity) {
        super(ModRegistry.ACCESS_PORT_SETTINGS_MENU.get(), containerId);
        int BG_HEIGHT = 253;
        int BG_WIDTH = 200;
        int invTop = BG_HEIGHT - 18 * 4 - 8;
        int invOffsetY = 1;
        this.blockEntity = blockEntity;

        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        this.recipeHandler = new ItemStackHandler(18);
        for (int i = 0; i < 18; i++) {
            recipeHandler.setStackInSlot(i, blockEntity.recipe.get(i));
        }

        this.indicatorHandler = new ItemStackHandler(1);
        indicatorHandler.setStackInSlot(0, blockEntity.indicator);

        // Ghost слоты рецепта
        for (int i = 0; i < activeRecipeSlots; i++) {
            int row = i / 9;
            int col = i % 9;
            addSlot(new SlotItemHandler(recipeHandler, i, 8 + col * 18, 30 + row * 18 + invOffsetY) {
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
        if (slotId >= 100 && slotId < 110) { // Наши виртуальные слоты жидкости
            int fluidSlotIndex = slotId - 100;
            if (fluidSlotIndex >= AccessPortBlockEntity.getFluidRecipeSlots()) return;

            ItemStack carried = getCarried();
            if (button == 0) { // ЛКМ
                if (!carried.isEmpty()) {
                    // Пытаемся вытащить жидкость из предмета
                    FluidUtil.getFluidContained(carried).ifPresent(fluid -> {
                        if (!fluid.isEmpty()) {
                            FluidStack copy = fluid.copy();
                            copy.setAmount(1000); // По умолчанию 1 ведро для рецепта
                            syncFluid(fluidSlotIndex, copy);
                        }
                    });
                }
            } else if (button == 1) { // ПКМ
                syncFluid(fluidSlotIndex, FluidStack.EMPTY);
            }
            return;
        }

        if (slotId < 0 || slotId >= slots.size()) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        Slot slot = slots.get(slotId);
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        boolean isRecipeSlot = slotId >= 0 && slotId < activeRecipeSlots;
        boolean isIndicatorSlot = slotId == activeRecipeSlots;

        if (isRecipeSlot || isIndicatorSlot) {
            ItemStack carried = getCarried();
            ItemStack current = slot.getItem();

            if (button == 0) {
                // ЛКМ
                if (!carried.isEmpty()) {
                    // Ставим или заменяем предмет
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    slot.set(copy);
                    syncSlot(slotId, copy);
                } else if (!current.isEmpty()) {
                    // Клик пустой рукой увеличивает на 1
                    scrollSlot(slotId, 1);
                }
            } else if (button == 1) {
                // ПКМ — убираем предмет
                slot.set(ItemStack.EMPTY);
                syncSlot(slotId, ItemStack.EMPTY);
            }
            return;
        }

        // Обычные слоты инвентаря — стандартная логика
        super.clicked(slotId, button, clickType, player);
    }

    public void scrollSlot(int slotId, int amount) {
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        if (slotId < 0 || slotId >= activeRecipeSlots + 1) return; // Разрешаем скролл для рецепта и индикатора
        Slot slot = slots.get(slotId);
        ItemStack current = slot.getItem();
        if (current.isEmpty()) return;

        int newCount = current.getCount() + amount;

        if (newCount < 1) {
            newCount = 1; // Минимум 1 предмет
        }
        
        int max = current.getMaxStackSize();
        if (newCount > max) newCount = max;
        
        if (newCount != current.getCount()) {
            ItemStack updated = current.copy();
            updated.setCount(newCount);
            slot.set(updated);
            syncSlot(slotId, updated);
        }
    }

    private void syncSlot(int slotId, ItemStack stack) {
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        if (slotId < activeRecipeSlots) {
            blockEntity.recipe.set(slotId, stack);
        } else if (slotId == activeRecipeSlots) {
            blockEntity.indicator = stack;
        }
        blockEntity.setChanged();
        ModNetwork.CHANNEL.sendToServer(new PacketUpdateRecipeSlot(slotId, stack));
    }

    public void syncFluid(int slot, FluidStack fluid) {
        if (slot >= 0 && slot < blockEntity.fluidsRecipe.size()) {
            blockEntity.fluidsRecipe.set(slot, fluid);
            blockEntity.setChanged();
            if (blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide) {
                ModNetwork.CHANNEL.sendToServer(new PacketUpdateFluidRecipe(slot, fluid));
            }
        }
    }

    public static AccessPortSettingsMenu create(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        List<String> recipients = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        net.minecraft.nbt.CompoundTag tag = buf.readNbt();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof AccessPortBlockEntity port) {
            if (tag != null) {
                port.load(tag);
            }
            AccessPortSettingsMenu menu = new AccessPortSettingsMenu(containerId, playerInventory, port);
            menu.availableRecipients = recipients;
            return menu;
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