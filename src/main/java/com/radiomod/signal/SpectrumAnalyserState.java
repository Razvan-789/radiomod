package com.radiomod.signal;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
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
import net.minecraft.server.level.ServerLevel;

/** Server-owned tuning and received transmitter readings. Visual noise is client-only. */
public final class SpectrumAnalyserState {
    private static final Map<UUID, Settings> SETTINGS = new HashMap<>();
    private static SyncSpectrumPayload clientSnapshot;
    private static long clientUpdatedAt;

    private SpectrumAnalyserState() { }

    public static final class Settings {
        public int band = 1; // FM, DAB, DRM, VOR, ADS-B; selected by frequency
        public int selectedService;
        public final int[] centers = {101_700, 220_352, 1_000, 113_000, 1_090_000};
        public final int[] spans = {2_000, 4_000, 100, 2_000, 2_000};
        public final int[] bandwidths = {200, 1_536, 10, 50, 1_000};
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

    public static int bandFor(int khz) {
        if (khz >= 140 && khz <= 30_000) return 2;
        if (khz >= 65_000 && khz < 108_000) return 0;
        if (khz >= 108_000 && khz <= 117_950) return 3;
        if (khz >= 174_000 && khz <= 240_000) return 1;
        if (khz >= 1_089_000 && khz <= 1_091_000) return 4;
        return -1;
    }
    public static int min(int band) { return new int[]{65_000, 174_000, 140, 108_000, 1_089_000}[band]; }
    public static int max(int band) { return new int[]{107_999, 240_000, 30_000, 117_950, 1_091_000}[band]; }
    public static int minSpan(int band) { return new int[]{500, 2_000, 25, 500, 500}[band]; }
    public static int maxSpan(int band) { return new int[]{10_000, 12_000, 5_000, 10_000, 5_000}[band]; }
    public static int minBandwidth(int band) { return new int[]{50, 500, 5, 25, 250}[band]; }
    public static int maxBandwidth(int band) { return new int[]{400, 2_000, 20, 200, 2_000}[band]; }
    public static int bandwidthStep(int band) { return new int[]{50, 250, 5, 25, 250}[band]; }
    public static int tuneStep(int band) { return new int[]{100, 100, 5, 50, 1_000}[band]; }
    public static void tune(Settings s, int khz) {
        int band = bandFor(khz);
        if (band < 0) return;
        if (s.band != band) s.selectedService = 0;
        s.band = band;
        s.centers[band] = khz;
    }

