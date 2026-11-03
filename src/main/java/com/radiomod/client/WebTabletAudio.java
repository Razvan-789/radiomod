package com.radiomod.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/** Private tablet playback with FM static mixed according to received quality. */
public final class WebTabletAudio {
    private static final WebTabletAudio INSTANCE = new WebTabletAudio();
    private static final AudioFormat FORMAT = new AudioFormat(44_100f, 16, 2, true, false);
    private Stream stream;
    private WebTabletAudio() { }
    public static WebTabletAudio get() { return INSTANCE; }

    public synchronized void play(Minecraft minecraft, boolean drm, boolean signal,
                                  double signalDbuv, double snrDb, String token, String fallback) {
        play(minecraft, drm, signal, signalDbuv, snrDb, token, fallback, 0, 1);
    }

    /** Spectrum receivers can impose extra passband noise without changing tablet playback. */
    public synchronized void play(Minecraft minecraft, boolean drm, boolean signal,
                                  double signalDbuv, double snrDb, String token, String fallback,
                                  double minimumNoise, double audioScale) {
        String url = signal ? FmAudioManager.resolvePlayableUrl(minecraft, token) : "";
        if (drm && url.isBlank()) { stop(); return; }
        String backup = signal ? FmAudioManager.resolvePlayableUrl(minecraft, fallback) : "";
        boolean start = stream == null || !stream.url.equals(url);
        if (start) {
            stop();
            stream = new Stream(url, backup);
        }
        double volume = minecraft.options.getSoundSourceVolume(SoundSource.RECORDS)
                * minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        stream.volume = volume;
        if (drm) {
            stream.audioGain = 1;
            stream.noiseGain = 0;
        } else if (!signal || url.isBlank()) {
            stream.audioGain = 0;
            stream.noiseGain = 0.68;
        } else {
            double audio, noise;
            if (snrDb >= 24) { audio = 1; noise = .015; }
            else if (snrDb >= 16) {
                double fade = (24 - snrDb) / 8;
                audio = lerp(fade, 1, .88); noise = lerp(fade, .015, .04);
            } else if (snrDb >= 8) {
                double fade = (16 - snrDb) / 8;
                audio = lerp(fade, .88, .58); noise = lerp(fade, .04, .12);
            } else if (snrDb >= 4) {
                double fade = (8 - snrDb) / 4;
                audio = lerp(fade, .58, .24); noise = lerp(fade, .12, .34);
            } else {
                double fade = clamp((4 - snrDb) / 6);
                audio = lerp(fade, .24, 0); noise = lerp(fade, .34, .68);
            }
            double signalFade = clamp((signalDbuv - 5) / 15);
            stream.audioGain = audio * lerp(signalFade, .18, 1) * clamp(audioScale);
            stream.noiseGain = Math.max(minimumNoise, Math.max(noise, lerp(signalFade, .62, .08)));
        }
        if (start) stream.start();
    }

    public synchronized void stop() {
        if (stream != null) { stream.close(); stream = null; }
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double lerp(double t, double from, double to) { return from + clamp(t) * (to - from); }

    private static final class Stream extends Thread {
        private final String url, fallback;
        private volatile boolean playing = true;
        private volatile double volume, audioGain, noiseGain;
        private volatile AudioInputStream input;
        private volatile SourceDataLine line;
        private int noiseState = 0x2468ACE1;
        private double smoothAudioGain, smoothNoiseGain;

        private Stream(String url, String fallback) {
            super("radio-web-tablet-audio"); setDaemon(true);
            this.url = url; this.fallback = fallback;
        }
        @Override public void run() {
            try (SourceDataLine output = AudioSystem.getSourceDataLine(FORMAT)) {
                line = output;
                output.open(FORMAT, 16_384);
                output.start();
                byte[] source = new byte[8192], mixed = new byte[8192];
                if (url.isBlank()) {
                    while (playing) { mix(null, mixed, mixed.length); output.write(mixed, 0, mixed.length); }
                    return;
                }
                String current = url;
                while (playing) {
                    try (AudioInputStream decoded = FmAudioManager.openPcmStreamForPlaced(current)) {
                        input = decoded;
                        int count;
                        while (playing && (count = decoded.read(source)) >= 0) {
                            count -= count % 2;
                            if (count == 0) continue;
                            mix(source, mixed, count);
                            output.write(mixed, 0, count);
                        }
                    } catch (Exception ignored) { }
                    finally { input = null; }
                    if (!playing) break;
                    if (current.equals(url) && !fallback.isBlank() && !fallback.equals(url)) current = fallback;
                    // Keep static audible while a broken or ended stream reconnects.
                    for (int i = 0; playing && i < 11; i++) {
                        mix(null, mixed, mixed.length);
                        output.write(mixed, 0, mixed.length);
                    }
                }
            } catch (Exception ignored) { }
            finally { line = null; }
        }
        private void mix(byte[] source, byte[] out, int count) {
            double noiseTarget = source == null && noiseGain > 0 ? Math.max(noiseGain, .68) : noiseGain;
            for (int i = 0; i < count; i += 2) {
                smoothAudioGain += (audioGain - smoothAudioGain) * .0005;
                smoothNoiseGain += (noiseTarget - smoothNoiseGain) * .0005;
                double audio = smoothAudioGain * volume;
                double staticLevel = smoothNoiseGain * Math.sqrt(Math.max(0, smoothNoiseGain)) * volume;
                short sample = source == null ? 0 : (short) ((source[i + 1] << 8) | (source[i] & 255));
                noiseState = noiseState * 1103515245 + 12345;
                int centered = ((noiseState >>> 16) & 0xFFFF) - 32768;
                int value = (int) Math.round(sample * audio + centered * .026 * staticLevel);
                value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
                out[i] = (byte) value;
                out[i + 1] = (byte) (value >> 8);
            }
        }
        private void close() {
            playing = false; interrupt();
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            try { if (line != null) { line.stop(); line.close(); } } catch (Exception ignored) { }
        }
    }
}
