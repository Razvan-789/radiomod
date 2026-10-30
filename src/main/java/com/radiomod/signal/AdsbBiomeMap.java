package com.radiomod.signal;

import com.radiomod.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Surface-biome colors sampled from world generation without loading map chunks. */
public final class AdsbBiomeMap {
    public static final int SIZE = 40;
    private static final Map<ServerLevel, Map<Key, Entry>> CACHE = new WeakHashMap<>();
    private record Key(int x, int z, int rangeKm) { }
    private record Entry(long createdAt, int[] pixels) { }
    private AdsbBiomeMap() { }

    public static int[] sample(ServerLevel level, BlockPos center, int rangeKm) {
        Map<Key, Entry> byViewport = CACHE.computeIfAbsent(level, ignored -> new HashMap<>());
        Key key = new Key(center.getX(), center.getZ(), rangeKm);
        Entry cached = byViewport.get(key);
        if (cached != null && level.getGameTime() - cached.createdAt() < 600) return cached.pixels();
        int[] pixels = new int[SIZE * SIZE];
        double blocksPerKm = 1.0 / Config.getKilometersPerBlock();
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            double east = ((x + .5) / SIZE * 2 - 1) * rangeKm;
            double south = ((z + .5) / SIZE * 2 - 1) * rangeKm;
            int worldX = (int) Math.floor(center.getX() + east * blocksPerKm);
            int worldZ = (int) Math.floor(center.getZ() + south * blocksPerKm);
            Holder<Biome> biome = surfaceBiome(level, worldX, worldZ);
            if (rangeKm > 20 && biome.is(BiomeTags.IS_RIVER))
                biome = nearestNonRiver(level, worldX, worldZ, biome);
            int color = biome.is(BiomeTags.IS_OCEAN) ? 0x285A81
                    : biome.is(BiomeTags.IS_RIVER) ? 0x397392
                    : biome.is(BiomeTags.IS_BEACH) ? 0xC5B78A
                    : biome.value().getGrassColor(worldX, worldZ);
            pixels[z * SIZE + x] = 0xFF000000 | color;
        }
        if (byViewport.size() >= 32) byViewport.clear();
        byViewport.put(key, new Entry(level.getGameTime(), pixels));
        return pixels;
    }

    /** Terrain mods can place surface biomes at a different Y than the old fixed quart sample. */
    private static Holder<Biome> surfaceBiome(ServerLevel level, int x, int z) {
        if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            return level.getBiome(new BlockPos(x, y, z));
        }
        var source = level.getChunkSource();
        int y = source.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                level, source.randomState());
        return source.getGenerator().getBiomeSource().getNoiseBiome(x >> 2, (y - 1) >> 2,
                z >> 2, source.randomState().sampler());
    }

    /** At wide zooms a river is narrower than a map pixel; color it as the nearest surrounding biome. */
    private static Holder<Biome> nearestNonRiver(ServerLevel level, int worldX, int worldZ, Holder<Biome> river) {
        Holder<Biome> nearest = null;
        int bestDistanceSquared = Integer.MAX_VALUE;
        for (int radius = 1; radius <= 32 && radius * radius <= bestDistanceSquared; radius++) {
            for (int side = 0; side < 4; side++) for (int offset = -radius; offset <= radius; offset++) {
                if (side >= 2 && Math.abs(offset) == radius) continue;
                int dx = side < 2 ? offset : side == 2 ? -radius : radius;
                int dz = side < 2 ? side == 0 ? -radius : radius : offset;
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared >= bestDistanceSquared) continue;
                Holder<Biome> candidate = surfaceBiome(level, worldX + dx * 4, worldZ + dz * 4);
                if (!candidate.is(BiomeTags.IS_RIVER)) {
                    nearest = candidate;
                    bestDistanceSquared = distanceSquared;
                }
            }
        }
        return nearest == null ? river : nearest;
    }
}
