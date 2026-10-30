package com.radiomod.signal;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded ADS-B servers indexed for the remote tablet directory. */
public final class AdsbServerNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private AdsbServerNetwork() { }
    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }
    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = BY_LEVEL.get(level);
        if (set != null) { set.remove(pos); if (set.isEmpty()) BY_LEVEL.remove(level); }
    }
    public static synchronized Set<BlockPos> get(Level level) {
        Set<BlockPos> set = BY_LEVEL.get(level);
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
