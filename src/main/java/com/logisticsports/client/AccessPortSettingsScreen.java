package com.logisticsports.client;

import com.logisticsports.menu.AccessPortSettingsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class AccessPortSettingsScreen extends AbstractContainerScreen<AccessPortSettingsMenu> {

    private static final int BG_WIDTH = 200;
    private static final int BG_HEIGHT = 253;
    private EditBox frequencyField;
    private EditBox recipientField;
    private Button packageModeButton;

    public AccessPortSettingsScreen(AccessPortSettingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        frequencyField = new EditBox(font,
                leftPos + 65, topPos + 58,
                50, 12, Component.literal("0"));
        frequencyField.setValue(String.valueOf(menu.blockEntity.frequency));
        frequencyField.setFilter(s -> s.matches("-?\\d*"));
        frequencyField.setResponder(val -> {
            try {
                int freq = Integer.parseInt(val);
                com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                        new com.logisticsports.network.PacketUpdateFrequency(
                                menu.blockEntity.getBlockPos(), freq));
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(frequencyField);

        // Кнопка назад
        addRenderableWidget(Button.builder(
                Component.literal("←"),
                btn -> goBack()
        ).pos(leftPos + 6, topPos + 5).size(14, 14).build());

        // Кнопка режима нехватки
        addRenderableWidget(Button.builder(
                Component.translatable(menu.blockEntity.requireAll ? "config.logisticsports.require.all" : "config.logisticsports.require.notall"),
                btn -> {
                    boolean newVal = !menu.blockEntity.requireAll;
                    menu.blockEntity.requireAll = newVal;
                    btn.setMessage(Component.translatable(newVal ? "config.logisticsports.require.all" : "config.logisticsports.require.notall"));
                    com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                            new com.logisticsports.network.PacketUpdateSettings(
                                    menu.blockEntity.getBlockPos(), newVal));
                }
        ).pos(leftPos + 95, topPos + 74).size(98, 14).build());

        // Кнопка упаковки посылки
        packageModeButton = Button.builder(
                Component.translatable(menu.blockEntity.packageMode ? "config.logisticsports.yes" : "config.logisticsports.no"),
                btn -> {
                    boolean newVal = !menu.blockEntity.packageMode;
                    menu.blockEntity.packageMode = newVal;
                    btn.setMessage(Component.translatable(newVal ? "config.logisticsports.yes" : "config.logisticsports.no"));
                    updateRecipientFieldVisibility();
                    com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                            new com.logisticsports.network.PacketUpdatePackageMode(
                                    menu.blockEntity.getBlockPos(), newVal));
                }
        ).pos(leftPos + 150, topPos + 91).size(40, 14).build();
        addRenderableWidget(packageModeButton);

        // Поле адресата
        recipientField = new EditBox(font,
                leftPos + 8, topPos + 113,
                150, 12, Component.literal(""));
        recipientField.setValue(menu.blockEntity.recipient);
        recipientField.setMaxLength(64);
        recipientField.setResponder(val -> {
            menu.blockEntity.recipient = val;
            com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                    new com.logisticsports.network.PacketUpdateRecipient(
                            menu.blockEntity.getBlockPos(), val));
        });
        addRenderableWidget(recipientField);
        updateRecipientFieldVisibility();
    }

    private void updateRecipientFieldVisibility() {
        if (recipientField != null) {
            recipientField.setVisible(menu.blockEntity.packageMode);
        }
    }

    private void goBack() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.closeContainer();
        com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                new com.logisticsports.network.PacketOpenMainScreen(
                        menu.blockEntity.getBlockPos()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (int i = 0; i < 10; i++) {
            Slot slot = menu.slots.get(i);
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) {
                menu.scrollSlot(i, delta > 0);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        g.drawString(font, Component.translatable("config.logisticsports.settings"), x + 26, y + 8, 0xFF222222, false);
        g.fill(x + 4, y + 18, x + BG_WIDTH - 4, y + 19, 0xFF888888);

        // Список заказа
        g.drawString(font, Component.translatable("config.logisticsports.order_list"), x + 8, y + 22, 0xFF222222, false);
        for (int i = 0; i < 9; i++) {
            int sx = x + 7 + i * 18;
            int sy = y + 30;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF888888);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFAAAAAA);
        }

        // Индикатор
        g.drawString(font, Component.translatable("config.logisticsports.indicator"), x + BG_WIDTH - 80, y + 8, 0xFF222222, false);
        int ix = x + BG_WIDTH - 26;
        int iy = y + 3;
        g.fill(ix, iy, ix + 18, iy + 18, 0xFF888888);
        g.fill(ix + 1, iy + 1, ix + 17, iy + 17, 0xFFDDDD55);

        // Частота
        g.drawString(font, Component.translatable("config.logisticsports.frequency_menu"), x + 8, y + 60, 0xFF222222, false);

        // При нехватке
        g.drawString(font, Component.translatable("config.logisticsports.shortage"), x + 8, y + 77, 0xFF222222, false);

        // Упаковывать посылку
        g.drawString(font, Component.translatable("config.logisticsports.pack"), x + 8, y + 94, 0xFF222222, false);

        // Адрес получателя (только если packageMode)
        if (menu.blockEntity.packageMode) {
            g.drawString(font, Component.translatable("config.logisticsports.recipient"), x + 8, y + 103, 0xFF222222, false);
        }

        // Разделитель перед инвентарём — прикреплён к инвентарю
        int invTop = BG_HEIGHT - 18 * 4 - 8;
        g.fill(x + 4, y + invTop - 12, x + BG_WIDTH - 4, y + invTop - 11, 0xFF888888);
        g.drawString(font, Component.translatable("config.logisticsports.inventory"), x + 8, y + invTop - 9, 0xFF555555, false);

        // Инвентарь
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 7 + col * 18;
                int sy = y + invTop + row * 18;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF888888);
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFAAAAAA);
            }
        }

        // Хотбар
        for (int col = 0; col < 9; col++) {
            int sx = x + 7 + col * 18;
            int sy = y + invTop + 3 * 18 + 4;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF888888);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFAAAAAA);
        }
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