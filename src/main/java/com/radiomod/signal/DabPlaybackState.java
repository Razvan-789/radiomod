package com.radiomod.signal;

import com.radiomod.item.RadioItem;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Server-selected DAB stream for the standard Radio. */
public final class DabPlaybackState {
    private static final long FRESH_MS = 2500L;
    @Nullable
    private static volatile Snapshot latest;

    private DabPlaybackState() {
    }

    public static void update(int channelIndex, String streamUrl, String sourceUrl,
                              int signalTimes10, int snrTimes10) {
        latest = new Snapshot(channelIndex, streamUrl, sourceUrl, signalTimes10 / 10.0,
                snrTimes10 / 10.0, System.currentTimeMillis());
    }

    @Nullable
    public static Snapshot getFresh(Player player) {
        Snapshot snapshot = latest;
        if (snapshot == null || System.currentTimeMillis() - snapshot.updatedAtMs() > FRESH_MS
                || snapshot.channelIndex() != RadioItem.getDabChannelIndex(player)) {
            return null;
        }
        return snapshot;
    }

    public record Snapshot(int channelIndex, String streamUrl, String sourceUrl,
                           double signalDbuv, double snrDb, long updatedAtMs) {
    }
}
