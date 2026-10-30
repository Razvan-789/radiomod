package com.radiomod.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Three world-persistent, player-owned slots for each transmitter type. */
public final class TransmitterPresets {
    public static final int SLOT_COUNT = 3;
    private static final String KEY = "radio_transmitter_presets";

    private TransmitterPresets() {
    }

    public static int occupiedMask(Player player, String type) {
        CompoundTag presets = player.getPersistentData().getCompound(KEY).getCompound(type);
        int mask = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (presets.contains(Integer.toString(slot))) {
                mask |= 1 << slot;
            }
        }
        return mask;
    }

    public static void save(Player player, String type, int slot, CompoundTag preset) {
        if (!valid(slot)) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag all = root.getCompound(KEY);
        CompoundTag byType = all.getCompound(type);
        byType.put(Integer.toString(slot), preset.copy());
        all.put(type, byType);
        root.put(KEY, all);
    }

    public static CompoundTag load(Player player, String type, int slot) {
        if (!valid(slot)) {
            return null;
        }
        CompoundTag byType = player.getPersistentData().getCompound(KEY).getCompound(type);
        return byType.contains(Integer.toString(slot)) ? byType.getCompound(Integer.toString(slot)).copy() : null;
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        if (original.contains(KEY)) {
            event.getEntity().getPersistentData().put(KEY, original.getCompound(KEY).copy());
        }
    }

    private static boolean valid(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }
}
