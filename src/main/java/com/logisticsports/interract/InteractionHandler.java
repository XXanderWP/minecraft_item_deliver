package com.logisticsports.interract;

import com.logisticsports.LogisticsPorts;
import com.logisticsports.block.AccessPortBlock;
import com.logisticsports.block.OutputPortBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.logisticsports.blockentity.AccessPortBlockEntity;
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

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // Проверяем блок
        if (!(block instanceof OutputPortBlock) &&
                !(block instanceof AccessPortBlock)) return;

        if (AllowWrenchInterract(player)) {
            if (player.isShiftKeyDown()) {
                BlockEntity be = level.getBlockEntity(pos);
                ItemStack drop = new ItemStack(block.asItem());

                if (be instanceof AccessPortBlockEntity || be instanceof OutputPortBlockEntity) {
                    CompoundTag tag = be.saveWithFullMetadata();
                    drop.getOrCreateTag().put("BlockEntityTag", tag);
                }

                level.destroyBlock(pos, false);

                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }

                AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos);

                // Отменяем всё остальное
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            } else {
                if (FaceRotation(level, pos)) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
            return;
        }

        // Логика синхронизации частоты
        if (!stack.isEmpty() && (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) &&
                (bi.getBlock() instanceof AccessPortBlock || bi.getBlock() instanceof OutputPortBlock)) {

            BlockEntity worldBe = level.getBlockEntity(pos);
            int worldFreq = 0;
            if (worldBe instanceof AccessPortBlockEntity ap) worldFreq = ap.frequency;
            else if (worldBe instanceof OutputPortBlockEntity op) worldFreq = op.frequency;

            int handFreq = 0;
            CompoundTag stackTag = stack.getTag();
            if (stackTag != null && stackTag.contains("BlockEntityTag")) {
                handFreq = stackTag.getCompound("BlockEntityTag").getInt("frequency");
            }

            if (worldFreq != 0 && handFreq == 0) {
                // Установить частоту в предмет
                CompoundTag beTag = stack.getOrCreateTagElement("BlockEntityTag");
                beTag.putInt("frequency", worldFreq);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[LP] Частота скопирована в предмет: " + worldFreq));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            } else if (worldFreq == 0 && handFreq != 0) {
                // Установить частоту в блок
                if (worldBe instanceof AccessPortBlockEntity ap) ap.frequency = handFreq;
                else if (worldBe instanceof OutputPortBlockEntity op) op.frequency = handFreq;
                worldBe.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[LP] Частота установлена в блок: " + handFreq));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            } else if (worldFreq == 0 && handFreq == 0) {
                // Оба пустые - генерируем случайную
                int newFreq = 1000 + level.random.nextInt(9000);
                if (worldBe instanceof AccessPortBlockEntity ap) ap.frequency = newFreq;
                else if (worldBe instanceof OutputPortBlockEntity op) op.frequency = newFreq;
                worldBe.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);

                CompoundTag beTag = stack.getOrCreateTagElement("BlockEntityTag");
                beTag.putInt("frequency", newFreq);

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[LP] Новая частота синхронизирована: " + newFreq));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            } else {
                // У обоих есть - тот что на земле получает частоту того что в руке
                if (worldBe instanceof AccessPortBlockEntity ap) ap.frequency = handFreq;
                else if (worldBe instanceof OutputPortBlockEntity op) op.frequency = handFreq;
                worldBe.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[LP] Частота блока обновлена из предмета: " + handFreq));
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