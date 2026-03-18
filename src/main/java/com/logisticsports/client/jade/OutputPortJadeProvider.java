package com.logisticsports.client.jade;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.blockentity.OutputPortBlockEntity;
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

public class OutputPortJadeProvider implements IBlockComponentProvider {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("logisticsports", "output_port");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof OutputPortBlockEntity be)) return;

        // Частота
        tooltip.add(Component.translatable("config.logisticsports.frequency_tooltip", be.frequency));

    }


    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}