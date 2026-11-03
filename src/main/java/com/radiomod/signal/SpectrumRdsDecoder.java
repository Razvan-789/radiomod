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
        if (pos == null || tx == null || !tx.isRdsEnabled()) {
            DECODERS.remove(player);
            return SyncSpectrumPayload.RdsReading.EMPTY;
        }
        int ber = signalDbuv > -10 && snrDb > -5
                ? Dx3Catalog.rdsBerPercent(signalDbuv, snrDb) : 100;
        String source = pos.asLong() + ":" + frequencyKhz;
        return DECODERS.computeIfAbsent(player, ignored -> new Decoder())
                .sample(tx, source, ber, player.level().getGameTime());
    }

    static void clear(Player player) { DECODERS.remove(player); }

    private static final class Decoder {
        private final Random random = new Random();
        private final char[] ps = new char[8], rt = new char[64];
        private String source = "", targetPs = "", targetRt = "", pi = "", group = "";
        private int pty, psSegment, rtSegment;
        private boolean tp;
        private long lastFrame = -1, lastGoodFrame = -1;

        private SyncSpectrumPayload.RdsReading sample(TransmitterBlockEntity tx,
                String currentSource, int ber, long gameTime) {
            long frame = Math.max(0, gameTime * 6 / 20);
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
            // The analyser receives samples every five ticks; examine the RDS frames between them.
            long first = Math.max(lastFrame + 1, frame - 11);
            double successChance = Math.clamp((100.0 - ber) / 100.0, 0, 1);
            for (long current = first; current <= frame; current++) {
                if (random.nextDouble() >= successChance) continue;
                long frameTick = (current * 20 + 5) / 6; // first game tick of this 6 Hz frame
                RdsFrameData received = tx.getCurrentRdsFrame(0, frameTick, "");
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
            return new SyncSpectrumPayload.RdsReading(true, decoded, ber,
                    decoded ? pi : "", decoded && !targetPs.isEmpty() ? new String(ps).stripTrailing() : "",
                    decoded ? new String(rt).stripTrailing() : "", decoded ? pty : 0,
                    decoded ? group : "", decoded && tp);
        }
    }
}
