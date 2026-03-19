package com.logisticsports.network;

import com.logisticsports.LogisticsPorts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(LogisticsPorts.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );



    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++,
                PacketUpdateRecipeSlot.class,
                PacketUpdateRecipeSlot::encode,
                PacketUpdateRecipeSlot::decode,
                PacketUpdateRecipeSlot::handle
        );

        CHANNEL.registerMessage(id++,
                PacketOpenSettings.class,
                PacketOpenSettings::encode,
                PacketOpenSettings::decode,
                PacketOpenSettings::handle
        );

        CHANNEL.registerMessage(id++,
                PacketPlaceOrder.class,
                PacketPlaceOrder::encode,
                PacketPlaceOrder::decode,
                PacketPlaceOrder::handle
        );

        CHANNEL.registerMessage(id++,
                PacketOpenMainScreen.class,
                PacketOpenMainScreen::encode,
                PacketOpenMainScreen::decode,
                PacketOpenMainScreen::handle
        );

        CHANNEL.registerMessage(id++,
                PacketUpdateFrequency.class,
                PacketUpdateFrequency::encode,
                PacketUpdateFrequency::decode,
                PacketUpdateFrequency::handle
        );

        CHANNEL.registerMessage(id++,
                PacketUpdateSettings.class,
                PacketUpdateSettings::encode,
                PacketUpdateSettings::decode,
                PacketUpdateSettings::handle
        );

        CHANNEL.registerMessage(id++,
                PacketUpdatePackageMode.class,
                PacketUpdatePackageMode::encode,
                PacketUpdatePackageMode::decode,
                PacketUpdatePackageMode::handle
        );

        CHANNEL.registerMessage(id++,
                PacketUpdateRecipient.class,
                PacketUpdateRecipient::encode,
                PacketUpdateRecipient::decode,
                PacketUpdateRecipient::handle
        );

        CHANNEL.registerMessage(id++,
                PacketUpdateFluidRecipe.class,
                PacketUpdateFluidRecipe::encode,
                PacketUpdateFluidRecipe::decode,
                PacketUpdateFluidRecipe::handle
        );

        CHANNEL.registerMessage(id++,
                PacketRefreshAvailableCache.class,
                PacketRefreshAvailableCache::encode,
                PacketRefreshAvailableCache::decode,
                PacketRefreshAvailableCache::handle
        );
    }
}