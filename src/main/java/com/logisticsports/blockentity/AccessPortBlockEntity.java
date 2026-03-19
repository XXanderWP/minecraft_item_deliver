package com.logisticsports.blockentity;

import com.logisticsports.block.AccessPortBlock;
import com.logisticsports.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.logisticsports.menu.AccessPortMenu;
import com.logisticsports.blockentity.OutputPortBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

public class AccessPortBlockEntity extends BlockEntity implements MenuProvider {
    public boolean packageMode = false;
    public String recipient = "";
    // Рецепт — 9 слотов
    public final NonNullList<ItemStack> recipe = NonNullList.withSize(9, ItemStack.EMPTY);
    // Индикаторный предмет
    public ItemStack indicator = ItemStack.EMPTY;
    // Частота сети
    public int frequency = 0;
    // Поведение при нехватке: true = только если всё есть, false = выдавать что есть
    public boolean requireAll = true;
    public String getAccessPortId() {
        return worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ();
    }

    public AccessPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ACCESS_PORT_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AccessPortBlockEntity be) {
        // Пока пусто, логика будет добавлена позже
    }

    public void placeOrder(Player player, int batches) {
        if (level == null || level.isClientSide) return;

        // Собираем список нужных предметов с учётом партий
        List<ItemStack> needed = buildOrderList(batches);
        if (needed.isEmpty()) {
            player.sendSystemMessage(Component.translatable("config.logisticsports.action.warning_chat", Component.translatable("config.logisticsports.recipe_is_empty")));
            setStatus(2); // провал
            return;
        }

        // Находим все Порты Выдачи на той же частоте
        List<OutputPortBlockEntity> ports = findOutputPorts();
        if (ports.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("config.logisticsports.action.error_chat", Component.translatable("config.logisticsports.action.no_output", frequency))
            );
            setStatus(2); // провал
            return;
        }

        String effectiveRecipient = recipient;
        if (packageMode && effectiveRecipient.isBlank()) {
            effectiveRecipient = getAccessPortId();
        }

        // Проверяем наличие предметов в хранилищах
        Map<ItemStack, Integer> available = scanAvailable(ports);
        List<String> missing = new ArrayList<>();

        for (ItemStack need : needed) {
            int have = getAvailableCount(available, need);
            if (have < need.getCount()) {
                if (requireAll) {
                    missing.add(need.getHoverName().getString()
                            + " (" + have + "/" + need.getCount() + ")");
                }
            }
        }

        if (requireAll && !missing.isEmpty()) {
            player.sendSystemMessage(Component.translatable("config.logisticsports.action.error_chat", Component.translatable("config.logisticsports.action.missing")));
            for (String m : missing) {
                player.sendSystemMessage(Component.literal("§c  - " + m));
            }
            setStatus(2); // провал
            return;
        }

        // Проверяем место в портах выдачи
        if (!hasEnoughSpace(ports, needed)) {
            player.sendSystemMessage(Component.translatable("config.logisticsports.action.error_chat", Component.translatable("config.logisticsports.action.no_space")));
            setStatus(2); // провал
            return;
        }

        // Перемещаем предметы
        executeOrder(ports, needed, available, effectiveRecipient);
        player.sendSystemMessage(Component.translatable("config.logisticsports.action.success_chat", Component.translatable("config.logisticsports.action.order_complete")));
        setStatus(1); // успех
    }

    public void setStatus(int status) {
        if (level == null) return;
        BlockState state = level.getBlockState(worldPosition);
        level.setBlock(worldPosition, state.setValue(AccessPortBlock.STATUS, status), 3);
        level.scheduleTick(worldPosition, state.getBlock(), 20);

        if (status == 1) {
            com.simibubi.create.AllSoundEvents.CONFIRM.playOnServer(level, worldPosition);
        } else if (status == 2) {
            com.simibubi.create.AllSoundEvents.DENY.playOnServer(level, worldPosition);
        }
    }

    private List<ItemStack> buildOrderList(int batches) {
        Map<String, ItemStack> grouped = new LinkedHashMap<>();
        for (ItemStack stack : recipe) {
            if (stack.isEmpty()) continue;
            String key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString();
            if (grouped.containsKey(key)) {
                grouped.get(key).grow(stack.getCount() * batches);
            } else {
                ItemStack copy = stack.copy();
                copy.setCount(stack.getCount() * batches);
                grouped.put(key, copy);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private List<OutputPortBlockEntity> findOutputPorts() {
        List<OutputPortBlockEntity> result = new ArrayList<>();
        if (level == null) return result;

        // Сканируем блоки в радиусе 64 блоков
        BlockPos center = worldPosition;
        int radius = 64;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OutputPortBlockEntity port && port.frequency == this.frequency) {
                result.add(port);
            }
        }
        return result;
    }

    private Map<ItemStack, Integer> scanAvailable(List<OutputPortBlockEntity> ports) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, ItemStack> stackRefs = new LinkedHashMap<>();

        for (OutputPortBlockEntity port : ports) {
            // Сканируем хранилища рядом с портом
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = port.getBlockPos().relative(dir);
                if (level == null) continue;
                BlockEntity neighbor_be = level.getBlockEntity(neighbor);
                if (neighbor_be == null) continue;
                var cap = neighbor_be.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                        dir.getOpposite());
                cap.ifPresent(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack s = handler.getStackInSlot(i);
                        if (s.isEmpty()) continue;
                        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(s.getItem()).toString();
                        counts.computeIfAbsent(key, k -> new int[]{0})[0] += s.getCount();
                        stackRefs.putIfAbsent(key, s);
                    }
                });
            }
        }

        Map<ItemStack, Integer> result = new LinkedHashMap<>();
        for (var entry : counts.entrySet()) {
            result.put(stackRefs.get(entry.getKey()), entry.getValue()[0]);
        }
        return result;
    }

    private int getAvailableCount(Map<ItemStack, Integer> available, ItemStack need) {
        for (var entry : available.entrySet()) {
            if (ItemStack.isSameItemSameTags(entry.getKey(), need)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private boolean hasEnoughSpace(List<OutputPortBlockEntity> ports, List<ItemStack> needed) {
        for (ItemStack need : needed) {
            int totalSpace = 0;
            for (OutputPortBlockEntity port : ports) {
                totalSpace += port.getFreeSpaceFor(need);
            }
            if (totalSpace < need.getCount()) return false;
        }
        return true;
    }

    private void executeOrder(List<OutputPortBlockEntity> ports,
                              List<ItemStack> needed,
                              Map<ItemStack, Integer> available,
                              String effectiveRecipient) {
        for (OutputPortBlockEntity port : ports) {

            if (packageMode) {
                // Собираем предметы для посылки
                List<ItemStack> collectedForPackage = new ArrayList<>();

                for (ItemStack need : needed) {
                    int remaining = need.getCount();

                    for (Direction dir : Direction.values()) {
                        if (remaining <= 0) break;
                        BlockPos neighbor = port.getBlockPos().relative(dir);
                        if (level == null) continue;
                        BlockEntity be = level.getBlockEntity(neighbor);
                        if (be == null) continue;

                        var capOpt = be.getCapability(
                                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                                dir.getOpposite());
                        if (!capOpt.isPresent()) continue;
                        var handler = capOpt.orElse(null);
                        if (handler == null) continue;

                        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
                            ItemStack s = handler.getStackInSlot(i);
                            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, need)) continue;
                            ItemStack extracted = handler.extractItem(i, remaining, false);
                            if (!extracted.isEmpty()) {
                                collectedForPackage.add(extracted.copy());
                                remaining -= extracted.getCount();
                            }
                        }
                    }
                }

                if (!collectedForPackage.isEmpty()) {
                    // Создаём посылку Create
                    ItemStack packageStack = com.simibubi.create.content.logistics.box.PackageItem
                            .containing(collectedForPackage);
                    if (!effectiveRecipient.isBlank()) {
                        com.simibubi.create.content.logistics.box.PackageItem
                                .addAddress(packageStack, effectiveRecipient);
                    }
                    port.insertItem(packageStack);
                }

            } else {
                // Обычная выдача предметов
                for (ItemStack need : needed) {
                    int remaining = need.getCount();

                    for (Direction dir : Direction.values()) {
                        if (remaining <= 0) break;
                        BlockPos neighbor = port.getBlockPos().relative(dir);
                        if (level == null) continue;
                        BlockEntity be = level.getBlockEntity(neighbor);
                        if (be == null) continue;

                        var capOpt = be.getCapability(
                                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                                dir.getOpposite());
                        if (!capOpt.isPresent()) continue;
                        var handler = capOpt.orElse(null);
                        if (handler == null) continue;

                        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
                            ItemStack s = handler.getStackInSlot(i);
                            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, need)) continue;
                            ItemStack extracted = handler.extractItem(i, remaining, false);
                            if (!extracted.isEmpty()) {
                                port.insertItem(extracted);
                                remaining -= extracted.getCount();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.logisticsports.access_port");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AccessPortMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, recipe);
        tag.putInt("frequency", frequency);
        tag.putBoolean("requireAll", requireAll);
        tag.putBoolean("packageMode", packageMode);
        tag.putString("recipient", recipient);
        if (!indicator.isEmpty()) {
            CompoundTag indicatorTag = new CompoundTag();
            indicator.save(indicatorTag);
            tag.put("indicator", indicatorTag);
        }
    }

    // Замени load чтобы читал кэш
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, recipe);
        frequency = tag.getInt("frequency");
        requireAll = tag.getBoolean("requireAll");
        packageMode = tag.getBoolean("packageMode");
        recipient = tag.getString("recipient");
        if (tag.contains("indicator")) {
            indicator = ItemStack.of(tag.getCompound("indicator"));
        }
        if (tag.contains("availableCache")) {
            availableCache.clear();
            CompoundTag cacheTag = tag.getCompound("availableCache");
            for (String key : cacheTag.getAllKeys()) {
                availableCache.put(key, cacheTag.getInt(key));
            }
        }
    }

    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (level == null || level.isClientSide) return stack;
            if (stack.isEmpty()) return stack;

            // Проверка подключений
            BlockEntity topBE = level.getBlockEntity(worldPosition.above());
            BlockEntity bottomBE = level.getBlockEntity(worldPosition.below());

            if (topBE == null || bottomBE == null) return stack;
            // Дополнительная проверка, что это не игрок. В Forge игроки не возвращают IItemHandler через getCapability блока.
            // Но мы должны убедиться, что сверху и снизу именно BlockEntity с инвентарями (или просто BlockEntity).
            // Требование гласит "не руками", что обычно означает автоматизацию.

            // Проверка фильтрации
            boolean allowed = false;
            if (packageMode) {
                // Если режим упаковки - принимаем только коробку с тем же адресом
                if (stack.getItem() instanceof com.simibubi.create.content.logistics.box.PackageItem) {
                    String effectiveRecipient = recipient.isBlank() ? getAccessPortId() : recipient;
                    CompoundTag tag = stack.getTag();
                    if (tag != null && tag.contains("Address") && tag.getString("Address").equals(effectiveRecipient)) {
                        allowed = true;
                    }
                }
            } else {
                // Если обычный режим - принимаем только вещи из рецепта
                for (ItemStack recipeStack : recipe) {
                    if (!recipeStack.isEmpty() && ItemStack.isSameItemSameTags(recipeStack, stack)) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) return stack;

            // Пробуем передать вниз
            var bottomCap = bottomBE.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
            if (bottomCap.isPresent()) {
                IItemHandler handler = bottomCap.orElse(null);
                if (handler != null) {
                    if (packageMode && stack.getItem() instanceof com.simibubi.create.content.logistics.box.PackageItem) {
                        if (simulate) return ItemStack.EMPTY;

                        net.minecraftforge.items.ItemStackHandler contents = com.simibubi.create.content.logistics.box.PackageItem.getContents(stack);
                        for (int i = 0; i < contents.getSlots(); i++) {
                            ItemStack content = contents.getStackInSlot(i);
                            if (!content.isEmpty()) {
                                net.minecraftforge.items.ItemHandlerHelper.insertItem(handler, content, false);
                            }
                        }
                        return ItemStack.EMPTY;
                    }
                    return net.minecraftforge.items.ItemHandlerHelper.insertItem(handler, stack, simulate);
                }
            }

            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    });

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side == Direction.UP) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    public boolean stillValid(Player player) {
        if (level == null) return false;
        if (level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5
        ) <= 64.0;
    }

    // Замени getUpdateTag чтобы включал кэш доступности
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        // Добавляем кэш доступности для клиента
        CompoundTag cacheTag = new CompoundTag();
        for (var entry : availableCache.entrySet()) {
            cacheTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("availableCache", cacheTag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Кэш доступных предметов (обновляется при открытии GUI)
    private Map<String, Integer> availableCache = new HashMap<>();

    public void refreshAvailableCache() {
        if (level == null || level.isClientSide) return;
        availableCache.clear();
        List<OutputPortBlockEntity> ports = findOutputPorts();

        for (OutputPortBlockEntity port : ports) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = port.getBlockPos().relative(dir);
                BlockEntity be = level.getBlockEntity(neighbor);
                if (be == null) continue;

                // Пропускаем сам порт выдачи
                if (be instanceof OutputPortBlockEntity) continue;

                var cap = be.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                        dir.getOpposite());

                cap.ifPresent(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack s = handler.getStackInSlot(i);
                        if (s.isEmpty()) continue;
                        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(s.getItem()).toString();
                        availableCache.merge(key, s.getCount(), Integer::sum);
                    }
                });
            }
        }

        // Синхронизируем с клиентом
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getAvailableCount(ItemStack stack) {
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString();
        return availableCache.getOrDefault(key, 0);
    }
}