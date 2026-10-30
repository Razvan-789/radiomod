package com.radiomod.signal;

import com.radiomod.Config;
import com.radiomod.blockentity.DirectionalAntennaBlockEntity;
import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.YagiAntennaBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SignalMath {
    public static final int FM_MIN_FREQUENCY_KHZ = 65_000;
    public static final int FM_MAX_FREQUENCY_KHZ = 108_000;
    private static final int MIN_FREQUENCY_KHZ = 50_000;
    private static final int MAX_FREQUENCY_KHZ = 300_000;
    private static final int MIN_POWER_W = 1;
    private static final int MAX_POWER_W = 250_000;
    private static final int MAX_OVERHEAD_BLOCK_CHECKS = 24;
    private static final double OVERHEAD_BLOCK_ATTENUATION_DB = 1.25;
    private static final double MAX_OVERHEAD_ATTENUATION_DB = 30.0;
    private static final double CENTER_SIGNAL_DBUV = 98.0;
    private static final double FM_UNDERWATER_LOSS_DB = 12.0;
    private static final double DAB_UNDERWATER_LOSS_DB = 20.0;
    private static final double DAB_RADIUS_FACTOR = 0.78;

    private SignalMath() {
    }

    /** Gain follows the linked antenna's bearing toward the other end of the radio path. */
    public static double linkedAntennaGainDb(Level level, BlockPos antennaPos,
                                             BlockPos endpointPos, Vec3 otherEnd) {
        if (antennaPos == null || level == null) return 0.0;
        if (level.getBlockEntity(antennaPos) instanceof DirectionalAntennaBlockEntity antenna
                && endpointPos.equals(antenna.getLinkedTransmitterPos())) {
            return antenna.gainToward(otherEnd);
        }
        if (level.getBlockEntity(antennaPos) instanceof YagiAntennaBlockEntity antenna
                && endpointPos.equals(antenna.getLinkedTransmitterPos())
                && (level.getBlockEntity(endpointPos) instanceof Dx3ReceiverBlockEntity
                    || level.getBlockEntity(endpointPos) instanceof DrmRadioBlockEntity
                    || level.getBlockEntity(endpointPos) instanceof WebserverBlockEntity
                    || level.getBlockEntity(endpointPos) instanceof AdsbReceiverBlockEntity))
            return antenna.gainToward(otherEnd);
        return 0.0;
    }

    public static int clampFrequencyKhz(int frequencyKhz) {
        return Mth.clamp(frequencyKhz, MIN_FREQUENCY_KHZ, MAX_FREQUENCY_KHZ);
    }

    public static int clampFmFrequencyKhz(int frequencyKhz) {
        return Mth.clamp(frequencyKhz, FM_MIN_FREQUENCY_KHZ, FM_MAX_FREQUENCY_KHZ);
    }

    public static boolean isFmBandFrequency(int frequencyKhz) {
        return frequencyKhz >= FM_MIN_FREQUENCY_KHZ && frequencyKhz <= FM_MAX_FREQUENCY_KHZ;
    }

    public static int clampPowerW(int powerW) {
        return Mth.clamp(powerW, MIN_POWER_W, MAX_POWER_W);
    }

    public static double calculateSignalDbuv(
            int powerW, int transmitterFrequencyKhz, int radioFrequencyKhz, boolean hasAntenna,
            boolean directionalEnabled, int directionDegrees, Vec3 transmitterPosition,
            Vec3 radioPosition, int overheadBlockCount, boolean underwater
    ) {
        double signalDbuv = calculateSignalUpperBoundDbuv(
                powerW,
                transmitterFrequencyKhz,
                radioFrequencyKhz,
                hasAntenna,
                directionalEnabled,
                directionDegrees,
                transmitterPosition,
                radioPosition,
                overheadBlockCount
        );
        return applyWeakSignalCurve(signalDbuv - (underwater ? FM_UNDERWATER_LOSS_DB : 0.0));
    }

    public static double calculateDabSignalDbuv(
            int powerW, int transmitterFrequencyKhz, int radioFrequencyKhz, boolean hasAntenna,
            boolean directionalEnabled, int directionDegrees, Vec3 transmitterPosition,
            Vec3 radioPosition, int overheadBlockCount, boolean underwater
    ) {
        double upper = calculateSignalUpperBoundDbuv(powerW, transmitterFrequencyKhz, radioFrequencyKhz,
                hasAntenna, directionalEnabled, directionDegrees, transmitterPosition, radioPosition,
                overheadBlockCount);
        double distance = calculatePropagationDistance(transmitterPosition, radioPosition);
        double fmRadius = calculateRadiusBlocks(powerW, hasAntenna, transmitterPosition.y, radioPosition.y);
        double distancePenalty = calculateDistanceSignalDbuv(distance, fmRadius)
                - calculateDistanceSignalDbuv(distance, fmRadius * DAB_RADIUS_FACTOR);
        double extraOverheadLoss = Math.min(MAX_OVERHEAD_ATTENUATION_DB,
                Math.max(0, overheadBlockCount) * OVERHEAD_BLOCK_ATTENUATION_DB);
        return applyWeakSignalCurve(upper - distancePenalty - extraOverheadLoss
                - (underwater ? DAB_UNDERWATER_LOSS_DB : 0.0));
    }

    public static double calculateSignalUpperBoundDbuv(
            int powerW,
            int transmitterFrequencyKhz,
            int radioFrequencyKhz,
            boolean hasAntenna,
            boolean directionalEnabled,
            int directionDegrees,
            Vec3 transmitterPosition,
            Vec3 radioPosition,
            int overheadBlockCount
    ) {
        double distanceBlocks = calculatePropagationDistance(transmitterPosition, radioPosition);
        double detunePenaltyDb = calculateDetunePenaltyDb(Math.abs(transmitterFrequencyKhz - radioFrequencyKhz));
        double radiusBlocks = calculateRadiusBlocks(powerW, hasAntenna, transmitterPosition.y, radioPosition.y);
        double distanceSignalDbuv = calculateDistanceSignalDbuv(distanceBlocks, radiusBlocks);
        distanceSignalDbuv += calculateWeakSignalInstabilityDb(distanceSignalDbuv, radioPosition);

        double directionalityPenaltyDb = calculateDirectionalityPenaltyDb(
                directionalEnabled,
                directionDegrees,
                transmitterPosition,
                radioPosition
        );
        double overheadPenaltyDb = Math.min(MAX_OVERHEAD_ATTENUATION_DB, Math.max(0, overheadBlockCount) * OVERHEAD_BLOCK_ATTENUATION_DB);
        return distanceSignalDbuv - detunePenaltyDb - directionalityPenaltyDb - overheadPenaltyDb;
    }

    public static int countOverheadBlocks(Level level, Vec3 receiverPosition) {
        BlockPos base = BlockPos.containing(receiverPosition.x, receiverPosition.y + 2.0, receiverPosition.z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(base.getX(), base.getY(), base.getZ());
        int count = 0;
        for (int i = 0; i < MAX_OVERHEAD_BLOCK_CHECKS; i++) {
            if (!level.isLoaded(cursor)) {
                break;
            }
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && state.canOcclude()) {
                count++;
            }
            cursor.move(0, 1, 0);
        }
        return count;
    }

    private static double calculateRadiusBlocks(int powerW, boolean hasAntenna, double transmitterY, double receiverY) {
        double baseRadius = 96.0 + (Math.sqrt(Math.max(1, powerW)) * 7.5);
        if (!hasAntenna) {
            baseRadius *= 0.42;
        }
        double transmitterHeightFactor = 1.0
                + Mth.clamp((float) ((transmitterY - 64.0) / 160.0), -0.22f, 0.60f)
                + Mth.clamp((float) ((transmitterY - 160.0) / 160.0), 0.0f, 0.30f);
        double receiverHeightFactor = 1.0
                + Mth.clamp((float) ((receiverY - 64.0) / 280.0), -0.15f, 0.35f)
                + Mth.clamp((float) ((receiverY - 160.0) / 160.0), 0.0f, 0.25f);
        return baseRadius * transmitterHeightFactor * receiverHeightFactor;
    }

    private static double calculatePropagationDistance(Vec3 transmitter, Vec3 receiver) {
        double dx = transmitter.x - receiver.x;
        double dz = transmitter.z - receiver.z;
        // A tower remains useful above the listener instead of its full height counting as ground range.
        double dy = Math.min(64.0, Math.abs(transmitter.y - receiver.y));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double calculateDistanceSignalDbuv(double distanceBlocks, double radiusBlocks) {
        double d = distanceBlocks / Math.max(1.0, radiusBlocks);
        if (d <= 0.25) {
            return lerpBand(d, 0.0, 0.25, CENTER_SIGNAL_DBUV, 90.0);
        }
        if (d <= 0.45) {
            return lerpBand(d, 0.25, 0.45, 90.0, 70.0);
        }
        if (d <= 0.65) {
            return lerpBand(d, 0.45, 0.65, 70.0, 50.0);
        }
        if (d <= 1.05) {
            return lerpBand(d, 0.65, 1.05, 50.0, 30.0);
        }
        if (d <= 1.60) {
            return lerpBand(d, 1.05, 1.60, 30.0, 15.0);
        }
        return 15.0 - ((d - 1.60) * 8.0);
    }

    private static double lerpBand(double value, double start, double end, double from, double to) {
        double t = Mth.clamp((value - start) / (end - start), 0.0, 1.0);
        return Mth.lerp((float) t, (float) from, (float) to);
    }

    private static double calculateWeakSignalInstabilityDb(double distanceSignalDbuv, Vec3 receiverPosition) {
        if (distanceSignalDbuv > 15.0) {
            return 0.0;
        }
        double strength = Mth.clamp((15.0 - distanceSignalDbuv) / 20.0, 0.0, 1.0);
        double wobble = (Math.sin(receiverPosition.x * 0.41) * 0.7)
                + (Math.sin(receiverPosition.z * 0.37) * 0.65)
                + (Math.sin((receiverPosition.x + receiverPosition.z) * 0.19) * 0.45);
        return wobble * strength;
    }

    private static double calculateDirectionalityPenaltyDb(
            boolean directionalEnabled,
            int directionDegrees,
            Vec3 transmitterPosition,
            Vec3 receiverPosition
    ) {
        if (!directionalEnabled) {
            return 0.0;
        }
        double dx = receiverPosition.x - transmitterPosition.x;
        double dz = receiverPosition.z - transmitterPosition.z;
        if ((dx * dx) + (dz * dz) < 0.0001) {
            return 0.0;
        }

        double targetDegrees = normalizeDegrees(Math.toDegrees(Math.atan2(dz, dx)));
        double headingDegrees = normalizeDegrees(directionDegrees);
        double diff = angleDifferenceDegrees(targetDegrees, headingDegrees);
        if (diff <= 35.0) {
            return 0.0;
        }
        if (diff <= 90.0) {
            return Mth.lerp((float) ((diff - 35.0) / 55.0), 0.0f, 12.0f);
        }
        return Mth.lerp((float) ((diff - 90.0) / 90.0), 12.0f, 26.0f);
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private static double angleDifferenceDegrees(double a, double b) {
        double diff = Math.abs(a - b) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private static double calculateDetunePenaltyDb(int deltaKhz) {
        if (deltaKhz <= 100) {
            double normalized = deltaKhz / 100.0;
            return normalized * normalized * 45.0;
        }

        double overEdge = (deltaKhz - 100) / 20.0;
        return Math.min(120.0, 45.0 + (overEdge * overEdge * 18.0));
    }

    public static double calculateNoiseFloorDbuv(Vec3 receiverPosition) {
        double minDbuv = Config.getNoiseFloorMinDbuv();
        double maxDbuv = Config.getNoiseFloorMaxDbuv();
        if (minDbuv == maxDbuv) {
            return minDbuv;
        }

        double x = receiverPosition.x;
        double z = receiverPosition.z;
        double largeScale = Math.sin((x * 0.085) + (z * 0.053));
        double mediumScale = Math.sin((x * 0.33) - (z * 0.21));
        double smallScale = Math.sin((x * 0.91) + (z * 0.77));
        double combined = (largeScale * 0.5) + (mediumScale * 0.35) + (smallScale * 0.15);
        double normalized = Mth.clamp((combined + 1.0) * 0.5, 0.0, 1.0);
        return minDbuv + ((maxDbuv - minDbuv) * normalized);
    }

    public static double calculateSnrDb(double signalDbuv, double noiseFloorDbuv) {
        // Calibrate FM display to roughly 53 dB at 98 dBuV with the default receiver noise floor.
        double raw = signalDbuv - noiseFloorDbuv;
        return raw <= 0.0 ? raw : Math.min(53.0, raw * 0.53);
    }

    public static double calculateDabSnrDb(double signalDbuv, double noiseFloorDbuv) {
        // A digital receiver's reported SNR approaches a ceiling as the RF signal grows.
        double raw = signalDbuv - noiseFloorDbuv;
        return raw <= 0.0 ? raw : 35.0 * (1.0 - Math.exp(-raw / 25.0));
    }

    private static double applyWeakSignalCurve(double signalDbuv) {
        if (signalDbuv >= 30.0) {
            return signalDbuv;
        }

        double transition = Mth.clamp((30.0 - signalDbuv) / 35.0, 0.0, 1.0);
        double extraAttenuationDb = (8.0 * transition) + (14.0 * transition * transition);
        return signalDbuv - extraAttenuationDb;
    }
}
