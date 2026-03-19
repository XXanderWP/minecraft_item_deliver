package com.logisticsports.client;

import com.logisticsports.block.OutputPortBlock;
import com.logisticsports.blockentity.OutputPortBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;

public class OutputPortRenderer implements BlockEntityRenderer<OutputPortBlockEntity> {

    public OutputPortRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(OutputPortBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        
        // Рендеринг жидкости
        if (be.processingFluid != null) {
            renderFluid(be.processingFluid, partialTick, poseStack, bufferSource, packedLight);
        }

        // Рендеринг предметов
        for (OutputPortBlockEntity.ProcessingItem pi : be.processingItems) {
            renderItem(pi, partialTick, poseStack, bufferSource, packedLight, packedOverlay, be);
        }
    }

    private void renderFluid(OutputPortBlockEntity.ProcessingFluid pf, float partialTick, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight) {
        FluidStack fluidStack = pf.fluid;
        float progress = (OutputPortBlockEntity.PROCESSING_TIME - (pf.timer - partialTick)) / (float) OutputPortBlockEntity.PROCESSING_TIME;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        float height;
        if (progress < 0.5f) {
            height = progress * 2.0f; // 0 to 1
        } else {
            height = 1.0f - (progress - 0.5f) * 2.0f; // 1 to 0
        }

        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation still = props.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
        int color = props.getTintColor(fluidStack);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        VertexConsumer builder = bufferSource.getBuffer(RenderType.translucent());
        
        float min = 0.125f;
        float max = 0.875f;
        float h = min + (max - min) * height;

        drawCube(poseStack, builder, min, min, min, max, h, max, sprite, r, g, b, a, packedLight);
    }

    private void drawCube(PoseStack ms, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, 
                          TextureAtlasSprite sprite, float r, float g, float b, float a, int light) {
        Matrix4f mat = ms.last().pose();
        
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Down
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, -1, 0).endVertex();
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, -1, 0).endVertex();
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, -1, 0).endVertex();
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, -1, 0).endVertex();

        // Up
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, 1, 0).endVertex();
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, 1, 0).endVertex();
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, 1, 0).endVertex();
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, 1, 0).endVertex();

        // North
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, 0, -1).endVertex();
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, 0, -1).endVertex();
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, 0, -1).endVertex();
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, 0, -1).endVertex();

        // South
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a).uv(u1, v1).uv2(light).normal(0, 0, 1).endVertex();
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(u1, v0).uv2(light).normal(0, 0, 1).endVertex();
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a).uv(u0, v0).uv2(light).normal(0, 0, 1).endVertex();
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a).uv(u0, v1).uv2(light).normal(0, 0, 1).endVertex();

        // West
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a).uv(u0, v1).uv2(light).normal(-1, 0, 0).endVertex();
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a).uv(u0, v0).uv2(light).normal(-1, 0, 0).endVertex();
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(-1, 0, 0).endVertex();
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(u1, v1).uv2(light).normal(-1, 0, 0).endVertex();

        // East
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a).uv(u1, v1).uv2(light).normal(1, 0, 0).endVertex();
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a).uv(u1, v0).uv2(light).normal(1, 0, 0).endVertex();
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(u0, v0).uv2(light).normal(1, 0, 0).endVertex();
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a).uv(u0, v1).uv2(light).normal(1, 0, 0).endVertex();
    }

    private void renderItem(OutputPortBlockEntity.ProcessingItem pi, float partialTick, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay, OutputPortBlockEntity be) {
        ItemStack stack = pi.stack;
        if (stack.isEmpty()) return;

        float progress = (OutputPortBlockEntity.PROCESSING_TIME - (pi.timer - partialTick)) / (float) OutputPortBlockEntity.PROCESSING_TIME;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        Direction source = pi.source;
        Direction facing = be.getBlockState().getValue(OutputPortBlock.FACING);

        poseStack.pushPose();
        
        // Позиция: от края source к центру (0.5 progress), затем от центра к краю facing (когда заберут)
        // В условии сказано: "въёхать" с той стороны откуда забирается в центр блока.
        // А потом "когда его заберут воронкой" - сдвинуть в сторону откуда забрали.
        // Пока реализуем только первую часть (въезд в центр), так как "когда заберут" - это отдельное состояние.
        
        float dist = 0.5f * (1.0f - progress); // от 0.5 до 0
        
        double x = 0.5 + source.getStepX() * dist;
        double y = 0.5 + source.getStepY() * dist;
        double z = 0.5 + source.getStepZ() * dist;

        poseStack.translate(x, y, z);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        
        // Вращение для красоты
        poseStack.mulPose(Axis.YP.rotationDegrees((be.getLevel().getGameTime() + partialTick) * 4));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                be.getLevel(),
                (int) be.getBlockPos().asLong()
        );

        poseStack.popPose();
    }
}
