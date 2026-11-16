package com.radiomod.client;

import com.radiomod.Radio;
import com.radiomod.network.SyncDx3ReceiverAudioPayload;
import com.radiomod.network.SyncReceiverPositionPayload;
import com.radiomod.signal.MovingRadioPositions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Plays selected world receivers on independent, distance-attenuated client lines. */
public final class PlacedReceiverAudioManager {
    private static final PlacedReceiverAudioManager INSTANCE = new PlacedReceiverAudioManager();
    private static final AudioFormat FORMAT = new AudioFormat(44_100.0f, 16, 2, true, false);
    private static final double RANGE = 32.0;
    private static final long STALE_MS = 2600;
    private static final long DAB_DECODER_LOCK_MS = 1_500;
    private final Map<BlockPos, Source> sources = new HashMap<>();
    private final Map<BlockPos, Stream> streams = new HashMap<>();
    private final Map<BlockPos, DecodeGate> dabGates = new HashMap<>();
    private Object currentLevel;

    private PlacedReceiverAudioManager() { }
    public static PlacedReceiverAudioManager get() { return INSTANCE; }

    public void update(SyncDx3ReceiverAudioPayload payload) {
        sources.put(payload.pos(), new Source(payload.url(), payload.fallbackUrl(), payload.dab(),
                payload.fmNoise(), payload.worldPos(), System.currentTimeMillis()));
    }

