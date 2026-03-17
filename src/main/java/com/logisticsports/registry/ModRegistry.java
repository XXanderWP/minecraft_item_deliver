package com.logisticsports.registry;

import com.logisticsports.LogisticsPorts;
import com.logisticsports.block.AccessPortBlock;
import com.logisticsports.block.OutputPortBlock;
import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.blockentity.OutputPortBlockEntity;
import com.logisticsports.menu.AccessPortSettingsMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.logisticsports.menu.AccessPortMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.ForgeRegistries;

public class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LogisticsPorts.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LogisticsPorts.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LogisticsPorts.MODID);

    // Блоки
    public static final RegistryObject<Block> ACCESS_PORT = BLOCKS.register("access_port",
            () -> new AccessPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    public static final RegistryObject<Block> OUTPUT_PORT = BLOCKS.register("output_port",
            () -> new OutputPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    // Предметы-блоки
    public static final RegistryObject<Item> ACCESS_PORT_ITEM = ITEMS.register("access_port",
            () -> new BlockItem(ACCESS_PORT.get(), new Item.Properties()));

    public static final RegistryObject<Item> OUTPUT_PORT_ITEM = ITEMS.register("output_port",
            () -> new BlockItem(OUTPUT_PORT.get(), new Item.Properties()));

    // Тайл-энтити
    public static final RegistryObject<BlockEntityType<AccessPortBlockEntity>> ACCESS_PORT_BE =
            BLOCK_ENTITIES.register("access_port", () -> BlockEntityType.Builder
                    .of(AccessPortBlockEntity::new, ACCESS_PORT.get()).build(null));

    public static final RegistryObject<BlockEntityType<OutputPortBlockEntity>> OUTPUT_PORT_BE =
            BLOCK_ENTITIES.register("output_port", () -> BlockEntityType.Builder
                    .of(OutputPortBlockEntity::new, OUTPUT_PORT.get()).build(null));

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, LogisticsPorts.MODID);

    public static final RegistryObject<MenuType<AccessPortMenu>> ACCESS_PORT_MENU =
            MENUS.register("access_port", () ->
                    IForgeMenuType.create(AccessPortMenu::create));

    public static final RegistryObject<MenuType<AccessPortSettingsMenu>> ACCESS_PORT_SETTINGS_MENU =
            MENUS.register("access_port_settings", () ->
                    IForgeMenuType.create(AccessPortSettingsMenu::create));


    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LogisticsPorts.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.literal("Logistics Ports"))
                    .icon(() -> ACCESS_PORT_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ACCESS_PORT_ITEM.get());
                        output.accept(OUTPUT_PORT_ITEM.get());
                    })
                    .build());
}