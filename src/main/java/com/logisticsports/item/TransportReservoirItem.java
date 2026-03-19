package com.logisticsports.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TransportReservoirItem extends Item {
    public static final int CAPACITY = 1000;

    public TransportReservoirItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.logisticsports.transport_reservoir.desc").withStyle(ChatFormatting.GRAY));

        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            tooltip.add(Component.translatable("config.logisticsports.fluid")
                    .append(fluid.getDisplayName())
                    .append(": " + fluid.getAmount() + "mB")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("config.logisticsports.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static FluidStack getFluid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Fluid")) {
            return FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
        }
        return FluidStack.EMPTY;
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) {
            stack.removeTagKey("Fluid");
        } else {
            CompoundTag tag = stack.getOrCreateTag();
            tag.put("Fluid", fluid.writeToNBT(new CompoundTag()));
        }
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidHandlerItemStack(stack, CAPACITY);
    }
}
