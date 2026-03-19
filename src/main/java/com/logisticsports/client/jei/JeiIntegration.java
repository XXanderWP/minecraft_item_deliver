package com.logisticsports.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class JeiIntegration implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("logisticsports", "jei_plugin");
    }



    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
                com.logisticsports.client.AccessPortSettingsScreen.class,
                new IGhostIngredientHandler<com.logisticsports.client.AccessPortSettingsScreen>() {

                    @Override
                    public void onComplete() {}

                    @Override
                    public <I> List<Target<I>> getTargetsTyped(
                            com.logisticsports.client.AccessPortSettingsScreen screen,
                            ITypedIngredient<I> ingredient,
                            boolean doStart) {

                        List<Target<I>> targets = new ArrayList<>();

                        Object ingredientObj = ingredient.getIngredient();
                        if (!(ingredientObj instanceof ItemStack) && !(ingredientObj instanceof FluidStack)) {
                            return targets;
                        }

                        // Добавляем все ghost слоты (0-9) как цели
                        for (int i = 0; i < 10; i++) {
                            Slot slot = screen.getMenu().slots.get(i);
                            int slotX = screen.getGuiLeft() + slot.x;
                            int slotY = screen.getGuiTop() + slot.y;

                            final int slotIndex = i;
                            targets.add(new Target<>() {
                                @Override
                                public Rect2i getArea() {
                                    return new Rect2i(slotX, slotY, 16, 16);
                                }

                                @Override
                                public void accept(I ingredient) {
                                    if (ingredient instanceof ItemStack stack) {
                                        screen.getMenu().clicked(
                                                slotIndex, 0,
                                                net.minecraft.world.inventory.ClickType.PICKUP,
                                                net.minecraft.client.Minecraft.getInstance().player
                                        );
                                        // Напрямую ставим предмет через syncSlot
                                        ItemStack copy = stack.copy();
                                        copy.setCount(1);
                                        screen.getMenu().slots.get(slotIndex).set(copy);
                                        screen.getMenu().syncSlotPublic(slotIndex, copy);
                                    }
                                }
                            });
                        }

                        // Добавляем слот жидкости (виртуальный)
                        int fx = screen.getGuiLeft() + 8 + 9 * 18 + 4 + 1;
                        int fy = screen.getGuiTop() + 30 + 1;
                        targets.add(new Target<>() {
                            @Override
                            public Rect2i getArea() {
                                return new Rect2i(fx, fy, 16, 16);
                            }

                            @Override
                            public void accept(I ingredient) {
                                if (ingredient instanceof FluidStack fluid) {
                                    FluidStack copy = fluid.copy();
                                    copy.setAmount(1000);
                                    screen.getMenu().syncFluid(copy);
                                } else if (ingredient instanceof ItemStack stack) {
                                    net.minecraftforge.fluids.FluidUtil.getFluidContained(stack).ifPresent(fluid -> {
                                        if (!fluid.isEmpty()) {
                                            FluidStack copy = fluid.copy();
                                            copy.setAmount(1000);
                                            screen.getMenu().syncFluid(copy);
                                        }
                                    });
                                }
                            }
                        });

                        return targets;
                    }
                }
        );
    }
}