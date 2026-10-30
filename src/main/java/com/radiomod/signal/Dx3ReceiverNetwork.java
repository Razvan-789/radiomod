package com.radiomod.signal;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Tracks loaded placed receivers for short-range speaker updates. */
public final class Dx3ReceiverNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private Dx3ReceiverNetwork() { }

    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> entries = BY_LEVEL.get(level);
        if (entries != null) entries.remove(pos);
    }

    public static synchronized Set<BlockPos> get(Level level) {
        Set<BlockPos> entries = BY_LEVEL.get(level);
        return entries == null ? Set.of() : Set.copyOf(entries);
    }
}
