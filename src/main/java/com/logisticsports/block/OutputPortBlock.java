package com.logisticsports.block;

import com.logisticsports.blockentity.OutputPortBlockEntity;
import com.logisticsports.interract.InteractionHandler;
import com.logisticsports.registry.ModRegistry;
import com.logisticsports.world.OutputPortSavedData;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class OutputPortBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public OutputPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING); // добавь FACING рядом со STATUS
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OutputPortBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionHandler.AllowWrenchInterract(player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // При взаимодействии проверяем регистрацию в данных мира
        OutputPortSavedData.get(level).addPort(pos);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OutputPortBlockEntity port) {
            port.giveAllToPlayer(player);
            player.sendSystemMessage(Component.translatable("config.logisticsports.action.success_chat", Component.translatable("config.logisticsports.action.returned")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                OutputPortSavedData.get(level).removePort(pos);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OutputPortBlockEntity port) {
                for (int i = 0; i < port.getInventory().getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), port.getInventory().getStackInSlot(i));
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            OutputPortSavedData.get(level).addPort(pos);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.OUTPUT_PORT_BE.get(),
                OutputPortBlockEntity::tick);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.BlockGetter level, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (Screen.hasShiftDown()) {
            String desc = Component.translatable("config.logisticsports.tooltip.output_port.desc").getString();
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
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}