package com.radiomod.signal;

import java.util.Random;
import org.jetbrains.annotations.Nullable;

public final class FmReceptionState {
    private static final long FRESH_MS = 2500L;
    private static final int PS_WIDTH = 8;
    private static final Random RDS_DECODE_RANDOM = new Random(0x5EEDC0DEL);
    @Nullable
    private static volatile Snapshot latest;

    private FmReceptionState() {
    }

    public static void update(boolean active, int signalDbuvTimes10, int snrDbTimes10, int rdsBerPercent, String streamUrl, String ps) {
        String normalizedStreamUrl = streamUrl == null ? "" : streamUrl;
        String normalizedPs = normalizePs(ps);
        Snapshot previous = latest;
        if (!active || !rdsStreamContinues(normalizedStreamUrl, previous)) {
            normalizedPs = "--------";
        }
        if (shouldHoldPreviousPs(active, normalizedStreamUrl, normalizedPs, previous)) {
            normalizedPs = previous.ps();
        } else if (active && previous != null && previous.active() && previous.streamUrl().equals(normalizedStreamUrl) && rdsBerPercent >= 99) {
            normalizedPs = previous.ps();
        } else if (active && previous != null && previous.active() && previous.streamUrl().equals(normalizedStreamUrl)) {
            normalizedPs = decodePsGradually(previous.ps(), normalizedPs, rdsBerPercent);
        }
        latest = new Snapshot(
                active,
                signalDbuvTimes10 / 10.0,
                snrDbTimes10 / 10.0,
                rdsBerPercent,
                normalizedStreamUrl,
                normalizedPs,
                System.currentTimeMillis()
        );
    }

    @Nullable
    public static Snapshot getFresh() {
        Snapshot snapshot = latest;
        if (snapshot == null) {
            return null;
        }
        if ((System.currentTimeMillis() - snapshot.updatedAtMs()) > FRESH_MS) {
            return null;
        }
        return snapshot;
    }

    public record Snapshot(boolean active, double signalDbuv, double snrDb, int rdsBerPercent, String streamUrl, String ps, long updatedAtMs) {
    }

    private static boolean shouldHoldPreviousPs(boolean active, String streamUrl, String ps, @Nullable Snapshot previous) {
        if (!active || previous == null || !previous.active()) {
            return false;
        }
        if (!previous.streamUrl().equals(streamUrl) || previous.ps().isBlank()) {
            return false;
        }
        String trimmed = ps.trim();
        return trimmed.isEmpty() || "--------".equals(trimmed);
    }

    private static boolean rdsStreamContinues(String streamUrl, @Nullable Snapshot previous) {
        return previous != null && previous.active() && previous.streamUrl().equals(streamUrl);
    }

    private static String normalizePs(@Nullable String ps) {
        String value = ps == null ? "" : ps;
        if (value.length() > PS_WIDTH) {
            value = value.substring(0, PS_WIDTH);
        }
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < PS_WIDTH) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static String decodePsGradually(String currentPs, String targetPs, int berPercent) {
        String current = normalizePs(currentPs);
        String target = normalizePs(targetPs);
        double quality = Math.max(0.0, Math.min(1.0, (100.0 - berPercent) / 100.0));
        double decodeChance = 0.08 + (quality * 0.88);
        double errorChance = (1.0 - quality) * 0.22;

        StringBuilder out = new StringBuilder(PS_WIDTH);
        for (int i = 0; i < PS_WIDTH; i++) {
            char cur = current.charAt(i);
            char tgt = target.charAt(i);
            if (cur == tgt) {
                out.append(cur);
                continue;
            }
            double roll = RDS_DECODE_RANDOM.nextDouble();
            if (roll < decodeChance) {
                out.append(tgt);
            } else if (roll < decodeChance + errorChance) {
                out.append(randomRdsGlyph());
            } else {
                out.append(cur);
            }
        }
        return out.toString();
    }

    private static char randomRdsGlyph() {
        String alphabet = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return alphabet.charAt(RDS_DECODE_RANDOM.nextInt(alphabet.length()));
    }
}
