package com.radiomod.signal;

import com.radiomod.network.SyncTechnisatReceiverHudPayload;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Client-side selection and latest data for a placed receiver's PIN overlay. */
public final class TechnisatPinnedState {
    @Nullable private static BlockPos pinned;
    @Nullable private static SyncTechnisatReceiverHudPayload latest;
    private static long receivedAt;
    private TechnisatPinnedState() { }
    public static void toggle(BlockPos pos) {
        pinned = pos.equals(pinned) ? null : pos.immutable();
        latest = null;
    }
    public static boolean isPinned(BlockPos pos) { return pos.equals(pinned); }
    public static boolean hasPinned() { return pinned != null; }
    public static void update(SyncTechnisatReceiverHudPayload payload) {
        if (payload.pos().equals(pinned)) { latest = payload; receivedAt = System.currentTimeMillis(); }
    }
    @Nullable public static SyncTechnisatReceiverHudPayload fresh() {
        return latest != null && System.currentTimeMillis() - receivedAt < 3000 ? latest : null;
    }
    public static void clear() { pinned = null; latest = null; }
}
