package com.radiomod.server;

import com.radiomod.Radio;
import com.radiomod.menu.DabServerMenu;
import com.radiomod.menu.RecorderMenu;
import com.radiomod.menu.TransmitterMenu;
import com.radiomod.menu.DrmTransmitterMenu;
import com.radiomod.network.AudioLibraryActionPayload;
import com.radiomod.network.SyncAudioLibraryPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned file catalog. Every action checks the open menu and the file owner. */
public final class AudioLibraryManager {
    private static final int PAGE_SIZE = 8;

    private AudioLibraryManager() {}

    public static void handle(ServerPlayer player, AudioLibraryActionPayload request) {
        if (player.containerMenu.containerId != request.containerId() || !validMenu(player)) {
            return;
        }
        try {
            switch (request.action()) {
                case "list" -> sendPage(player, request.page());
                case "rename", "delete" -> {
                    if (!(player.containerMenu instanceof RecorderMenu)) {
                        return;
                    }
                    UUID id = UUID.fromString(request.id());
                    if (request.action().equals("rename")) {
                        ServerAudioProxy.renameUploadedAudio(player.getUUID(), id, request.name());
                    } else {
                        ServerAudioProxy.deleteUploadedAudio(player.getUUID(), id);
                    }
                    sendPage(player, request.page());
                }
                case "select" -> {
                    UUID id = UUID.fromString(request.id());
                    if (!ServerAudioProxy.ownsUploadedAudio(player.getUUID(), id)) {
                        throw new IOException("Audio file was not found in your library");
                    }
                    // Keep the station's start time in its saved source URL. Each station can
                    // run the same file on its own clock, including across server restarts.
                    String source = ServerAudioProxy.restartUploadedAudioClock(
                            "radio-file://" + player.getUUID() + "/" + id);
                    if (player.containerMenu instanceof TransmitterMenu fm) {
                        fm.setUploadedAudioSource(source);
                    } else if (player.containerMenu instanceof DrmTransmitterMenu drm) {
                        drm.setUploadedAudioSource(source);
                    } else if (player.containerMenu instanceof DabServerMenu dab
                            && dab.getMode() == DabServerMenu.MODE_STATION_DETAIL) {
                        dab.setUploadedAudioSource(source);
                    }
                }
                default -> { }
            }
        } catch (IOException | IllegalArgumentException ex) {
            Radio.LOGGER.warn("Audio library action {} failed for {}", request.action(), player.getUUID(), ex);
            String detail = ex.getMessage();
            player.displayClientMessage(Component.literal("Audio library: "
                    + (detail == null || detail.isBlank() ? ex.getClass().getSimpleName() : detail)), false);
        }
    }

    public static void sendPage(ServerPlayer player, int requestedPage) throws IOException {
        if (!validMenu(player)) {
            return;
        }
        List<ServerAudioProxy.StoredAudio> entries = ServerAudioProxy.listUploadedAudio(player.getUUID());
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        StringBuilder encoded = new StringBuilder();
        int end = Math.min(entries.size(), (page + 1) * PAGE_SIZE);
        for (int index = page * PAGE_SIZE; index < end; index++) {
            ServerAudioProxy.StoredAudio entry = entries.get(index);
            encoded.append(entry.id()).append('|').append(entry.bytes()).append('|')
                    .append(Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(entry.name().getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        PacketDistributor.sendToPlayer(player,
                new SyncAudioLibraryPayload(player.containerMenu.containerId, page, pageCount, encoded.toString()));
    }

    private static boolean validMenu(ServerPlayer player) {
        return player.containerMenu instanceof RecorderMenu
                || player.containerMenu instanceof TransmitterMenu
                || player.containerMenu instanceof DrmTransmitterMenu
                || player.containerMenu instanceof DabServerMenu dab
                    && dab.getMode() == DabServerMenu.MODE_STATION_DETAIL;
    }
}
