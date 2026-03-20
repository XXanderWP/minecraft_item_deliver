package com.logisticsports.blockentity;

import com.logisticsports.block.AccessPortBlock;
import com.logisticsports.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import com.logisticsports.config.ModConfig;
import com.logisticsports.menu.AccessPortMenu;
import com.logisticsports.blockentity.OutputPortBlockEntity;
import com.logisticsports.world.OutputPortSavedData;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

public class AccessPortBlockEntity extends BlockEntity implements MenuProvider {
    public static int getRecipeSlots() {
        return ModConfig.SERVER != null ? ModConfig.SERVER.recipeSlots.get() : 18;
    }
    public static int getFluidRecipeSlots() {
        return ModConfig.SERVER != null ? ModConfig.SERVER.fluidRecipeSlots.get() : 9;
    }
    public boolean packageMode = false;
    public String recipient = "";
    // Рецепт
    public final NonNullList<ItemStack> recipe = NonNullList.withSize(18, ItemStack.EMPTY);
    // Рецепт жидкости
    public final NonNullList<FluidStack> fluidsRecipe = NonNullList.withSize(9, FluidStack.EMPTY);
    // Индикаторный предмет
    public ItemStack indicator = ItemStack.EMPTY;
    // Частота сети
    public int frequency = 0;
    // Номер интегральной схемы GregTech (0-24)
    public int gtcCircuit = 0;
    // Режим мультипорта
    public boolean isMultiport = false;
    // Поведение при нехватке: true = только если всё есть, false = выдавать что есть
    public boolean requireAll = true;
    public long lastRefreshTime = 0;

    public String getAccessPortId() {
        return worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ();
    }

