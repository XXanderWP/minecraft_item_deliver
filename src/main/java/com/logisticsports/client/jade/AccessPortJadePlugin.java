package com.logisticsports.client.jade;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class AccessPortJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(
                new AccessPortJadeProvider(),
                com.logisticsports.block.AccessPortBlock.class
        );
    }
}