package com.radiomod.server;

import com.radiomod.Radio;
import com.radiomod.network.AudioRelayChunkPayload;
import com.radiomod.network.RequestAudioRelayPayload;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends server-owned uploaded audio through the player's Minecraft connection. */
public final class AudioRelayManager {
    private static final int MAX_STREAMS_PER_PLAYER = 8;
    private static final Map<UUID, Map<Long, Relay>> ACTIVE = new ConcurrentHashMap<>();
    private AudioRelayManager() { }

    public static void start(ServerPlayer player, RequestAudioRelayPayload request) {
        String localUrl = ServerAudioProxy.localUploadedRelayUrl(request.token());
        if (localUrl.isBlank()) {
            PacketDistributor.sendToPlayer(player, new AudioRelayChunkPayload(request.sessionId(), new byte[0], true));
            return;
        }
        Map<Long, Relay> sessions = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
        if (sessions.size() >= MAX_STREAMS_PER_PLAYER && !sessions.containsKey(request.sessionId())) {
            PacketDistributor.sendToPlayer(player, new AudioRelayChunkPayload(request.sessionId(), new byte[0], true));
            return;
        }
        Relay relay = new Relay(player, request.sessionId(), localUrl, sessions);
        Relay old = sessions.put(request.sessionId(), relay);
        if (old != null) old.cancel();
        relay.start();
    }

    public static void stop(ServerPlayer player, long sessionId) {
        Map<Long, Relay> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        Relay relay = sessions.remove(sessionId);
        if (relay != null) relay.cancel();
    }

    public static void stopAll() {
        for (Map<Long, Relay> sessions : ACTIVE.values()) for (Relay relay : sessions.values()) relay.cancel();
        ACTIVE.clear();
    }

    private static final class Relay extends Thread {
        private final ServerPlayer player;
        private final long id;
        private final String localUrl;
        private final Map<Long, Relay> sessions;
        private volatile boolean running = true;
        private volatile HttpURLConnection connection;

        private Relay(ServerPlayer player, long id, String localUrl, Map<Long, Relay> sessions) {
            super("radio-uploaded-audio-relay");
            setDaemon(true);
            this.player = player; this.id = id; this.localUrl = localUrl; this.sessions = sessions;
        }
        @Override public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(localUrl).toURL().openConnection();
                connection = conn;
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() != 200) return;
                try (InputStream input = conn.getInputStream()) {
                    byte[] buffer = new byte[AudioRelayChunkPayload.MAX_CHUNK];
                    int count;
                    while (running && (count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        if (player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) break;
                        byte[] chunk = Arrays.copyOf(buffer, count);
                        player.getServer().execute(() -> {
                            if (running && sessions.get(id) == this)
                                PacketDistributor.sendToPlayer(player, new AudioRelayChunkPayload(id, chunk, false));
                        });
                    }
                }
            } catch (Exception ex) {
                if (running) Radio.LOGGER.warn("Uploaded audio relay stopped: {}", ex.getMessage());
            } finally {
                running = false;
                if (connection != null) connection.disconnect();
                sessions.remove(id, this);
                player.getServer().execute(() -> {
                    if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == player)
                        PacketDistributor.sendToPlayer(player, new AudioRelayChunkPayload(id, new byte[0], true));
                });
            }
        }
        private void cancel() {
            running = false;
            if (connection != null) connection.disconnect();
            interrupt();
        }
    }
}
