package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.network.Dx3Station;
import com.radiomod.item.RadioItem;
import com.radiomod.server.ServerAudioProxy;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class Dx3Catalog {
    private static final int MAX_STATIONS = 128;
    private static final double MIN_SIGNAL_DBUV = 5.0;
    private static final Map<Integer, int[]> LOGO_CACHE = new HashMap<>();

    private Dx3Catalog() {
    }

    public static List<Dx3Station> scan(Player player) {
        Level level = player.level();
        Vec3 position = player.position();
        int overhead = SignalMath.countOverheadBlocks(level, position);
        double noise = SignalMath.calculateNoiseFloorDbuv(position);
        Map<String, Candidate> best = new HashMap<>();
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm) {
                int frequency = fm.getFrequencyKhz();
                double signal = signal(fm.getPowerW(), frequency, fm.hasAntennaConnected(),
                        fm.isDirectionalEnabled(), fm.getDirectionDegrees(), pos, position, overhead,
                        player.isUnderWater()) + fm.antennaGainToward(position);
                signal = MeteorScatter.signal(level, fm, position, frequency, signal, player.isUnderWater());
                if (signal < MIN_SIGNAL_DBUV) {
                    continue;
                }
                double snr = SignalMath.calculateSnrDb(signal, noise);
                boolean rdsDecoded = canDecodeRds(fm, signal, snr);
                String pi = rdsDecoded ? fm.getRdsPi().trim().toUpperCase(Locale.ROOT) : "";
                String ps = rdsDecoded ? fm.getCurrentPsFrame(level.getGameTime()).trim() : "";
                String label = ps.isBlank() ? String.format(java.util.Locale.ROOT, "FM %.1f", frequency / 1000.0) : ps;
                String sortName = rdsDecoded ? firstPsFrame(fm) : "";
                if (sortName.isBlank()) sortName = label;
                String pty = rdsDecoded ? "PTY: " + PtyNames.name(fm.getRdsPty()) : "PTY unavailable";
                Dx3Station station = new Dx3Station(false, frequency, limit(pi, 16), "", limit(label, 96),
                        limit(sortName, 96), pty, null);
                putStrongest(best, pi.isBlank() ? "F:" + frequency : "FPI:" + pi, station, signal);
            }
        }
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
            DabTransmitterBlockEntity dab = group.transmitter();
            double strength = group.signalDbuv();
            if (strength < dab.getErrorCorrection().getMinimumSignalDbuv()
                    || group.snrDb() < dab.getErrorCorrection().getThresholdSnrDb() + 1.8) {
                continue;
            }
            int channel = DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz());
            String mux = limit(group.ensemble().name(), 64);
            for (DabServerBlockEntity.DabStationDefinition service : group.ensemble().stations()) {
                String sid = limit(service.sid(), 16);
                String label = service.label().isBlank() ? "Station " + sid : service.label();
                Dx3Station station = new Dx3Station(true, channel, sid, mux, limit(label, 96),
                        limit(label, 96), "PTY: " + PtyNames.name(service.pty()), motPixels(service.motPng()));
                putStrongest(best, sid.isBlank() ? "D:" + channel + ":" + mux
                        : "DSID:" + sid.toUpperCase(Locale.ROOT), station, strength);
            }
        }
        Map<String, int[]> dabLogos = new HashMap<>();
        for (Candidate candidate : best.values()) {
            Dx3Station station = candidate.station();
            if (station.dab() && !station.sid().isBlank() && station.logoPixels() != null) {
                dabLogos.put(station.sid().toUpperCase(Locale.ROOT), station.logoPixels());
            }
        }
        best.replaceAll((key, candidate) -> {
            Dx3Station station = candidate.station();
            int[] linkedLogo = station.dab() ? null : dabLogos.get(station.sid().toUpperCase(Locale.ROOT));
            return linkedLogo == null ? candidate : new Candidate(new Dx3Station(false, station.tuning(),
                    station.sid(), station.mux(), station.label(), station.sortName(), station.detail(), linkedLogo), candidate.signal());
        });
        List<Candidate> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingDouble(Candidate::signal).reversed());
        if (ranked.size() > MAX_STATIONS) {
            ranked = ranked.subList(0, MAX_STATIONS);
        }
        return ranked.stream().map(Candidate::station)
                .sorted(Comparator.comparing(Dx3Station::sortName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Dx3Station::dab).thenComparingInt(Dx3Station::tuning))
                .toList();
    }

    public static String firstPsFrame(TransmitterBlockEntity transmitter) {
        String ps = transmitter.getRdsPs();
        return ps == null ? "" : ps.substring(0, Math.min(8, ps.length())).trim();
    }

    public static DabPlayback findDabPlayback(Player player) {
        String sid = com.radiomod.item.RadioItem.getDabStationSid(player);
        String mux = com.radiomod.item.RadioItem.getDabStationMux(player);
        if (sid.isBlank() || mux.isBlank()) {
            return new DabPlayback("", "", 0, 0);
        }
        Level level = player.level();
        Vec3 position = player.position();
        int overhead = SignalMath.countOverheadBlocks(level, position);
        int channel = DabChannel.clampIndex(com.radiomod.item.RadioItem.getDabChannelIndex(player));
        DabPlayback best = new DabPlayback("", "", 0, 0);
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
            DabTransmitterBlockEntity transmitter = group.transmitter();
            if (DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz()) != channel) {
                continue;
            }
            DabServerBlockEntity.DabEnsembleDefinition ensemble = group.ensemble();
            if (ensemble == null || !ensemble.name().equals(mux)) {
                continue;
            }
            double strength = group.signalDbuv();
            double snr = group.snrDb();
            if (strength < transmitter.getErrorCorrection().getMinimumSignalDbuv()
                    || snr < transmitter.getErrorCorrection().getThresholdSnrDb() + 1.8) {
                continue;
            }
            for (DabServerBlockEntity.DabStationDefinition station : ensemble.stations()) {
                if (station.sid().equalsIgnoreCase(sid) && !station.streamUrl().isBlank()
                        && (best.streamUrl().isBlank() || strength > best.signalDbuv())) {
                    best = new DabPlayback(ServerAudioProxy.buildDabProxyToken(station.streamUrl(),
                            transmitter.getDabVersion() == DabVersion.DAB_PLUS, station.bitrateKbps()),
                            station.streamUrl(), strength, snr);
                }
            }
        }
        return best;
    }

    /** Follow a selected PI/SID across FM frequencies and DAB ensembles. */
    public static void autoLink(Player player) {
        if (!RadioItem.isDx3Selected(player)) {
            return;
        }
        String id = RadioItem.getDx3LinkId(player);
        if (id.isBlank()) {
            return;
        }
        Level level = player.level();
        Vec3 position = player.position();
        int overhead = SignalMath.countOverheadBlocks(level, position);
        double noise = SignalMath.calculateNoiseFloorDbuv(position);
        FmLink bestFm = null;
        FmLink currentFm = null;
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm)
                    || !fm.isRdsEnabled() || !id.equalsIgnoreCase(fm.getRdsPi())
                    || fm.getStreamUrl().isBlank()) {
                continue;
            }
            double strength = signal(fm.getPowerW(), fm.getFrequencyKhz(), fm.hasAntennaConnected(),
                    fm.isDirectionalEnabled(), fm.getDirectionDegrees(), pos, position, overhead,
                    player.isUnderWater()) + fm.antennaGainToward(position);
            strength = MeteorScatter.signal(level, fm, position, fm.getFrequencyKhz(), strength, player.isUnderWater());
            if (strength < MIN_SIGNAL_DBUV) {
                continue;
            }
            FmLink candidate = new FmLink(fm.getFrequencyKhz(), strength,
                    SignalMath.calculateSnrDb(strength, noise));
            if (bestFm == null || strength > bestFm.signalDbuv()) {
                bestFm = candidate;
            }
            if (fm.getFrequencyKhz() == RadioItem.getFrequencyKhz(player)
                    && (currentFm == null || strength > currentFm.signalDbuv())) {
                currentFm = candidate;
            }
        }
        DabLink bestDab = null;
        DabLink qualityDab = null;
        DabLink currentDab = null;
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
            DabTransmitterBlockEntity transmitter = group.transmitter();
            double strength = group.signalDbuv();
            double threshold = transmitter.getErrorCorrection().getThresholdSnrDb();
            if (strength < transmitter.getErrorCorrection().getMinimumSignalDbuv()
                    || group.snrDb() < threshold + 1.8) {
                continue;
            }
            int channel = DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz());
            for (DabServerBlockEntity.DabStationDefinition service : group.ensemble().stations()) {
                if (!id.equalsIgnoreCase(service.sid()) || service.streamUrl().isBlank()) {
                    continue;
                }
                DabLink candidate = new DabLink(channel, group.ensemble().name(), service.sid(),
                        strength, group.snrDb(), threshold, service.bitrateKbps(),
                        transmitter.getDabVersion() == DabVersion.DAB_PLUS);
                if (bestDab == null || strength > bestDab.signalDbuv()) {
                    bestDab = candidate;
                }
                if (candidate.qualityEligible() && (qualityDab == null || strength > qualityDab.signalDbuv())) {
                    qualityDab = candidate;
                }
                if (channel == RadioItem.getDabChannelIndex(player)
                        && candidate.mux().equals(RadioItem.getDabStationMux(player))
                        && (currentDab == null || strength > currentDab.signalDbuv())) {
                    currentDab = candidate;
                }
            }
        }
        FmLink fm = currentFm != null && bestFm != null
                && bestFm.signalDbuv() <= currentFm.signalDbuv() + 1.5 ? currentFm : bestFm;
        int preference = RadioItem.getDx3LinkMode(player);
        DabLink preferredDab = preference == RadioItem.DX3_PREFER_QUALITY && fm != null
                ? qualityDab : bestDab;
        DabLink dab = currentDab != null && preferredDab != null
                && (preference != RadioItem.DX3_PREFER_QUALITY || currentDab.qualityEligible())
                && preferredDab.signalDbuv() <= currentDab.signalDbuv() + 1.5
                ? currentDab : preferredDab;
        boolean useDab = switch (preference) {
            case RadioItem.DX3_PREFER_DAB -> dab != null;
            case RadioItem.DX3_PREFER_FM -> fm == null && dab != null;
            case RadioItem.DX3_PREFER_QUALITY -> dab != null || fm == null && bestDab != null;
            default -> {
                if (fm == null) {
                    yield bestDab != null;
                }
                if (dab == null) {
                    yield false;
                }
                if (RadioItem.getMode(player) == RadioItem.MODE_DAB) {
                    yield !(dab.snrDb() < dab.thresholdSnrDb() + 3.0 && fm.snrDb() >= 16.0);
                }
                yield fm.snrDb() < 12.0 && dab.snrDb() >= dab.thresholdSnrDb() + 5.0;
            }
        };
        if (useDab) {
            if (dab == null) {
                dab = bestDab;
            }
            if (dab != null) {
                RadioItem.setMode(player, RadioItem.MODE_DAB);
                RadioItem.setDabChannelIndex(player, dab.channel());
                RadioItem.setDabStation(player, dab.mux(), dab.sid());
            }
        } else if (fm != null) {
            RadioItem.setMode(player, RadioItem.MODE_FM);
            RadioItem.setFrequencyKhz(player, fm.frequencyKhz());
            RadioItem.setDx3FmPi(player, id);
        }
    }

    public static PlayerInfo playerInfo(Player player) {
        if (!RadioItem.isDx3Selected(player)) {
            RdsTextDecoding.clearPlayer(player);
            return PlayerInfo.EMPTY;
        }
        Level level = player.level();
        Vec3 position = player.position();
        int overhead = SignalMath.countOverheadBlocks(level, position);
        if (RadioItem.getMode(player) == RadioItem.MODE_FM) {
            int tuned = RadioItem.getFrequencyKhz(player);
            TransmitterBlockEntity best = null;
            double bestSignal = Double.NEGATIVE_INFINITY;
            for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
                if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm && fm.getFrequencyKhz() == tuned) {
                    double strength = signal(fm.getPowerW(), tuned, fm.hasAntennaConnected(),
                            fm.isDirectionalEnabled(), fm.getDirectionDegrees(), pos, position, overhead,
                            player.isUnderWater()) + fm.antennaGainToward(position);
                    strength = MeteorScatter.signal(level, fm, position, tuned, strength, player.isUnderWater());
                    if (strength >= MIN_SIGNAL_DBUV && strength > bestSignal) {
                        best = fm;
                        bestSignal = strength;
                    }
                }
            }
            if (best == null) {
                RdsTextDecoding.clearPlayer(player);
                return PlayerInfo.EMPTY;
            }
            double snr = SignalMath.calculateSnrDb(bestSignal, SignalMath.calculateNoiseFloorDbuv(position));
            boolean decode = canDecodeRds(best, bestSignal, snr);
            String ps = decode ? best.getCurrentPsFrame(level.getGameTime()).trim() : "";
            String longPs = decode && best.isRdsGroupEnabled(28) ? best.getRdsLongPs().trim() : "";
            String rt = decode && best.isRdsGroupEnabled(4) ? best.getCurrentRtText(level.getGameTime()).trim() : "";
            if (decode) {
                RdsTextDecoding.Text text = RdsTextDecoding.forPlayer(player,
                        "F:" + best.getBlockPos().asLong() + ":" + tuned, ps, longPs, rt,
                        rdsBerPercent(bestSignal, snr), level.getGameTime());
                ps = text.ps(); longPs = text.longPs(); rt = text.rt();
            } else RdsTextDecoding.clearPlayer(player);
            boolean ctBroadcast = decode && best.isRdsCtEnabled() && best.isRdsGroupEnabled(8);
            RdsFrameData frame = ctBroadcast ? best.getCurrentRdsFrame(
                    rdsBerPercent(bestSignal, snr), level.getGameTime(), "") : null;
            boolean ctFrame = frame != null && "4A".equals(frame.groupCode());
            return new PlayerInfo(true, ps, longPs, limit(rt, 256), decode ? best.getRdsPty() : 0,
                    bars(bestSignal), ctBroadcast, ctFrame, "F:" + best.getBlockPos().asLong() + ":" + tuned,
                    0, "", decode);
        }
        RdsTextDecoding.clearPlayer(player);
        int channel = DabChannel.clampIndex(RadioItem.getDabChannelIndex(player));
        String sid = RadioItem.getDabStationSid(player);
        String mux = RadioItem.getDabStationMux(player);
        DabServerBlockEntity.DabStationDefinition best = null;
        DabVersion bestVersion = null;
        double bestSignal = Double.NEGATIVE_INFINITY;
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
            DabTransmitterBlockEntity dab = group.transmitter();
            if (DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz()) != channel) {
                continue;
            }
            DabServerBlockEntity.DabEnsembleDefinition ensemble = group.ensemble();
            if (ensemble == null || !ensemble.name().equals(mux)) {
                continue;
            }
            double strength = group.signalDbuv();
            if (strength < dab.getErrorCorrection().getMinimumSignalDbuv()
                    || group.snrDb() < dab.getErrorCorrection().getThresholdSnrDb() + 1.8
                    || strength <= bestSignal) {
                continue;
            }
            for (DabServerBlockEntity.DabStationDefinition service : ensemble.stations()) {
                if (service.sid().equalsIgnoreCase(sid)) {
                    best = service;
                    bestVersion = dab.getDabVersion();
                    bestSignal = strength;
                }
            }
        }
        return best == null ? PlayerInfo.EMPTY : new PlayerInfo(true, limit(best.label(), 96), "",
                limit(DabDlsResolver.text(best), 256), best.pty(), bars(bestSignal), false, false,
                "D:" + channel + ":" + mux + ":" + sid, best.bitrateKbps(),
                bestVersion.getLabel() + " / " + bestVersion.getCodecLabel(), false);
    }

    public static int[] playerLogo(Player player) {
        if (!RadioItem.isDx3Selected(player) || RadioItem.getMode(player) != RadioItem.MODE_DAB) {
            return null;
        }
        Level level = player.level();
        Vec3 position = player.position();
        int overhead = SignalMath.countOverheadBlocks(level, position);
        int channel = DabChannel.clampIndex(RadioItem.getDabChannelIndex(player));
        String sid = RadioItem.getDabStationSid(player);
        String mux = RadioItem.getDabStationMux(player);
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead, 0)) {
            DabTransmitterBlockEntity dab = group.transmitter();
            if (DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz()) != channel
                    || group.signalDbuv() < dab.getErrorCorrection().getMinimumSignalDbuv()
                    || group.snrDb() < dab.getErrorCorrection().getThresholdSnrDb() + 1.8
                    || !group.ensemble().name().equals(mux)) {
                continue;
            }
            for (DabServerBlockEntity.DabStationDefinition service : group.ensemble().stations()) {
                if (service.sid().equalsIgnoreCase(sid)) {
                    return motPixels(service.motPng(), 64);
                }
            }
        }
        return null;
    }

    private static int bars(double signalDbuv) {
        return signalDbuv < -12 ? 0 : signalDbuv < 0 ? 1 : signalDbuv < 12 ? 2
                : signalDbuv < 24 ? 3 : signalDbuv < 36 ? 4 : 5;
    }

    private static boolean canDecodeRds(TransmitterBlockEntity transmitter, double signalDbuv, double snrDb) {
        if (!transmitter.isRdsEnabled() || signalDbuv <= -10.0 || snrDb <= -5.0) {
            return false;
        }
        return rdsBerPercent(signalDbuv, snrDb) <= 98;
    }

    public static int rdsBerPercent(double signalDbuv, double snrDb) {
        double quality = signalDbuv * 0.45 + snrDb * 0.55;
        return (int) Math.round(Math.max(0.0,
                Math.min(99.0, 99.0 * Math.exp(-0.191 * (quality - 9.11)))));
    }

    private static double signal(int power, int frequency, boolean antenna, boolean directional,
                                 int direction, BlockPos pos, Vec3 player, int overhead, boolean underwater) {
        return SignalMath.calculateSignalDbuv(power, frequency, frequency, antenna, directional,
                direction, Vec3.atCenterOf(pos), player, overhead, underwater);
    }

    private static void putStrongest(Map<String, Candidate> entries, String key, Dx3Station station, double signal) {
        Candidate old = entries.get(key);
        if (old == null || signal > old.signal()) {
            entries.put(key, new Candidate(station, signal));
        }
    }

    public static String limit(String value, int maxBytes) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            String next = new String(Character.toChars(point));
            if ((result.toString() + next).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                break;
            }
            result.append(next);
            offset += Character.charCount(point);
        }
        return result.toString();
    }

    private static int[] motPixels(byte[] png) {
        return motPixels(png, 16);
    }

    public static int[] motPixels(byte[] png, int size) {
        if (png == null || png.length == 0 || png.length > 1_000_000) {
            return null;
        }
        int key = 31 * Arrays.hashCode(png) + size;
        synchronized (LOGO_CACHE) {
            int[] cached = LOGO_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null || source.getWidth() > 1024 || source.getHeight() > 1024) {
                return null;
            }
            BufferedImage thumbnail = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = thumbnail.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, size, size, null);
            graphics.dispose();
            int[] pixels = thumbnail.getRGB(0, 0, size, size, null, 0, size);
            synchronized (LOGO_CACHE) {
                if (LOGO_CACHE.size() >= 256) {
                    LOGO_CACHE.clear();
                }
                LOGO_CACHE.put(key, pixels);
            }
            return pixels;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record Candidate(Dx3Station station, double signal) {
    }

    private record FmLink(int frequencyKhz, double signalDbuv, double snrDb) {
    }

    private record DabLink(int channel, String mux, String sid, double signalDbuv, double snrDb,
                           double thresholdSnrDb, int bitrateKbps, boolean dabPlus) {
        boolean qualityEligible() {
            return bitrateKbps > (dabPlus ? 72 : 128);
        }
    }

    public record DabPlayback(String streamUrl, String sourceUrl, double signalDbuv, double snrDb) {
    }

    public record PlayerInfo(boolean active, String ps, String longPs, String rt, int pty,
                             int bars, boolean ctBroadcast, boolean ctFrame, String source,
                             int bitrateKbps, String codec, boolean rds) {
        public static final PlayerInfo EMPTY = new PlayerInfo(false, "", "", "", 0, 0, false, false, "", 0, "", false);
    }
}
