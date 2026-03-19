package com.logisticsports.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

public class ModConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new ClientConfig(builder);
        CLIENT_SPEC = builder.build();
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue renderIndicator;
        public final ForgeConfigSpec.BooleanValue rotateIndicator;
        public final ForgeConfigSpec.IntValue renderDistance;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.push("client");

            renderIndicator = builder
                    .comment("Whether to draw the output item (indicator) on the Access Port block.")
                    .translation("config.logisticsports.render_indicator")
                    .define("renderIndicator", true);

            rotateIndicator = builder
                    .comment("Whether to rotate the output item (indicator) if it is drawn.")
                    .translation("config.logisticsports.rotate_indicator")
                    .define("rotateIndicator", true);

            renderDistance = builder
                    .comment("Maximum distance (in blocks) at which the output item (indicator) will be rendered.")
                    .translation("config.logisticsports.render_distance")
                    .defineInRange("renderDistance", 10, 1, 100);

            builder.pop();
        }
    }

    public static void register(ModLoadingContext context) {
        context.registerConfig(Type.CLIENT, CLIENT_SPEC);
    }
}
