package com.radiomod.server;

import com.radiomod.Radio;
import com.radiomod.menu.DabServerMenu;
import com.radiomod.menu.RecorderMenu;
import com.radiomod.menu.TransmitterMenu;
import com.radiomod.network.UploadAudioChunkPayload;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Reassembles bounded client uploads on disk and assigns valid audio to the open station. */
public final class AudioUploadManager {
    public static final int MAX_BYTES = 128 * 1024 * 1024;
    public static final int CHUNK_BYTES = 64 * 1024;
    private static final Map<UUID, Upload> ACTIVE = new HashMap<>();

    private AudioUploadManager() {
    }

    public static void accept(ServerPlayer player, UploadAudioChunkPayload chunk) {
        if (chunk.totalBytes() < 1 || chunk.totalBytes() > MAX_BYTES
                || chunk.data().length < 1 || chunk.data().length > CHUNK_BYTES
                || chunk.offset() < 0 || chunk.offset() + chunk.data().length > chunk.totalBytes()
                || player.containerMenu.containerId != chunk.containerId()
                || !(chunk.target().equals("fm") || chunk.target().equals("dab") || chunk.target().equals("library"))) {
            discard(player.getUUID());
            return;
        }
        if (!ServerAudioProxy.canPlayUploadedAudio()) {
            discard(player.getUUID());
            if (chunk.offset() == 0) {
                notify(player, "Local audio needs FFmpeg and the server audio proxy");
            }
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        Upload upload = ACTIVE.get(player.getUUID());
        if (chunk.offset() == 0) {
            discard(player.getUUID());
            if (!validMenu(menu, chunk.target())) {
                return;
            }
            try {
                upload = new Upload(player.getUUID(), menu, chunk.target(), chunk.filename(), chunk.totalBytes());
                ACTIVE.put(player.getUUID(), upload);
            } catch (IOException ex) {
                notifyFailure(player, ex);
                return;
            }
        }
        if (upload == null || upload.menu != menu || !upload.target.equals(chunk.target())
                || upload.totalBytes != chunk.totalBytes() || upload.receivedBytes != chunk.offset()
                || System.currentTimeMillis() - upload.lastChunkAt > 30_000L) {
            discard(player.getUUID());
            return;
        }
        try {
            upload.output.write(chunk.data());
            upload.receivedBytes += chunk.data().length;
            upload.lastChunkAt = System.currentTimeMillis();
            if (upload.receivedBytes == upload.totalBytes) {
                ACTIVE.remove(player.getUUID());
                upload.output.close();
                if (!validMenu(menu, upload.target) || !matchesStation(upload)) {
                    upload.deleteTemporary();
                    notify(player, "Upload cancelled: the station selection changed");
                    return;
                }
                notify(player, "Audio received. Checking duration before saving...");
                finishInBackground(player, upload);
            }
        } catch (IOException ex) {
            discard(player.getUUID());
            upload.deleteTemporary();
            notifyFailure(player, ex);
        }
    }

    private static void finishInBackground(ServerPlayer player, Upload upload) {
        MinecraftServer server = player.getServer();
        Thread worker = new Thread(() -> {
            try {
                String source = ServerAudioProxy.finishUploadedAudio(player.getUUID(),
                        upload.temporary, upload.totalBytes, upload.filename);
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
                        return;
                    }
                    if (upload.target.equals("library")) {
                        notify(player, "Audio saved to your library");
                        if (player.containerMenu == upload.menu) {
                            try {
                                AudioLibraryManager.sendPage(player, 0);
                            } catch (IOException ex) {
                                Radio.LOGGER.warn("Could not refresh audio library for {}", player.getUUID(), ex);
                            }
                        }
                        return;
                    }
                    if (player.containerMenu != upload.menu || !validMenu(upload.menu, upload.target)
                            || !matchesStation(upload)) {
                        notify(player, "Upload finished, but the station screen changed before it could be applied");
                        return;
                    }
                    if (upload.menu instanceof TransmitterMenu fm) {
                        fm.setUploadedAudioSource(source);
                    } else if (upload.menu instanceof DabServerMenu dab) {
                        dab.setUploadedAudioSource(source);
                    }
                    notify(player, "Audio uploaded. The station will play the file on repeat.");
                });
            } catch (IOException ex) {
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                        notifyFailure(player, ex);
                    }
                });
            } finally {
                upload.deleteTemporary();
            }
        }, "radio-audio-upload-check");
        worker.setDaemon(true);
        worker.start();
    }

    public static void prune(ServerPlayer player) {
        Upload upload = ACTIVE.get(player.getUUID());
        if (upload != null && (upload.menu != player.containerMenu
                || System.currentTimeMillis() - upload.lastChunkAt > 30_000L)) {
            discard(player.getUUID());
        }
    }

    public static void clear() {
        for (UUID playerId : ACTIVE.keySet().toArray(UUID[]::new)) {
            discard(playerId);
        }
    }

    private static void discard(UUID playerId) {
        Upload removed = ACTIVE.remove(playerId);
        if (removed != null) {
            try {
                removed.output.close();
            } catch (IOException ignored) {
            }
            removed.deleteTemporary();
        }
    }

    private static boolean validMenu(AbstractContainerMenu menu, String target) {
        return target.equals("fm") ? menu instanceof TransmitterMenu
                : target.equals("library") ? menu instanceof RecorderMenu
                : menu instanceof DabServerMenu dab && dab.getMode() == DabServerMenu.MODE_STATION_DETAIL;
    }

    private static boolean matchesStation(Upload upload) {
        return !(upload.menu instanceof DabServerMenu dab)
                || dab.matchesUploadTarget(upload.ensembleIndex, upload.stationIndex, upload.sid);
    }

    private static void notify(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
    }

    private static void notifyFailure(ServerPlayer player, IOException ex) {
        Radio.LOGGER.error("Audio upload failed for player {}", player.getUUID(), ex);
        String detail = ex.getMessage();
        notify(player, "Audio upload failed: " + (detail == null || detail.isBlank()
                ? ex.getClass().getSimpleName() : detail));
    }

    private static final class Upload {
        private final AbstractContainerMenu menu;
        private final String target;
        private final String filename;
        private final int totalBytes;
        private final int ensembleIndex;
        private final int stationIndex;
        private final String sid;
        private final Path temporary;
        private final OutputStream output;
        private int receivedBytes;
        private long lastChunkAt = System.currentTimeMillis();

        private Upload(UUID playerId, AbstractContainerMenu menu, String target, String filename, int totalBytes) throws IOException {
            this.menu = menu;
            this.target = target;
            this.filename = filename;
            this.totalBytes = totalBytes;
            if (menu instanceof DabServerMenu dab) {
                ensembleIndex = dab.getSelectedEnsembleIndex();
                stationIndex = dab.getSelectedStationIndex();
                sid = dab.getStationSid();
            } else {
                ensembleIndex = -1;
                stationIndex = -1;
                sid = "";
            }
            temporary = ServerAudioProxy.createTemporaryUpload(playerId);
            try {
                output = Files.newOutputStream(temporary, StandardOpenOption.WRITE);
            } catch (IOException ex) {
                Files.deleteIfExists(temporary);
                throw ex;
            }
        }

        private void deleteTemporary() {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }
}
