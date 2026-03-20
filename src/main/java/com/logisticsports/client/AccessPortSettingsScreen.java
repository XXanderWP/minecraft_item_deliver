package com.logisticsports.client;

import net.minecraftforge.fluids.FluidStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.client.Minecraft;
import com.logisticsports.menu.AccessPortSettingsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AccessPortSettingsScreen extends AbstractContainerScreen<AccessPortSettingsMenu> {

    private static final int BG_WIDTH = 200;
    private static final int BG_HEIGHT = 253;
    private EditBox frequencyField;
    private EditBox recipientField;
    private Button packageModeButton;
    private final List<String> filteredSuggestions = new ArrayList<>();
    private boolean showSuggestions = false;
    private int scrollOffset = 0;
    private static final int SUGGESTIONS_COUNT = 10;

    public AccessPortSettingsScreen(AccessPortSettingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        System.out.println("[DEBUG_LOG] Initializing AccessPortSettingsScreen. Suggestions available: " + menu.availableRecipients.size() + " -> " + menu.availableRecipients);

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
            updateSuggestions(val);
            com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                    new com.logisticsports.network.PacketUpdateRecipient(
                            menu.blockEntity.getBlockPos(), val));
        });
        addRenderableWidget(recipientField);

        updateRecipientFieldVisibility();
    }

    private void updateSuggestions(String input) {
        filteredSuggestions.clear();
        String lowerInput = input.toLowerCase();
        for (String s : menu.availableRecipients) {
            if (s.toLowerCase().contains(lowerInput)) {
                filteredSuggestions.add(s);
            }
        }
        scrollOffset = 0;
        System.out.println("[DEBUG_LOG] Updated suggestions for '" + input + "'. Found: " + filteredSuggestions.size());
    }

    private void updateRecipientFieldVisibility() {
        if (recipientField != null) {
            boolean visible = menu.blockEntity.packageMode;
            recipientField.setVisible(visible);
            if (!visible) showSuggestions = false;
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
        if (showSuggestions && !filteredSuggestions.isEmpty()) {
            int x = leftPos + 8;
            int y = topPos + 125;
            int width = 150;
            int height = Math.min(filteredSuggestions.size(), SUGGESTIONS_COUNT) * 12;
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                int maxScroll = Math.max(0, filteredSuggestions.size() - SUGGESTIONS_COUNT);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
                return true;
            }
        }

        boolean shift = hasShiftDown();
        boolean ctrl = hasControlDown();
        int amount = 1;
        if (shift && ctrl) amount = 1000;
        else if (ctrl) amount = 100;
        else if (shift) amount = 10;
        
        int finalAmount = (delta > 0 ? 1 : -1) * amount;

        // Слот жидкости (100)
        int fx = leftPos + 8 + 9 * 18 + 4;
        int fy = topPos + 30;
        if (mouseX >= fx && mouseX <= fx + 16 && mouseY >= fy && mouseY <= fy + 16) {
             FluidStack fluid = menu.blockEntity.fluidRecipe;
             if (!fluid.isEmpty()) {
                 int currentAmount = fluid.getAmount();
                 int newAmount = currentAmount + finalAmount;
                 if (newAmount < 1) {
                     newAmount = 1; // Минимум 1 mB
                 }
                 if (newAmount != currentAmount) {
                     FluidStack copy = fluid.copy();
                     copy.setAmount(newAmount);
                     menu.syncFluid(copy);
                 }
             }
             return true;
        }

        for (int i = 0; i < 10; i++) {
            Slot slot = menu.slots.get(i);
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) {
                menu.scrollSlot(i, finalAmount);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showSuggestions && !filteredSuggestions.isEmpty()) {
            int x = leftPos + 8;
            int y = topPos + 125;
            int maxShow = Math.min(filteredSuggestions.size(), SUGGESTIONS_COUNT);
            for (int i = 0; i < maxShow; i++) {
                if (mouseX >= x && mouseX <= x + 150 && mouseY >= y + i * 12 && mouseY <= y + (i + 1) * 12) {
                    recipientField.setValue(filteredSuggestions.get(i + scrollOffset));
                    showSuggestions = false;
                    return true;
                }
            }
        }

        if (recipientField != null && recipientField.isVisible() && mouseX >= recipientField.getX() && mouseX <= recipientField.getX() + recipientField.getWidth() 
                && mouseY >= recipientField.getY() && mouseY <= recipientField.getY() + recipientField.getHeight()) {
            showSuggestions = true;
            updateSuggestions(recipientField.getValue());
        } else {
            showSuggestions = false;
        }

        int fx = leftPos + 8 + 9 * 18 + 4;
        int fy = topPos + 30;
        if (mouseX >= fx && mouseX <= fx + 16 && mouseY >= fy && mouseY <= fy + 16) {
            if (button == 0) { // ЛКМ
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) {
                    FluidStack fluid = menu.blockEntity.fluidRecipe;
                    if (!fluid.isEmpty()) {
                        FluidStack copy = fluid.copy();
                        copy.setAmount(fluid.getAmount() + 1000);
                        menu.syncFluid(copy);
                        return true;
                    }
                }
            }
            menu.clicked(100, button, net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
            return true;
        }

        // Клик по слотам рецепта и индикатору
        for (int i = 0; i < 10; i++) {
            Slot slot = menu.slots.get(i);
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) {
                // Все клики по слотам 0-9 теперь обрабатываются через виртуальный клик
                // аналогично слоту жидкости (100)
                menu.clicked(i, button, net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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

        // Слот жидкости
        int fx = x + 8 + 9 * 18 + 4;
        int fy = y + 30;
        g.fill(fx, fy, fx + 18, fy + 18, 0xFF888888);
        g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0xFF777777);

        FluidStack fluid = menu.blockEntity.fluidRecipe;
        if (!fluid.isEmpty()) {
            boolean isDraggingFluid = !minecraft.player.containerMenu.getCarried().isEmpty() && 
                    net.minecraftforge.fluids.FluidUtil.getFluidContained(minecraft.player.containerMenu.getCarried()).isPresent();
            int dx = 0;
            int dy = 0;
            renderFluid(g, fluid, fx + 1 + dx, fy + 1 + dy);
        }

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
        super.render(g, mx, my, partialTick);

        if (showSuggestions && !filteredSuggestions.isEmpty() && recipientField != null && recipientField.isVisible()) {
            g.pose().pushPose();
            // Поднимаем предложения над слотами (которые обычно 0-100), но под тултипы (которые на 400)
            g.pose().translate(0, 0, 300);
            int x = leftPos + 8;
            int y = topPos + 125;
            int maxShow = Math.min(filteredSuggestions.size(), SUGGESTIONS_COUNT);
            int height = maxShow * 12;
            int width = 150;
            g.fill(x, y, x + width, y + height, 0xDD000000);
            
            // Скроллбар
            if (filteredSuggestions.size() > SUGGESTIONS_COUNT) {
                int scrollWidth = 2;
                int scrollX = x + width - scrollWidth;
                int scrollHeight = (int) (height * (double) SUGGESTIONS_COUNT / filteredSuggestions.size());
                int scrollY = y + (int) ((height - scrollHeight) * (double) scrollOffset / (filteredSuggestions.size() - SUGGESTIONS_COUNT));
                g.fill(scrollX, y, x + width, y + height, 0xFF444444);
                g.fill(scrollX, scrollY, x + width, scrollY + scrollHeight, 0xFF888888);
            }

            for (int i = 0; i < maxShow; i++) {
                int color = 0xFFFFFFFF;
                String suggestion = filteredSuggestions.get(i + scrollOffset);
                if (mx >= x && mx <= x + width && my >= y + i * 12 && my <= y + (i + 1) * 12) {
                    color = 0xFFFFFFA0;
                    g.fill(x, y + i * 12, x + width, y + (i + 1) * 12, 0x44FFFFFF);
                }
                g.drawString(font, suggestion, x + 2, y + 2 + i * 12, color);
            }
            g.pose().popPose();
        }

        // Тултип для жидкости
        int fx = leftPos + 8 + 9 * 18 + 4;
        int fy = topPos + 30;
        if (mx >= fx && mx <= fx + 18 && my >= fy && my <= fy + 18) {
            FluidStack fluid = menu.blockEntity.fluidRecipe;
            if (!fluid.isEmpty()) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(fluid.getDisplayName());
                tooltip.add(Component.literal("§7" + fluid.getAmount() + " mB"));
                g.renderComponentTooltip(font, tooltip, mx, my);
            }
        }

        renderTooltip(g, mx, my);
    }

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

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}
}