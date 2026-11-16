package com.radiomod.signal;

import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.network.SyncSpectrumPayload;
import java.util.Arrays;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Independent RDS group decoder for the spectrum receiver. Runs only on the server. */
final class SpectrumRdsDecoder {
    private static final WeakHashMap<Player, Decoder> DECODERS = new WeakHashMap<>();
    private SpectrumRdsDecoder() { }

    static SyncSpectrumPayload.RdsReading sample(Player player, BlockPos pos,
            TransmitterBlockEntity tx, int frequencyKhz, double signalDbuv, double snrDb) {
        Decoder decoder = DECODERS.computeIfAbsent(player, ignored -> new Decoder());
        decoder.tune(frequencyKhz);
        if (pos == null || tx == null) return decoder.reading(false, false, 100);
        if (!tx.isRdsEnabled()) {
            DECODERS.remove(player);
            return SyncSpectrumPayload.RdsReading.EMPTY;
        }
        int ber = signalDbuv > -10 && snrDb > -5
                ? Dx3Catalog.rdsBerPercent(signalDbuv, snrDb) : 100;
        String source = pos.asLong() + ":" + frequencyKhz;
        return decoder.sample(tx, source, ber, player.level().getGameTime());
    }

    static void clear(Player player) { DECODERS.remove(player); }

    private static final class Decoder {
        private final Random random = new Random();
        private final char[] ps = new char[8], rt = new char[64];
        private String source = "", targetPs = "", targetRt = "", pi = "", group = "";
        private int pty, psSegment, rtSegment;
        private boolean tp;
        private int frequencyKhz = -1;
        private long lastFrame = -1, lastGoodFrame = -1;

        private void tune(int frequency) {
            if (frequency == frequencyKhz) return;
            frequencyKhz = frequency;
            source = "";
            pi = group = targetPs = targetRt = "";
            pty = psSegment = rtSegment = 0;
            tp = false;
            Arrays.fill(ps, '_');
            Arrays.fill(rt, ' ');
            lastFrame = lastGoodFrame = -1;
        }

        private SyncSpectrumPayload.RdsReading sample(TransmitterBlockEntity tx,
                String currentSource, int ber, long gameTime) {
            long frame = RdsTiming.groupAtTick(gameTime);
            if (!currentSource.equals(source) || frame < lastFrame) {
                source = currentSource;
                pi = group = targetPs = targetRt = "";
                pty = psSegment = rtSegment = 0;
                tp = false;
                Arrays.fill(ps, '_');
                Arrays.fill(rt, ' ');
                lastFrame = frame - 1;
                lastGoodFrame = -1;
            }
            // The analyser receives samples every five ticks; examine the groups between them.
            long first = Math.max(lastFrame + 1, frame - 11);
            double successChance = Math.clamp((100.0 - ber) / 100.0, 0, 1);
            for (long current = first; current <= frame; current++) {
                if (random.nextDouble() >= successChance) continue;
                long frameTick = RdsTiming.firstTickForGroup(current);
                RdsFrameData received = tx.getRdsFrameAtGroup(0, current, frameTick, "");
                if (received == null) continue;
                lastGoodFrame = current;
                pi = received.pi();
                pty = received.pty();
                tp = received.tp();
                group = received.groupCode();
                if ((group.equals("0A") || group.equals("0B")) && !received.ps().isBlank()) {
                    if (!received.ps().equals(targetPs)) {
                        targetPs = received.ps();
                        Arrays.fill(ps, '_');
                        psSegment = 0;
                    }
                    int start = (psSegment++ % 4) * 2;
                    for (int i = start; i < start + 2; i++)
                        ps[i] = i < targetPs.length() ? targetPs.charAt(i) : ' ';
                }
                if (group.equals("2A")) {
                    if (!received.rt().equals(targetRt)) {
                        targetRt = received.rt();
                        Arrays.fill(rt, ' ');
                        rtSegment = 0;
                    }
                    if (!targetRt.isBlank()) {
                        int start = (rtSegment++ % 16) * 4;
                        for (int i = start; i < start + 4; i++)
                            rt[i] = i < targetRt.length() ? targetRt.charAt(i) : ' ';
                    }
                }
            }
            lastFrame = frame;
            boolean decoded = ber <= 98 && lastGoodFrame >= 0 && frame - lastGoodFrame <= 12;
            return reading(true, decoded, ber);
        }

        private SyncSpectrumPayload.RdsReading reading(boolean present, boolean decoded, int ber) {
            if (!present && pi.isBlank()) return SyncSpectrumPayload.RdsReading.EMPTY;
            return new SyncSpectrumPayload.RdsReading(present, decoded, ber,
                    pi, targetPs.isEmpty() ? "" : new String(ps).stripTrailing(),
                    new String(rt).stripTrailing(), pty, group, tp);
        }
    }
}