    public static SyncSpectrumPayload sample(Player player) {
        Settings s = settings(player);
        int band = s.band, center = s.centers[band], span = s.spans[band];
        Vec3 receiver = player.position();
        int overhead = SignalMath.countOverheadBlocks(player.level(), receiver);
        double floor = SignalMath.calculateNoiseFloorDbuv(receiver);
        double tunedSignal = Double.NEGATIVE_INFINITY;
        boolean signalPresent = false;
        String sourceUrl = "";
        String serviceInfo = "";
        int adsbContacts = 0;
        boolean dabPlus = true;
        int dabBitrate = 64;
        int drmBitrate = 32;
        List<SyncSpectrumPayload.Service> services = new ArrayList<>();
        TransmitterBlockEntity tunedFm = null;
        BlockPos tunedFmPos = null;
        List<SyncSpectrumPayload.Carrier> carriers = new ArrayList<>();
        if (band == 1) {
            DabSfnReception.Group selected = null;
            for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
                if (Math.abs(group.frequencyKhz() - center) > span / 2 + 1_000) continue;
                carriers.add(new SyncSpectrumPayload.Carrier(group.frequencyKhz(), 1_536,
                        (int) Math.round(group.signalDbuv() * 10), 1));
                if (Math.abs(group.frequencyKhz() - center) <= 100
                        && s.bandwidths[band] >= 1_536 && group.signalDbuv() > tunedSignal) {
                    tunedSignal = group.signalDbuv();
                    signalPresent = group.transmitter().getErrorCorrection()
                            .decodeState(group.signalDbuv(), group.snrDb(), 0) > 0;
                    selected = group;
                }
            }
            if (signalPresent && selected != null) {
                var tx = selected.transmitter();
                serviceInfo = selected.ensemble().name() + "  " + tx.getDabVersion().getLabel()
                        + "  EC " + tx.getErrorCorrection().getLabel() + "  TII " + tx.getTiiCode();
                List<DabServerBlockEntity.DabStationDefinition> stations = selected.ensemble().stations();
                s.selectedService = Math.clamp(s.selectedService, 0, Math.max(0, stations.size() - 1));
                for (int i = 0; i < Math.min(32, stations.size()); i++) {
                    var station = stations.get(i);
                    services.add(new SyncSpectrumPayload.Service(station.label(),
                            station.bitrateKbps() + " kbps  SID " + station.sid(), i == s.selectedService));
                }
                if (!stations.isEmpty()) {
                    var station = stations.get(s.selectedService);
                    sourceUrl = station.streamUrl().trim();
                    dabPlus = tx.getDabVersion() == DabVersion.DAB_PLUS;
                    dabBitrate = station.bitrateKbps();
                    serviceInfo += "  " + station.label() + "  " + station.bitrateKbps() + " kbps";
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
                        if (signalPresent) {
                            sourceUrl = tx.getStreamUrl() == null ? "" : tx.getStreamUrl().trim();
                            drmBitrate = tx.getOpusBitrateKbps();
                            serviceInfo = tx.getServiceLabel() + "  " + tx.getShortLabel()
                                    + "  Mode " + tx.getModeLabel() + "  " + tx.getOpusBitrateKbps()
                                    + " kbps  " + tx.getTextMessage();
                        }
                    }
                }
            }
        }
        if (band == 3 && player.level() instanceof ServerLevel level) {
            for (BlockPos pos : VorNetwork.get(level)) {
                if (!(level.getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor)
                        || !vor.hasAntennaConnected() || Math.abs(vor.frequencyKhz() - center) > span / 2 + 100) continue;
                var fix = VorReception.receive(level, receiver, vor.frequencyKhz());
                if (fix == null || !fix.transmitterPos().equals(pos)) continue;
                double strength = floor + Math.max(0, fix.snrDb());
                carriers.add(new SyncSpectrumPayload.Carrier(vor.frequencyKhz(), 30,
                        (int) Math.round(strength * 10), 3));
                if (Math.abs(vor.frequencyKhz() - center) <= s.bandwidths[band] / 2 && strength > tunedSignal) {
                    tunedSignal = strength; signalPresent = true;
                    serviceInfo = "VOR " + vor.identifier() + (fix.dme() ? "  DME " + (int) fix.dmeBlocks() + " blocks" : "");
                }
            }
        }
        if (band == 4 && player.level() instanceof ServerLevel level && Math.abs(center - 1_090_000) <= span / 2 + 1_000) {
            var contacts = AdsbReception.receivePortable(level, receiver);
            adsbContacts = contacts.size();
            if (!contacts.isEmpty()) {
                double bestSnr = contacts.stream().mapToDouble(AdsbReception.Contact::snrDb).max().orElse(0);
                tunedSignal = floor + bestSnr;
                signalPresent = true;
                carriers.add(new SyncSpectrumPayload.Carrier(1_090_000, 1_000,
                        (int) Math.round(tunedSignal * 10), 4));
                serviceInfo = "ADS-B  " + contacts.size() + " receivable transponders";
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
        if (serviceInfo.length() > 200) serviceInfo = serviceInfo.substring(0, 200);
        String audioUrl = signalPresent && !sourceUrl.isBlank()
                ? band == 1 ? ServerAudioProxy.buildDabProxyToken(sourceUrl,
                        dabPlus, dabBitrate)
                : band == 2 ? ServerAudioProxy.buildOpusEquivalentMp3ProxyToken(sourceUrl, drmBitrate)
                : ServerAudioProxy.buildProxyToken(sourceUrl) : "";
        return new SyncSpectrumPayload(band, center, span, s.bandwidths[band],
                (int) Math.round(floor * 10), (int) Math.round(captured * 10), overhead, List.copyOf(carriers),
                signalPresent, (int) Math.round((Double.isFinite(tunedSignal) ? tunedSignal : floor) * 10),
                (int) Math.round(snr * 10), audioUrl, audioUrl.isBlank() ? "" : sourceUrl, rds,
                serviceInfo, List.copyOf(services), adsbContacts);
    }
}