    public void updatePosition(SyncReceiverPositionPayload payload) {
        sources.computeIfPresent(payload.pos(), (pos, source) -> new Source(source.url(),
                source.fallbackUrl(), source.dab(), source.fmNoise(), payload.worldPos(), source.updatedAt()));
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.level != currentLevel) {
            for (Stream stream : streams.values()) stream.stopPlaying();
            streams.clear(); sources.clear(); dabGates.clear(); currentLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null) return;
        long now = System.currentTimeMillis();
        Vec3 listener = MovingRadioPositions.world(minecraft.level, minecraft.player.position());
        double records = minecraft.options.getSoundSourceVolume(SoundSource.RECORDS)
                * minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        List<BlockPos> nearby = new ArrayList<>();
        for (Map.Entry<BlockPos, Source> entry : sources.entrySet()) {
            BlockPos pos = entry.getKey();
            Source source = entry.getValue();
            double distance = listener.distanceTo(source.worldPos());
            if (now - source.updatedAt() > STALE_MS || source.url().isBlank() && source.fmNoise() <= 0) {
                dabGates.remove(pos);
                continue;
            }
            if (distance >= RANGE) {
                dabGates.remove(pos);
                continue;
            }
            if (source.dab()) {
                DecodeGate gate = dabGates.get(pos);
                if (gate == null || !gate.url().equals(source.url())) {
                    gate = new DecodeGate(source.url(), now + DAB_DECODER_LOCK_MS);
                    dabGates.put(pos, gate);
                }
                if (now < gate.readyAtMs()) continue;
            } else {
                dabGates.remove(pos);
            }
            nearby.add(pos);
        }
        nearby.sort(Comparator.comparingDouble(pos -> listener.distanceToSqr(sources.get(pos).worldPos())));
        Set<BlockPos> wanted = new HashSet<>(nearby.subList(0, Math.min(4, nearby.size())));
        streams.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            entry.getValue().stopPlaying();
            return true;
        });
        for (BlockPos pos : wanted) {
            Source source = sources.get(pos);
            Vec3 relative = source.worldPos().subtract(listener);
            double distance = relative.length();
            double fade = Math.max(0.0, 1.0 - distance / RANGE);
            double volume = fade * fade * records;
            double yaw = Math.toRadians(minecraft.player.getYRot());
            double pan = distance < 0.001 ? 0.0
                    : Math.max(-1.0, Math.min(1.0,
                            (relative.x * -Math.cos(yaw) + relative.z * -Math.sin(yaw)) / distance));
            String url = FmAudioManager.resolvePlayableUrl(minecraft, source.url());
            String fallback = FmAudioManager.resolvePlayableUrl(minecraft, source.fallbackUrl());
            Stream stream = streams.get(pos);
            if (stream == null || !stream.url.equals(url)) {
                if (stream != null) stream.stopPlaying();
                stream = new Stream(pos, url, fallback);
                streams.put(pos, stream);
                stream.start();
            }
            stream.leftGain = volume * (pan > 0 ? 1.0 - pan * 0.7 : 1.0);
            stream.rightGain = volume * (pan < 0 ? 1.0 + pan * 0.7 : 1.0);
            stream.noiseGain = source.fmNoise();
        }
        sources.entrySet().removeIf(entry -> now - entry.getValue().updatedAt() > STALE_MS);
    }

    private record Source(String url, String fallbackUrl, boolean dab, float fmNoise,
                          Vec3 worldPos, long updatedAt) { }
    private record DecodeGate(String url, long readyAtMs) { }

    private static final class Stream extends Thread {
        private final BlockPos pos;
        private final String url;
        private final String fallback;
        private volatile boolean playing = true;
        private volatile double leftGain;
        private volatile double rightGain;
        private volatile float noiseGain;
        private final Object resourcesLock = new Object();
        private AudioInputStream input;
        private SourceDataLine line;

        private Stream(BlockPos pos, String url, String fallback) {
            super("radio-dx3-receiver-" + pos.asLong());
            setDaemon(true);
            this.pos = pos;
            this.url = url;
            this.fallback = fallback;
        }

        @Override public void run() {
            String current = url;
            int noiseState = pos.hashCode() ^ 0x2468ACE1;
            while (playing && !isInterrupted()) {
                AudioInputStream decoded = null;
                SourceDataLine output = null;
                boolean adopted = false;
                try {
                    if (!current.isBlank()) decoded = FmAudioManager.openPcmStreamForPlaced(current);
                    output = AudioSystem.getSourceDataLine(FORMAT);
                    synchronized (resourcesLock) {
                        if (playing) {
                            input = decoded;
                            line = output;
                            adopted = true;
                        }
                    }
                    if (!adopted) break;
                    output.open(FORMAT, 16_384);
                    output.start();
                    byte[] in = new byte[8192];
                    byte[] out = new byte[8192];
                    while (playing && !isInterrupted()) {
                        int count = decoded == null ? in.length : decoded.read(in, 0, in.length);
                        if (count < 0) break;
                        count -= count % 4;
                        if (decoded == null) java.util.Arrays.fill(in, (byte) 0);
                        double left = leftGain, right = rightGain;
                        double hiss = noiseGain * Math.sqrt(Math.max(0, noiseGain));
                        double music = Math.max(0, Math.min(1, (0.68 - noiseGain) / 0.64));
                        for (int offset = 0; offset < count; offset += 2) {
                            short sample = (short) ((in[offset + 1] << 8) | (in[offset] & 0xFF));
                            noiseState = noiseState * 1103515245 + 12345;
                            short noise = (short) ((((noiseState >>> 16) & 0xFFFF) - 32768) * 0.07);
                            double channelGain = offset % 4 == 0 ? left : right;
                            int adjusted = (int) Math.round((sample * music
                                    + noise * hiss) * channelGain);
                            adjusted = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, adjusted));
                            out[offset] = (byte) adjusted;
                            out[offset + 1] = (byte) (adjusted >> 8);
                        }
                        output.write(out, 0, count);
                    }
                } catch (Exception exception) {
                    if (playing) Radio.LOGGER.warn("DX3 receiver audio at {}: {}", pos, exception.getMessage());
                } finally {
                    releaseResources(decoded, output, adopted);
                }
                if (current.equals(url) && fallback != null && !fallback.isBlank() && !fallback.equals(url))
                    current = fallback;
                if (playing) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { interrupt(); }
                }
            }
        }

        private void stopPlaying() {
            playing = false;
            interrupt();
            AudioInputStream oldInput;
            SourceDataLine oldLine;
            synchronized (resourcesLock) {
                oldInput = input;
                oldLine = line;
                input = null;
                line = null;
            }
            closeResources(oldInput, oldLine);
        }

        private void releaseResources(AudioInputStream decoded, SourceDataLine output, boolean adopted) {
            AudioInputStream oldInput = adopted ? null : decoded;
            SourceDataLine oldLine = adopted ? null : output;
            if (adopted) {
                synchronized (resourcesLock) {
                    if (input == decoded) {
                        oldInput = input;
                        input = null;
                    }
                    if (line == output) {
                        oldLine = line;
                        line = null;
                    }
                }
            }
            closeResources(oldInput, oldLine);
        }

        private static void closeResources(AudioInputStream oldInput, SourceDataLine oldLine) {
            try { if (oldInput != null) oldInput.close(); } catch (Exception ignored) { }
            if (oldLine != null) {
                try { oldLine.stop(); } catch (RuntimeException ignored) { }
                try { oldLine.close(); } catch (RuntimeException ignored) { }
            }
        }
    }
}