    public AccessPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ACCESS_PORT_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AccessPortBlockEntity be) {
        if (level != null && !level.isClientSide && ModConfig.SERVER.enableAutoSync.get()) {
            int intervalTicks = Math.max(1, ModConfig.SERVER.autoSyncInterval.get() * 20);
            if (level.getGameTime() % intervalTicks == 0) {
                int dist = ModConfig.SERVER.autoSyncDistance.get();
                if (level.hasNearbyAlivePlayer(pos.getX(), pos.getY(), pos.getZ(), dist)) {
                    be.refreshAvailableCache();
                }
            }
        }
    }

    // Состояние редстоуна (для детекции фронта)
    private boolean lastRedstoneState = false;

    public void placeOrder(@Nullable Player player, int batches) {
        placeOrder(player, batches, -1);
    }

    public void placeOrder(@Nullable Player player, int batches, int slotIndex) {
        if (level == null || level.isClientSide) return;

        // Собираем список нужных предметов и жидкостей с учётом партий
        List<ItemStack> needed;
        List<FluidStack> neededFluids = new ArrayList<>();

        int activeRecipeSlots = getRecipeSlots();
        if (isMultiport && slotIndex >= 0 && slotIndex < activeRecipeSlots) {
            needed = new ArrayList<>();
            ItemStack stack = recipe.get(slotIndex);
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(copy.getCount() * batches);
                needed.add(copy);
            }
        } else {
            needed = buildOrderList(batches);
            int activeFluidSlots = getFluidRecipeSlots();
            for (int i = 0; i < activeFluidSlots; i++) {
                FluidStack stack = fluidsRecipe.get(i);
                if (!stack.isEmpty()) {
                    FluidStack copy = stack.copy();
                    copy.setAmount(copy.getAmount() * batches);
                    neededFluids.add(copy);
                }
            }
        }

        if (needed.isEmpty() && neededFluids.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.translatable("config.logisticsports.action.warning_chat", Component.translatable("config.logisticsports.recipe_is_empty")));
            setStatus(2); // провал
            return;
        }

        // Находим все Порты Выдачи на той же частоте
        List<OutputPortBlockEntity> ports = findOutputPorts();
        if (ports.isEmpty()) {
            if (player != null) player.sendSystemMessage(
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
        Map<FluidStack, Integer> availableFluids = scanAvailableFluids(ports);
        List<Component> missing = new ArrayList<>();

        for (ItemStack need : needed) {
            int have = getAvailableCount(available, need);
            if (have < need.getCount()) {
                if (requireAll) {
                    missing.add(Component.literal("§c  - ").append(need.getHoverName())
                            .append(Component.literal(" (" + have + "/" + need.getCount() + ")")));
                }
            }
        }

        for (FluidStack neededFluid : neededFluids) {
            int have = getAvailableFluidCount(availableFluids, neededFluid);
            if (have < neededFluid.getAmount()) {
                if (requireAll) {
                    missing.add(Component.literal("§c  - ").append(neededFluid.getDisplayName())
                            .append(Component.literal(" (" + have + "/" + neededFluid.getAmount() + "mB)")));
                }
            }
        }

        if (requireAll && !missing.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("config.logisticsports.action.error_chat", Component.translatable("config.logisticsports.action.missing")));
                for (Component m : missing) {
                    player.sendSystemMessage(m);
                }
            }
            setStatus(2); // провал
            return;
        }

        // Генерируем интегральную схему GTCEu, если нужно
        ItemStack circuitStack = ItemStack.EMPTY;
        if (gtcCircuit > 0) {
            circuitStack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gtceu", "programmed_circuit")));
            if (!circuitStack.isEmpty()) {
                CompoundTag circuitTag = new CompoundTag();
                circuitTag.putInt("Configuration", gtcCircuit);
                circuitStack.setTag(circuitTag);
            }
        }

        // Проверяем место в портах выдачи
        if (!hasEnoughSpace(ports, needed, neededFluids, circuitStack)) {
            if (player != null) player.sendSystemMessage(Component.translatable("config.logisticsports.action.error_chat", Component.translatable("config.logisticsports.action.no_space")));
            setStatus(2); // провал
            return;
        }

        // Перемещаем предметы и жидкости
        executeOrder(ports, needed, neededFluids, available, availableFluids, effectiveRecipient, circuitStack);

        if (player != null) player.sendSystemMessage(Component.translatable("config.logisticsports.action.success_chat", Component.translatable("config.logisticsports.action.order_complete")));
        setStatus(1); // успех
    }

    public void onNeighborUpdate(boolean hasSignal) {
        if (isMultiport) return; // Редстоун не работает для мультипорт блока
        if (hasSignal && !lastRedstoneState) {
            placeOrder(null, 1);
            setChanged();
        }
        if (hasSignal != lastRedstoneState) {
            lastRedstoneState = hasSignal;
            setChanged();
        }
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
        List<ItemStack> grouped = new ArrayList<>();
        int activeRecipeSlots = getRecipeSlots();
        for (int i = 0; i < activeRecipeSlots; i++) {
            ItemStack stack = recipe.get(i);
            if (stack.isEmpty()) continue;
            boolean found = false;
            for (ItemStack existing : grouped) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    existing.grow(stack.getCount() * batches);
                    found = true;
                    break;
                }
            }
            if (!found) {
                ItemStack copy = stack.copy();
                copy.setCount(stack.getCount() * batches);
                grouped.add(copy);
            }
        }
        return grouped;
    }

    private List<OutputPortBlockEntity> findOutputPorts() {
        List<OutputPortBlockEntity> result = new ArrayList<>();
        if (level == null || level.isClientSide) return result;

        OutputPortSavedData data = OutputPortSavedData.get(level);
        Set<BlockPos> positions = data.getPositions();
        List<BlockPos> toRemove = new ArrayList<>();

        BlockPos center = worldPosition;
        int radiusSq = ModConfig.SERVER.searchRadius.get();
        radiusSq *= radiusSq;

        for (BlockPos pos : positions) {
            // Проверка радиуса (сферический поиск эффективнее и логичнее для списка)
            if (pos.distSqr(center) <= radiusSq) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof OutputPortBlockEntity port) {
                    if (port.frequency == this.frequency) {
                        result.add(port);
                    }
                } else {
                    // Блока больше нет, помечаем на удаление
                    toRemove.add(pos);
                }
            }
        }

        // Очистка несуществующих портов
        for (BlockPos pos : toRemove) {
            data.removePort(pos);
        }

        return result;
    }

    private Map<ItemStack, Integer> scanAvailable(List<OutputPortBlockEntity> ports) {
        Map<ItemTagKey, Integer> counts = new LinkedHashMap<>();
        Map<ItemTagKey, ItemStack> stackRefs = new LinkedHashMap<>();
        Set<IItemHandler> scannedHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (OutputPortBlockEntity port : ports) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = port.getBlockPos().relative(dir);
                if (level == null) continue;
                BlockEntity neighbor_be = level.getBlockEntity(neighbor);
                if (neighbor_be == null || neighbor_be instanceof OutputPortBlockEntity) continue;

                var cap = neighbor_be.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                        dir.getOpposite());
                cap.ifPresent(handler -> {
                    if (!scannedHandlers.add(handler)) return;

                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack s = handler.getStackInSlot(i);
                        if (s.isEmpty()) continue;
                        ItemTagKey key = new ItemTagKey(s);
                        counts.merge(key, s.getCount(), Integer::sum);
                        stackRefs.putIfAbsent(key, s);
                    }
                });
            }
        }

        Map<ItemStack, Integer> result = new LinkedHashMap<>();
        for (var entry : counts.entrySet()) {
            result.put(stackRefs.get(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static record ItemTagKey(net.minecraft.world.item.Item item, @Nullable CompoundTag tag) {
        public ItemTagKey(ItemStack stack) {
            this(stack.getItem(), stack.getTag() == null ? null : stack.getTag().copy());
        }
    }

    private int getAvailableCount(Map<ItemStack, Integer> available, ItemStack need) {
        for (var entry : available.entrySet()) {
            if (ItemStack.isSameItemSameTags(entry.getKey(), need)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private Map<FluidStack, Integer> scanAvailableFluids(List<OutputPortBlockEntity> ports) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, FluidStack> fluidRefs = new LinkedHashMap<>();
        Set<net.minecraftforge.fluids.capability.IFluidHandler> scannedHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (OutputPortBlockEntity port : ports) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = port.getBlockPos().relative(dir);
                if (level == null) continue;
                BlockEntity neighbor_be = level.getBlockEntity(neighbor);
                if (neighbor_be == null || neighbor_be instanceof OutputPortBlockEntity) continue;

                var cap = neighbor_be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite());
                cap.ifPresent(handler -> {
                    if (!scannedHandlers.add(handler)) return;

                    for (int i = 0; i < handler.getTanks(); i++) {
                        FluidStack s = handler.getFluidInTank(i);
                        if (s.isEmpty()) continue;
                        String key = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(s.getFluid()).toString();
                        counts.merge(key, s.getAmount(), Integer::sum);
                        fluidRefs.putIfAbsent(key, s);
                    }
                });
            }
        }

        Map<FluidStack, Integer> result = new LinkedHashMap<>();
        for (var entry : counts.entrySet()) {
            result.put(fluidRefs.get(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private int getAvailableFluidCount(Map<FluidStack, Integer> available, FluidStack need) {
        for (var entry : available.entrySet()) {
            if (entry.getKey().isFluidEqual(need)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private boolean hasEnoughSpace(List<OutputPortBlockEntity> ports, List<ItemStack> needed, List<FluidStack> neededFluids, ItemStack gtcCircuitStack) {
        if (packageMode) {
            // В режиме упаковки предметы могут распределиться по портам.
            // В худшем случае на каждый порт по пакету.
            int portsWithSpace = 0;
            for (OutputPortBlockEntity port : ports) {
                if (port.getFreeSpaceFor(new ItemStack(net.minecraft.world.item.Items.BARRIER)) > 0) {
                    portsWithSpace++;
                }
            }
            // Если хотя бы один порт имеет место, мы можем начать.
            // При новой логике executeOrder предметы будут максимально группироваться в первых портах.
            return portsWithSpace >= 1;
        }

        int totalFluidNeeded = 0;
        for (FluidStack stack : neededFluids) {
            if (!stack.isEmpty()) {
                totalFluidNeeded += (int) Math.ceil(stack.getAmount() / 1000.0);
            }
        }
        
        // Для упрощения: каждый резервуар занимает 1 слот
        List<ItemStack> allNeededItems = new ArrayList<>(needed);
        if (totalFluidNeeded > 0) {
            allNeededItems.add(new ItemStack(ModRegistry.TRANSPORT_RESERVOIR.get(), totalFluidNeeded));
        }
        if (!gtcCircuitStack.isEmpty()) {
            allNeededItems.add(gtcCircuitStack);
        }

        // Мы должны проверить, что суммарно во всех портах хватит места для всех предметов.
        // Но предметы могут распределяться между портами.
        // getFreeSpaceFor(need) возвращает общее кол-во предметов 'need', которое влезет в порт.
        
        for (ItemStack need : allNeededItems) {
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
                              List<FluidStack> neededFluids,
                              Map<ItemStack, Integer> available,
                              Map<FluidStack, Integer> availableFluids,
                              String effectiveRecipient,
                              ItemStack gtcCircuitStack) {
        
        Map<OutputPortBlockEntity, List<ItemStack>> portToReservoirs = new HashMap<>();
        for (FluidStack neededFluid : neededFluids) {
            int remainingFluid = neededFluid.getAmount();
            if (remainingFluid > 0) {
                for (OutputPortBlockEntity port : ports) {
                    if (remainingFluid <= 0) break;
                    Set<net.minecraftforge.fluids.capability.IFluidHandler> usedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
                    for (Direction dir : Direction.values()) {
                        if (remainingFluid <= 0) break;
                        BlockPos neighbor = port.getBlockPos().relative(dir);
                        BlockEntity be = level.getBlockEntity(neighbor);
                        if (be == null || be instanceof OutputPortBlockEntity) continue;
                        var cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite());
                        if (!cap.isPresent()) continue;
                        var handler = cap.orElse(null);
                        if (handler == null || !usedFluidHandlers.add(handler)) continue;
                        
                        while (remainingFluid > 0) {
                            FluidStack toDrain = neededFluid.copy();
                            toDrain.setAmount(Math.min(remainingFluid, 1000));
                            FluidStack drained = handler.drain(toDrain, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                            if (drained.isEmpty()) break;
                            
                            ItemStack reservoir = new ItemStack(ModRegistry.TRANSPORT_RESERVOIR.get());
                            com.logisticsports.item.TransportReservoirItem.setFluid(reservoir, drained);
                            portToReservoirs.computeIfAbsent(port, k -> new ArrayList<>()).add(reservoir);
                            port.startProcessingFluid(drained);
                            remainingFluid -= drained.getAmount();
                        }
                    }
                }
            }
        }

        // Создаем копию списка требуемых предметов для отслеживания глобального остатка
        List<ItemStack> globalRemainingItems = new ArrayList<>();
        for (ItemStack s : needed) {
            globalRemainingItems.add(s.copy());
        }

        boolean circuitDelivered = gtcCircuitStack.isEmpty();

        for (OutputPortBlockEntity port : ports) {
            Set<IItemHandler> portHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
            List<ItemStack> toDeliver = new ArrayList<>();
            // Сначала предметы
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = port.getBlockPos().relative(dir);
                BlockEntity neighborBe = level.getBlockEntity(neighbor);
                if (neighborBe == null || neighborBe instanceof OutputPortBlockEntity) continue;

                var cap = neighborBe.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite());
                cap.ifPresent(handler -> {
                    if (!portHandlers.add(handler)) return;

                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack s = handler.getStackInSlot(i);
                        if (s.isEmpty()) continue;

                        for (ItemStack remainingNeed : globalRemainingItems) {
                            if (remainingNeed.getCount() <= 0) continue;
                            if (ItemStack.isSameItemSameTags(s, remainingNeed)) {
                                int toExtract = Math.min(remainingNeed.getCount(), s.getCount());
                                ItemStack extracted = handler.extractItem(i, toExtract, false);
                                if (!extracted.isEmpty()) {
                                    toDeliver.add(extracted);
                                    remainingNeed.shrink(extracted.getCount());
                                    // Обновляем локальную ссылку на стак в слоте, так как мы могли извлечь только часть
                                    s = handler.getStackInSlot(i);
                                    if (s.isEmpty()) break; // Слот опустел, переходим к следующему слоту
                                }
                            }
                        }
                    }
                });
            }
            
            // Добавляем резервуары, которые относятся К ЭТОМУ ПОРТУ
            List<ItemStack> reservoirs = portToReservoirs.get(port);
            if (reservoirs != null) {
                toDeliver.addAll(reservoirs);
            }

            if (toDeliver.isEmpty()) continue;

            // Если есть что выдавать в этом порту, добавляем схему
            if (!circuitDelivered) {
                toDeliver.add(gtcCircuitStack.copy());
                circuitDelivered = true;
            }

            if (packageMode) {
                ItemStack packageStack = com.simibubi.create.content.logistics.box.PackageItem.containing(toDeliver);
                if (!effectiveRecipient.isBlank()) {
                    com.simibubi.create.content.logistics.box.PackageItem.addAddress(packageStack, effectiveRecipient);
                }
                port.startProcessingItem(packageStack, Direction.UP); 
            } else {
                for (ItemStack s : toDeliver) {
                    port.startProcessingItem(s, Direction.UP);
                }
            }
        }

        // Если схема еще не выдана (например, заказ состоит ТОЛЬКО из схемы), выдаем ее первому порту
        if (!circuitDelivered && !ports.isEmpty()) {
            OutputPortBlockEntity firstPort = ports.get(0);
            if (packageMode) {
                List<ItemStack> toDeliver = new ArrayList<>();
                toDeliver.add(gtcCircuitStack.copy());
                ItemStack packageStack = com.simibubi.create.content.logistics.box.PackageItem.containing(toDeliver);
                if (!effectiveRecipient.isBlank()) {
                    com.simibubi.create.content.logistics.box.PackageItem.addAddress(packageStack, effectiveRecipient);
                }
                firstPort.startProcessingItem(packageStack, Direction.UP);
            } else {
                firstPort.startProcessingItem(gtcCircuitStack.copy(), Direction.UP);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        if (isMultiport) {
            return Component.translatable("block.logisticsports.multiport_access");
        }
        return Component.translatable("block.logisticsports.access_port");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AccessPortMenu(containerId, inventory, this);
    }

    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, recipe);

        CompoundTag fluidsTag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < fluidsRecipe.size(); i++) {
            FluidStack stack = fluidsRecipe.get(i);
            if (!stack.isEmpty()) {
                CompoundTag fluidTag = new CompoundTag();
                fluidTag.putInt("Slot", i);
                stack.writeToNBT(fluidTag);
                list.add(fluidTag);
            }
        }
        fluidsTag.put("Fluids", list);
        tag.put("fluidsRecipe", fluidsTag);

        tag.putInt("frequency", frequency);
        tag.putInt("gtcCircuit", gtcCircuit);
        tag.putBoolean("isMultiport", isMultiport);
        tag.putBoolean("requireAll", requireAll);
        tag.putBoolean("packageMode", packageMode);
        tag.putString("recipient", recipient);
        tag.putBoolean("lastRedstoneState", lastRedstoneState);
        if (!indicator.isEmpty()) {
            CompoundTag indicatorTag = new CompoundTag();
            indicator.save(indicatorTag);
            tag.put("indicator", indicatorTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, recipe);

        // Миграция со старого формата fluidRecipe
        if (tag.contains("fluidRecipe")) {
            FluidStack oldFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("fluidRecipe"));
            if (!oldFluid.isEmpty()) {
                fluidsRecipe.set(0, oldFluid);
            }
        }

        if (tag.contains("fluidsRecipe")) {
            CompoundTag fluidsTag = tag.getCompound("fluidsRecipe");
            ListTag list = fluidsTag.getList("Fluids", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag fluidTag = list.getCompound(i);
                int slot = fluidTag.getInt("Slot");
                if (slot >= 0 && slot < fluidsRecipe.size()) {
                    fluidsRecipe.set(slot, FluidStack.loadFluidStackFromNBT(fluidTag));
                }
            }
        }

        frequency = tag.getInt("frequency");
        gtcCircuit = tag.getInt("gtcCircuit");
        isMultiport = tag.getBoolean("isMultiport");
        requireAll = tag.getBoolean("requireAll");
        packageMode = tag.getBoolean("packageMode");
        recipient = tag.getString("recipient");
        lastRedstoneState = tag.getBoolean("lastRedstoneState");
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
                int activeRecipeSlots = getRecipeSlots();
                for (int i = 0; i < activeRecipeSlots; i++) {
                    ItemStack recipeStack = recipe.get(i);
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
        Set<IItemHandler> scannedItemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<net.minecraftforge.fluids.capability.IFluidHandler> scannedFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (OutputPortBlockEntity port : ports) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = port.getBlockPos().relative(dir);
                BlockEntity be = level.getBlockEntity(neighbor);
                if (be == null || be instanceof OutputPortBlockEntity) continue;

                var itemCap = be.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                        dir.getOpposite());

                itemCap.ifPresent(handler -> {
                    if (!scannedItemHandlers.add(handler)) return;

                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack s = handler.getStackInSlot(i);
                        if (s.isEmpty()) continue;
                        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(s.getItem()).toString();
                        if (s.hasTag()) {
                            key += "@" + s.getTag().toString();
                        }
                        availableCache.merge(key, s.getCount(), Integer::sum);
                    }
                });
                
                var fluidCap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite());
                fluidCap.ifPresent(handler -> {
                    if (!scannedFluidHandlers.add(handler)) return;

                    for (int i = 0; i < handler.getTanks(); i++) {
                        FluidStack s = handler.getFluidInTank(i);
                        if (s.isEmpty()) continue;
                        String key = "fluid:" + net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(s.getFluid()).toString();
                        availableCache.merge(key, s.getAmount(), Integer::sum);
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
        if (stack.hasTag()) {
            key += "@" + stack.getTag().toString();
        }
        return availableCache.getOrDefault(key, 0);
    }

    public int getAvailableFluidCount(FluidStack stack) {
        String key = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(stack.getFluid()).toString();
        return availableCache.getOrDefault("fluid:" + key, 0);
    }
}