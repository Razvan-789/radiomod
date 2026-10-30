package com.radiomod.client;

import com.radiomod.network.AudioRelayChunkPayload;
import com.radiomod.network.RequestAudioRelayPayload;
import com.radiomod.network.StopAudioRelayPayload;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** A blocking input stream backed by MP3 chunks arriving over the game connection. */
public final class AudioRelayClient {
    private static final byte[] END = new byte[0];
    private static final Map<Long, RelayInput> STREAMS = new ConcurrentHashMap<>();
    private AudioRelayClient() { }

    public static InputStream open(String token) {
        long id;
        do { id = ThreadLocalRandom.current().nextLong(); } while (id == 0 || STREAMS.containsKey(id));
        RelayInput input = new RelayInput(id);
        STREAMS.put(id, input);
        Minecraft.getInstance().execute(() -> {
            if (STREAMS.get(input.id) == input)
                PacketDistributor.sendToServer(new RequestAudioRelayPayload(input.id, token));
        });
        return input;
    }

    public static void accept(AudioRelayChunkPayload chunk) {
        RelayInput input = STREAMS.get(chunk.sessionId());
        if (input == null || input.closed || input.terminal) return;
        if (chunk.end()) {
            input.terminal = true;
            // Preserve audio already received before the end marker.
            if (!input.queue.offer(END)) {
                input.queue.clear();
                input.queue.offer(END);
            }
        } else if (chunk.bytes().length > 0 && !input.queue.offer(chunk.bytes())) {
            // Dropping MP3 bytes would corrupt playback. End this stream and let the
            // decoder reconnect instead of silently playing damaged data.
            input.terminal = true;
            input.queue.clear();
            input.queue.offer(END);
        }
    }

    private static final class RelayInput extends InputStream {
        private final long id;
        private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(128);
        private byte[] current = END;
        private int offset;
        private volatile boolean closed;
        private volatile boolean terminal;
        private RelayInput(long id) { this.id = id; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }
        @Override public int read(byte[] destination, int off, int length) throws IOException {
            if (destination == null) throw new NullPointerException();
            if (off < 0 || length < 0 || off > destination.length - length) throw new IndexOutOfBoundsException();
            if (length == 0) return 0;
            if (closed) return -1;
            while (offset >= current.length) {
                try {
                    current = queue.poll(20, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    close();
                    throw new IOException("Uploaded audio relay interrupted", ex);
                }
                if (current == null) {
                    close();
                    throw new IOException("Uploaded audio relay timed out");
                }
                offset = 0;
                if (current.length == 0) { close(); return -1; }
            }
            int count = Math.min(length, current.length - offset);
            System.arraycopy(current, offset, destination, off, count);
            offset += count;
            return count;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            STREAMS.remove(id, this);
            queue.clear(); queue.offer(END);
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().getConnection() != null)
                    PacketDistributor.sendToServer(new StopAudioRelayPayload(id));
            });
        }
    }
}
