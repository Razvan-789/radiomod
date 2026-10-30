package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.server.ServerAudioProxy;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Samples RF at the server's antenna and decodes FM identification over time. */
public final class WebserverReception {
    private static final Map<WebserverBlockEntity, PiDecoder> PI_DECODERS = new WeakHashMap<>();
    private static final Map<WebserverBlockEntity, AfDecoder> AF_DECODERS = new WeakHashMap<>();
    private WebserverReception() { }

    public record Sample(boolean signal, double signalDbuv, double snrDb, int berPercent,
                         String ps, String pi, String country, String rt, boolean identified,
                         String stationName, int txX, int txY, int txZ, int powerW,
                         int azimuthDegrees, int distanceMeters, int[] logo,
                         String streamUrl, String sourceUrl, List<Integer> afFrequenciesKhz) {
        public static final Sample EMPTY = new Sample(false, -100, -20, 100,
                "", "", "", "", false, "", 0, 0, 0, 0, 0, 0, null, "", "", List.of());
    }

    public static void clear(WebserverBlockEntity webserver) {
        PI_DECODERS.remove(webserver);
        AF_DECODERS.remove(webserver);
        RdsTextDecoding.clearWebserver(webserver);
    }

    public static Sample sample(WebserverBlockEntity webserver) {
        Level level = webserver.getLevel();
        if (level == null) return Sample.EMPTY;
        BlockPos receivePos = webserver.receivePos();
        Vec3 receive = Vec3.atCenterOf(receivePos);
        double noiseFloor = SignalMath.calculateNoiseFloorDbuv(receive);
        double drift = slowSignalDrift(level.getGameTime(), webserver.getBlockPos());
        boolean underwater = level.getFluidState(receivePos).is(FluidTags.WATER);
        if (webserver.drm()) {
            clear(webserver);
            DrmTransmitterBlockEntity best = null;
            double bestSignal = -100;
            for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
                if (!(level.getBlockEntity(pos) instanceof DrmTransmitterBlockEntity tx)) continue;
                double signal = DrmSignal.signal(level, tx, receivePos, webserver.frequencyKhz(), underwater)
                        + SignalMath.linkedAntennaGainDb(level, webserver.antennaPos(), webserver.getBlockPos(),
                                Vec3.atCenterOf(pos)) - (webserver.hasAntennaConnected() ? 0 : 22);
                if (signal > bestSignal) { bestSignal = signal; best = tx; }
            }
            if (best != null) bestSignal += drift;
            double snr = Math.max(-20, Math.min(40, (bestSignal - noiseFloor) * .48));
            if (best == null || bestSignal < 5 || snr < best.getMinimumSnrDb())
                return new Sample(false, noiseFloor, 0, 100, "", "", "", "", false,
                        "", 0, 0, 0, 0, 0, 0, null, "", "", List.of());
            return new Sample(true, bestSignal, snr, 100,
                    best.getShortLabel().isBlank() ? best.getServiceLabel() : best.getShortLabel(),
                    "", "", best.getTextMessage(), false, "", 0, 0, 0, 0, 0, 0, null,
                    ServerAudioProxy.buildOpusEquivalentMp3ProxyToken(best.getStreamUrl(), best.getOpusBitrateKbps()),
                    best.getStreamUrl(), List.of());
        }

