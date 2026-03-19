package com.logisticsports;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.logisticsports.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = LogisticsPorts.MODID)
public class UpdateChecker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/XXanderWP/minecraft_item_deliver/releases/latest";
    private static final String RELEASE_PAGE_URL = "https://github.com/XXanderWP/minecraft_item_deliver/releases/latest";
    
    private static String latestVersion = null;
    private static long lastCheckTime = 0;
    private static boolean updateAvailable = false;

    public static void init() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER || FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(UpdateChecker.class);
            checkForUpdates();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        if (!ModConfig.SERVER.checkUpdates.get()) return;

        long currentTime = System.currentTimeMillis();
        long intervalMs = ModConfig.SERVER.updateCheckInterval.get() * 60 * 1000L;

        if (currentTime - lastCheckTime > intervalMs) {
            checkForUpdates();
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (updateAvailable && event.getEntity() instanceof ServerPlayer player) {
            if (player.hasPermissions(2)) { // Operator permission level
                notifyPlayer(player);
            }
        }
    }

    private static void checkForUpdates() {
        lastCheckTime = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(GITHUB_RELEASES_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        String tagName = json.get("tag_name").getAsString();
                        
                        // GitHub tags often start with 'v', e.g., 'v1.1.0'
                        String remoteVersionStr = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                        
                        ArtifactVersion currentVersion = ModList.get().getModContainerById(LogisticsPorts.MODID)
                                .map(container -> container.getModInfo().getVersion())
                                .orElse(new DefaultArtifactVersion("0.0.0"));
                        
                        ArtifactVersion remoteVersion = new DefaultArtifactVersion(remoteVersionStr);

                        if (remoteVersion.compareTo(currentVersion) > 0) {
                            if (!updateAvailable || !remoteVersionStr.equals(latestVersion)) {
                                updateAvailable = true;
                                latestVersion = remoteVersionStr;
                                LOGGER.info("New version of Logistics Ports available: {}", remoteVersionStr);
                            }
                        } else {
                            updateAvailable = false;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates: {}", e.getMessage());
            }
        });
    }

    private static void notifyPlayer(ServerPlayer player) {
        Component message = Component.literal("[Logistics Ports] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("chat.logisticsports.update_available", latestVersion)
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" [")
                        .append(Component.translatable("chat.logisticsports.download")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.AQUA)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASE_PAGE_URL))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.logisticsports.click_to_open")))))
                        .append(Component.literal("]")));
        
        player.sendSystemMessage(message);
    }
}
