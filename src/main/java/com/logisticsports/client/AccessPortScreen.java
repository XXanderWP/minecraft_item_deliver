package com.logisticsports.client;

import com.logisticsports.menu.AccessPortMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AccessPortScreen extends AbstractContainerScreen<AccessPortMenu> {

    private static final int BG_WIDTH = 200;
    private static final int BG_HEIGHT = 253;
    private EditBox batchesField;

    public AccessPortScreen(AccessPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        batchesField = new EditBox(font,
                leftPos + 50, topPos + BG_HEIGHT - 22,
                30, 12, Component.literal("1"));
        batchesField.setValue("1");
        batchesField.setFilter(s -> s.matches("[1-9]\\d*") || s.equals(""));
        batchesField.setResponder(val -> {
            if (val.isEmpty()) batchesField.setValue("1");
        });
        addRenderableWidget(batchesField);

        // Кнопка настроек — расширенная
        addRenderableWidget(Button.builder(
                Component.literal("Настройки"),
                btn -> openSettings()
        ).pos(leftPos + BG_WIDTH - 68, topPos + 5).size(62, 14).build());

        // Кнопка заказать
        addRenderableWidget(Button.builder(
                Component.literal("Заказать"),
                btn -> placeOrder()
        ).pos(leftPos + BG_WIDTH - 82, topPos + BG_HEIGHT - 22).size(76, 14).build());
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
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        g.drawString(font, "Порт Доступа", x + 8, y + 8, 0xFF222222, false);
        g.fill(x + 4, y + 18, x + BG_WIDTH - 4, y + 19, 0xFF888888);

        // Список предметов
        int batches = 1;
        try {
            batches = Integer.parseInt(batchesField.getValue());
            if (batches < 1) batches = 1;
        } catch (NumberFormatException ignored) {}

        List<ItemStack> grouped = getGroupedRecipe(batches);
        int listY = y + 24;

        if (grouped.isEmpty()) {
            g.drawString(font, "Рецепт не задан", x + 8, y + 30, 0xFF888888, false);
        } else {
            for (int i = 0; i < grouped.size(); i++) {
                var stack = grouped.get(i);
                int rowColor = (i % 2 == 0) ? 0xFFBBBBBB : 0xFFC8C8C8;
                g.fill(x + 4, listY, x + BG_WIDTH - 4, listY + 20, rowColor);

                g.renderItem(stack, x + 5, listY + 2);

                String name = stack.getHoverName().getString();
                if (name.length() > 14) name = name.substring(0, 12) + "..";
                g.drawString(font, name, x + 26, listY + 6, 0xFF222222, false);

                // Нужно x количество
                int needed = stack.getCount();
                // Доступно — запрашиваем с сервера через данные блока
                int avail = menu.blockEntity.getAvailableCount(stack);
                int availColor = avail >= needed ? 0xFF22AA22 : 0xFFAA2222;
                g.drawString(font, "x" + needed + " (" + avail + ")",
                        x + BG_WIDTH - 55, listY + 6, availColor, false);

                listY += 21;
            }
        }

        // Индикатор (результат) — над нижней панелью
        if (!menu.blockEntity.indicator.isEmpty()) {
            ItemStack ind = menu.blockEntity.indicator;
            int indY = y + BG_HEIGHT - 54;
            // Рамка другого цвета — золотистая
            g.fill(x + 4, indY, x + BG_WIDTH - 4, indY + 22, 0xFF8B7000);
            g.fill(x + 5, indY + 1, x + BG_WIDTH - 5, indY + 21, 0xFFD4A017);
            g.renderItem(ind, x + 8, indY + 3);
            String indName = ind.getHoverName().getString();
            if (indName.length() > 14) indName = indName.substring(0, 12) + "..";
            g.drawString(font, "§6Результат: " + indName +
                            (ind.getCount() > 1 ? " x" + ind.getCount() : ""),
                    x + 28, indY + 7, 0xFF222222, false);
        }

        // Нижняя панель
        g.fill(x + 4, y + BG_HEIGHT - 30, x + BG_WIDTH - 4, y + BG_HEIGHT - 29, 0xFF888888);
        g.drawString(font, "Партий:", x + 8, y + BG_HEIGHT - 22, 0xFF222222, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}
}