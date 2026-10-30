package com.radiomod.signal;

import com.radiomod.item.RadioItem;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class Dx3PlaybackState {
    private static final long FRESH_MS = 2500L;
    @Nullable
    private static volatile Snapshot latest;

    private Dx3PlaybackState() {
    }

    public static void update(int channelIndex, String sid, String mux, String streamUrl, String sourceUrl,
                              int signalTimes10, int snrTimes10) {
        latest = new Snapshot(channelIndex, sid, mux, streamUrl, sourceUrl, signalTimes10 / 10.0,
                snrTimes10 / 10.0, System.currentTimeMillis());
    }

    @Nullable
    public static Snapshot getFresh(Player player) {
        Snapshot snapshot = latest;
        if (snapshot == null || System.currentTimeMillis() - snapshot.updatedAtMs() > FRESH_MS
                || snapshot.channelIndex() != RadioItem.getDabChannelIndex(player)
                || !snapshot.sid().equalsIgnoreCase(RadioItem.getDabStationSid(player))
                || !snapshot.mux().equals(RadioItem.getDabStationMux(player))) {
            return null;
        }
        return snapshot;
    }

    public record Snapshot(int channelIndex, String sid, String mux, String streamUrl, String sourceUrl,
                           double signalDbuv, double snrDb, long updatedAtMs) {
    }
}
