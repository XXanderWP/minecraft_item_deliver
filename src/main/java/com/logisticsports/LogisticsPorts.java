package com.logisticsports;

import com.logisticsports.config.ModConfig;
import com.logisticsports.network.ModNetwork;
import com.logisticsports.registry.ModRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LogisticsPorts.MODID)
public class LogisticsPorts {

    public static final String MODID = "logisticsports";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LogisticsPorts(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModConfig.register(context);

        modEventBus.addListener(this::commonSetup);

        ModRegistry.BLOCKS.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.BLOCK_ENTITIES.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        ModRegistry.MENUS.register(modEventBus);
        ModRegistry.CREATIVE_TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("Logistics Ports loaded.");
    }
}