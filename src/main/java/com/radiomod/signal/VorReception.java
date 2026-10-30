package com.radiomod.signal;

import com.radiomod.Config;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** VOR bearing and optional DME slant range, using ADS-B-style height and obstruction. */
public final class VorReception {
    private VorReception() { }
    public record Fix(String identifier, int frequencyKhz, int radialDegrees, double horizontalKm,
                      double dmeBlocks, double snrDb, boolean dme, BlockPos transmitterPos) { }

    /** Transmitter and receiver altitude contribute equally to line-of-sight range. */
    public static double rangeKm(double navigatorY, double antennaY) {
        return AdsbReception.rangeKm(navigatorY, antennaY);
    }

    @Nullable public static Fix receive(ServerLevel level, Vec3 listener, int frequencyKhz) {
        Fix best = null;
        int listenerOverhead = SignalMath.countOverheadBlocks(level, listener);
        for (BlockPos pos : VorNetwork.get(level)) {
            if (!(level.getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor)
                    || vor.frequencyKhz() != frequencyKhz || !vor.hasAntennaConnected()) continue;
            BlockPos antenna = vor.antennaPos();
            Vec3 source = Vec3.atCenterOf(antenna);
            double dx = listener.x - source.x, dz = listener.z - source.z;
            double horizontalKm = Math.hypot(dx, dz) * Config.getKilometersPerBlock();
            double maximumRangeKm = rangeKm(listener.y, source.y);
            if (horizontalKm > maximumRangeKm) continue;
            int transmitterOverhead = SignalMath.countOverheadBlocks(level, source);
            double snrDb = 24 * (1 - Math.pow(horizontalKm / maximumRangeKm, 1.35))
                    - Math.min(18, transmitterOverhead * 2.5)
                    - Math.min(12, listenerOverhead * 1.5);
            if (snrDb < 0) continue;
            int radial = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
            boolean dme = vor.hasDmeAttached();
            double slantBlocks = listener.distanceTo(Vec3.atCenterOf(pos));
            Fix fix = new Fix(vor.identifier(), frequencyKhz, radial, horizontalKm,
                    dme ? slantBlocks : 0, snrDb, dme, pos);
            if (best == null || fix.snrDb() > best.snrDb()) best = fix;
        }
        return best;
    }
}
