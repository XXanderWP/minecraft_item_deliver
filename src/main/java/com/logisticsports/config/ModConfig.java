package com.logisticsports.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

public class ModConfig {
    
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;
    
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;
    
    static {
        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new ClientConfig(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
        
        ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
        SERVER = new ServerConfig(serverBuilder);
        SERVER_SPEC = serverBuilder.build();
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

    public static class ServerConfig {
        public final ForgeConfigSpec.BooleanValue checkUpdates;
        public final ForgeConfigSpec.IntValue updateCheckInterval;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.push("updates");

            checkUpdates = builder
                    .comment("Whether to check for mod updates.")
                    .translation("config.logisticsports.check_updates")
                    .define("checkUpdates", true);

            updateCheckInterval = builder
                    .comment("Interval between update checks in minutes (5 to 120).")
                    .translation("config.logisticsports.update_check_interval")
                    .defineInRange("updateCheckInterval", 30, 5, 120);

            builder.pop();
        }
    }

    public static void register(ModLoadingContext context) {
        context.registerConfig(Type.CLIENT, CLIENT_SPEC);
        context.registerConfig(Type.SERVER, SERVER_SPEC);
    }
}
