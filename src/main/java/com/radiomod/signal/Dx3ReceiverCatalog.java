package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.PureBugBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.item.RadioItem;
import com.radiomod.network.Dx3Station;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.server.ServerAudioProxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** DX3 service discovery at a fixed receiver/antenna position. */
public final class Dx3ReceiverCatalog {
    private static final double MIN_SIGNAL = 5.0;
    private Dx3ReceiverCatalog() { }

    public static List<Dx3Station> scan(Dx3ReceiverBlockEntity receiver) {
        Level level = receiver.getLevel();
        if (level == null) return List.of();
        Vec3 point = receiver.receptionPoint();
        int overhead = SignalMath.countOverheadBlocks(level, point);
        double noise = SignalMath.calculateNoiseFloorDbuv(point);
        double loss = receiver.receiverLossDb();
        Map<String, Ranked> best = new HashMap<>();
        if (!(receiver instanceof PureBugBlockEntity)) for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm)) continue;
            double signal = fmSignal(receiver, fm, point, overhead) - loss;
            if (signal < MIN_SIGNAL) continue;
            double snr = SignalMath.calculateSnrDb(signal, noise);
            boolean rds = canDecodeRds(fm, signal, snr);
            String pi = rds ? fm.getRdsPi().trim().toUpperCase(Locale.ROOT) : "";
            String ps = rds ? fm.getCurrentPsFrame(level.getGameTime()).trim() : "";
            String label = ps.isBlank() ? String.format(Locale.ROOT, "FM %.1f", fm.getFrequencyKhz() / 1000.0) : ps;
            String sortName = rds ? Dx3Catalog.firstPsFrame(fm) : "";
            if (sortName.isBlank()) sortName = label;
            Dx3Station station = new Dx3Station(false, fm.getFrequencyKhz(), Dx3Catalog.limit(pi, 16), "", Dx3Catalog.limit(label, 96),
                    Dx3Catalog.limit(sortName, 96), rds ? "PTY: " + PtyNames.name(fm.getRdsPty()) : "PTY unavailable", null);
            put(best, pi.isBlank() ? "F:" + fm.getFrequencyKhz() : "FPI:" + pi, station, signal);
        }
        for (DabSfnReception.Group group : DabSfnReception.scan(level, point, overhead, 0, false,
                receiver.getAntennaPos(), receiver.getBlockPos())) {
            if (receiver instanceof PureBugBlockEntity
                    && group.transmitter().getDabVersion() != DabVersion.DAB) continue;
            double signal = group.signalDbuv() - loss;
            double snr = SignalMath.calculateDabSnrDb(signal, noise);
            if (signal < group.transmitter().getErrorCorrection().getMinimumSignalDbuv()
                    || snr < group.transmitter().getErrorCorrection().getThresholdSnrDb() + 1.8) continue;
            int channel = DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz());
            for (DabServerBlockEntity.DabStationDefinition service : group.ensemble().stations()) {
                Dx3Station station = new Dx3Station(true, channel, Dx3Catalog.limit(service.sid(), 16),
                        Dx3Catalog.limit(group.ensemble().name(), 64),
                        Dx3Catalog.limit(service.label().isBlank() ? "Station " + service.sid() : service.label(), 96),
                        Dx3Catalog.limit(service.label().isBlank() ? "Station " + service.sid() : service.label(), 96),
                        "PTY: " + PtyNames.name(service.pty()), Dx3Catalog.motPixels(service.motPng(), 16));
                put(best, service.sid().isBlank() ? "D:" + channel + ":" + group.ensemble().name()
                        : "DSID:" + service.sid().toUpperCase(Locale.ROOT), station, signal);
            }
        }
        Map<String, int[]> logos = new HashMap<>();
        for (Ranked ranked : best.values()) {
            Dx3Station station = ranked.station();
            if (station.dab() && !station.sid().isBlank() && station.logoPixels() != null)
                logos.put(station.sid().toUpperCase(Locale.ROOT), station.logoPixels());
        }
        List<Dx3Station> entries = new ArrayList<>();
        for (Ranked ranked : best.values()) {
            Dx3Station station = ranked.station();
            int[] logo = station.dab() ? null : logos.get(station.sid().toUpperCase(Locale.ROOT));
            entries.add(logo == null ? station : new Dx3Station(false, station.tuning(), station.sid(),
                    station.mux(), station.label(), station.sortName(), station.detail(), logo));
        }
        entries.sort(Comparator.comparing(Dx3Station::sortName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Dx3Station::dab).thenComparingInt(Dx3Station::tuning));
        return entries.size() > 128 ? List.copyOf(entries.subList(0, 128)) : List.copyOf(entries);
    }

    public static Sample sample(Dx3ReceiverBlockEntity receiver) {
        Level level = receiver.getLevel();
        if (level == null) { RdsTextDecoding.clearReceiver(receiver); return Sample.EMPTY; }
        Vec3 point = receiver.receptionPoint();
        int overhead = SignalMath.countOverheadBlocks(level, point);
        double noise = SignalMath.calculateNoiseFloorDbuv(point);
        double loss = receiver.receiverLossDb();
        if (!receiver.isDab()) {
            TransmitterBlockEntity best = null;
            double bestSignal = Double.NEGATIVE_INFINITY;
            for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
                if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm)
                        || fm.getFrequencyKhz() != receiver.getFrequencyKhz()
                        || !receiver.getPi().isBlank() && !receiver.getPi().equalsIgnoreCase(fm.getRdsPi())) continue;
                double signal = fmSignal(receiver, fm, point, overhead) - loss;
                if (signal > bestSignal) { bestSignal = signal; best = fm; }
            }
            if (best == null || bestSignal < MIN_SIGNAL) {
                RdsTextDecoding.clearReceiver(receiver);
                return Sample.EMPTY;
            }
            double snr = SignalMath.calculateSnrDb(bestSignal, noise);
            boolean rds = canDecodeRds(best, bestSignal, snr);
            String ps = rds ? best.getCurrentPsFrame(level.getGameTime()).trim() : "";
            String longPs = rds && best.isRdsGroupEnabled(28) ? best.getRdsLongPs().trim() : "";
            String rt = rds && best.isRdsGroupEnabled(4) ? best.getCurrentRtText(level.getGameTime()).trim() : "";
            if (rds) {
                RdsTextDecoding.Text text = RdsTextDecoding.forReceiver(receiver,
                        "F:" + best.getBlockPos().asLong() + ":" + receiver.getFrequencyKhz(),
                        ps, longPs, rt, Dx3Catalog.rdsBerPercent(bestSignal, snr), level.getGameTime());
                ps = text.ps(); longPs = text.longPs(); rt = text.rt();
            } else RdsTextDecoding.clearReceiver(receiver);
            boolean ct = rds && best.isRdsCtEnabled() && best.isRdsGroupEnabled(8);
            String time = "";
            if (ct) {
                long ticks = Math.floorMod(level.dayTime() + 6000L, 24000L);
                time = String.format(Locale.ROOT, "%02d:%02d", ticks / 1000, ticks % 1000 * 60 / 1000);
            }
            SyncDx3PlayerPayload info = new SyncDx3PlayerPayload(0, true, ps, longPs, rt,
                    rds ? best.getRdsPty() : 0, bars(bestSignal), ct, time, 0, "", rds);
            return new Sample(ServerAudioProxy.buildProxyToken(best.getStreamUrl()), best.getStreamUrl(),
                    bestSignal, snr, info, null);
        }
        RdsTextDecoding.clearReceiver(receiver);
        DabSfnReception.Group bestGroup = null;
        DabServerBlockEntity.DabStationDefinition bestService = null;
        double bestSignal = Double.NEGATIVE_INFINITY;
        double bestSnr = 0;
        for (DabSfnReception.Group group : DabSfnReception.scan(level, point, overhead, 0, false,
                receiver.getAntennaPos(), receiver.getBlockPos())) {
            if (receiver instanceof PureBugBlockEntity
                    && group.transmitter().getDabVersion() != DabVersion.DAB) continue;
            if (DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz()) != receiver.getChannelIndex()
                    || !group.ensemble().name().equals(receiver.getMux())) continue;
            double signal = group.signalDbuv() - loss;
            double snr = SignalMath.calculateDabSnrDb(signal, noise);
            if (signal < group.transmitter().getErrorCorrection().getMinimumSignalDbuv()
                    || snr < group.transmitter().getErrorCorrection().getThresholdSnrDb() + 1.8
                    || signal <= bestSignal) continue;
            for (DabServerBlockEntity.DabStationDefinition service : group.ensemble().stations()) {
                if (service.sid().equalsIgnoreCase(receiver.getSid())) {
                    bestGroup = group; bestService = service; bestSignal = signal; bestSnr = snr;
                }
            }
        }
        if (bestService == null) return Sample.EMPTY;
        String source = bestService.streamUrl();
        String url = ServerAudioProxy.buildDabProxyToken(source,
                bestGroup.transmitter().getDabVersion() == DabVersion.DAB_PLUS, bestService.bitrateKbps());
        SyncDx3PlayerPayload info = new SyncDx3PlayerPayload(0, true, Dx3Catalog.limit(bestService.label(), 96), "",
                Dx3Catalog.limit(DabDlsResolver.text(bestService), 256), bestService.pty(), bars(bestSignal), false, "",
                bestService.bitrateKbps(), bestGroup.transmitter().getDabVersion().getLabel() + " / "
                        + bestGroup.transmitter().getDabVersion().getCodecLabel());
        return new Sample(url, source, bestSignal, bestSnr, info,
                Dx3Catalog.motPixels(bestService.motPng(), 64));
    }

    public static void autoLink(Dx3ReceiverBlockEntity receiver) {
        String id = receiver.getLinkId();
        if (id.isBlank()) return;
        List<Dx3Station> entries = scan(receiver);
        Dx3Station fm = null, dab = null;
        for (Dx3Station station : entries) {
            if (!id.equalsIgnoreCase(station.sid())) continue;
            if (station.dab()) dab = station;
            else fm = station;
        }
        if (fm == null && dab == null) return;
        boolean useDab = switch (receiver.getLinkMode()) {
            case RadioItem.DX3_PREFER_DAB -> dab != null;
            case RadioItem.DX3_PREFER_FM -> fm == null && dab != null;
            case RadioItem.DX3_PREFER_QUALITY -> dab != null && (fm == null || qualityEligible(receiver, dab));
            default -> {
                if (fm == null) yield dab != null;
                if (dab == null) yield false;
                Sample current = sample(receiver);
                if (receiver.isDab()) yield current.snr() >= 9;
                yield current.snr() < 12;
            }
        };
        Dx3Station target = useDab ? dab : fm;
        if (target != null && (receiver.isDab() != target.dab()
                || target.dab() && receiver.getChannelIndex() != target.tuning()
                || !target.dab() && receiver.getFrequencyKhz() != target.tuning()))
            receiver.retune(target.dab(), target.tuning(), target.sid(), target.mux());
    }

    private static boolean qualityEligible(Dx3ReceiverBlockEntity receiver, Dx3Station station) {
        Level level = receiver.getLevel();
        if (level == null) return false;
        for (DabSfnReception.Group group : DabSfnReception.scan(level, receiver.receptionPoint(),
                SignalMath.countOverheadBlocks(level, receiver.receptionPoint()), 0, false,
                receiver.getAntennaPos(), receiver.getBlockPos())) {
            if (!group.ensemble().name().equals(station.mux())) continue;
            for (DabServerBlockEntity.DabStationDefinition service : group.ensemble().stations()) {
                if (service.sid().equalsIgnoreCase(station.sid()))
                    return service.bitrateKbps() > (group.transmitter().getDabVersion() == DabVersion.DAB_PLUS ? 72 : 128);
            }
        }
        return false;
    }

    private static double fmSignal(Dx3ReceiverBlockEntity dx3, TransmitterBlockEntity fm, Vec3 receiver, int overhead) {
        double signal = SignalMath.calculateSignalDbuv(fm.getPowerW(), fm.getFrequencyKhz(), fm.getFrequencyKhz(),
                fm.hasAntennaConnected(), fm.isDirectionalEnabled(), fm.getDirectionDegrees(),
                Vec3.atCenterOf(fm.getBlockPos()), receiver, overhead, false)
                + fm.antennaGainToward(receiver);
        signal = MeteorScatter.signal(fm.getLevel(), fm, receiver, fm.getFrequencyKhz(), signal);
        return signal + dx3.antennaGainToward(Vec3.atCenterOf(fm.getBlockPos()));
    }

    private static boolean canDecodeRds(TransmitterBlockEntity fm, double signal, double snr) {
        double quality = signal * 0.45 + snr * 0.55;
        int ber = (int) Math.round(Math.max(0, Math.min(99, 99 * Math.exp(-0.191 * (quality - 9.11)))));
        return fm.isRdsEnabled() && signal > -10 && snr > -5 && ber <= 98;
    }

    private static int bars(double signal) {
        return signal < -12 ? 0 : signal < 0 ? 1 : signal < 12 ? 2 : signal < 24 ? 3 : signal < 36 ? 4 : 5;
    }

    private static void put(Map<String, Ranked> entries, String key, Dx3Station station, double signal) {
        Ranked old = entries.get(key);
        if (old == null || signal > old.signal()) entries.put(key, new Ranked(station, signal));
    }

    private record Ranked(Dx3Station station, double signal) { }
    public record Sample(String url, String sourceUrl, double signal, double snr,
                         SyncDx3PlayerPayload info, int[] logo) {
        public static final Sample EMPTY = new Sample("", "", 0, 0,
                new SyncDx3PlayerPayload(0, false, "", "", "", 0, 0, false, "", 0, ""), null);
    }
}
