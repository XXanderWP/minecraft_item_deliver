package com.logisticsports.client.jade;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.client.Convert;
import com.logisticsports.network.ModNetwork;
import com.logisticsports.network.PacketRefreshAvailableCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;

public class AccessPortJadeProvider implements IBlockComponentProvider {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("logisticsports", "access_port");

    private static final java.util.Map<BlockPos, Long> LAST_REFRESH_CLIENT = new java.util.HashMap<>();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof AccessPortBlockEntity be)) return;

        // Запрос обновления кэша
        long now = System.currentTimeMillis();
        BlockPos pos = accessor.getPosition();
        if (now - LAST_REFRESH_CLIENT.getOrDefault(pos, 0L) > 5000) {
            LAST_REFRESH_CLIENT.put(pos, now);
            ModNetwork.CHANNEL.sendToServer(new PacketRefreshAvailableCache(pos));
        }

        if(be.isMultiport) {
            tooltip.add(Component.translatable("block.logisticsports.multiport_access"));
        }

        // Частота
        tooltip.add(Component.translatable("config.logisticsports.frequency_tooltip", be.frequency));

        // Индикатор (результат)
        if (!be.indicator.isEmpty()) {
            Component resultLabel = Component.translatable("config.logisticsports.result_tooltip", 
                    Component.empty().append(be.indicator.getHoverName())
                            .append(be.indicator.getCount() > 1 ? Component.literal(" x" + be.indicator.getCount()) : Component.empty()));
            tooltip.add(resultLabel);
        }

        // Рецепт
        List<ItemStack> grouped = getGrouped(be);
        boolean hasFluid = false;
        int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
        for (int i = 0; i < activeFluidSlots; i++) {
            if (!be.fluidsRecipe.get(i).isEmpty()) {
                hasFluid = true;
                break;
            }
        }

        if (!grouped.isEmpty() || hasFluid) {
            tooltip.add(Component.translatable( be.isMultiport ? "config.logisticsports.require_multiple_tooltip" : "config.logisticsports.require_tooltip"));
            for (ItemStack stack : grouped) {
                int avail = be.getAvailableCount(stack);
                int needed = stack.getCount();
                String color = avail >= needed ? "§a" : "§c";
                
                tooltip.add(Component.literal("  ").append(stack.getHoverName()).append(Component.literal(" §f" + Convert.ShowAmountString(needed, false) + " " + color + "(" + Convert.ShowAmountString(avail, true) + ")")));
            }
            if (hasFluid && !be.isMultiport) {
                for (int i = 0; i < activeFluidSlots; i++) {
                    var fluid = be.fluidsRecipe.get(i);
                    if (fluid.isEmpty()) continue;
                    
                    int avail = be.getAvailableFluidCount(fluid);
                    int needed = fluid.getAmount();
                    String color = avail >= needed ? "§a" : "§c";

                    tooltip.add(Component.literal("  ").append(fluid.getDisplayName()).append(Component.literal(" §f" + Convert.ShowAmountString(needed, false, true) + " " + color + "(" + Convert.ShowAmountString(avail, true, true) + ")")));
                }
            }
        }
    }

    private List<ItemStack> getGrouped(AccessPortBlockEntity be) {
        List<ItemStack> result = new ArrayList<>();
        for (var stack : be.recipe) {
            if (stack.isEmpty()) continue;
            boolean found = false;
            for (var existing : result) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    existing.grow(stack.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) result.add(stack.copy());
        }
        return result;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}