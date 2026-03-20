package com.logisticsports.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashSet;
import java.util.Set;

public class OutputPortSavedData extends SavedData {
    private static final String DATA_NAME = "logisticsports_output_ports";
    private final Set<BlockPos> positions = new HashSet<>();

    public OutputPortSavedData() {
    }

    public static OutputPortSavedData get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new RuntimeException("Attempted to access OutputPortSavedData on client side");
        }

        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(OutputPortSavedData::load, OutputPortSavedData::new, DATA_NAME);
    }

    public static OutputPortSavedData load(CompoundTag tag) {
        OutputPortSavedData data = new OutputPortSavedData();
        ListTag list = tag.getList("positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.positions.add(NbtUtils.readBlockPos(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("positions", list);
        return tag;
    }

    public void addPort(BlockPos pos) {
        if (positions.add(pos)) {
            setDirty();
        }
    }

    public void removePort(BlockPos pos) {
        if (positions.remove(pos)) {
            setDirty();
        }
    }

    public Set<BlockPos> getPositions() {
        return new HashSet<>(positions);
    }
}
