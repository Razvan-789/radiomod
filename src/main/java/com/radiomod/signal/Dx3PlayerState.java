package com.radiomod.signal;

import com.radiomod.network.SyncDx3PlayerPayload;

/** Player metadata used by the DX3 pinned display after its menu closes. */
public final class Dx3PlayerState {
    private static volatile SyncDx3PlayerPayload latest;
    private static volatile long updatedAt;
    private static volatile int[] logoPixels;

    private Dx3PlayerState() {
    }

    public static void update(SyncDx3PlayerPayload payload) {
        latest = payload;
        updatedAt = System.currentTimeMillis();
    }

    public static SyncDx3PlayerPayload getFresh() {
        return System.currentTimeMillis() - updatedAt <= 2500L ? latest : null;
    }

    /** Keep a small MOT thumbnail available after the DX3 menu closes. */
    public static void updateLogo(int[] pixels) {
        if (pixels == null || pixels.length != 64 * 64) {
            logoPixels = null;
            return;
        }
        int[] thumbnail = new int[32 * 32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                thumbnail[y * 32 + x] = pixels[(y * 2 + 1) * 64 + x * 2 + 1];
            }
        }
        logoPixels = thumbnail;
    }

    public static int[] getLogoPixels() {
        return logoPixels;
    }
}
