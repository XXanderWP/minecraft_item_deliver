package com.logisticsports.client;

import com.logisticsports.blockentity.AccessPortBlockEntity;
import com.logisticsports.menu.AccessPortMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import java.util.ArrayList;
import java.util.List;

public class AccessPortScreen extends AbstractContainerScreen<AccessPortMenu> {

    private static final int BG_WIDTH = 200;
    private static final int BG_HEIGHT = 253;
    private EditBox batchesField;

    private float scrollAmount = 0;
    private final List<Button> multiportOrderButtons = new ArrayList<>();

    public AccessPortScreen(AccessPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    private Button orderButton;

    @Override
    protected void init() {
        super.init();

        batchesField = new EditBox(font,
                leftPos + 71, topPos + BG_HEIGHT - 22,
                30, 12, Component.literal("1"));
        batchesField.setValue("1");
        batchesField.setFilter(s -> s.matches("[1-9]\\d*") || s.isEmpty());
        batchesField.setResponder(val -> {
            if (val.isEmpty()) batchesField.setValue("1");
        });
        addRenderableWidget(batchesField);

        // Кнопка настроек — расширенная
        addRenderableWidget(Button.builder(
                Component.translatable("config.logisticsports.settings"),
                btn -> openSettings()
        ).pos(leftPos + BG_WIDTH - 68, topPos + 5).size(62, 14).build());

        // Кнопка заказать
        orderButton = Button.builder(
                Component.translatable("config.logisticsports.order"),
                btn -> placeOrder()
        ).pos(leftPos + BG_WIDTH - 82, topPos + BG_HEIGHT - 23).size(76, 14).build();
        addRenderableWidget(orderButton);
        
        if (menu.blockEntity.isMultiport) {
            orderButton.visible = false;
            multiportOrderButtons.clear();
            int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
            for (int i = 0; i < activeRecipeSlots; i++) {
                final int slot = i;
                Button btn = Button.builder(Component.literal(""), b -> placeMultiportOrder(slot))
                        .pos(leftPos + BG_WIDTH - 25, topPos + 24 + i * 21 + 3)
                        .size(14, 14)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("config.logisticsports.order")))
                        .build();
                btn.visible = !menu.blockEntity.recipe.get(i).isEmpty();
                addRenderableWidget(btn);
                multiportOrderButtons.add(btn);
            }
        }
    }

    private void placeMultiportOrder(int slotIndex) {
        int batches = 1;
        try {
            batches = Integer.parseInt(batchesField.getValue());
            if (batches < 1) batches = 1;
        } catch (NumberFormatException ignored) {}
        com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                new com.logisticsports.network.PacketPlaceMultiportOrder(
                        menu.blockEntity.getBlockPos(), batches, slotIndex)
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int bx = leftPos + 50;
        int by = topPos + BG_HEIGHT - 22;
        if (mouseX >= bx && mouseX <= bx + 30 && mouseY >= by && mouseY <= by + 12) {
            try {
                int current = Integer.parseInt(batchesField.getValue());
                int next = Math.max(1, current + (delta > 0 ? 1 : -1));
                batchesField.setValue(String.valueOf(next));
            } catch (NumberFormatException ignored) {
                batchesField.setValue("1");
            }
            return true;
        }

        // Скролл списка
        int listHeight = getListHeight();
        int viewHeight = getViewHeight();
        if (listHeight > viewHeight) {
            scrollAmount = Math.min(Math.max(scrollAmount - (float)delta * 10, 0), listHeight - viewHeight);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getListHeight() {
        int batches = 1;
        try {
            batches = Integer.parseInt(batchesField.getValue());
            if (batches < 1) batches = 1;
        } catch (NumberFormatException ignored) {}

        int count = 0;
        if (menu.blockEntity.isMultiport) {
            count = AccessPortBlockEntity.getRecipeSlots();
        } else {
            count = getGroupedRecipe(batches).size();
            int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
            for (int i = 0; i < activeFluidSlots; i++) {
                if (!menu.blockEntity.fluidsRecipe.get(i).isEmpty()) {
                    count++;
                }
            }
        }
        return count * 21;
    }

    private int getViewHeight() {
        return (menu.blockEntity.indicator.isEmpty() ? BG_HEIGHT - 30 : BG_HEIGHT - 54) - 24;
    }

    private void openSettings() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.closeContainer();
        com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                new com.logisticsports.network.PacketOpenSettings(menu.blockEntity.getBlockPos())
        );
    }

    private void placeOrder() {
        int batches = 1;
        try {
            batches = Integer.parseInt(batchesField.getValue());
            if (batches < 1) batches = 1;
        } catch (NumberFormatException ignored) {}
        com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                new com.logisticsports.network.PacketPlaceOrder(
                        menu.blockEntity.getBlockPos(), batches)
        );
    }

    private List<ItemStack> getGroupedRecipe(int batches) {
        List<ItemStack> result = new ArrayList<>();
        for (var stack : menu.blockEntity.recipe) {
            if (stack.isEmpty()) continue;
            boolean found = false;
            for (var existing : result) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    existing.grow(stack.getCount() * batches);
                    found = true;
                    break;
                }
            }
            if (!found) {
                ItemStack copy = stack.copy();
                copy.setCount(stack.getCount() * batches);
                result.add(copy);
            }
        }
        return result;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos;
        int y = topPos;

        // Фон
        g.fill(x, y, x + BG_WIDTH, y + BG_HEIGHT, 0xFFD0D0D0);

        // Рамка
        g.fill(x, y, x + BG_WIDTH, y + 1, 0xFF444444);
        g.fill(x, y + BG_HEIGHT - 1, x + BG_WIDTH, y + BG_HEIGHT, 0xFF444444);
        g.fill(x, y, x + 1, y + BG_HEIGHT, 0xFF444444);
        g.fill(x + BG_WIDTH - 1, y, x + BG_WIDTH, y + BG_HEIGHT, 0xFF444444);

        // Заголовок
        Component title = menu.blockEntity.isMultiport ? 
                Component.translatable("block.logisticsports.multiport_access") : 
                Component.translatable("block.logisticsports.access_port");
        g.drawString(font, title, x + 8, y + 8, 0xFF222222, false);
        g.fill(x + 4, y + 18, x + BG_WIDTH - 4, y + 19, 0xFF888888);

        // Список предметов
        int batches = 1;
        try {
            batches = Integer.parseInt(batchesField.getValue());
            if (batches < 1) batches = 1;
        } catch (NumberFormatException ignored) {}

        List<ItemStack> grouped;
        if (menu.blockEntity.isMultiport) {
            grouped = new ArrayList<>();
            for (ItemStack s : menu.blockEntity.recipe) {
                if (s.isEmpty()) {
                    grouped.add(ItemStack.EMPTY);
                } else {
                    ItemStack copy = s.copy();
                    copy.setCount(s.getCount() * batches);
                    grouped.add(copy);
                }
            }
        } else {
            grouped = getGroupedRecipe(batches);
        }

        int viewHeight = getViewHeight();
        int listHeight = getListHeight();

        // Зажим скролла (если список стал меньше)
        if (scrollAmount > Math.max(0, listHeight - viewHeight)) {
            scrollAmount = Math.max(0, listHeight - viewHeight);
        }

        int listY = y + 24;
        int viewportY = listY;

        g.enableScissor(x + 4, viewportY, x + BG_WIDTH - 4, viewportY + viewHeight);

        int currentY = listY - (int)scrollAmount;

        if (grouped.isEmpty() || (menu.blockEntity.isMultiport && menu.blockEntity.recipe.stream().allMatch(ItemStack::isEmpty))) {
            g.drawString(font, Component.translatable("config.logisticsports.recipe_is_empty"), x + 8, currentY + 6, 0xFF888888, false);
        } else {
            for (int i = 0; i < grouped.size(); i++) {
                var stack = grouped.get(i);
                int rowColor = (i % 2 == 0) ? 0xFFBBBBBB : 0xFFC8C8C8;
                g.fill(x + 4, currentY, x + BG_WIDTH - 4, currentY + 20, rowColor);

                if (!stack.isEmpty()) {
                    g.renderItem(stack, x + 5, currentY + 2);

                    Component name = stack.getHoverName();
                    g.drawString(font, name, x + 26, currentY + 1, 0xFF222222, false);

                    // Нужно x количество
                    int needed = stack.getCount();
                    // Доступно — запрашиваем с сервера через данные блока
                    int avail = menu.blockEntity.getAvailableCount(stack);
                    int availColor = avail >= needed ? 0xFF22AA22 : 0xFFAA2222;
                    g.drawString(font, Convert.ShowAmountString(needed, false) + " (" + Convert.ShowAmountString(avail, true) + ")",
                            x + 26, currentY + 11, availColor, false);
                    
                    if (menu.blockEntity.isMultiport) {
                        // Стрелочка/иконка на кнопке заказа
                        g.drawString(font, "→", x + BG_WIDTH - 21, currentY + 6, 0xFF222222, false);
                    }
                }

                currentY += 21;
            }
        }

        // Жидкость
        if (!menu.blockEntity.isMultiport) {
            int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
            int fluidIndexInList = grouped.size();
            for (int i = 0; i < activeFluidSlots; i++) {
                FluidStack fluid = menu.blockEntity.fluidsRecipe.get(i);
                if (!fluid.isEmpty()) {
                    int rowColor = (fluidIndexInList % 2 == 0) ? 0xFFBBBBBB : 0xFFC8C8C8;
                    g.fill(x + 4, currentY, x + BG_WIDTH - 4, currentY + 20, rowColor);

                    renderFluid(g, fluid, x + 5, currentY + 2);

                    Component name = fluid.getDisplayName();
                    g.drawString(font, name, x + 26, currentY + 1, 0xFF222222, false);

                    // Нужно x количество (mB)
                    int needed = fluid.getAmount() * batches;
                    // Доступно — запрашиваем с сервера через данные блока
                    int avail = menu.blockEntity.getAvailableFluidCount(fluid);
                    int availColor = avail >= needed ? 0xFF22AA22 : 0xFFAA2222;
                    g.drawString(font, Convert.ShowAmountString(needed, false, true) + " (" + Convert.ShowAmountString(avail, true, true) + ")",
                            x + 26, currentY + 11, availColor, false);

                    currentY += 21;
                    fluidIndexInList++;
                }
            }
        }

        g.disableScissor();

        // Скроллбар
        if (listHeight > viewHeight) {
            int scrollbarX = x + BG_WIDTH - 3;
            int scrollbarHeight = (int)((viewHeight / (float)listHeight) * viewHeight);
            int scrollbarY = viewportY + (int)((scrollAmount / (float)listHeight) * viewHeight);
            g.fill(scrollbarX, viewportY, x + BG_WIDTH - 1, viewportY + viewHeight, 0xFF444444);
            g.fill(scrollbarX, scrollbarY, x + BG_WIDTH - 1, scrollbarY + scrollbarHeight, 0xFF888888);
        }

        // Индикатор (результат) — над нижней панелью
        if (!menu.blockEntity.indicator.isEmpty()) {
            ItemStack ind = menu.blockEntity.indicator;
            int indY = y + BG_HEIGHT - 54;
            // Рамка другого цвета — золотистая
            g.fill(x + 4, indY, x + BG_WIDTH - 4, indY + 22, 0xFF8B7000);
            g.fill(x + 5, indY + 1, x + BG_WIDTH - 5, indY + 21, 0xFFD4A017);
            g.renderItem(ind, x + 8, indY + 3);
            Component indName = ind.getHoverName();
            Component resultText = Component.translatable("config.logisticsports.result", 
                    Component.empty().append(indName).append(ind.getCount() > 1 ? " x" + ind.getCount() : ""));
            g.drawString(font, resultText, x + 28, indY + 7, 0xFF222222, false);
        }

        // Нижняя панель
        g.fill(x + 4, y + BG_HEIGHT - 30, x + BG_WIDTH - 4, y + BG_HEIGHT - 29, 0xFF888888);
        g.drawString(font, Component.translatable("config.logisticsports.count"), x + 8, y + BG_HEIGHT - 22 + 2, 0xFF222222, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (menu.blockEntity.isMultiport) {
            int viewportY = topPos + 24;
            int viewHeight = getViewHeight();
            for (int i = 0; i < multiportOrderButtons.size(); i++) {
                Button btn = multiportOrderButtons.get(i);
                int btnY = viewportY + i * 21 + 3 - (int)scrollAmount;
                btn.setY(btnY);
                btn.visible = !menu.blockEntity.recipe.get(i).isEmpty() && btnY >= viewportY && (btnY + 14) <= (viewportY + viewHeight);
            }
        }
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private void renderFluid(GuiGraphics g, FluidStack fluid, int x, int y) {
        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation icon = props.getStillTexture(fluid);
        if (icon == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        int color = props.getTintColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g_col = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        g.setColor(r, g_col, b, a);
        g.blit(x, y, 0, 16, 16, sprite);
        g.setColor(1f, 1f, 1f, 1f);
    }
}