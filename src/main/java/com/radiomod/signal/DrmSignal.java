package com.radiomod.signal;

import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** DRM groundwave and skywave approximation, in Minecraft blocks. */
public final class DrmSignal {
    public static final int MIN_KHZ = 140;
    public static final int MAX_KHZ = 30_000;
    private DrmSignal() { }

    public static int clampFrequency(int khz) { return Math.max(MIN_KHZ, Math.min(MAX_KHZ, khz)); }
    public static String band(int khz) { return khz < 300 ? "LW" : khz < 3000 ? "MW" : "SW"; }
    public static boolean isNight(Level level) {
        long time = Math.floorMod(level.dayTime(), 24_000L);
        return time >= 13_000L && time < 23_000L;
    }

    public static double signal(Level level, DrmTransmitterBlockEntity tx, BlockPos receiver,
                                int tunedKhz, boolean underwater) {
        int delta = Math.abs(tx.getFrequencyKhz() - tunedKhz);
        if (delta > Math.max(3, tx.getBandwidthKhz() / 2 + 2)) return -100;
        Vec3 source = Vec3.atCenterOf(tx.getAntennaPos() != null && tx.hasAntennaConnected()
                ? tx.getAntennaPos() : tx.getBlockPos());
        Vec3 target = Vec3.atCenterOf(receiver);
        double distance = Math.sqrt(Math.pow(source.x - target.x, 2) + Math.pow(source.z - target.z, 2)
                + Math.pow(Math.min(64, Math.abs(source.y - target.y)), 2));
        double power = 10.0 * Math.log10(Math.max(1, tx.getPowerW()) / 50.0);
        double antenna = tx.hasAntennaConnected() ? 8.0 : -14.0;
        double signal;
        switch (band(tx.getFrequencyKhz())) {
            case "LW" -> signal = 91 + power + antenna - distance * 0.045;
            case "MW" -> signal = 91 + power + antenna - distance * (isNight(level) ? 0.043 : 0.085);
            default -> {
                double ground = 91 + power + antenna - distance * 0.16;
                // SW groundwave gives way to a distant skywave region after a skip zone.
                double sky = 67 + power + antenna - Math.abs(distance - (isNight(level) ? 1650 : 1250)) * 0.033;
                double onset = Math.max(0, Math.min(1, (distance - 500) / 350.0));
                signal = Math.max(ground, sky * onset + (-30) * (1 - onset));
            }
        }
        signal += tx.antennaGainToward(target);
        int overhead = SignalMath.countOverheadBlocks(level, target);
        signal -= Math.min(24, overhead * (band(tx.getFrequencyKhz()).equals("SW") ? 1.4 :
                band(tx.getFrequencyKhz()).equals("MW") ? 0.65 : 0.35));
        if (receiver.getY() < 60) signal -= Math.min(45, (60 - receiver.getY())
                * (band(tx.getFrequencyKhz()).equals("SW") ? 0.75 :
                band(tx.getFrequencyKhz()).equals("MW") ? 0.25 : 0.1));
        if (underwater) signal -= band(tx.getFrequencyKhz()).equals("SW") ? 23 : 12;
        return signal - delta * 5.0;
    }

    public static double snr(Level level, DrmTransmitterBlockEntity tx, BlockPos receiver, int tunedKhz,
                             boolean underwater) {
        double strength = signal(level, tx, receiver, tunedKhz, underwater);
        double noise = SignalMath.calculateNoiseFloorDbuv(Vec3.atCenterOf(receiver));
        return Math.max(-20, Math.min(40, (strength - noise) * 0.48));
    }
}
