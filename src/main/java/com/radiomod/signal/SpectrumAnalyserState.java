package com.radiomod.signal;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.network.SyncSpectrumPayload;
import com.radiomod.server.ServerAudioProxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-owned tuning and received transmitter readings. Visual noise is client-only. */
public final class SpectrumAnalyserState {
    private static final Map<UUID, Settings> SETTINGS = new HashMap<>();
    private static SyncSpectrumPayload clientSnapshot;
    private static long clientUpdatedAt;

    private SpectrumAnalyserState() { }

    public static final class Settings {
        public int band = 1; // FM, DAB, DRM
        public final int[] centers = {101_700, 220_352, 1_000};
        public final int[] spans = {2_000, 4_000, 100};
        public final int[] bandwidths = {200, 1_536, 10};
    }

    public static Settings settings(Player player) {
        return SETTINGS.computeIfAbsent(player.getUUID(), ignored -> new Settings());
    }

    public static boolean hasAnalyser(Player player) {
        return player.getInventory().contains(new ItemStack(Radio.SPECTRUM_ANALYSER_ITEM.get()));
    }

    public static void updateClient(SyncSpectrumPayload value) {
        clientSnapshot = value;
        clientUpdatedAt = System.currentTimeMillis();
    }

    public static void clearClient() {
        clientSnapshot = null;
        clientUpdatedAt = 0;
    }

    public static void clearRds(Player player) {
        SpectrumRdsDecoder.clear(player);
    }

    public static SyncSpectrumPayload freshClient() {
        return System.currentTimeMillis() - clientUpdatedAt < 2500 ? clientSnapshot : null;
    }

    public static int min(int band) { return band == 0 ? 65_000 : band == 1 ? 174_000 : 140; }
    public static int max(int band) { return band == 0 ? 108_000 : band == 1 ? 240_000 : 30_000; }
    public static int minSpan(int band) { return band == 0 ? 500 : band == 1 ? 2_000 : 25; }
    public static int maxSpan(int band) { return band == 0 ? 10_000 : band == 1 ? 12_000 : 5_000; }
    public static int minBandwidth(int band) { return band == 0 ? 50 : band == 1 ? 500 : 5; }
    public static int maxBandwidth(int band) { return band == 0 ? 400 : band == 1 ? 2_000 : 20; }
    public static int bandwidthStep(int band) { return band == 0 ? 50 : band == 1 ? 250 : 5; }
    public static int tuneStep(int band) { return band == 0 ? 100 : band == 1 ? 100 : 5; }

