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

    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> inventory);

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
            }
        }

        // 2. Выталкиваем предметы в соседние блоки (всенаправленно)
        for (int i = 0; i < be.inventory.getSlots(); i++) {
            ItemStack stack = be.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Если это резервуар и он еще не пуст - не выталкиваем пока (опционально)
            if (stack.getItem() instanceof TransportReservoirItem && !TransportReservoirItem.getFluid(stack).isEmpty()) {
                continue;
            }

            for (Direction dir : Direction.values()) {
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
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
    }
}
