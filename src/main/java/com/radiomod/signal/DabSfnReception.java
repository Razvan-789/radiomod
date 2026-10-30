package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Combines synchronized transmitters carrying the same multiplex on one frequency. */
public final class DabSfnReception {
    private DabSfnReception() {
    }

    /** A nonpositive receiver frequency measures each group at its own transmission frequency. */
    public static List<Group> scan(Player player, int overhead, int receiverFrequencyKhz) {
        Entity moving = player.getVehicle() == null ? player : player.getVehicle();
        if (moving.getDeltaMovement().length() * 20.0 > 84.0) {
            return List.of();
        }
        return scan(player.level(), player.position(), overhead, receiverFrequencyKhz, player.isUnderWater());
    }

    /** Static receivers are not subject to player or vehicle speed limits. */
    public static List<Group> scan(Level level, Vec3 receiver, int overhead,
                                   int receiverFrequencyKhz, boolean underwater) {
        return scan(level, receiver, overhead, receiverFrequencyKhz, underwater, null, null);
    }

    public static List<Group> scan(Level level, Vec3 receiver, int overhead,
                                   int receiverFrequencyKhz, boolean underwater,
                                   BlockPos receiverAntennaPos, BlockPos receiverBlockPos) {
        Map<String, Aggregate> aggregates = new HashMap<>();
        double noise = SignalMath.calculateNoiseFloorDbuv(receiver);
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof DabTransmitterBlockEntity transmitter)) {
                continue;
            }
            DabServerBlockEntity.DabEnsembleDefinition ensemble = transmitter.resolveBroadcastMux();
            if (ensemble == null) {
                continue;
            }
            int frequency = transmitter.getFrequencyKhz();
            int receiveFrequency = receiverFrequencyKhz > 0 ? receiverFrequencyKhz : frequency;
            double signal = SignalMath.calculateDabSignalDbuv(transmitter.getPowerW(), frequency,
                    receiveFrequency, transmitter.hasAntennaConnected(), transmitter.isDirectionalEnabled(),
                    transmitter.getDirectionDegrees(), Vec3.atCenterOf(pos), receiver, overhead, underwater)
                    + transmitter.antennaGainToward(receiver);
            if (receiverBlockPos != null) {
                signal += SignalMath.linkedAntennaGainDb(level, receiverAntennaPos,
                        receiverBlockPos, Vec3.atCenterOf(pos));
            }
            String key = frequency + ":" + transmitter.getEnsembleId() + ":" + transmitter.getSfnIdentity()
                    + ":" + transmitter.getDabVersion() + ":" + transmitter.getErrorCorrection();
            aggregates.computeIfAbsent(key, ignored -> new Aggregate(frequency, transmitter, ensemble))
                    .add(transmitter, signal);
        }
        List<Group> result = new ArrayList<>(aggregates.size());
        for (Aggregate aggregate : aggregates.values()) {
            double combined = aggregate.strongestSignal + 10.0 * Math.log10(aggregate.relativePowerSum);
            result.add(new Group(aggregate.frequency, aggregate.strongestTransmitter, aggregate.ensemble,
                    combined, SignalMath.calculateDabSnrDb(combined, noise), aggregate.count,
                    aggregate.members.stream()
                            .sorted(Comparator.comparingDouble(Member::signalDbuv).reversed())
                            .map(member -> new TiiSignal(member.transmitter().getTiiCode(),
                                    member.signalDbuv() - aggregate.strongestSignal))
                            .toList()));
        }
        return result;
    }

    public record Group(int frequencyKhz, DabTransmitterBlockEntity transmitter,
                        DabServerBlockEntity.DabEnsembleDefinition ensemble,
                        double signalDbuv, double snrDb, int transmitterCount, List<TiiSignal> tiiSignals) {
    }

    public record TiiSignal(String code, double relativeDb) {
    }

    private record Member(DabTransmitterBlockEntity transmitter, double signalDbuv) {
    }

    private static final class Aggregate {
        private final int frequency;
        private final DabServerBlockEntity.DabEnsembleDefinition ensemble;
        private DabTransmitterBlockEntity strongestTransmitter;
        private double strongestSignal = Double.NEGATIVE_INFINITY;
        private double relativePowerSum;
        private int count;
        private final List<Member> members = new ArrayList<>();

        private Aggregate(int frequency, DabTransmitterBlockEntity transmitter,
                          DabServerBlockEntity.DabEnsembleDefinition ensemble) {
            this.frequency = frequency;
            this.strongestTransmitter = transmitter;
            this.ensemble = ensemble;
        }

        private void add(DabTransmitterBlockEntity transmitter, double signal) {
            members.add(new Member(transmitter, signal));
            count++;
            if (signal > strongestSignal) {
                relativePowerSum = count == 1 ? 1.0
                        : relativePowerSum * Math.pow(10.0, (strongestSignal - signal) / 10.0) + 1.0;
                strongestSignal = signal;
                strongestTransmitter = transmitter;
            } else {
                relativePowerSum += Math.pow(10.0, (signal - strongestSignal) / 10.0);
            }
        }
    }
}
