package com.logisticsports.client;

import com.logisticsports.block.AccessPortBlock;
import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class AccessPortRenderer implements BlockEntityRenderer<AccessPortBlockEntity> {

    public AccessPortRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AccessPortBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        ItemStack indicator = be.indicator;
        if (indicator.isEmpty()) return;

        Level level = be.getLevel();
        if (level == null) return;

        var itemRenderer = Minecraft.getInstance().getItemRenderer();

        Direction facing = be.getBlockState().getValue(AccessPortBlock.FACING);

        for (Direction dir : Direction.values()) {
            // Проверяем что грань не закрыта соседним блоком
            BlockState neighbor = level.getBlockState(be.getBlockPos().relative(dir));
            if (neighbor.isSolidRender(level, be.getBlockPos().relative(dir))) continue;
            // Индикатор только на лицевой грани
            if (dir != facing) continue;

            poseStack.pushPose();

            // Смещаемся к центру блока
            poseStack.translate(0.5, 0.5, 0.5);

            // Поворачиваем в зависимости от грани
            switch (dir) {
                case UP ->    poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                case DOWN ->  poseStack.mulPose(Axis.XP.rotationDegrees(90));
                case NORTH -> {} // лицом к нам — без поворота
                case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
                case WEST ->  poseStack.mulPose(Axis.YP.rotationDegrees(90));
                case EAST ->  poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            }

            // Смещаемся к грани блока (0.5 + чуть вперёд чтобы не z-fighting)
            poseStack.translate(0, 0, -0.505);

            // Масштабируем предмет
            poseStack.scale(0.4f, 0.4f, 0.4f);

            itemRenderer.renderStatic(
                    indicator,
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    level,
                    (int) be.getBlockPos().asLong()
            );

            poseStack.popPose();
        }
    }
}