package com.radiomod.signal;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded webservers, indexed by dimension for tablet discovery. */
public final class WebserverNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private WebserverNetwork() { }
    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }
    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> entries = BY_LEVEL.get(level);
        if (entries != null) {
            entries.remove(pos);
            if (entries.isEmpty()) BY_LEVEL.remove(level);
        }
    }
    public static synchronized Set<BlockPos> get(Level level) {
        Set<BlockPos> entries = BY_LEVEL.get(level);
        return entries == null ? Set.of() : Set.copyOf(entries);
    }
}
