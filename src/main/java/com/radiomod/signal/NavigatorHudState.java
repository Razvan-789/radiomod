package com.radiomod.signal;

import com.radiomod.network.SyncNavigatorHudPayload;

/** Client-side snapshot that expires when server updates stop. */
public final class NavigatorHudState {
    private static SyncNavigatorHudPayload snapshot;
    private static long receivedAt;
    private NavigatorHudState() { }
    public static void set(SyncNavigatorHudPayload value) {
        snapshot = value; receivedAt = System.currentTimeMillis();
    }
    public static SyncNavigatorHudPayload fresh() {
        return System.currentTimeMillis() - receivedAt <= 1500 ? snapshot : null;
    }
    public static void clear() { snapshot = null; receivedAt = 0; }
}
