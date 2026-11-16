package com.radiomod.signal;

import com.radiomod.Radio;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Projects Sable plot coordinates into the parent world's coordinates when Sable is present. */
public final class MovingRadioPositions {
    private static final Bridge BRIDGE = loadBridge();
    private static final Plots PLOTS = loadPlots();

    private MovingRadioPositions() { }

    public static boolean available() { return BRIDGE != null && !BRIDGE.unavailable; }

    public static Vec3 world(Level level, BlockPos pos) {
        return world(level, Vec3.atCenterOf(pos));
    }

    public static Vec3 world(Level level, Vec3 pos) {
        return BRIDGE == null || level == null ? pos : BRIDGE.project(level, pos);
    }

    public static boolean isOnContraption(Level level, BlockPos pos) {
        return BRIDGE != null && level != null && BRIDGE.contains(level, pos);
    }

    public static boolean isPlotChunk(Level level, int chunkX, int chunkZ) {
        return BRIDGE != null && level != null && BRIDGE.contains(level, chunkX, chunkZ);
    }

    /** Loaded plot chunks only; never asks Minecraft or Sable to load additional chunks. */
    public static List<LevelChunk> loadedPlotChunks(ServerLevel level) {
        return PLOTS == null ? List.of() : PLOTS.loadedChunks(level);
    }

    /** Convert a world-space source to the antenna's local bearing for rotated contraptions. */
    public static Vec3 localBearingTarget(Level level, BlockPos antenna, Vec3 worldTarget) {
        if (!isOnContraption(level, antenna)) return worldTarget;
        Vec3 center = Vec3.atCenterOf(antenna);
        Vec3 worldCenter = world(level, center);
        Vec3 east = world(level, center.add(1, 0, 0)).subtract(worldCenter);
        Vec3 south = world(level, center.add(0, 0, 1)).subtract(worldCenter);
        Vec3 delta = worldTarget.subtract(worldCenter);
        return center.add(delta.dot(east), 0, delta.dot(south));
    }

    private static Bridge loadBridge() {
        try {
            Class.forName("dev.ryanhcode.sable.Sable");
            Class<?> api = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Field instance = api.getField("INSTANCE");
            return new Bridge(instance.get(null),
                    api.getMethod("getContaining", Level.class, int.class, int.class),
                    api.getMethod("projectOutOfSubLevel", Level.class, Position.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            Radio.LOGGER.warn("Sable radio position integration unavailable: {}", failure.toString());
            return null;
        }
    }

    private static Plots loadPlots() {
        try {
            Class<?> container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> plot = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
            Class<?> holder = Class.forName("dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder");
            return new Plots(container.getMethod("getContainer", Level.class),
                    container.getMethod("getAllSubLevels"), subLevel.getMethod("getPlot"),
                    plot.getMethod("getLoadedChunks"), holder.getMethod("getChunk"));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            Radio.LOGGER.warn("Sable plot discovery unavailable: {}", failure.toString());
            return null;
        }
    }

    private static final class Bridge {
        private final Object companion;
        private final Method containing, project;
        private boolean unavailable;

        private Bridge(Object companion, Method containing, Method project) {
            this.companion = companion;
            this.containing = containing;
            this.project = project;
        }

        private boolean contains(Level level, BlockPos pos) {
            return contains(level, pos.getX() >> 4, pos.getZ() >> 4);
        }

        private boolean contains(Level level, int chunkX, int chunkZ) {
            if (unavailable) return false;
            try {
                return containing.invoke(companion, level, chunkX, chunkZ) != null;
            } catch (ReflectiveOperationException | LinkageError failure) {
                unavailable = true;
                Radio.LOGGER.warn("Sable radio position integration stopped: {}", failure.toString());
                return false;
            }
        }

        private Vec3 project(Level level, Vec3 pos) {
            if (unavailable) return pos;
            try {
                return (Vec3) project.invoke(companion, level, pos);
            } catch (ReflectiveOperationException | LinkageError failure) {
                unavailable = true;
                Radio.LOGGER.warn("Sable radio position integration stopped: {}", failure.toString());
                return pos;
            }
        }
    }

    private static final class Plots {
        private final Method container, subLevels, plot, loadedChunks, chunk;
        private boolean unavailable;

        private Plots(Method container, Method subLevels, Method plot, Method loadedChunks, Method chunk) {
            this.container = container;
            this.subLevels = subLevels;
            this.plot = plot;
            this.loadedChunks = loadedChunks;
            this.chunk = chunk;
        }

        private List<LevelChunk> loadedChunks(ServerLevel level) {
            if (unavailable) return List.of();
            try {
                Object owner = container.invoke(null, level);
                if (owner == null) return List.of();
                List<LevelChunk> result = new ArrayList<>();
                for (Object subLevel : (Iterable<?>) subLevels.invoke(owner)) {
                    Object levelPlot = plot.invoke(subLevel);
                    for (Object holder : (Iterable<?>) loadedChunks.invoke(levelPlot)) {
                        Object loaded = chunk.invoke(holder);
                        if (loaded instanceof LevelChunk levelChunk) result.add(levelChunk);
                    }
                }
                return result;
            } catch (ReflectiveOperationException | LinkageError failure) {
                unavailable = true;
                Radio.LOGGER.warn("Sable plot discovery stopped: {}", failure.toString());
                return List.of();
            }
        }
    }
}
