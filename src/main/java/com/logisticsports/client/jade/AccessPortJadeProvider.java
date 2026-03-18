package com.logisticsports.client.jade;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

public class AccessPortJadeProvider implements IBlockComponentProvider {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("logisticsports", "access_port");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof AccessPortBlockEntity be)) return;

        // Частота
        tooltip.add(Component.translatable("config.logisticsports.frequency_tooltip", be.frequency));

        // Индикатор (результат)
        if (!be.indicator.isEmpty()) {
            IElementHelper helper = IElementHelper.get();
            IElement icon = helper.item(be.indicator, 1f);
            tooltip.add(Component.translatable("config.logisticsports.result_tooltip", be.indicator.getHoverName().getString()
                    + (be.indicator.getCount() > 1 ? " x" + be.indicator.getCount() : "")));
        }

        // Рецепт
        List<ItemStack> grouped = getGrouped(be);
        if (!grouped.isEmpty()) {
            tooltip.add(Component.translatable("config.logisticsports.require_tooltip"));
            for (ItemStack stack : grouped) {
                int avail = be.getAvailableCount(stack);
                int needed = stack.getCount();
                String color = avail >= needed ? "§a" : "§c";
                tooltip.add(Component.literal("  §7" +
                        stack.getHoverName().getString() +
                        " §fx" + needed +
                        " " + color + "(" + avail + ")"));
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