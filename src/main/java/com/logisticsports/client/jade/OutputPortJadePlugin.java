package com.logisticsports.client.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class OutputPortJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(
                new OutputPortJadeProvider(),
                com.logisticsports.block.OutputPortBlock.class
        );
    }
}