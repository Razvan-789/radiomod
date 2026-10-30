package com.radiomod.signal;

import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Player;

/** Per-radio RDS text assembled over simulated received frames. */
public final class RdsTextDecoding {
    private static final Map<Player, Decoder> PLAYERS = new WeakHashMap<>();
    private static final Map<Dx3ReceiverBlockEntity, Decoder> RECEIVERS = new WeakHashMap<>();
    private static final Map<WebserverBlockEntity, Decoder> WEBSERVERS = new WeakHashMap<>();
    private RdsTextDecoding() { }

    public record Text(String ps, String longPs, String rt) { }

    public static Text forPlayer(Player player, String source, String ps, String longPs, String rt,
                                 int berPercent, long gameTime) {
        return PLAYERS.computeIfAbsent(player, ignored -> new Decoder())
                .update(source, ps, longPs, rt, berPercent, gameTime);
    }

    public static Text forReceiver(Dx3ReceiverBlockEntity receiver, String source, String ps,
                                   String longPs, String rt, int berPercent, long gameTime) {
        return RECEIVERS.computeIfAbsent(receiver, ignored -> new Decoder())
                .update(source, ps, longPs, rt, berPercent, gameTime);
    }

    public static void clearPlayer(Player player) { PLAYERS.remove(player); }
    public static void clearReceiver(Dx3ReceiverBlockEntity receiver) { RECEIVERS.remove(receiver); }
    public static Text forWebserver(WebserverBlockEntity webserver, String source, String ps, String rt,
                                    int berPercent, long gameTime) {
        return WEBSERVERS.computeIfAbsent(webserver, ignored -> new Decoder())
                .update(source, ps, "", rt, berPercent, gameTime);
    }
    public static void clearWebserver(WebserverBlockEntity webserver) { WEBSERVERS.remove(webserver); }

    private static final class Decoder {
        private final Random random = new Random();
        private String source = "";
        private long lastFrame = -1;
        private final char[] ps = spaces(8), longPs = spaces(32), rt = spaces(64);
        private int psSegment, longPsSegment, rtSegment;

        private Text update(String newSource, String targetPs, String targetLongPs, String targetRt,
                            int berPercent, long gameTime) {
            long frame = Math.max(0L, gameTime * 6L / 20L);
            if (!source.equals(newSource) || frame < lastFrame) {
                source = newSource;
                Arrays.fill(ps, ' ');
                Arrays.fill(longPs, ' ');
                Arrays.fill(rt, ' ');
                psSegment = longPsSegment = rtSegment = 0;
                lastFrame = frame - 1;
            }
            long first = Math.max(lastFrame + 1, frame - 11);
            for (long step = first; step <= frame; step++) {
                if (!targetPs.isBlank())
                    decodeSegment(ps, targetPs, psSegment++ % 4, 2, berPercent);
                else Arrays.fill(ps, ' ');
                if (!targetLongPs.isBlank())
                    decodeSegment(longPs, targetLongPs, longPsSegment++ % 8, 4, berPercent);
                else Arrays.fill(longPs, ' ');
                if (!targetRt.isBlank())
                    decodeSegment(rt, targetRt, rtSegment++ % 16, 4, berPercent);
                else Arrays.fill(rt, ' ');
            }
            lastFrame = frame;
            return new Text(new String(ps).stripTrailing(), new String(longPs).stripTrailing(),
                    new String(rt).stripTrailing());
        }

        private void decodeSegment(char[] current, String target, int segment, int width,
                                   int berPercent) {
            double quality = Math.max(0.0, Math.min(1.0, (100.0 - berPercent) / 100.0));
            int start = segment * width;
            if (random.nextDouble() < quality) {
                for (int i = start; i < Math.min(start + width, current.length); i++)
                    current[i] = i < target.length() ? target.charAt(i) : ' ';
            } else if (random.nextDouble() < (1.0 - quality) * 0.08) {
                int index = start + random.nextInt(width);
                if (index < current.length) current[index] = '?';
            }
        }

        private static char[] spaces(int length) {
            char[] out = new char[length];
            Arrays.fill(out, ' ');
            return out;
        }
    }
}
