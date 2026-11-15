package com.radiomod.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.radiomod.network.SyncStreamHistoryPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-player stream URLs, with the station identity most recently used for each URL. */
public final class StreamHistory {
    private static final String KEY = "radio_stream_history";
    private static final int MAX_ENTRIES = 64;
    private static final int PAGE_SIZE = 8;

    private StreamHistory() { }

    public static void remember(Player player, String mode, String url, String code, String name) {
        if (player == null || url == null) return;
        String cleaned = url.strip();
        if (!validStream(cleaned)) return;
        ListTag history = player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND);
        ListTag updated = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("Mode", limit(mode, 8));
        entry.putString("Url", limit(cleaned, 1024));
        entry.putString("Code", limit(code, 16));
        entry.putString("Name", limit(name, 64));
        updated.add(entry);
        for (int i = 0; i < history.size() && updated.size() < MAX_ENTRIES; i++) {
            CompoundTag old = history.getCompound(i);
            if (!old.getString("Mode").equals(entry.getString("Mode"))
                    || !old.getString("Code").equalsIgnoreCase(entry.getString("Code"))
                    || !old.getString("Url").equals(entry.getString("Url"))) updated.add(old.copy());
        }
        player.getPersistentData().put(KEY, updated);
    }

    public static void sendPage(ServerPlayer player, int containerId, int requestedPage) {
        ListTag history = player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (history.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        JsonArray rows = new JsonArray();
        for (int i = page * PAGE_SIZE; i < Math.min(history.size(), (page + 1) * PAGE_SIZE); i++) {
            CompoundTag saved = history.getCompound(i);
            JsonObject row = new JsonObject();
            row.addProperty("mode", saved.getString("Mode"));
            row.addProperty("url", saved.getString("Url"));
            row.addProperty("code", saved.getString("Code"));
            row.addProperty("name", saved.getString("Name"));
            rows.add(row);
        }
        PacketDistributor.sendToPlayer(player, new SyncStreamHistoryPayload(containerId, page, pageCount, rows.toString()));
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KEY)) event.getEntity().getPersistentData().put(KEY,
                old.getList(KEY, Tag.TAG_COMPOUND).copy());
    }

    private static boolean validStream(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("radio-file://");
    }

    private static String limit(String value, int max) {
        String cleaned = value == null ? "" : value.strip().replaceAll("[\\p{Cntrl}]", "");
        return cleaned.substring(0, Math.min(max, cleaned.length()));
    }
}
