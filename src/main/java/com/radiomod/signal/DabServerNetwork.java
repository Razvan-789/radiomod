package com.radiomod.signal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class DabServerNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> SERVERS_BY_LEVEL = new WeakHashMap<>();

    private DabServerNetwork() {
    }

    public static synchronized void register(Level level, BlockPos pos) {
        SERVERS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void unregister(Level level, BlockPos pos) {
        Set<BlockPos> positions = SERVERS_BY_LEVEL.get(level);
        if (positions == null) {
            return;
        }
        positions.remove(pos);
        if (positions.isEmpty()) {
            SERVERS_BY_LEVEL.remove(level);
        }
    }

    public static synchronized Set<BlockPos> getServers(Level level) {
        Set<BlockPos> positions = SERVERS_BY_LEVEL.get(level);
        if (positions == null || positions.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new HashSet<>(positions));
    }
}
