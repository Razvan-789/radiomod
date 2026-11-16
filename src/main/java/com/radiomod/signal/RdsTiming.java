package com.radiomod.signal;

/** RDS group cadence: 11.4 groups per real second at the normal 20 TPS game rate. */
public final class RdsTiming {
    private static final long GROUPS_PER_100_TICKS = 57L;
    private RdsTiming() { }

    public static long groupAtTick(long gameTick) {
        return Math.max(0L, gameTick * GROUPS_PER_100_TICKS / 100L);
    }

    public static long firstTickForGroup(long group) {
        return Math.max(0L, (group * 100L + GROUPS_PER_100_TICKS - 1L) / GROUPS_PER_100_TICKS);
    }
}
