package com.radiomod.signal;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded VOR transmitters, including their force-loaded transmitter chunks. */
public final class VorNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private VorNetwork() { }
    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, unused -> new HashSet<>()).add(pos.immutable());
    }
    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> positions = BY_LEVEL.get(level);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) BY_LEVEL.remove(level);
        }
    }
    public static synchronized Set<BlockPos> get(Level level) {
        Set<BlockPos> positions = BY_LEVEL.get(level);
        return positions == null ? Set.of() : Set.copyOf(positions);
    }
}
