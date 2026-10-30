package com.radiomod.signal;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.network.SyncSignalMeterPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Meter tuning is independent of radio tuning and never selects an audio stream. */
public final class SignalMeterState {
    private static final Map<UUID, Settings> SETTINGS = new HashMap<>();
    private static SyncSignalMeterPayload clientSnapshot;
    private static long clientUpdatedAt;

    private SignalMeterState() { }

    public static final class Settings {
        public boolean dab;
        public int frequencyKhz = 101_700;
        public int channelIndex = DabChannel.nearestIndexForFrequencyKhz(220_352);
        public boolean pinned;
        public final LinkedHashSet<String> decodedGroups = new LinkedHashSet<>();
        private String source = "";
        private String pi = "";
        private String ps = "";
    }

    public static Settings settings(Player player) {
        return SETTINGS.computeIfAbsent(player.getUUID(), ignored -> new Settings());
    }

    public static boolean hasMeter(Player player) {
        return player.getInventory().contains(new net.minecraft.world.item.ItemStack(Radio.SIGNAL_METER_ITEM.get()));
    }

    public static void updateClient(SyncSignalMeterPayload snapshot) {
        clientSnapshot = snapshot;
        clientUpdatedAt = System.currentTimeMillis();
    }

    public static SyncSignalMeterPayload freshClient() {
        return System.currentTimeMillis() - clientUpdatedAt < 2500 ? clientSnapshot : null;
    }

    public static SyncSignalMeterPayload sample(Player player) {
        Settings settings = settings(player);
        int overhead = SignalMath.countOverheadBlocks(player.level(), player.position());
        return settings.dab ? dabSample(player, settings, overhead) : fmSample(player, settings, overhead);
    }

    private static SyncSignalMeterPayload fmSample(Player player, Settings settings, int overhead) {
        int frequency = settings.frequencyKhz;
        Vec3 receiver = player.position();
        double bestSignal = Double.NEGATIVE_INFINITY;
        TransmitterBlockEntity best = null;
        BlockPos bestPos = null;
        for (BlockPos pos : SignalNetwork.getTransmitters(player.level())) {
            if (!(player.level().getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter)
                    || Math.abs(transmitter.getFrequencyKhz() - frequency) > 100) continue;
            double signal = SignalMath.calculateSignalDbuv(transmitter.getPowerW(),
                    transmitter.getFrequencyKhz(), frequency, transmitter.hasAntennaConnected(),
                    transmitter.isDirectionalEnabled(), transmitter.getDirectionDegrees(),
                    Vec3.atCenterOf(pos), receiver, overhead, player.isUnderWater())
                    + transmitter.antennaGainToward(receiver);
            signal = MeteorScatter.signal(player.level(), transmitter, receiver, frequency, signal, player.isUnderWater());
            if (signal > bestSignal) { bestSignal = signal; best = transmitter; bestPos = pos; }
        }
        boolean active = best != null && bestSignal >= -5;
        double snr = active ? SignalMath.calculateSnrDb(bestSignal, SignalMath.calculateNoiseFloorDbuv(receiver)) : 0;
        int ber = active && best.isRdsEnabled() ? ber(bestSignal, snr) : 100;
        String source = bestPos == null ? "" : frequency + ":" + bestPos.asLong();
        if (!active) source = "";
        if (!source.equals(settings.source)) {
            settings.source = source;
            settings.decodedGroups.clear();
            settings.pi = "";
            settings.ps = "";
        }
        String current = "";
        if (active && ber <= 98) {
            RdsFrameData frame = best.getCurrentRdsFrame(ber, player.level().getGameTime(), "");
            if (frame != null) {
                current = frame.groupCode();
                settings.decodedGroups.add(current);
                settings.pi = frame.pi();
                settings.ps = frame.ps();
            }
        }
        return new SyncSignalMeterPayload(false, frequency, settings.channelIndex, settings.pinned,
                active, tenths(active ? bestSignal : 0), tenths(snr), ber, settings.pi, settings.ps, current,
                String.join(" ", settings.decodedGroups), "", "", 0, 0, 0, List.of(), List.of());
    }

    private static SyncSignalMeterPayload dabSample(Player player, Settings settings, int overhead) {
        int frequency = DabChannel.byIndex(settings.channelIndex).frequencyKhz();
        DabSfnReception.Group best = DabSfnReception.scan(player, overhead, frequency).stream()
                .filter(group -> group.frequencyKhz() == frequency)
                .max(Comparator.comparingDouble(DabSfnReception.Group::signalDbuv)).orElse(null);
        boolean active = best != null && best.signalDbuv() >= -5;
        double signal = active ? best.signalDbuv() : 0;
        double snr = active ? best.snrDb() : 0;
        DabErrorCorrection ec = active ? best.transmitter().getErrorCorrection() : null;
        int fic = state(signal, snr, ec, 0);
        int msc = state(signal, snr, ec, 0.8);
        int fs = state(signal, snr, ec, -0.5);
        List<String> stations = new ArrayList<>();
        if (active && fic > 0) {
            for (DabServerBlockEntity.DabStationDefinition station : best.ensemble().stations()) {
                stations.add(station.label());
            }
        }
        List<SyncSignalMeterPayload.TiiReading> tii = new ArrayList<>();
        if (active && fs > 0) {
            for (DabSfnReception.TiiSignal reading : best.tiiSignals()) {
                tii.add(new SyncSignalMeterPayload.TiiReading(reading.code(), tenths(reading.relativeDb())));
            }
        }
        return new SyncSignalMeterPayload(true, settings.frequencyKhz, settings.channelIndex, settings.pinned,
                active, tenths(signal), tenths(snr), 100, "", "", "", "",
                active && fic > 0 ? best.ensemble().name() : "", ec == null ? "" : ec.getLabel(),
                fic, msc, fs, stations, tii);
    }

    private static int state(double signal, double snr, DabErrorCorrection ec, double bias) {
        if (ec == null) return 0;
        return ec.decodeState(signal, snr, bias);
    }

    private static int ber(double signal, double snr) {
        double quality = signal * 0.45 + snr * 0.55;
        return (int) Math.round(Math.max(0, Math.min(99, 99 * Math.exp(-0.191 * (quality - 9.11)))));
    }

    private static int tenths(double value) { return (int) Math.round(value * 10); }
}
