package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AudioRelayChunkPayload(long sessionId, byte[] bytes, boolean end) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 8192;
    public static final Type<AudioRelayChunkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "audio_relay_chunk"));
    public static final StreamCodec<ByteBuf, AudioRelayChunkPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeLong(p.sessionId); b.writeBoolean(p.end); b.writeInt(p.bytes.length); b.writeBytes(p.bytes); },
            b -> {
                long id = b.readLong(); boolean end = b.readBoolean(); int length = b.readInt();
                if (length < 0 || length > MAX_CHUNK) throw new IllegalArgumentException("Invalid audio relay chunk");
                byte[] bytes = new byte[length]; b.readBytes(bytes);
                return new AudioRelayChunkPayload(id, bytes, end);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
