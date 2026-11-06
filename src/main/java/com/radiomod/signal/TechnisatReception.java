package com.radiomod.signal;

import com.radiomod.blockentity.TechnisatReceiverBlockEntity;
import com.radiomod.blockentity.PureBugBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.network.Dx3Station;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Keeps a scan's original channels while checking which services still decode there. */
public final class TechnisatReception {
    private TechnisatReception() { }

    public static List<Dx3Station> visible(Player player, List<Dx3Station> scanned) {
        if (scanned.isEmpty()) return List.of();
        List<DabSfnReception.Group> groups = DabSfnReception.scan(player,
                SignalMath.countOverheadBlocks(player.level(), player.position()), 0);
        return visible(scanned, groups, 0.0, SignalMath.calculateNoiseFloorDbuv(player.position()));
    }

    public static List<Dx3Station> visible(TechnisatReceiverBlockEntity receiver,
                                           List<Dx3Station> scanned) {
        if (scanned.isEmpty() || receiver.getLevel() == null) return List.of();
        Vec3 point = receiver.receptionPoint();
        List<DabSfnReception.Group> groups = DabSfnReception.scan(receiver.getLevel(), point,
                SignalMath.countOverheadBlocks(receiver.getLevel(), point), 0, false,
                receiver.getAntennaPos(), receiver.getBlockPos());
        if (receiver instanceof PureBugBlockEntity)
            groups = groups.stream().filter(group -> group.transmitter().getDabVersion() == DabVersion.DAB).toList();
        return visible(scanned, groups, receiver.receiverLossDb(),
                SignalMath.calculateNoiseFloorDbuv(point));
    }

    private static List<Dx3Station> visible(List<Dx3Station> scanned,
                                             List<DabSfnReception.Group> groups, double loss,
                                             double noise) {
        return scanned.stream().map(station -> groups.stream().map(group -> {
            if (!station.dab() || group.ensemble() == null
                    || DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz()) != station.tuning()
                    || !Dx3Catalog.limit(group.ensemble().name(), 64).equals(station.mux())) return (Dx3Station) null;
            double signal = group.signalDbuv() - loss;
            double snr = loss == 0.0 ? group.snrDb() : SignalMath.calculateDabSnrDb(signal, noise);
            if (signal < group.transmitter().getErrorCorrection().getMinimumSignalDbuv()
                    || snr < group.transmitter().getErrorCorrection().getThresholdSnrDb() + 1.8) return null;
            return group.ensemble().stations().stream()
                    .filter(service -> service.sid().equalsIgnoreCase(station.sid()))
                    .findFirst().map(service -> new Dx3Station(true, station.tuning(), station.sid(),
                            station.mux(), station.label(), station.sortName(), station.detail(),
                            Dx3Catalog.motPixels(service.motPng(), 16))).orElse(null);
        }).filter(java.util.Objects::nonNull).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    public static List<Dx3Station> scanDrm(Player player) {
        return scanDrm(player.level(), player.blockPosition(), player.isUnderWater(), 8.0, null, null);
    }

    public static List<Dx3Station> scanDrm(TechnisatReceiverBlockEntity receiver) {
        if (receiver.getLevel() == null) return List.of();
        BlockPos antenna = receiver.hasAntennaConnected() ? receiver.getAntennaPos() : null;
        BlockPos position = antenna != null ? antenna : receiver.getBlockPos();
        return scanDrm(receiver.getLevel(), position,
                receiver.getLevel().getFluidState(position).is(FluidTags.WATER), 0.0,
                antenna, receiver.getBlockPos());
    }

    private static List<Dx3Station> scanDrm(Level level, BlockPos receivePos, boolean underwater,
                                            double receiverLossDb, BlockPos antennaPos, BlockPos endpointPos) {
        Map<Integer, DrmCandidate> strongest = new HashMap<>();
        double noise = SignalMath.calculateNoiseFloorDbuv(Vec3.atCenterOf(receivePos));
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof DrmTransmitterBlockEntity tx)) continue;
            int frequency = tx.getFrequencyKhz();
            double signal = DrmSignal.signal(level, tx, receivePos, frequency, underwater) - receiverLossDb;
            if (endpointPos != null)
                signal += SignalMath.linkedAntennaGainDb(level, antennaPos, endpointPos,
                        Vec3.atCenterOf(pos));
            double snr = Math.max(-20, Math.min(40, (signal - noise) * .48));
            if (signal < 5 || snr < tx.getMinimumSnrDb()) continue;
            DrmCandidate previous = strongest.get(frequency);
            if (previous == null || signal > previous.signal()) strongest.put(frequency,
                    new DrmCandidate(tx, signal));
        }
        return strongest.values().stream().map(candidate -> {
            DrmTransmitterBlockEntity tx = candidate.tx();
            String shortLabel = Dx3Catalog.limit(tx.getShortLabel(), 16);
            String serviceLabel = Dx3Catalog.limit(tx.getServiceLabel(), 64);
            String label = serviceLabel.isBlank() ? shortLabel : serviceLabel;
            String detail = DrmSignal.band(tx.getFrequencyKhz()) + " " + tx.getFrequencyKhz()
                    + " kHz  Mode " + tx.getModeLabel();
            return new Dx3Station(false, tx.getFrequencyKhz(), shortLabel, serviceLabel,
                    Dx3Catalog.limit(label, 96), Dx3Catalog.limit(label, 96), detail, null);
        }).sorted(Comparator.comparing(Dx3Station::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Dx3Station::tuning)).limit(128).toList();
    }

    public static List<Dx3Station> visibleDrm(Player player, List<Dx3Station> scanned) {
        return scanned.isEmpty() ? List.of() : visibleDrm(scanned, scanDrm(player));
    }

    public static List<Dx3Station> visibleDrm(TechnisatReceiverBlockEntity receiver,
                                              List<Dx3Station> scanned) {
        return scanned.isEmpty() ? List.of() : visibleDrm(scanned, scanDrm(receiver));
    }

    private static List<Dx3Station> visibleDrm(List<Dx3Station> scanned, List<Dx3Station> current) {
        if (scanned.isEmpty()) return List.of();
        return scanned.stream().filter(saved -> current.stream().anyMatch(now ->
                now.tuning() == saved.tuning() && now.sid().equals(saved.sid())
                        && now.mux().equals(saved.mux()))).toList();
    }

    private record DrmCandidate(DrmTransmitterBlockEntity tx, double signal) { }
}
