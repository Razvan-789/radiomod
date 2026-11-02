package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncAudioLibraryPayload(int containerId, int page, int pageCount, String entries,
                                      long usedBytes, long limitBytes)
        implements CustomPacketPayload {
    public static final Type<SyncAudioLibraryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_audio_library"));
    public static final StreamCodec<ByteBuf, SyncAudioLibraryPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.containerId);
                buffer.writeInt(payload.page);
                buffer.writeInt(payload.pageCount);
                ByteBufCodecs.stringUtf8(4096).encode(buffer, payload.entries);
                buffer.writeLong(payload.usedBytes);
                buffer.writeLong(payload.limitBytes);
            },
            buffer -> new SyncAudioLibraryPayload(buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    ByteBufCodecs.stringUtf8(4096).decode(buffer), buffer.readLong(), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
