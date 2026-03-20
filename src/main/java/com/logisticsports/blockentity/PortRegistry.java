package com.logisticsports.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortRegistry {
    private static final Map<Level, Set<BlockPos>> outputPorts = new ConcurrentHashMap<>();

    public static void addPort(Level level, BlockPos pos) {
        outputPorts.computeIfAbsent(level, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pos);
    }

    public static void removePort(Level level, BlockPos pos) {
        Set<BlockPos> ports = outputPorts.get(level);
        if (ports != null) {
            ports.remove(pos);
            if (ports.isEmpty()) {
                outputPorts.remove(level);
            }
        }
    }

    public static List<OutputPortBlockEntity> findPorts(Level level, int frequency) {
        List<OutputPortBlockEntity> result = new ArrayList<>();
        Set<BlockPos> positions = outputPorts.get(level);
        if (positions != null) {
            Iterator<BlockPos> iterator = positions.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (level.isLoaded(pos)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof OutputPortBlockEntity port) {
                        if (port.frequency == frequency) {
                            result.add(port);
                        }
                    } else {
                        // Блок больше не является портом, удаляем из реестра
                        iterator.remove();
                    }
                }
            }
        }
        return result;
    }
}
