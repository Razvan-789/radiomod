package com.radiomod.client;

import com.radiomod.Radio;
import com.radiomod.network.UploadAudioChunkPayload;
import com.radiomod.server.AudioUploadManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Native file picker and paced client-to-server audio transfer. */
public final class LocalAudioUpload {
    private static final Set<String> EXTENSIONS = Set.of("mp3", "wav", "ogg", "opus", "flac", "aac", "m4a", "mp4");
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private LocalAudioUpload() {
    }

    public static void chooseAndUpload(int containerId, String target) {
        if (!BUSY.compareAndSet(false, true)) {
            message("An audio upload is already in progress");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                String selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                        "Choose station audio", null, null, null, false);
                if (selectedPath == null) {
                    return;
                }
                File selected = new File(selectedPath);
                String uploadName = selected.getName().substring(0, Math.min(80, selected.getName().length()));
                String name = selected.getName().toLowerCase(Locale.ROOT);
                int dot = name.lastIndexOf('.');
                if (dot < 0 || !EXTENSIONS.contains(name.substring(dot + 1))) {
                    message("Choose an MP3, WAV, OGG, Opus, FLAC, AAC, M4A or MP4 audio file");
                    return;
                }
                long size = Files.size(selected.toPath());
                if (size < 1 || size > AudioUploadManager.MAX_BYTES) {
                    message("Audio file must be 1 byte to 128 MiB");
                    return;
                }
                message("Uploading " + selected.getName() + " — keep this screen open");
                AtomicBoolean cancelled = new AtomicBoolean();
                Minecraft minecraft = Minecraft.getInstance();
                try (InputStream input = Files.newInputStream(selected.toPath())) {
                    for (int offset = 0; offset < size && !cancelled.get();) {
                        int start = offset;
                        byte[] chunk = input.readNBytes((int) Math.min(AudioUploadManager.CHUNK_BYTES, size - offset));
                        if (chunk.length == 0) {
                            throw new IOException("Audio file changed during upload");
                        }
                        minecraft.execute(() -> {
                            if (minecraft.player == null || minecraft.player.containerMenu.containerId != containerId) {
                                cancelled.set(true);
                                return;
                            }
                            PacketDistributor.sendToServer(new UploadAudioChunkPayload(containerId, target, uploadName,
                                    start, (int) size, chunk));
                        });
                        offset += chunk.length;
                        Thread.sleep(20L);
                    }
                }
                if (cancelled.get()) {
                    message("Audio upload cancelled because the station screen closed");
                } else {
                    Thread.sleep(250L);
                }
            } catch (Exception ex) {
                Radio.LOGGER.error("Local audio upload failed", ex);
                String detail = ex.getMessage();
                message("Audio upload failed: " + (detail == null || detail.isBlank()
                        ? ex.getClass().getSimpleName() : detail));
            } finally {
                BUSY.set(false);
            }
        }, "radio-local-audio-upload");
        worker.setDaemon(true);
        worker.start();
    }

    private static void message(String text) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(text), false);
            }
        });
    }
}