        TransmitterBlockEntity best = null;
        double bestSignal = -100;
        int tuned = webserver.frequencyKhz();
        int overhead = SignalMath.countOverheadBlocks(level, receive);
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity tx)
                    || tx.getFrequencyKhz() != tuned) continue;
            double signal = SignalMath.calculateSignalDbuv(tx.getPowerW(), tx.getFrequencyKhz(), tuned,
                    tx.hasAntennaConnected(), tx.isDirectionalEnabled(), tx.getDirectionDegrees(),
                    Vec3.atCenterOf(pos), receive, overhead, underwater)
                    + tx.antennaGainToward(receive)
                    + SignalMath.linkedAntennaGainDb(level, webserver.antennaPos(), webserver.getBlockPos(),
                            Vec3.atCenterOf(pos))
                    - (webserver.hasAntennaConnected() ? 0 : 22);
            if (signal > bestSignal) { bestSignal = signal; best = tx; }
        }
        if (best != null) bestSignal += drift;
        double snr = SignalMath.calculateSnrDb(bestSignal, noiseFloor);
        if (best == null || bestSignal < 5) {
            clear(webserver);
            return new Sample(false, noiseFloor, 0, 100, "", "", "", "", false,
                    "", 0, 0, 0, 0, 0, 0, null, "", "", List.of());
        }
        int ber = best.isRdsEnabled() ? Dx3Catalog.rdsBerPercent(bestSignal, snr) : 100;
        boolean rds = best.isRdsEnabled() && bestSignal > -10 && snr > -5 && ber <= 98;
        String ps = "", pi = "", rt = "", country = "";
        boolean identified = false;
        int[] logo = null;
        List<Integer> af = List.of();
        if (rds) {
            String source = best.getBlockPos().asLong() + ":" + tuned + ":" + best.getRdsPi();
            RdsTextDecoding.Text text = RdsTextDecoding.forWebserver(webserver, source,
                    best.getCurrentPsFrame(level.getGameTime()),
                    best.isRdsGroupEnabled(4) ? best.getCurrentRtText(level.getGameTime()) : "",
                    ber, level.getGameTime());
            ps = text.ps(); rt = text.rt();
            PiDecoder decoder = PI_DECODERS.computeIfAbsent(webserver, ignored -> new PiDecoder());
            pi = decoder.decode(source, best.getRdsPi(), ber);
            af = AF_DECODERS.computeIfAbsent(webserver, ignored -> new AfDecoder())
                    .decode(source, best.isRdsGroupEnabled(0) ? best.getRdsAf() : "", ber);
            identified = !pi.isBlank() && ps.chars().anyMatch(ch -> ch != ' ' && ch != '?');
            if (identified) {
                if (level instanceof ServerLevel serverLevel)
                    country = RegistryCountries.get(serverLevel.getServer()).countryFor(pi);
                logo = logoForPi(level, pi);
            }
        } else clear(webserver);
        BlockPos txPos = best.getBlockPos();
        double dx = txPos.getX() - receivePos.getX(), dz = txPos.getZ() - receivePos.getZ();
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
        int distance = (int) Math.round(receive.distanceTo(Vec3.atCenterOf(txPos)));
        return new Sample(true, bestSignal, snr, ber, ps, pi, country, rt, identified,
                identified ? Dx3Catalog.firstPsFrame(best) : "",
                identified ? txPos.getX() : 0, identified ? txPos.getY() : 0,
                identified ? txPos.getZ() : 0, identified ? best.getPowerW() : 0,
                identified ? bearing : 0, identified ? distance : 0, logo,
                ServerAudioProxy.buildProxyToken(best.getStreamUrl()), best.getStreamUrl(), af);
    }

    /** Smooth, position-specific variation shared by every viewer of this webserver. */
    static double slowSignalDrift(long gameTime, BlockPos webserverPos) {
        double phase = (webserverPos.asLong() & 0xFFFFL) * 0.00013;
        return 1.4 * Math.sin(gameTime / 90.0 + phase)
                + 0.6 * Math.sin(gameTime / 167.0 + phase * 1.7);
    }

    private static int[] logoForPi(Level level, String pi) {
        for (BlockPos pos : DabServerNetwork.getServers(level)) {
            if (!(level.getBlockEntity(pos) instanceof DabServerBlockEntity server)) continue;
            for (int mux = 0; mux < server.getEnsembleCount(); mux++) {
                for (DabServerBlockEntity.DabStationDefinition station : server.getEnsemble(mux).stations()) {
                    if (station.sid().equalsIgnoreCase(pi)) {
                        int[] pixels = Dx3Catalog.motPixels(station.motPng(), 64);
                        if (pixels != null) return pixels;
                    }
                }
            }
        }
        return null;
    }

    private static final class PiDecoder {
        private final Random random = new Random();
        private String source = "", decoded = "";
        private String decode(String key, String target, int ber) {
            if (!source.equals(key)) { source = key; decoded = ""; }
            if (!target.matches("(?i)[0-9a-f]{4}")) return "";
            double quality = Math.max(.02, (100.0 - ber) / 100.0);
            if (decoded.isBlank() && random.nextDouble() < 1.0 - Math.pow(1.0 - quality, 4))
                decoded = target.toUpperCase(Locale.ROOT);
            return decoded;
        }
    }

    /** Group 0A AF entries accumulate as the receiver successfully decodes frames. */
    private static final class AfDecoder {
        private static final Pattern TOKEN = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");
        private final Random random = new Random();
        private final TreeSet<Integer> decoded = new TreeSet<>();
        private String source = "";
        private int frame;

        private List<Integer> decode(String stationKey, String raw, int ber) {
            String key = stationKey + ":" + raw;
            if (!source.equals(key)) { source = key; decoded.clear(); frame = 0; }
            if (raw.isBlank()) return List.of();
            TreeSet<Integer> broadcast = new TreeSet<>();
            Matcher matcher = TOKEN.matcher(raw);
            while (matcher.find()) {
                String token = matcher.group();
                try {
                    int khz;
                    if (token.contains(".") || token.contains(","))
                        khz = (int) Math.round(Double.parseDouble(token.replace(',', '.')) * 1000);
                    else {
                        int number = Integer.parseInt(token);
                        khz = number >= 65_000 ? number : number >= 650 ? number * 100 : number * 1000;
                    }
                    if (khz >= 65_000 && khz <= 108_000) broadcast.add(khz);
                } catch (NumberFormatException ignored) { }
            }
            if (!broadcast.isEmpty() && frame++ % 2 == 0 && random.nextDouble() < Math.max(.02, (100.0 - ber) / 100.0)) {
                List<Integer> remaining = broadcast.stream().filter(value -> !decoded.contains(value)).toList();
                if (!remaining.isEmpty()) decoded.add(remaining.get(random.nextInt(remaining.size())));
            }
            return List.copyOf(decoded);
        }
    }
}
