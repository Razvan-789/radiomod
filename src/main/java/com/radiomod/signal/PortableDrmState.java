package com.radiomod.signal;

import com.radiomod.network.SyncPortableDrmPayload;
import org.jetbrains.annotations.Nullable;

public final class PortableDrmState {
    @Nullable private static SyncPortableDrmPayload current;
    private static long receivedAt;
    private PortableDrmState() { }
    public static void update(SyncPortableDrmPayload payload) {
        current = payload; receivedAt = System.currentTimeMillis();
    }
    @Nullable public static SyncPortableDrmPayload fresh() {
        return current != null && System.currentTimeMillis() - receivedAt < 3000 ? current : null;
    }
}
