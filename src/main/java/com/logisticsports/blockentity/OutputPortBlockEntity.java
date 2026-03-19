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

import java.util.ArrayList;
import java.util.List;

public class OutputPortBlockEntity extends BlockEntity {

    public static final int INVENTORY_SIZE = 20;
    public static final int PROCESSING_TIME = 40; // 2 second

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

    // Обработка предметов
    public static class ProcessingItem {
        public ItemStack stack;
        public Direction source;
        public int timer;

        public ProcessingItem(ItemStack stack, Direction source, int timer) {
            this.stack = stack;
            this.source = source;
            this.timer = timer;
        }

        public CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.put("stack", stack.serializeNBT());
            nbt.putInt("source", source.ordinal());
            nbt.putInt("timer", timer);
            return nbt;
        }

        public static ProcessingItem deserializeNBT(CompoundTag nbt) {
            return new ProcessingItem(
                    ItemStack.of(nbt.getCompound("stack")),
                    Direction.values()[nbt.getInt("source")],
                    nbt.getInt("timer")
            );
        }
    }

    public static class ProcessingFluid {
        public net.minecraftforge.fluids.FluidStack fluid;
        public int timer;

        public ProcessingFluid(net.minecraftforge.fluids.FluidStack fluid, int timer) {
            this.fluid = fluid;
            this.timer = timer;
        }

        public CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.put("fluid", fluid.writeToNBT(new CompoundTag()));
            nbt.putInt("timer", timer);
            return nbt;
        }

        public static ProcessingFluid deserializeNBT(CompoundTag nbt) {
            return new ProcessingFluid(
                    net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(nbt.getCompound("fluid")),
                    nbt.getInt("timer")
            );
        }
    }

    public final List<ProcessingItem> processingItems = new ArrayList<>();
    public ProcessingFluid processingFluid = null;

    public OutputPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.OUTPUT_PORT_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OutputPortBlockEntity be) {
        boolean changed = false;

        // Обработка предметов
        for (int i = 0; i < be.processingItems.size(); i++) {
            ProcessingItem pi = be.processingItems.get(i);
            pi.timer--;
            if (pi.timer <= 0) {
                be.insertItem(pi.stack);
                be.processingItems.remove(i);
                i--;
                changed = true;
            }
        }

        // Обработка жидкости
        if (be.processingFluid != null) {
            be.processingFluid.timer--;
            if (be.processingFluid.timer <= 0) {
                // Жидкость просто исчезает вникуда (визуальный эффект завершен),
                // так как в оригинале она тоже не имела места хранения в OutputPort,
                // кроме как в виде резервуара, но резервуар - это предмет.
                // Если AccessPort шлет жидкость НЕ в резервуаре, она должна была куда-то деться.
                // Но AccessPort всегда оборачивает жидкость в TRANSPORT_RESERVOIR.
                be.processingFluid = null;
                changed = true;
            }
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public void startProcessingItem(ItemStack stack, Direction source) {
        processingItems.add(new ProcessingItem(stack, source, PROCESSING_TIME));
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void startProcessingFluid(net.minecraftforge.fluids.FluidStack fluid) {
        processingFluid = new ProcessingFluid(fluid, PROCESSING_TIME);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

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

        net.minecraft.nbt.ListTag itemsTag = new net.minecraft.nbt.ListTag();
        for (ProcessingItem pi : processingItems) {
            itemsTag.add(pi.serializeNBT());
        }
        tag.put("processingItems", itemsTag);

        if (processingFluid != null) {
            tag.put("processingFluid", processingFluid.serializeNBT());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        frequency = tag.getInt("frequency");
        packageMode = tag.getBoolean("packageMode");
        recipient = tag.getString("recipient");

        processingItems.clear();
        net.minecraft.nbt.ListTag itemsTag = tag.getList("processingItems", 10);
        for (int i = 0; i < itemsTag.size(); i++) {
            processingItems.add(ProcessingItem.deserializeNBT(itemsTag.getCompound(i)));
        }

        if (tag.contains("processingFluid")) {
            processingFluid = ProcessingFluid.deserializeNBT(tag.getCompound("processingFluid"));
        } else {
            processingFluid = null;
        }
    }
}