    public static SyncSpectrumPayload sample(Player player) {
        Settings s = settings(player);
        int band = s.band, center = s.centers[band], span = s.spans[band];
        Vec3 receiver = player.position();
        int overhead = SignalMath.countOverheadBlocks(player.level(), receiver);
        double floor = SignalMath.calculateNoiseFloorDbuv(receiver);
        double tunedSignal = Double.NEGATIVE_INFINITY;
        boolean signalPresent = false;
        String sourceUrl = "";
        TransmitterBlockEntity tunedFm = null;
        BlockPos tunedFmPos = null;
        List<SyncSpectrumPayload.Carrier> carriers = new ArrayList<>();
        if (band == 1) {
            for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
                if (Math.abs(group.frequencyKhz() - center) > span / 2 + 1_000) continue;
                carriers.add(new SyncSpectrumPayload.Carrier(group.frequencyKhz(), 1_536,
                        (int) Math.round(group.signalDbuv() * 10), 1));
                if (Math.abs(group.frequencyKhz() - center) <= 100
                        && s.bandwidths[band] >= 1_536 && group.signalDbuv() > tunedSignal) {
                    tunedSignal = group.signalDbuv();
                    signalPresent = group.transmitter().getErrorCorrection()
                            .decodeState(group.signalDbuv(), group.snrDb(), 0) > 0;
                }
            }
        } else {
            for (BlockPos pos : SignalNetwork.getTransmitters(player.level())) {
                if (band == 0 && player.level().getBlockEntity(pos) instanceof TransmitterBlockEntity tx) {
                    if (Math.abs(tx.getFrequencyKhz() - center) > span / 2 + 300) continue;
                    double strength = SignalMath.calculateSignalDbuv(tx.getPowerW(), tx.getFrequencyKhz(),
                            tx.getFrequencyKhz(), tx.hasAntennaConnected(), tx.isDirectionalEnabled(),
                            tx.getDirectionDegrees(), Vec3.atCenterOf(pos), receiver, overhead,
                            player.isUnderWater()) + tx.antennaGainToward(receiver);
                    strength = MeteorScatter.signal(player.level(), tx, receiver, tx.getFrequencyKhz(),
                            strength, player.isUnderWater());
                    carriers.add(new SyncSpectrumPayload.Carrier(tx.getFrequencyKhz(), 180,
                            (int) Math.round(strength * 10), 0));
                    if (Math.abs(tx.getFrequencyKhz() - center) <= Math.min(100, s.bandwidths[band] / 2)) {
                        double tuned = SignalMath.calculateSignalDbuv(tx.getPowerW(), tx.getFrequencyKhz(),
                                center, tx.hasAntennaConnected(), tx.isDirectionalEnabled(),
                                tx.getDirectionDegrees(), Vec3.atCenterOf(pos), receiver, overhead,
                                player.isUnderWater()) + tx.antennaGainToward(receiver);
                        tuned = MeteorScatter.signal(player.level(), tx, receiver, center,
                                tuned, player.isUnderWater());
                        tuned += 10 * Math.log10(Math.min(1.0, s.bandwidths[band] / 180.0));
                        if (tuned > tunedSignal) {
                            tunedSignal = tuned;
                            sourceUrl = tx.getStreamUrl() == null ? "" : tx.getStreamUrl().trim();
                            signalPresent = tuned >= -12;
                            tunedFm = tx;
                            tunedFmPos = pos;
                        }
                    }
                } else if (band == 2 && player.level().getBlockEntity(pos) instanceof DrmTransmitterBlockEntity tx) {
                    if (Math.abs(tx.getFrequencyKhz() - center) > span / 2 + 30) continue;
                    double strength = DrmSignal.signal(player.level(), tx, player.blockPosition(),
                            tx.getFrequencyKhz(), player.isUnderWater());
                    carriers.add(new SyncSpectrumPayload.Carrier(tx.getFrequencyKhz(), tx.getBandwidthKhz(),
                            (int) Math.round(strength * 10), 2));
                    double tuned = DrmSignal.signal(player.level(), tx, player.blockPosition(),
                            center, player.isUnderWater()) - 8;
                    if (s.bandwidths[band] >= tx.getBandwidthKhz()
                            && Math.abs(tx.getFrequencyKhz() - center) <= tx.getBandwidthKhz() / 2 + 2
                            && tuned > tunedSignal) {
                        tunedSignal = tuned;
                        double snr = Math.max(-20, Math.min(40, (tunedSignal - floor) * .48));
                        signalPresent = tunedSignal >= 5 && snr >= tx.getMinimumSnrDb();
                    }
                }
            }
        }
        carriers.sort(Comparator.comparingInt(SyncSpectrumPayload.Carrier::signalTenths).reversed());
        if (carriers.size() > 64) carriers = carriers.subList(0, 64);
        double captured = floor;
        for (SyncSpectrumPayload.Carrier carrier : carriers) {
            int separation = Math.abs(carrier.frequencyKhz() - center);
            int captureHalf = s.bandwidths[band] / 2 + carrier.widthKhz() / 2;
            if (separation <= captureHalf) {
                // Insufficient bandwidth loses some of the station's energy.
                double fraction = Math.min(1.0, s.bandwidths[band] / (double) carrier.widthKhz());
                captured = Math.max(captured, carrier.signalTenths() / 10.0 + 10 * Math.log10(fraction));
            }
        }
        double snr = Double.isFinite(tunedSignal) ? SignalMath.calculateSnrDb(tunedSignal, floor) : 0;
        SyncSpectrumPayload.RdsReading rds;
        if (band == 0) rds = SpectrumRdsDecoder.sample(player, tunedFmPos, tunedFm,
                center, tunedSignal, snr);
        else {
            SpectrumRdsDecoder.clear(player);
            rds = SyncSpectrumPayload.RdsReading.EMPTY;
        }
        String audioUrl = band == 0 && signalPresent && !sourceUrl.isBlank()
                ? ServerAudioProxy.buildProxyToken(sourceUrl) : "";
        return new SyncSpectrumPayload(band, center, span, s.bandwidths[band],
                (int) Math.round(floor * 10), (int) Math.round(captured * 10), overhead, List.copyOf(carriers),
                signalPresent, (int) Math.round((Double.isFinite(tunedSignal) ? tunedSignal : floor) * 10),
                (int) Math.round(snr * 10), audioUrl, audioUrl.isBlank() ? "" : sourceUrl, rds);
    }
}
