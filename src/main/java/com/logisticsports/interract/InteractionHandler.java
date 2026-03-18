package com.logisticsports.interract;

import com.logisticsports.LogisticsPorts;
import com.logisticsports.block.AccessPortBlock;
import com.logisticsports.block.OutputPortBlock;
import com.logisticsports.blockentity.OutputPortBlockEntity;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LogisticsPorts.MODID)
public class InteractionHandler {

    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> WRENCH_TAG =
            net.minecraft.tags.ItemTags.create(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench"));

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();

        // Только сервер!
        if (level.isClientSide) return;

        if (!AllowWrenchInterract(player)) return;
        BlockState state = level.getBlockState(pos);

        Block block = state.getBlock();

        // Проверяем блок
        if (!(block instanceof OutputPortBlock) &&
                !(block instanceof AccessPortBlock)) return;

        if (player.isShiftKeyDown()) {

            level.destroyBlock(pos, false);

            ItemStack drop = new ItemStack(block.asItem());
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }

            AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos);

            // Отменяем всё остальное
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else {
            if(FaceRotation(level, pos)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    public static boolean FaceRotation(Level level, BlockPos pos) {
        if (level.isClientSide) return false;
        DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        BlockState state = level.getBlockState(pos);
        Direction newFacing = state.getValue(FACING).getClockWise();
        level.setBlock(pos, state.setValue(FACING, newFacing), 3);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        return true;
    }

    public static boolean AllowWrenchInterract(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        return heldItem.is(WRENCH_TAG);
    }
    public static boolean AllowWrenchInterract(Player player) {
        return AllowWrenchInterract(player, InteractionHand.MAIN_HAND);
    }
}