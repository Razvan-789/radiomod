package com.radiomod.signal;

import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TechnisatReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

public final class DrmReception {
    private DrmReception() { }
    public record Sample(DrmTransmitterBlockEntity transmitter, double signalDbuv, double snrDb) {
        public boolean decoded() { return transmitter != null; }
    }
    public static Sample sample(DrmRadioBlockEntity radio) {
        Level level = radio.getLevel();
        if (level == null || !radio.hasReceiveAntennaConnected()) return new Sample(null, -100, -20);
        return sampleAt(level, radio.getAntennaPos(), radio.getFrequencyKhz(), 0.0,
                radio.getAntennaPos(), radio.getBlockPos());
    }
    public static Sample samplePortable(Player player, int frequencyKhz) {
        return sampleAt(player.level(), player.blockPosition(), frequencyKhz, 8.0, null, null);
    }
    public static Sample sampleReceiver(TechnisatReceiverBlockEntity radio) {
        Level level = radio.getLevel();
        if (level == null) return new Sample(null, -100, -20);
        BlockPos antenna = radio.hasAntennaConnected() ? radio.getAntennaPos() : null;
        return sampleAt(level, antenna != null ? antenna : radio.getBlockPos(),
                radio.getDrmFrequencyKhz(), 0.0, antenna, radio.getBlockPos());
    }
    private static Sample sampleAt(Level level, BlockPos receivePos, int frequencyKhz,
                                   double receiverLossDb, BlockPos antennaPos, BlockPos endpointPos) {
        DrmTransmitterBlockEntity best = null;
        double bestSignal = -100, bestSnr = -20;
        int bestFrequencyOffset = Integer.MAX_VALUE;
        boolean underwater = level.getFluidState(receivePos).is(net.minecraft.tags.FluidTags.WATER);
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof DrmTransmitterBlockEntity tx)) continue;
            double signal = DrmSignal.signal(level, tx, receivePos, frequencyKhz, underwater) - receiverLossDb;
            if (endpointPos != null)
                signal += SignalMath.linkedAntennaGainDb(level, antennaPos, endpointPos,
                        net.minecraft.world.phys.Vec3.atCenterOf(pos));
            int frequencyOffset = Math.abs(tx.getFrequencyKhz() - frequencyKhz);
            if (frequencyOffset > bestFrequencyOffset
                    || frequencyOffset == bestFrequencyOffset && signal <= bestSignal) continue;
            double snr = Math.max(-20, Math.min(40, (signal
                    - SignalMath.calculateNoiseFloorDbuv(net.minecraft.world.phys.Vec3.atCenterOf(receivePos))) * .48));
            if (signal < 5 || snr < tx.getMinimumSnrDb()) continue;
            best = tx; bestSignal = signal; bestSnr = snr; bestFrequencyOffset = frequencyOffset;
        }
        return new Sample(best, bestSignal, bestSnr);
    }
}
