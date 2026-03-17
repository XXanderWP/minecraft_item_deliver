package com.logisticsports.blockentity;

import com.logisticsports.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class OutputPortBlockEntity extends BlockEntity {

    public static final int INVENTORY_SIZE = 20;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> lazyInventory = LazyOptional.of(() -> inventory);

    public int frequency = 0;
    public boolean packageMode = false;
    public String recipient = "";

    public OutputPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.OUTPUT_PORT_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OutputPortBlockEntity be) {}

    public ItemStackHandler getInventory() {
        return inventory;
    }

    // Возвращает все предметы игроку при ПКМ
    public void giveAllToPlayer(Player player) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!player.addItem(stack.copy())) {
                    player.drop(stack.copy(), false);
                }
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    // Пытается добавить предмет в инвентарь, возвращает остаток
    public ItemStack insertItem(ItemStack stack) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            stack = inventory.insertItem(i, stack, false);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    // Сколько свободного места для данного предмета
    public int getFreeSpaceFor(ItemStack stack) {
        int space = 0;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing.isEmpty()) {
                space += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                space += existing.getMaxStackSize() - existing.getCount();
            }
        }
        return space;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return lazyInventory.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyInventory.invalidate();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putInt("frequency", frequency);
        tag.putBoolean("packageMode", packageMode);
        tag.putString("recipient", recipient);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        frequency = tag.getInt("frequency");
        packageMode = tag.getBoolean("packageMode");
        recipient = tag.getString("recipient");
    }
}