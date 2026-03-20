package com.logisticsports.block;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.interract.InteractionHandler;
import com.logisticsports.registry.ModRegistry;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

public class AccessPortBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;


    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Блок смотрит на игрока (противоположная сторона от взгляда)
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(STATUS, 0);
    }

    public AccessPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(STATUS, 0));
    }

    public static final IntegerProperty STATUS = IntegerProperty.create("status", 0, 2);


    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, state.setValue(STATUS, 0), 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS); // добавь FACING рядом со STATUS
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccessPortBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> WRENCH_TAG =
            net.minecraft.tags.ItemTags.create(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench"));


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionHandler.AllowWrenchInterract(player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AccessPortBlockEntity port)) return InteractionResult.PASS;

        if (player.isShiftKeyDown() || port.isMultiport) {
            port.refreshAvailableCache();
            NetworkHooks.openScreen((ServerPlayer) player, port, buf -> buf.writeBlockPos(pos));
        } else {
            port.placeOrder(player, 1);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AccessPortBlockEntity port) {
            port.onNeighborUpdate(level.hasNeighborSignal(pos));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.BlockGetter level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (Screen.hasShiftDown()) {
            String desc = Component.translatable("config.logisticsports.tooltip.access_port.desc").getString();
            for (String line : desc.split("\n")) {
                tooltip.add(Component.literal(line));
            }
        } else {
            tooltip.add(Component.translatable("config.logisticsports.tooltip.shift_hint"));
        }

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BlockEntityTag")) {
            net.minecraft.nbt.CompoundTag beTag = tag.getCompound("BlockEntityTag");
            int freq = beTag.getInt("frequency");
            tooltip.add(Component.translatable("config.logisticsports.frequency_tooltip2", freq));

            if (beTag.contains("Items")) {
                net.minecraft.core.NonNullList<ItemStack> recipe = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
                net.minecraft.world.ContainerHelper.loadAllItems(beTag, recipe);
                boolean hasRecipe = false;
                for (ItemStack s : recipe) {
                    if (!s.isEmpty()) {
                        if (!hasRecipe) {
                            tooltip.add(Component.translatable("config.logisticsports.require"));
                            hasRecipe = true;
                        }
                        tooltip.add(net.minecraft.network.chat.Component.literal("§8 - ").append(s.getHoverName()).append(net.minecraft.network.chat.Component.literal(" x" + s.getCount())));
                    }
                }
            }
            if (beTag.contains("indicator")) {
                ItemStack indicator = ItemStack.of(beTag.getCompound("indicator"));
                if (!indicator.isEmpty()) {
                    tooltip.add(net.minecraft.network.chat.Component.translatable("config.logisticsports.output").append(indicator.getHoverName()));
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.ACCESS_PORT_BE.get(),
                AccessPortBlockEntity::tick);
    }
}