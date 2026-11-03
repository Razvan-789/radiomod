package com.radiomod.client;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.client.screen.SpectrumAnalyserScreen;
import com.radiomod.item.RadioItem;
import com.radiomod.item.PortableDrmRadioItem;
import com.radiomod.item.TechnisatRadioItem;
import com.radiomod.signal.PortableDrmState;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.DabSfnReception;
import com.radiomod.signal.DabPlaybackState;
import com.radiomod.signal.Dx3PlaybackState;
import com.radiomod.signal.FmReceptionState;
import com.radiomod.signal.DabVersion;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.SignalNetwork;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class FmAudioManager {
    private static final FmAudioManager INSTANCE = new FmAudioManager();
    private static final AudioFormat PCM_FORMAT = new AudioFormat(44_100.0f, 16, 2, true, false);
    private static final int PCM_QUEUE_SIZE = 512;
    private static final double STREAM_GAIN_ATTACK_SMOOTHING = 0.08;
    private static final double STREAM_GAIN_RELEASE_SMOOTHING = 0.35;
    private static final double NOISE_GAIN_ATTACK_SMOOTHING = 0.18;
    private static final double NOISE_GAIN_RELEASE_SMOOTHING = 0.22;
    private static final long SIGNAL_LOSS_GRACE_MS = 250L;
    private static final long DAB_DECODER_LOCK_MS = 1_500L;
    private static final int STREAM_PREBUFFER_CHUNKS_START = 8;
    private static final int STREAM_PREBUFFER_CHUNKS_RESUME = 3;
    private static final double STREAM_SAMPLE_SMOOTHING = 0.15;

    private static final double STRONG_SNR_DB = 24.0;
    private static final double HIGH_QUALITY_SNR_DB = 16.0;
    private static final double DISTURBANCE_START_SNR_DB = 8.0;
    private static final double DISTURBANCE_FULL_SNR_DB = 4.0;
    private static final double FULL_STATIC_SNR_DB = -2.0;
    private static final double MIN_AUDIBLE_SIGNAL_DBUV = -12.0;
    private static final double STATIC_BEGIN_SIGNAL_DBUV = 20.0;
    private static final double STATIC_DISTURB_SIGNAL_DBUV = 5.0;

    private final ArrayBlockingQueue<byte[]> streamPcmQueue = new ArrayBlockingQueue<>(PCM_QUEUE_SIZE);
    private volatile double streamGain;
    private volatile double noiseGain;
    private boolean initialized;
    private boolean failed;
    private String activeStreamUrl = "";
    private long lastValidSignalMs;
    private String pendingDabStation = "";
    private long dabReadyAtMs;
    private volatile boolean streamRestarted;
    private final Object decoderLock = new Object();
    @Nullable
    private StreamDecoderThread decoderThread;
    @Nullable
    private Thread mixerThread;

    private FmAudioManager() {
    }

    public static FmAudioManager get() {
        return INSTANCE;
    }

    public void tick(Minecraft minecraft) {
        if (failed) {
            return;
        }
        ensureInitialized();
        if (!initialized) {
            return;
        }

        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            pendingDabStation = "";
            stopStreamDecoder();
            setMix(0.0, 0.0);
            return;
        }
        if (minecraft.screen instanceof SpectrumAnalyserScreen
                || SpectrumAnalyserAudio.ownsBackgroundAudio(minecraft)) {
            pendingDabStation = "";
            stopStreamDecoder();
            setMix(0.0, 0.0);
            return;
        }
        if (RadioItem.isTechnisatSelected(player) && !TechnisatRadioItem.hasInInventory(player))
            RadioItem.setTechnisatSelected(player, false);

        boolean portableDrm = PortableDrmRadioItem.isSelected(player) && PortableDrmRadioItem.hasInInventory(player);
        boolean technisatDrm = RadioItem.isTechnisatSelected(player)
                && TechnisatRadioItem.hasInInventory(player)
                && RadioItem.getMode(player) == RadioItem.MODE_DRM;
        if ((!portableDrm && !hasRadioInInventory(player))
                || (portableDrm ? PortableDrmRadioItem.muted(player) : RadioItem.isMuted(player))) {
            pendingDabStation = "";
            stopStreamDecoder();
            setMix(0.0, 0.0);
            return;
        }

        int mode = RadioItem.getMode(player);
        TransmitterSample sample;
        boolean perfectUntilFail = false;
        if (portableDrm || technisatDrm) {
            var reception = PortableDrmState.fresh();
            sample = reception != null && reception.active()
                    && reception.frequencyKhz() == (portableDrm ? PortableDrmRadioItem.frequency(player)
                            : TechnisatRadioItem.drmFrequency(player))
                    ? new TransmitterSample(reception.signalTenths() / 10.0, reception.snrTenths() / 10.0,
                    reception.streamUrl(), reception.sourceUrl()) : null;
            perfectUntilFail = true;
        } else if (mode == RadioItem.MODE_DAB) {
            if (hasDx3InInventory(player) && RadioItem.isDx3Selected(player)
                    && !RadioItem.getDabStationSid(player).isBlank()) {
                Dx3PlaybackState.Snapshot reception = Dx3PlaybackState.getFresh(player);
                sample = reception == null ? null : new TransmitterSample(
                        reception.signalDbuv(), reception.snrDb(), reception.streamUrl(), reception.sourceUrl());
            } else if (RadioItem.isTechnisatSelected(player)) {
                // Technisat DAB needs a service selected from its explicit scan list.
                sample = null;
            } else {
                DabPlaybackState.Snapshot reception = DabPlaybackState.getFresh(player);
                if (reception != null) {
                    sample = new TransmitterSample(reception.signalDbuv(), reception.snrDb(),
                            reception.streamUrl(), reception.sourceUrl());
                } else {
                    int dabIndex = DabChannel.clampIndex(RadioItem.getDabChannelIndex(player));
                    sample = findStrongestDab(player, DabChannel.byIndex(dabIndex).frequencyKhz());
                }
            }
            perfectUntilFail = true;
        } else {
            pendingDabStation = "";
            int tunedFrequencyKhz = SignalMath.clampFmFrequencyKhz(RadioItem.getFrequencyKhz(player));
            sample = findServerOrLocalFm(level, player.position(), tunedFrequencyKhz, player.isUnderWater());
        }
        if (sample == null || sample.streamUrl == null || sample.streamUrl.isBlank()) {
            if (perfectUntilFail) {
                pendingDabStation = "";
                stopStreamDecoder();
            }
            long now = System.currentTimeMillis();
            if (activeStreamUrl.isBlank() || (now - lastValidSignalMs) >= SIGNAL_LOSS_GRACE_MS) {
                stopStreamDecoder();
            }
            setMix(0.0, perfectUntilFail ? 0.0 : 0.34);
            return;
        }
        lastValidSignalMs = System.currentTimeMillis();

        String normalizedUrl = sample.streamUrl.trim();
        normalizedUrl = resolvePlayableUrl(minecraft, normalizedUrl);
        if (normalizedUrl.startsWith("radio-file://")) {
            if (perfectUntilFail) pendingDabStation = "";
            stopStreamDecoder();
            setMix(0.0, perfectUntilFail ? 0.0 : 0.34);
            return;
        }
        if (perfectUntilFail) {
            String station = portableDrm || technisatDrm ? "drm:"
                    + (portableDrm ? PortableDrmRadioItem.frequency(player)
                    : TechnisatRadioItem.drmFrequency(player)) + ":" + normalizedUrl
                    : RadioItem.getDabChannelIndex(player) + ":"
                    + RadioItem.getDabStationSid(player) + ":" + normalizedUrl;
            long now = System.currentTimeMillis();
            if (!station.equals(pendingDabStation)) {
                pendingDabStation = station;
                dabReadyAtMs = now + DAB_DECODER_LOCK_MS;
                stopStreamDecoder();
            }
            if (now < dabReadyAtMs) {
                setMix(0.0, 0.0);
                return;
            }
        }
        if (!normalizedUrl.equals(activeStreamUrl)) {
            startStreamDecoder(normalizedUrl, sample.fallbackUrl);
        }

        double effectiveSnrDb = sample.snrDb;
        if (perfectUntilFail) {
            setMix(1.0, 0.0);
            return;
        }
        double targetStreamGain;
        double targetNoiseGain;
        if (effectiveSnrDb >= STRONG_SNR_DB) {
            targetStreamGain = 1.0;
            targetNoiseGain = 0.015;
        } else if (effectiveSnrDb >= HIGH_QUALITY_SNR_DB) {
            double fade = Mth.clamp((STRONG_SNR_DB - effectiveSnrDb) / (STRONG_SNR_DB - HIGH_QUALITY_SNR_DB), 0.0, 1.0);
            targetStreamGain = Mth.lerp(fade, 1.0, 0.88);
            targetNoiseGain = Mth.lerp(fade, 0.015, 0.04);
        } else if (effectiveSnrDb >= DISTURBANCE_START_SNR_DB) {
            double fade = Mth.clamp((HIGH_QUALITY_SNR_DB - effectiveSnrDb) / (HIGH_QUALITY_SNR_DB - DISTURBANCE_START_SNR_DB), 0.0, 1.0);
            targetStreamGain = Mth.lerp(fade, 0.88, 0.58);
            targetNoiseGain = Mth.lerp(fade, 0.04, 0.12);
        } else if (effectiveSnrDb >= DISTURBANCE_FULL_SNR_DB) {
            double fade = Mth.clamp((DISTURBANCE_START_SNR_DB - effectiveSnrDb) / (DISTURBANCE_START_SNR_DB - DISTURBANCE_FULL_SNR_DB), 0.0, 1.0);
            targetStreamGain = Mth.lerp(fade, 0.58, 0.24);
            targetNoiseGain = Mth.lerp(fade, 0.12, 0.34);
        } else {
            double fade = Mth.clamp((DISTURBANCE_FULL_SNR_DB - effectiveSnrDb) / (DISTURBANCE_FULL_SNR_DB - FULL_STATIC_SNR_DB), 0.0, 1.0);
            targetStreamGain = Mth.lerp(fade, 0.24, 0.0);
            targetNoiseGain = Mth.lerp(fade, 0.34, 0.68);
        }
        double signalStaticFade = Mth.clamp(
                (sample.signalDbuv - STATIC_DISTURB_SIGNAL_DBUV) / (STATIC_BEGIN_SIGNAL_DBUV - STATIC_DISTURB_SIGNAL_DBUV),
                0.0,
                1.0
        );
        targetStreamGain *= Mth.lerp(signalStaticFade, 0.18, 1.0);
        targetNoiseGain = Math.max(targetNoiseGain, Mth.lerp(signalStaticFade, 0.62, 0.08));

        if (sample.signalDbuv <= MIN_AUDIBLE_SIGNAL_DBUV) {
            targetStreamGain = 0.0;
            targetNoiseGain = Math.max(targetNoiseGain, 0.65);
        }
        setMix(targetStreamGain, targetNoiseGain);
    }

    private void ensureInitialized() {
        if (initialized || failed) {
            return;
        }
        try {
            mixerThread = new Thread(this::runMixer, "radio-fm-audio-mixer");
            mixerThread.setDaemon(true);
            mixerThread.start();
            initialized = true;
        } catch (Exception ex) {
            failed = true;
            Radio.LOGGER.error("FM audio init failed.", ex);
        }
    }

    private void startStreamDecoder(String streamUrl, String fallbackUrl) {
        stopStreamDecoder();
        activeStreamUrl = streamUrl;
        streamPcmQueue.clear();
        streamRestarted = true;
        StreamDecoderThread thread = new StreamDecoderThread(streamUrl, fallbackUrl, streamPcmQueue);
        thread.setDaemon(true);
        decoderThread = thread;
        thread.start();
    }

    private void stopStreamDecoder() {
        synchronized (decoderLock) {
            activeStreamUrl = "";
            streamPcmQueue.clear();
            if (decoderThread != null) {
                decoderThread.shutdown();
                decoderThread = null;
            }
        }
    }

    private void runMixer() {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(PCM_FORMAT)) {
            line.open(PCM_FORMAT);
            line.start();
            byte[] out = new byte[960 * 4];
            byte[] chunk = null;
            int chunkOffset = 0;
            int noiseState = 0x2468ACE1;
            short lastStreamSample = 0;
            double smoothedStreamSample = 0.0;
            int restartWarmupFrames = 0;
            boolean waitingForPrebuffer = false;

            while (!Thread.currentThread().isInterrupted()) {
                double stream = streamGain;
                double noise = noiseGain;
                if (streamRestarted) {
                    streamRestarted = false;
                    chunk = null;
                    chunkOffset = 0;
                    lastStreamSample = 0;
                    smoothedStreamSample = 0.0;
                    restartWarmupFrames = 2400;
                    waitingForPrebuffer = true;
                }
                if (stream <= 0.0 && noise <= 0.0) {
                    line.write(new byte[out.length], 0, out.length);
                    continue;
                }

                for (int i = 0; i < out.length; i += 2) {
                    short streamSample = 0;
                    if (stream > 0.0) {
                        if (restartWarmupFrames > 0) {
                            restartWarmupFrames--;
                        } else if (waitingForPrebuffer) {
                            if (streamPcmQueue.size() >= STREAM_PREBUFFER_CHUNKS_START) {
                                waitingForPrebuffer = false;
                            }
                        } else {
                            if (chunk == null || chunkOffset + 1 >= chunk.length) {
                                try {
                                    chunk = streamPcmQueue.poll(2, TimeUnit.MILLISECONDS);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                                chunkOffset = 0;
                            }
                            if (chunk != null && chunkOffset + 1 < chunk.length) {
                                int lo = chunk[chunkOffset] & 0xFF;
                                int hi = chunk[chunkOffset + 1];
                                streamSample = (short) ((hi << 8) | lo);
                                lastStreamSample = streamSample;
                                chunkOffset += 2;
                            } else {
                                waitingForPrebuffer = true;
                                if (streamPcmQueue.size() >= STREAM_PREBUFFER_CHUNKS_RESUME) {
                                    waitingForPrebuffer = false;
                                }
                                streamSample = lastStreamSample;
                            }
                        }
                    }

                    smoothedStreamSample += (streamSample - smoothedStreamSample) * STREAM_SAMPLE_SMOOTHING;
                    streamSample = (short) Math.round(smoothedStreamSample);

                    noiseState = (noiseState * 1103515245) + 12345;
                    int centered = ((noiseState >>> 16) & 0xFFFF) - 32768;
                    short noiseSample = (short) Math.round(centered * 0.026);

                    double effectiveNoise = noise * Math.sqrt(Math.max(0.0, noise));
                    int mixed = (int) Math.round((streamSample * stream) + (noiseSample * effectiveNoise));
                    mixed = Mth.clamp(mixed, Short.MIN_VALUE, Short.MAX_VALUE);
                    out[i] = (byte) (mixed & 0xFF);
                    out[i + 1] = (byte) ((mixed >> 8) & 0xFF);
                }
                line.write(out, 0, out.length);
            }
            line.stop();
            line.flush();
        } catch (LineUnavailableException ex) {
            failed = true;
            Radio.LOGGER.error("FM audio mixer stopped.", ex);
        }
    }

    private TransmitterSample findServerOrLocalFm(Level level, Vec3 radioPosition,
                                                   int tunedFrequencyKhz, boolean underwater) {
        FmReceptionState.Snapshot snapshot = FmReceptionState.getFresh();
        if (snapshot != null) {
            return new TransmitterSample(snapshot.signalDbuv(), snapshot.snrDb(), snapshot.streamUrl());
        }
        return findStrongestFm(level, radioPosition, tunedFrequencyKhz, underwater);
    }

    private TransmitterSample findStrongestFm(Level level, Vec3 radioPosition,
                                              int tunedFrequencyKhz, boolean underwater) {
        Set<BlockPos> transmitters = SignalNetwork.getTransmitters(level);
        double noiseFloorDbuv = SignalMath.calculateNoiseFloorDbuv(radioPosition);
        int overheadBlockCount = SignalMath.countOverheadBlocks(level, radioPosition);
        TransmitterSample bestWithStream = null;
        TransmitterSample bestAny = null;
        for (BlockPos pos : transmitters) {
            if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter)) {
                continue;
            }

            double signalDbuv = SignalMath.calculateSignalDbuv(
                    transmitter.getPowerW(),
                    transmitter.getFrequencyKhz(),
                    tunedFrequencyKhz,
                    transmitter.hasAntennaConnected(),
                    transmitter.isDirectionalEnabled(),
                    transmitter.getDirectionDegrees(),
                    Vec3.atCenterOf(pos),
                    radioPosition,
                    overheadBlockCount,
                    underwater
            ) + transmitter.antennaGainToward(radioPosition);
            double snrDb = SignalMath.calculateSnrDb(signalDbuv, noiseFloorDbuv);
            TransmitterSample sample = new TransmitterSample(signalDbuv, snrDb, transmitter.getStreamUrl());
            if (bestAny == null || signalDbuv > bestAny.signalDbuv) {
                bestAny = sample;
            }

            String streamUrl = transmitter.getStreamUrl();
            if (streamUrl != null && !streamUrl.isBlank()) {
                if (bestWithStream == null || signalDbuv > bestWithStream.signalDbuv) {
                    bestWithStream = sample;
                }
            }
        }
        return bestWithStream != null ? bestWithStream : bestAny;
    }

    private TransmitterSample findStrongestDab(Player player, int channelFrequencyKhz) {
        int overheadBlockCount = SignalMath.countOverheadBlocks(player.level(), player.position());
        TransmitterSample best = null;
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overheadBlockCount, channelFrequencyKhz)) {
            DabTransmitterBlockEntity transmitter = group.transmitter();
            double signalDbuv = group.signalDbuv();
            double snrDb = group.snrDb();
            int mscState = transmitter.getErrorCorrection().decodeState(signalDbuv, snrDb, 0.8);
            if (mscState <= 0) {
                continue;
            }
            String streamUrl = resolveDabStationStream(transmitter);
            TransmitterSample sample = new TransmitterSample(signalDbuv, snrDb, streamUrl);
            if (!streamUrl.isBlank() && (best == null || signalDbuv > best.signalDbuv)) {
                best = sample;
            }
        }
        return best;
    }

    private String resolveDabStationStream(DabTransmitterBlockEntity transmitter) {
        DabServerBlockEntity.DabEnsembleDefinition ensemble = transmitter.resolveBroadcastMux();
        if (ensemble == null) {
            return "";
        }
        for (DabServerBlockEntity.DabStationDefinition station : ensemble.stations()) {
            String url = station.streamUrl();
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private static boolean hasRadioInInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).is(Radio.RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.BLACK_DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.DX3_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.TECHNISAT_RADIO_ITEM.get())) return true;
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            if (inventory.offhand.get(i).is(Radio.RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.BLACK_DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.DX3_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.TECHNISAT_RADIO_ITEM.get())) return true;
        }
        return false;
    }

    private static boolean hasDx3InInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (stack.is(Radio.DX3_RADIO_ITEM.get()) || stack.is(Radio.TECHNISAT_RADIO_ITEM.get())) {
                return true;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(Radio.DX3_RADIO_ITEM.get()) || stack.is(Radio.TECHNISAT_RADIO_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    private void setMix(double stream, double noise) {
        double clampedStream = Mth.clamp(stream, 0.0, 1.0);
        double clampedNoise = Mth.clamp(noise, 0.0, 1.0);
        streamGain = smoothGain(streamGain, clampedStream, STREAM_GAIN_ATTACK_SMOOTHING, STREAM_GAIN_RELEASE_SMOOTHING);
        noiseGain = smoothGain(noiseGain, clampedNoise, NOISE_GAIN_ATTACK_SMOOTHING, NOISE_GAIN_RELEASE_SMOOTHING);
    }

    private static double smoothGain(double current, double target, double attack, double release) {
        double smoothing = target >= current ? attack : release;
        return current + ((target - current) * smoothing);
    }

    private record TransmitterSample(double signalDbuv, double snrDb, String streamUrl, String fallbackUrl) {
        private TransmitterSample(double signalDbuv, double snrDb, String streamUrl) {
            this(signalDbuv, snrDb, streamUrl, "");
        }
    }

    static String resolvePlayableUrl(Minecraft minecraft, String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank() || !streamUrl.startsWith("radio-proxy://")) {
            return streamUrl;
        }
        String rest = streamUrl.substring("radio-proxy://".length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) {
            return streamUrl;
        }
        String portPart = rest.substring(0, slash);
        String encoded = rest.substring(slash + 1);
        int port;
        try {
            port = Integer.parseInt(portPart);
        } catch (NumberFormatException ex) {
            return streamUrl;
        }
        String host = "127.0.0.1";
        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            host = extractHost(serverData.ip.trim());
        }
        return "http://" + host + ":" + port + "/" + encoded;
    }

    private static String extractHost(String address) {
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end > 1) {
                return address.substring(1, end);
            }
            return "127.0.0.1";
        }
        int colon = address.indexOf(':');
        if (colon > 0) {
            return address.substring(0, colon);
        }
        return address;
    }

    static AudioInputStream openPcmStreamForPlaced(String url) throws Exception {
        return StreamDecoderThread.openPcmStream(url);
    }

    private static final class StreamDecoderThread extends Thread {
        private final String streamUrl;
        private final String fallbackUrl;
        private final ArrayBlockingQueue<byte[]> outputQueue;
        private volatile boolean running = true;

        private StreamDecoderThread(String streamUrl, String fallbackUrl, ArrayBlockingQueue<byte[]> outputQueue) {
            super("radio-fm-stream-decoder");
            this.streamUrl = streamUrl;
            this.fallbackUrl = fallbackUrl;
            this.outputQueue = outputQueue;
        }

        @Override
        public void run() {
            String currentUrl = streamUrl;
            while (running && !isInterrupted()) {
                try (AudioInputStream stream = openPcmStream(currentUrl)) {
                    Radio.LOGGER.info("Started radio stream decode for {}", currentUrl);
                    byte[] buffer = new byte[16384];
                    byte[] packetBuffer = new byte[16384 + 4];
                    int carry = 0;
                    while (running && !isInterrupted()) {
                        int read = stream.read(buffer);
                        if (read < 0) {
                            break;
                        }
                        if (read == 0) {
                            continue;
                        }
                        System.arraycopy(buffer, 0, packetBuffer, carry, read);
                        int total = carry + read;
                        int aligned = total - (total % 4);
                        if (aligned > 0) {
                            byte[] pcm = Arrays.copyOf(packetBuffer, aligned);
                            if (!outputQueue.offer(pcm)) {
                                outputQueue.poll();
                                outputQueue.offer(pcm);
                            }
                        }
                        carry = total - aligned;
                        if (carry > 0) {
                            System.arraycopy(packetBuffer, aligned, packetBuffer, 0, carry);
                        }
                    }
                } catch (Exception ex) {
                    Radio.LOGGER.warn("Radio stream decode failed for {}: {}", currentUrl, ex.getMessage());
                }
                if (running && !isInterrupted()) {
                    if (currentUrl.equals(streamUrl) && fallbackUrl != null && !fallbackUrl.isBlank()
                            && !fallbackUrl.startsWith("radio-file://")
                            && !fallbackUrl.equals(streamUrl)) {
                        currentUrl = fallbackUrl;
                        Radio.LOGGER.info("Trying original DAB stream after proxy playback ended.");
                    }
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException ignored) {
                        interrupt();
                    }
                }
            }
        }

        private static AudioInputStream openPcmStream(String streamUrl) throws Exception {
            boolean relayedUpload = streamUrl.startsWith("radio-relay://");
            URLConnection connection = relayedUpload ? null : URI.create(streamUrl).toURL().openConnection();
            int icyMetaInt = -1;
            if (connection instanceof HttpURLConnection http) {
                http.setConnectTimeout(5000);
                http.setReadTimeout(10000);
                http.setRequestProperty("User-Agent", "RadioPlus/1.0");
                http.setRequestProperty("Icy-MetaData", "0");
                http.setRequestProperty("Connection", "keep-alive");
                String header = http.getHeaderField("icy-metaint");
                if (header != null && !header.isBlank()) {
                    try {
                        icyMetaInt = Integer.parseInt(header.trim());
                    } catch (NumberFormatException ignored) {
                        icyMetaInt = -1;
                    }
                }
            }

            InputStream raw = relayedUpload ? AudioRelayClient.open(streamUrl) : connection.getInputStream();
            if (icyMetaInt > 0) {
                Radio.LOGGER.info("FM stream {} reported ICY metadata interval {} bytes; stripping metadata blocks.", streamUrl, icyMetaInt);
                raw = new IcyMetadataStrippingInputStream(raw, icyMetaInt);
            }
            BufferedInputStream input = new BufferedInputStream(raw, 256 * 1024);
            AudioInputStream encoded;
            try {
                encoded = AudioSystem.getAudioInputStream(input);
            } catch (UnsupportedAudioFileException ex) {
                input.close();
                if (relayedUpload) throw ex;
                Radio.LOGGER.warn("Primary JavaSound decode failed for {}; trying ffmpeg fallback: {}", streamUrl, ex.getMessage());
                return openFfmpegPcmStream(streamUrl, ex);
            }
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    PCM_FORMAT.getSampleRate(),
                    16,
                    2,
                    4,
                    PCM_FORMAT.getSampleRate(),
                    false
            );
            return AudioSystem.getAudioInputStream(decodedFormat, encoded);
        }

        private static AudioInputStream openFfmpegPcmStream(String streamUrl, Exception rootCause) throws Exception {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-i", streamUrl,
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    "-ac", "2",
                    "-ar", "44100",
                    "pipe:1"
            );
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process;
            try {
                process = pb.start();
            } catch (IOException io) {
                io.addSuppressed(rootCause);
                throw io;
            }
            InputStream pcm = new BufferedInputStream(new ProcessInputStream(process), 64 * 1024);
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    PCM_FORMAT.getSampleRate(),
                    16,
                    2,
                    4,
                    PCM_FORMAT.getSampleRate(),
                    false
            );
            Radio.LOGGER.info("Using ffmpeg fallback decoder for {}", streamUrl);
            return new AudioInputStream(pcm, decodedFormat, AudioSystem.NOT_SPECIFIED);
        }

        private void shutdown() {
            running = false;
            interrupt();
        }

        private static final class IcyMetadataStrippingInputStream extends InputStream {
            private final InputStream delegate;
            private final int metadataInterval;
            private int bytesUntilMetadata;

            private IcyMetadataStrippingInputStream(InputStream delegate, int metadataInterval) {
                this.delegate = delegate;
                this.metadataInterval = metadataInterval;
                this.bytesUntilMetadata = metadataInterval;
            }

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int read = read(one, 0, 1);
                return read < 0 ? -1 : (one[0] & 0xFF);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                int total = 0;
                while (len > 0) {
                    if (bytesUntilMetadata == 0) {
                        int lengthByte = delegate.read();
                        if (lengthByte < 0) {
                            return total == 0 ? -1 : total;
                        }
                        int metadataBytes = lengthByte * 16;
                        skipFully(metadataBytes);
                        bytesUntilMetadata = metadataInterval;
                        continue;
                    }

                    int toRead = Math.min(len, bytesUntilMetadata);
                    int read = delegate.read(b, off, toRead);
                    if (read < 0) {
                        return total == 0 ? -1 : total;
                    }
                    if (read == 0) {
                        break;
                    }
                    off += read;
                    len -= read;
                    total += read;
                    bytesUntilMetadata -= read;
                }
                return total;
            }

            private void skipFully(int bytes) throws IOException {
                int remaining = bytes;
                byte[] scratch = new byte[256];
                while (remaining > 0) {
                    int read = delegate.read(scratch, 0, Math.min(scratch.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        break;
                    }
                    remaining -= read;
                }
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        }

        private static final class ProcessInputStream extends InputStream {
            private final Process process;
            private final InputStream delegate;

            private ProcessInputStream(Process process) {
                this.process = process;
                this.delegate = process.getInputStream();
            }

            @Override
            public int read() throws IOException {
                return delegate.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return delegate.read(b, off, len);
            }

            @Override
            public void close() throws IOException {
                try {
                    delegate.close();
                } finally {
                    if (process.isAlive()) {
                        process.destroy();
                        try {
                            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                                process.destroyForcibly();
                            }
                        } catch (InterruptedException ignored) {
                            process.destroyForcibly();
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
    }
}
