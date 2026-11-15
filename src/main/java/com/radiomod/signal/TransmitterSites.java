package com.radiomod.signal;

import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The same connected 50-block site grouping used by Squaremap. */
public final class TransmitterSites {
    private static final int MAX_NAME = 64;
    private static final int LINK_DISTANCE_SQUARED = 50 * 50;
    private TransmitterSites() { }

    public static String name(Level level, BlockPos origin) {
        if (level == null || origin == null) return "";
        for (BlockPos pos : members(level, origin)) {
            String saved = storedName(level.getBlockEntity(pos));
            if (!saved.isBlank()) return saved;
        }
        return "";
    }

    public static String displayName(Level level, BlockPos origin) {
        String name = name(level, origin);
        return name.isBlank() ? "Unnamed transmitter site" : name;
    }

    public static void setName(Level level, BlockPos origin, String requested) {
        if (level == null || level.isClientSide()) return;
        String name = clean(requested);
        for (BlockPos pos : members(level, origin)) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof TransmitterBlockEntity fm) fm.setSiteName(name);
            else if (entity instanceof DabTransmitterBlockEntity dab) dab.setSiteName(name);
            else if (entity instanceof DrmTransmitterBlockEntity drm) drm.setSiteName(name);
        }
    }

    /** A transmitter added later inherits the loaded site's existing name. */
    public static void reconcile(Level level, BlockPos origin) {
        if (level == null || level.isClientSide()) return;
        String inherited = name(level, origin);
        if (!inherited.isBlank()) setName(level, origin, inherited);
    }

    public static String clean(String requested) {
        if (requested == null) return "";
        String cleaned = requested.replaceAll("[\\p{Cntrl}]", " ").strip();
        return cleaned.substring(0, Math.min(MAX_NAME, cleaned.length()));
    }

    private static List<BlockPos> members(Level level, BlockPos origin) {
        Set<BlockPos> remaining = new HashSet<>(SignalNetwork.getTransmitters(level));
        if (!remaining.remove(origin)) return List.of();
        List<BlockPos> found = new ArrayList<>();
        found.add(origin);
        for (int i = 0; i < found.size(); i++) {
            BlockPos current = found.get(i);
            for (var iterator = remaining.iterator(); iterator.hasNext();) {
                BlockPos candidate = iterator.next();
                if (current.distSqr(candidate) <= LINK_DISTANCE_SQUARED) {
                    found.add(candidate);
                    iterator.remove();
                }
            }
        }
        found.sort(Comparator.comparingLong(BlockPos::asLong));
        return found;
    }

    private static String storedName(BlockEntity entity) {
        if (entity instanceof TransmitterBlockEntity fm) return fm.getSiteName();
        if (entity instanceof DabTransmitterBlockEntity dab) return dab.getSiteName();
        if (entity instanceof DrmTransmitterBlockEntity drm) return drm.getSiteName();
        return "";
    }
}
