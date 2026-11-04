package com.radiomod.signal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SignalNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> TRANSMITTERS_BY_LEVEL = new WeakHashMap<>();
    private static final WeakHashMap<Level, Long> REVISIONS = new WeakHashMap<>();

    private SignalNetwork() {
    }

    public static synchronized void registerTransmitter(Level level, BlockPos pos) {
        if (TRANSMITTERS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable())) {
            REVISIONS.merge(level, 1L, Long::sum);
        }
    }

    public static synchronized void unregisterTransmitter(Level level, BlockPos pos) {
        Set<BlockPos> positions = TRANSMITTERS_BY_LEVEL.get(level);
        if (positions == null) {
            return;
        }
        if (positions.remove(pos)) {
            REVISIONS.merge(level, 1L, Long::sum);
        }
        if (positions.isEmpty()) {
            TRANSMITTERS_BY_LEVEL.remove(level);
        }
    }

    public static synchronized Set<BlockPos> getTransmitters(Level level) {
        Set<BlockPos> positions = TRANSMITTERS_BY_LEVEL.get(level);
        if (positions == null || positions.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new HashSet<>(positions));
    }

    public static synchronized long revision(Level level) {
        return REVISIONS.getOrDefault(level, 0L);
    }

    /** Broadcast metadata or RF configuration changed without a transmitter being placed/removed. */
    public static synchronized void markChanged(Level level) {
        if (level != null && !level.isClientSide()) REVISIONS.merge(level, 1L, Long::sum);
    }
}
