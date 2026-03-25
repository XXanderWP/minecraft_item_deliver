package com.logisticsports.network;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;
import java.util.ArrayList;

import java.util.function.Supplier;

public class PacketOpenSettings {

    private final BlockPos pos;

    public PacketOpenSettings(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketOpenSettings msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        
        // Сканируем доступных получателей (только на сервере, но метод вызывается перед отправкой)
        // На самом деле этот метод вызывается при отправке пакета С КЛИЕНТА на СЕРВЕР или наоборот?
        // PacketOpenSettings отправляется с КЛИЕНТА на СЕРВЕР (в AccessPortScreen.openSettings).
        // Так что здесь нечего сканировать.
    }

    public static PacketOpenSettings decode(FriendlyByteBuf buf) {
        return new PacketOpenSettings(buf.readBlockPos());
    }

    public static void handle(PacketOpenSettings msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            System.out.println("[DEBUG_LOG] Handling PacketOpenSettings for " + player.getName().getString() + " at " + msg.pos);
            var be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AccessPortBlockEntity port) {
                List<String> recipients = new java.util.ArrayList<>();
                net.minecraft.world.level.Level level = player.level();
                int radius = 64;
                
                System.out.println("[DEBUG_LOG] Scanning for OutputPorts and clipboards in radius " + radius);
                // 1. Scan for OutputPorts (Optimized using SavedData)
                com.logisticsports.world.OutputPortSavedData savedData = com.logisticsports.world.OutputPortSavedData.get(level);
                for (BlockPos p : savedData.getPositions()) {
                    if (p.distSqr(msg.pos) <= radius * radius) {
                        net.minecraft.world.level.block.entity.BlockEntity neighborBE = level.getBlockEntity(p);
                        if (neighborBE instanceof com.logisticsports.blockentity.OutputPortBlockEntity outputPort) {
                            if (outputPort.frequency == port.frequency && !outputPort.recipient.isEmpty()) {
                                if (!recipients.contains(outputPort.recipient)) {
                                    recipients.add(outputPort.recipient);
                                    System.out.println("[DEBUG_LOG] Found OutputPort suggestion: " + outputPort.recipient);
                                }
                            }
                        }
                    }
                }

                // 2. Scan for create:clipboard in world (Reduced radius for performance)
                int clipRadius = 8;
                for (BlockPos p : BlockPos.betweenClosed(
                        msg.pos.offset(-clipRadius, -clipRadius, -clipRadius),
                        msg.pos.offset(clipRadius, clipRadius, clipRadius))) {
                    net.minecraft.world.level.block.entity.BlockEntity neighborBE = level.getBlockEntity(p);
                    if (neighborBE != null) {
                        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(neighborBE.getType());
                        if (id != null && id.toString().equals("create:clipboard")) {
                            System.out.println("[DEBUG_LOG] Found clipboard in world at " + p);
                            extractClipboardSuggestions(neighborBE.saveWithFullMetadata(), recipients, 0);
                        }
                    }
                }

                // 3. Scan player inventory for create:clipboard
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;
                    net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemId.toString().equals("create:clipboard")) {
                        System.out.println("[DEBUG_LOG] Found clipboard in inventory slot " + i);
                        net.minecraft.nbt.CompoundTag tag = stack.getTag();
                        if (tag != null) {
                            if (tag.contains("BlockEntityTag")) {
                                extractClipboardSuggestions(tag.getCompound("BlockEntityTag"), recipients, 0);
                            } else {
                                // Try direct tag (some items store content directly)
                                extractClipboardSuggestions(tag, recipients, 0);
                            }
                        } else {
                            System.out.println("[DEBUG_LOG] Clipboard in inventory has no NBT tag");
                        }
                    }
                }
                
                System.out.println("[DEBUG_LOG] Total suggestions found: " + recipients.size() + " -> " + recipients);

                NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                    @Override
                    public net.minecraft.network.chat.Component getDisplayName() {
                        return net.minecraft.network.chat.Component.literal("Настройки");
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                            int id, net.minecraft.world.entity.player.Inventory inv,
                            net.minecraft.world.entity.player.Player p) {
                        com.logisticsports.menu.AccessPortSettingsMenu menu = new com.logisticsports.menu.AccessPortSettingsMenu(id, inv, port);
                        menu.availableRecipients = recipients;
                        return menu;
                    }
                }, buf -> {
                    buf.writeBlockPos(msg.pos);
                    buf.writeCollection(recipients, FriendlyByteBuf::writeUtf);
                    // Передаем только нужные теги, чтобы избежать переполнения буфера пакета
                    net.minecraft.nbt.CompoundTag updateTag = new net.minecraft.nbt.CompoundTag();
                    updateTag.putInt("frequency", port.frequency);
                    updateTag.putInt("gtcCircuit", port.gtcCircuit);
                    updateTag.putBoolean("isMultiport", port.isMultiport);
                    updateTag.putBoolean("requireAll", port.requireAll);
                    updateTag.putBoolean("packageMode", port.packageMode);
                    updateTag.putString("recipient", port.recipient);
                    if (!port.indicator.isEmpty()) {
                        net.minecraft.nbt.CompoundTag indicatorTag = new net.minecraft.nbt.CompoundTag();
                        port.indicator.save(indicatorTag);
                        updateTag.put("indicator", indicatorTag);
                    }
                    // Добавляем рецепты, но БЕЗ availableCache
                    net.minecraft.world.ContainerHelper.saveAllItems(updateTag, port.recipe);
                    
                    net.minecraft.nbt.CompoundTag fluidsTag = new net.minecraft.nbt.CompoundTag();
                    net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                    for (int i = 0; i < port.fluidsRecipe.size(); i++) {
                        net.minecraftforge.fluids.FluidStack fs = port.fluidsRecipe.get(i);
                        if (!fs.isEmpty()) {
                            net.minecraft.nbt.CompoundTag fTag = new net.minecraft.nbt.CompoundTag();
                            fTag.putInt("Slot", i);
                            fs.writeToNBT(fTag);
                            list.add(fTag);
                        }
                    }
                    fluidsTag.put("Fluids", list);
                    updateTag.put("fluidsRecipe", fluidsTag);

                    buf.writeNbt(updateTag);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static void extractClipboardSuggestions(net.minecraft.nbt.CompoundTag tag, List<String> recipients, int depth) {
        if (tag == null || depth > 10) return;
        // System.out.println("[DEBUG_LOG] Full Clipboard Tag (depth " + depth + "): " + tag.toString());

        // В новых версиях Create данные могут быть вложены в тег "Item"
        if (tag.contains("Item", 10)) {
            net.minecraft.nbt.CompoundTag itemTag = tag.getCompound("Item");
            if (itemTag.contains("tag", 10)) {
                extractClipboardSuggestions(itemTag.getCompound("tag"), recipients, depth + 1);
                return;
            }
        }

        // Обработка структуры "Pages" -> "Entries" -> "Text"
        if (tag.contains("Pages", 9)) {
            net.minecraft.nbt.ListTag pages = tag.getList("Pages", 10);
            for (int i = 0; i < pages.size(); i++) {
                net.minecraft.nbt.CompoundTag page = pages.getCompound(i);
                if (page.contains("Entries", 9)) {
                    net.minecraft.nbt.ListTag entries = page.getList("Entries", 10);
                    for (int j = 0; j < entries.size(); j++) {
                        net.minecraft.nbt.CompoundTag entry = entries.getCompound(j);
                        if (entry.contains("Text", 8)) {
                            processText(entry.getString("Text"), recipients);
                        }
                    }
                }
            }
        }

        // Совместимость с другими версиями/структурами
        if (tag.contains("Content", 9)) {
            processClipboardContent(tag.getList("Content", 10), recipients);
        }
    }

    private static void processText(String jsonOrText, List<String> recipients) {
        if (jsonOrText == null || jsonOrText.isEmpty()) return;
        
        String plainText = jsonOrText;
        // Если это JSON (например, '{"text":"#asdasdasd"}')
        if (jsonOrText.startsWith("{") && jsonOrText.endsWith("}")) {
            try {
                // Пытаемся извлечь содержимое "text" вручную или через простую логику, 
                // так как в этой среде может не быть удобного парсера JSON под рукой, 
                // а регулярные выражения или substring справятся для простых случаев.
                int start = jsonOrText.indexOf("\"text\":\"");
                if (start != -1) {
                    start += 8;
                    int end = jsonOrText.indexOf("\"", start);
                    if (end != -1) {
                        plainText = jsonOrText.substring(start, end);
                    }
                }
            } catch (Exception e) {
                System.out.println("[DEBUG_LOG] Failed to parse JSON text: " + jsonOrText);
            }
        }

        System.out.println("[DEBUG_LOG] Processed line: " + plainText);
        if (plainText.startsWith("#") && plainText.length() > 1) {
            String suggestion = plainText.substring(1).trim();
            if (!suggestion.isEmpty() && !recipients.contains(suggestion)) {
                recipients.add(suggestion);
                System.out.println("[DEBUG_LOG] Extracted suggestion: " + suggestion);
            }
        }
    }

    private static void processClipboardContent(net.minecraft.nbt.ListTag content, List<String> recipients) {
        for (int j = 0; j < content.size(); j++) {
            net.minecraft.nbt.CompoundTag entry = content.getCompound(j);
            if (entry.contains("Text", 8)) {
                processText(entry.getString("Text"), recipients);
            }
        }
    }
}