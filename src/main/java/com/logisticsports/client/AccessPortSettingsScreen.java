package com.logisticsports.client;

import com.logisticsports.blockentity.AccessPortBlockEntity;
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
    private static final boolean GREG_DEBUG = !net.minecraftforge.fml.loading.FMLLoader.isProduction();
    private EditBox frequencyField;
    private EditBox gtcCircuitField;
    private EditBox recipientField;
    private Button packageModeButton;
    private Button multiportButton;
    private Button requireAllButton;
    private final List<String> filteredSuggestions = new ArrayList<>();
    private boolean showSuggestions = false;
    private int scrollOffset = 0;
    private float settingsScroll = 0;
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

        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        int recipeRows = (activeRecipeSlots + 8) / 9;
        int settingsStartY = 30 + recipeRows * 18 + 10;

        frequencyField = new EditBox(font,
                leftPos + 55, topPos + settingsStartY,
                40, 12, Component.literal("0"));
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

        // GregTech интегральная схема
        boolean isGregTechLoaded = net.minecraftforge.fml.ModList.get().isLoaded("gtceu");
        if (GREG_DEBUG || isGregTechLoaded) {
            gtcCircuitField = new EditBox(font,
                    leftPos + 160, topPos + settingsStartY,
                    30, 12, Component.literal("0"));
            gtcCircuitField.setValue(String.valueOf(menu.blockEntity.gtcCircuit));
            gtcCircuitField.setFilter(s -> s.matches("\\d*"));
            gtcCircuitField.setResponder(val -> {
                try {
                    int circuit = val.isEmpty() ? 0 : Integer.parseInt(val);
                    if (circuit > 24) circuit = 24;
                    if (circuit < 0) circuit = 0;
                    
                    com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                            new com.logisticsports.network.PacketUpdateGTCCircuit(
                                    menu.blockEntity.getBlockPos(), circuit));
                } catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(gtcCircuitField);
        }

        // Кнопка назад
        addRenderableWidget(Button.builder(
                Component.literal("←"),
                btn -> goBack()
        ).pos(leftPos + 6, topPos + 5).size(14, 14).build());

        // Кнопка режима нехватки
        requireAllButton = Button.builder(
                Component.translatable(menu.blockEntity.requireAll ? "config.logisticsports.require.all" : "config.logisticsports.require.notall"),
                btn -> {
                    boolean newVal = !menu.blockEntity.requireAll;
                    menu.blockEntity.requireAll = newVal;
                    btn.setMessage(Component.translatable(newVal ? "config.logisticsports.require.all" : "config.logisticsports.require.notall"));
                    com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                            new com.logisticsports.network.PacketUpdateSettings(
                                    menu.blockEntity.getBlockPos(), newVal));
                }
        ).pos(leftPos + 95, topPos + settingsStartY + 16).size(98, 14).build();
        addRenderableWidget(requireAllButton);

        // Кнопка режима мультипорта
        multiportButton = Button.builder(
                Component.translatable(menu.blockEntity.isMultiport ? "config.logisticsports.yes" : "config.logisticsports.no"),
                btn -> {
                    boolean newVal = !menu.blockEntity.isMultiport;
                    menu.blockEntity.isMultiport = newVal;
                    btn.setMessage(Component.translatable(newVal ? "config.logisticsports.yes" : "config.logisticsports.no"));
                    com.logisticsports.network.ModNetwork.CHANNEL.sendToServer(
                            new com.logisticsports.network.PacketUpdateMultiportMode(newVal));
                }
        ).pos(leftPos + 150, topPos + settingsStartY + 33).size(40, 14).build();
        addRenderableWidget(multiportButton);

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
        ).pos(leftPos + 150, topPos + settingsStartY + 49).size(40, 14).build();
        addRenderableWidget(packageModeButton);

        // Поле адресата
        recipientField = new EditBox(font,
                leftPos + 8, topPos + settingsStartY + 77,
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
        updateWidgetPositions();
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

            // Если включен режим упаковки - сдвигаем элементы
//            if (visible) {
//                recipientField.setY(topPos + 135);
//                packageModeButton.setY(topPos + 91);
//                multiportButton.setY(topPos + 107);
//            } else {
//                packageModeButton.setY(topPos + 91);
//                multiportButton.setY(topPos + 107);
//            }
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
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        int recipeRows = (activeRecipeSlots + 8) / 9;
        int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
        boolean horizontalFluids = activeFluidSlots > recipeRows;
        int fluidRows = horizontalFluids ? (activeFluidSlots + 8) / 9 : 0;
        int settingsStartY = 30 + (recipeRows + fluidRows) * 18 + 10;
        int invTop = BG_HEIGHT - 18 * 4 - 8;

        if (showSuggestions && !filteredSuggestions.isEmpty()) {
            int x = leftPos + 8;
            int y = (int) (topPos + settingsStartY + 77 - 10 - settingsScroll);
            int width = 150;
            int height = Math.min(filteredSuggestions.size(), SUGGESTIONS_COUNT) * 12;
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                int maxScroll = Math.max(0, filteredSuggestions.size() - SUGGESTIONS_COUNT);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
                return true;
            }
        }

        int scrollableViewHeight = invTop - 12 - settingsStartY;
        int totalContentHeight = 105; // Высота всех настроек с запасом

        if (mouseX >= leftPos + 4 && mouseX <= leftPos + BG_WIDTH - 4 && 
            mouseY >= topPos + settingsStartY && mouseY <= topPos + invTop - 12) {
            if (totalContentHeight > scrollableViewHeight) {
                settingsScroll = Math.min(Math.max(settingsScroll - (float)delta * 10, 0), totalContentHeight - scrollableViewHeight);
                updateWidgetPositions();
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

        for (int i = 0; i < activeFluidSlots; i++) {
            int fx, fy;
            if (horizontalFluids) {
                int row = i / 9;
                int col = i % 9;
                fx = leftPos + 7 + col * 18;
                fy = topPos + 30 + (recipeRows + row) * 18;
            } else {
                fx = leftPos + 8 + 9 * 18 + 4;
                fy = topPos + 30 + i * 18;
            }

            if (mouseX >= fx && mouseX <= fx + 16 && mouseY >= fy && mouseY <= fy + 16) {
                FluidStack fluid = menu.blockEntity.fluidsRecipe.get(i);
                if (!fluid.isEmpty()) {
                    int currentAmount = fluid.getAmount();
                    int newAmount = currentAmount + finalAmount;
                    if (newAmount < 1) {
                        newAmount = 1; // Минимум 1 mB
                    }
                    if (newAmount != currentAmount) {
                        FluidStack copy = fluid.copy();
                        copy.setAmount(newAmount);
                        menu.syncFluid(i, copy);
                    }
                }
                return true;
            }
        }

        for (int i = 0; i < activeRecipeSlots + 1; i++) {
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

    private void updateWidgetPositions() {
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        int recipeRows = (activeRecipeSlots + 8) / 9;
        int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
        boolean horizontalFluids = activeFluidSlots > recipeRows;
        int fluidRows = horizontalFluids ? (activeFluidSlots + 8) / 9 : 0;
        int settingsStartY = 30 + (recipeRows + fluidRows) * 18 + 10;
        int currentY = (int) (topPos + settingsStartY - settingsScroll);

        int invTop = BG_HEIGHT - 18 * 4 - 8;
        int scrollableViewStartY = topPos + settingsStartY;
        int scrollableViewEndY = topPos + invTop - 12;

        if (frequencyField != null) {
            frequencyField.setY(currentY);
            frequencyField.visible = isWidgetVisible(frequencyField.getY(), frequencyField.getHeight(), scrollableViewStartY, scrollableViewEndY);
        }
        if (gtcCircuitField != null) {
            gtcCircuitField.setY(currentY);
            gtcCircuitField.visible = isWidgetVisible(gtcCircuitField.getY(), gtcCircuitField.getHeight(), scrollableViewStartY, scrollableViewEndY);
        }
        
        if (requireAllButton != null) {
            requireAllButton.setY(currentY + 16);
            requireAllButton.visible = isWidgetVisible(requireAllButton.getY(), requireAllButton.getHeight(), scrollableViewStartY, scrollableViewEndY);
        }

        if (multiportButton != null) {
            multiportButton.setY(currentY + 33);
            multiportButton.visible = isWidgetVisible(multiportButton.getY(), multiportButton.getHeight(), scrollableViewStartY, scrollableViewEndY);
        }

        if (packageModeButton != null) {
            packageModeButton.setY(currentY + 49);
            packageModeButton.visible = isWidgetVisible(packageModeButton.getY(), packageModeButton.getHeight(), scrollableViewStartY, scrollableViewEndY);
        }

        if (recipientField != null) {
            recipientField.setY(currentY + 77);
            boolean visibleInScroll = isWidgetVisible(recipientField.getY(), recipientField.getHeight(), scrollableViewStartY, scrollableViewEndY);
            recipientField.visible = menu.blockEntity.packageMode && visibleInScroll;
        }

        // Кнопка requireAll не сохранена в поле, но она есть в renderables. 
        // Для простоты я добавлю её обновление через перебор renderables если нужно, 
        // но лучше просто обновлять те что важны.
        // Переделаем init чтобы сохранить все важные кнопки.
    }

    private boolean isWidgetVisible(int widgetY, int widgetHeight, int startY, int endY) {
        return widgetY >= startY && (widgetY + widgetHeight) <= endY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        int recipeRows = (activeRecipeSlots + 8) / 9;
        int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
        boolean horizontalFluids = activeFluidSlots > recipeRows;
        int fluidRows = horizontalFluids ? (activeFluidSlots + 8) / 9 : 0;
        int settingsStartY = 30 + (recipeRows + fluidRows) * 18 + 10;
        int invTop = BG_HEIGHT - 18 * 4 - 8;

        if (showSuggestions && !filteredSuggestions.isEmpty()) {
            int x = leftPos + 8;
            int y = (int) (topPos + settingsStartY + 77 - 10 - settingsScroll);
            int maxShow = Math.min(filteredSuggestions.size(), SUGGESTIONS_COUNT);
            for (int i = 0; i < maxShow; i++) {
                if (mouseX >= x && mouseX <= x + 150 && mouseY >= y + i * 12 && mouseY <= y + (i + 1) * 12) {
                    recipientField.setValue(filteredSuggestions.get(i + scrollOffset));
                    showSuggestions = false;
                    return true;
                }
            }
        }

        if (recipientField != null && recipientField.isVisible() && recipientField.visible &&
            mouseX >= recipientField.getX() && mouseX <= recipientField.getX() + recipientField.getWidth() 
                && mouseY >= recipientField.getY() && mouseY <= recipientField.getY() + recipientField.getHeight()) {
            showSuggestions = true;
            updateSuggestions(recipientField.getValue());
        } else {
            showSuggestions = false;
        }

        for (int i = 0; i < activeFluidSlots; i++) {
            int curFx, curFy;
            if (horizontalFluids) {
                int row = i / 9;
                int col = i % 9;
                curFx = leftPos + 7 + col * 18;
                curFy = topPos + 30 + (recipeRows + row) * 18;
            } else {
                curFx = leftPos + 8 + 9 * 18 + 4;
                curFy = topPos + 30 + i * 18;
            }
            if (mouseX >= curFx && mouseX <= curFx + 16 && mouseY >= curFy && mouseY <= curFy + 16) {
                if (button == 0) { // ЛКМ
                    ItemStack carried = menu.getCarried();
                    if (carried.isEmpty()) {
                        FluidStack fluid = menu.blockEntity.fluidsRecipe.get(i);
                        if (!fluid.isEmpty()) {
                            FluidStack copy = fluid.copy();
                            copy.setAmount(fluid.getAmount() + 1000);
                            menu.syncFluid(i, copy);
                            return true;
                        }
                    }
                }
                menu.clicked(100 + i, button, net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
                return true;
            }
        }

        // Клик по слотам рецепта и индикатору
        for (int i = 0; i < activeRecipeSlots + 1; i++) {
            Slot slot = menu.slots.get(i);
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) {
                menu.clicked(i, button, net.minecraft.world.inventory.ClickType.PICKUP, minecraft.player);
                return true;
            }
        }

        // Клик только по видимым виджетам в области скролла
        boolean inScrollArea = mouseX >= leftPos + 4 && mouseX <= leftPos + BG_WIDTH - 4 && 
                               mouseY >= topPos + settingsStartY && mouseY <= topPos + invTop - 12;

        if (inScrollArea) {
            return super.mouseClicked(mouseX, mouseY, button);
        } else {
            // Клик вне скролл-зоны — обрабатываем только виджеты вне её (например, кнопка назад)
            // или стандартный клик инвентаря
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        int recipeRows = (activeRecipeSlots + 8) / 9;
        for (int i = 0; i < activeRecipeSlots; i++) {
            int row = i / 9;
            int col = i % 9;
            int sx = x + 7 + col * 18;
            int sy = y + 30 + row * 18;
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
        int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
        boolean horizontalFluids = activeFluidSlots > recipeRows;

        for (int i = 0; i < activeFluidSlots; i++) {
            int fx, fy;
            if (horizontalFluids) {
                int row = i / 9;
                int col = i % 9;
                fx = x + 7 + col * 18;
                fy = y + 30 + (recipeRows + row) * 18;
            } else {
                fx = x + 8 + 9 * 18 + 4;
                fy = y + 30 + i * 18;
            }
            g.fill(fx, fy, fx + 18, fy + 18, 0xFF888888);
            g.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0xFF777777);

            FluidStack fluid = menu.blockEntity.fluidsRecipe.get(i);
            if (!fluid.isEmpty()) {
                renderFluid(g, fluid, fx + 1, fy + 1);
            }
        }

        // --- Область скролла настроек ---
        int fluidRows = horizontalFluids ? (activeFluidSlots + 8) / 9 : 0;
        int settingsStartY = 30 + (recipeRows + fluidRows) * 18 + 10;
        int invTop = BG_HEIGHT - 18 * 4 - 8;
        int scrollableViewHeight = invTop - 12 - settingsStartY;
        int totalContentHeight = 105;

        g.enableScissor(x + 4, y + settingsStartY, x + BG_WIDTH - 4, y + invTop - 12);
        
        int currentY = (int) (y + settingsStartY - settingsScroll);

        // Частота
        g.drawString(font, Component.translatable("config.logisticsports.frequency_menu"), x + 8, currentY + 2, 0xFF222222, false);

        // При нехватке
        g.drawString(font, Component.translatable("config.logisticsports.shortage"), x + 8, currentY + 19, 0xFF222222, false);

        // Режим мультипорта
        g.drawString(font, Component.translatable("config.logisticsports.multiport"), x + 8, currentY + 36, 0xFF222222, false);

        // Упаковывать посылку
        g.drawString(font, Component.translatable("config.logisticsports.pack"), x + 8, currentY + 52, 0xFF222222, false);

        // GregTech текст
        if (gtcCircuitField != null) {
            g.drawString(font, Component.literal("GT Circuit:"), x + 105, currentY + 2, 0xFF222222, false);
        }

        // Адрес получателя (только если packageMode)
        if (menu.blockEntity.packageMode) {
            g.drawString(font, Component.translatable("config.logisticsports.recipient"), x + 8, currentY + 68, 0xFF222222, false);
        }

        g.disableScissor();

        // Скроллбар для настроек
        if (totalContentHeight > scrollableViewHeight) {
            int scrollbarX = x + BG_WIDTH - 3;
            int scrollbarHeight = (int)((scrollableViewHeight / (float)totalContentHeight) * scrollableViewHeight);
            int scrollbarY = y + settingsStartY + (int)((settingsScroll / (float)totalContentHeight) * scrollableViewHeight);
            g.fill(scrollbarX, y + settingsStartY, x + BG_WIDTH - 1, y + invTop - 12, 0xFF444444);
            g.fill(scrollbarX, scrollbarY, x + BG_WIDTH - 1, scrollbarY + scrollbarHeight, 0xFF888888);
        }

        // Разделитель перед инвентарём — прикреплён к инвентарю
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
        updateWidgetPositions(); // Обновляем позиции перед отрисовкой
        super.render(g, mx, my, partialTick);

        if (showSuggestions && !filteredSuggestions.isEmpty() && recipientField != null && recipientField.isVisible() && recipientField.visible) {
            g.pose().pushPose();
            // Поднимаем предложения над слотами (которые обычно 0-100), но под тултипы (которые на 400)
            g.pose().translate(0, 0, 300);
            int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
            int recipeRows = (activeRecipeSlots + 8) / 9;
            int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
            boolean horizontalFluids = activeFluidSlots > recipeRows;
            int fluidRows = horizontalFluids ? (activeFluidSlots + 8) / 9 : 0;
            int settingsStartY = 30 + (recipeRows + fluidRows) * 18 + 10;
            int x = leftPos + 8;
            int y = (int) (topPos + settingsStartY + 77 - 10 - settingsScroll);
            int maxShow = Math.min(filteredSuggestions.size(), SUGGESTIONS_COUNT);
            int height = maxShow * 12;
            int width = 150;
            
            // Клиппинг предложений, чтобы они не вылезали за скролл-область?
            // Но обычно они должны перекрывать инвентарь. Пользователь просил "не наезжало на инвентарь"
            // для настроек. Подсказки - это временный оверлей.
            
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
        int activeRecipeSlots = AccessPortBlockEntity.getRecipeSlots();
        int recipeRows = (activeRecipeSlots + 8) / 9;
        int activeFluidSlots = AccessPortBlockEntity.getFluidRecipeSlots();
        boolean horizontalFluids = activeFluidSlots > recipeRows;

        for (int i = 0; i < activeFluidSlots; i++) {
            int curFx, curFy;
            if (horizontalFluids) {
                int row = i / 9;
                int col = i % 9;
                curFx = leftPos + 7 + col * 18;
                curFy = topPos + 30 + (recipeRows + row) * 18;
            } else {
                curFx = leftPos + 8 + 9 * 18 + 4;
                curFy = topPos + 30 + i * 18;
            }
            if (mx >= curFx && mx <= curFx + 18 && my >= curFy && my <= curFy + 18) {
                FluidStack fluid = menu.blockEntity.fluidsRecipe.get(i);
                if (!fluid.isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(fluid.getDisplayName());
                    tooltip.add(Component.literal("§7" + fluid.getAmount() + " mB"));
                    g.renderComponentTooltip(font, tooltip, mx, my);
                }
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