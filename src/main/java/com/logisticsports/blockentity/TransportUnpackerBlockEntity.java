package com.logisticsports.blockentity;

import com.logisticsports.item.TransportReservoirItem;
import com.logisticsports.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransportUnpackerBlockEntity extends BlockEntity {

    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final Direction[] lastInsertSide = new Direction[9];

    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> inventory);

    private final IItemHandler[] sideHandlers = new IItemHandler[6];

    public TransportUnpackerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TRANSPORT_UNPACKER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TransportUnpackerBlockEntity be) {
        if (level.isClientSide) return;

        // 1. Пытаемся распаковать резервуары
        for (int i = 0; i < be.inventory.getSlots(); i++) {
            ItemStack stack = be.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof TransportReservoirItem) {
                FluidStack fluid = TransportReservoirItem.getFluid(stack);
                if (!fluid.isEmpty()) {
                    // Пытаемся вытолкнуть жидкость в соседние блоки
                    int drained = be.pushFluid(fluid);
                    if (drained > 0) {
                        fluid.shrink(drained);
                        TransportReservoirItem.setFluid(stack, fluid);
                        be.setChanged();
                    }
                }
                
                // Если после слива жидкости (или изначально) резервуар пуст - удаляем его
                if (TransportReservoirItem.getFluid(stack).isEmpty()) {
                    be.inventory.setStackInSlot(i, ItemStack.EMPTY);
                    be.setChanged();
                }
            }
        }

        // 2. Выталкиваем предметы в соседние блоки (всенаправленно)
        for (int i = 0; i < be.inventory.getSlots(); i++) {
            ItemStack stack = be.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Если это резервуар - его мы уже обработали или удалили выше
            if (stack.getItem() instanceof TransportReservoirItem) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                if (dir == be.lastInsertSide[i]) continue;

                BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                if (neighbor != null) {
                    var cap = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite());
                    if (cap.isPresent()) {
                        IItemHandler handler = cap.orElse(null);
                        ItemStack remainder = net.minecraftforge.items.ItemHandlerHelper.insertItem(handler, stack, true);
                        if (remainder.getCount() < stack.getCount()) {
                            int toExtract = stack.getCount() - remainder.getCount();
                            ItemStack extracted = be.inventory.extractItem(i, toExtract, false);
                            net.minecraftforge.items.ItemHandlerHelper.insertItem(handler, extracted, false);
                            break;
                        }
                    }
                }
            }
        }
    }

    private int pushFluid(FluidStack fluid) {
        if (level == null) return 0;
        int totalDrained = 0;
        FluidStack toPush = fluid.copy();

        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor != null) {
                var cap = neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite());
                if (cap.isPresent()) {
                    IFluidHandler handler = cap.orElse(null);
                    int accepted = handler.fill(toPush, IFluidHandler.FluidAction.EXECUTE);
                    totalDrained += accepted;
                    toPush.shrink(accepted);
                    if (toPush.isEmpty()) break;
                }
            }
        }
        return totalDrained;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) return itemHandler.cast();
            int index = side.ordinal();
            if (sideHandlers[index] == null) {
                sideHandlers[index] = new SideItemHandler(inventory, side);
            }
            return LazyOptional.of(() -> sideHandlers[index]).cast();
        }
        return super.getCapability(cap, side);
    }

    private class SideItemHandler implements IItemHandler {
        private final IItemHandler parent;
        private final Direction side;

        public SideItemHandler(IItemHandler parent, Direction side) {
            this.parent = parent;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return parent.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return parent.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            ItemStack result = parent.insertItem(slot, stack, simulate);
            if (!simulate && result.getCount() < stack.getCount()) {
                lastInsertSide[slot] = side;
            }
            return result;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return parent.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return parent.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return parent.isItemValid(slot, stack);
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        for (int i = 0; i < 9; i++) {
            if (lastInsertSide[i] != null) {
                tag.putInt("lastSide" + i, lastInsertSide[i].ordinal());
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        for (int i = 0; i < 9; i++) {
            if (tag.contains("lastSide" + i)) {
                lastInsertSide[i] = Direction.values()[tag.getInt("lastSide" + i)];
            }
        }
    }
}
