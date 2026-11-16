package com.radiomod.signal;

import com.radiomod.Config;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** DRM groundwave and skywave approximation using the world's distance scale. */
public final class DrmSignal {
    public static final int MIN_KHZ = 140;
    public static final int MAX_KHZ = 30_000;
    // At 0.05 mod km/block, 50 kW MW near 909 kHz reaches roughly 4,000 blocks
    // by day and 15,000–17,000 blocks by night in the clear with a TX antenna.
    private static final double LW_GROUND_LOSS_DB_PER_KM = 0.28;
    private static final double MW_DAY_LOSS_DB_PER_KM = 0.50;
    private static final double MW_NIGHT_LOSS_DB_PER_KM = 0.13;
    private static final double SW_GROUND_LOSS_DB_PER_KM = 1.90;
    private static final double SW_DAY_SKY_LOSS_DB_PER_KM = 0.10;
    private static final double SW_NIGHT_SKY_LOSS_DB_PER_KM = 0.085;
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
        Vec3 target = MovingRadioPositions.world(level, receiver);
        double distance = Math.sqrt(Math.pow(source.x - target.x, 2) + Math.pow(source.z - target.z, 2)
                + Math.pow(Math.min(64, Math.abs(source.y - target.y)), 2));
        double distanceKm = distance * Config.getKilometersPerBlock();
        double power = 10.0 * Math.log10(Math.max(1, tx.getPowerW()) / 50.0);
        double antenna = tx.hasAntennaConnected() ? 8.0 : -14.0;
        double signal;
        boolean night = isNight(level);
        switch (band(tx.getFrequencyKhz())) {
            case "LW" -> {
                // Lower LW frequencies travel farther; night changes the groundwave only modestly.
                double frequencyFactor = Math.sqrt(tx.getFrequencyKhz() / 220.0);
                signal = 91 + power + antenna - distanceKm * LW_GROUND_LOSS_DB_PER_KM
                        * frequencyFactor * (night ? 0.90 : 1.0);
            }
            case "MW" -> signal = 91 + power + antenna - distanceKm
                    * (night ? MW_NIGHT_LOSS_DB_PER_KM : MW_DAY_LOSS_DB_PER_KM);
            default -> {
                double ground = 91 + power + antenna - distanceKm * SW_GROUND_LOSS_DB_PER_KM;
                // Skywave reappears beyond a skip zone. Higher SW frequencies skip farther;
                // lower SW frequencies receive a larger nighttime benefit.
                double highBand = Math.clamp((tx.getFrequencyKhz() - 3_000) / 27_000.0, 0.0, 1.0);
                double skipStartKm = (night ? 180.0 : 250.0) + highBand * 120.0;
                double skyPeakKm = skipStartKm + 240.0;
                double frequencyBiasDb = (highBand - 0.35) * (night ? -7.0 : 7.0);
                double skyPeak = 63 + power + antenna + (night ? 4.0 : 0.0) + frequencyBiasDb;
                double skyLoss = night ? SW_NIGHT_SKY_LOSS_DB_PER_KM : SW_DAY_SKY_LOSS_DB_PER_KM;
                double skyOnset = Math.clamp((distanceKm - skipStartKm) / 80.0, 0.0, 1.0);
                double sky = skyPeak - Math.abs(distanceKm - skyPeakKm) * skyLoss
                        - (1.0 - skyOnset) * 80.0;
                signal = Math.max(ground, sky);
            }
        }
        signal += tx.antennaGainToward(target);
        int overhead = SignalMath.countOverheadBlocks(level, target);
        signal -= Math.min(24, overhead * (band(tx.getFrequencyKhz()).equals("SW") ? 1.4 :
                band(tx.getFrequencyKhz()).equals("MW") ? 0.65 : 0.35));
        if (target.y < 60) signal -= Math.min(45, (60 - target.y)
                * (band(tx.getFrequencyKhz()).equals("SW") ? 0.75 :
                band(tx.getFrequencyKhz()).equals("MW") ? 0.25 : 0.1));
        if (underwater) signal -= band(tx.getFrequencyKhz()).equals("SW") ? 23 : 12;
        return signal - delta * 5.0;
    }

    public static double snr(Level level, DrmTransmitterBlockEntity tx, BlockPos receiver, int tunedKhz,
                             boolean underwater) {
        double strength = signal(level, tx, receiver, tunedKhz, underwater);
        double noise = SignalMath.calculateNoiseFloorDbuv(MovingRadioPositions.world(level, receiver));
        return Math.max(-20, Math.min(40, (strength - noise) * 0.48));
    }
}
