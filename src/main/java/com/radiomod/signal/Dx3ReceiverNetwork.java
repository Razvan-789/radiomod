package com.radiomod.signal;

import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Tracks loaded placed receivers for short-range speaker updates. */
public final class Dx3ReceiverNetwork {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private static final WeakHashMap<Level, MovingSnapshot> MOVING = new WeakHashMap<>();
    private static final WeakHashMap<Level, PlotSnapshot> PLOT_RECEIVERS = new WeakHashMap<>();
    private Dx3ReceiverNetwork() { }

    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
        MOVING.remove(level);
    }

    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> entries = BY_LEVEL.get(level);
        if (entries != null) entries.remove(pos);
        MOVING.remove(level);
    }

    public static synchronized Set<BlockPos> get(Level level) {
        Set<BlockPos> entries = BY_LEVEL.get(level);
        Set<BlockPos> plot = plotReceivers(level);
        if (entries == null || entries.isEmpty()) return plot;
        if (plot.isEmpty()) return Set.copyOf(entries);
        Set<BlockPos> combined = new HashSet<>(entries);
        combined.addAll(plot);
        return Set.copyOf(combined);
    }

    private static Set<BlockPos> plotReceivers(Level level) {
        if (!(level instanceof ServerLevel server)) return Set.of();
        long tick = level.getGameTime();
        PlotSnapshot cached = PLOT_RECEIVERS.get(level);
        if (cached != null && tick >= cached.tick() && tick - cached.tick() < 40)
            return cached.positions();
        Set<BlockPos> found = new HashSet<>();
        for (var chunk : MovingRadioPositions.loadedPlotChunks(server)) {
            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                var be = level.getBlockEntity(pos);
                if (be instanceof Dx3ReceiverBlockEntity || be instanceof DrmRadioBlockEntity)
                    found.add(pos.immutable());
            }
        }
        Set<BlockPos> result = Set.copyOf(found);
        PLOT_RECEIVERS.put(level, new PlotSnapshot(tick, result));
        return result;
    }

    /** Refresh moving membership at most once per second, regardless of player count. */
    public static synchronized Set<BlockPos> moving(Level level) {
        if (!MovingRadioPositions.available()) return Set.of();
        MovingSnapshot snapshot = MOVING.get(level);
        long tick = level.getGameTime();
        if (snapshot != null && tick >= snapshot.tick() && tick - snapshot.tick() < 20)
            return snapshot.positions();
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos pos : get(level))
            if (MovingRadioPositions.isOnContraption(level, pos)) positions.add(pos);
        Set<BlockPos> result = Set.copyOf(positions);
        MOVING.put(level, new MovingSnapshot(tick, result));
        return result;
    }

    private record MovingSnapshot(long tick, Set<BlockPos> positions) { }
    private record PlotSnapshot(long tick, Set<BlockPos> positions) { }
}
