package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UploadAudioChunkPayload(int containerId, String target, String filename, int offset, int totalBytes, byte[] data)
        implements CustomPacketPayload {
    public static final Type<UploadAudioChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "upload_audio_chunk"));
    public static final StreamCodec<ByteBuf, UploadAudioChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UploadAudioChunkPayload::containerId,
            ByteBufCodecs.stringUtf8(8), UploadAudioChunkPayload::target,
            ByteBufCodecs.stringUtf8(128), UploadAudioChunkPayload::filename,
            ByteBufCodecs.VAR_INT, UploadAudioChunkPayload::offset,
            ByteBufCodecs.VAR_INT, UploadAudioChunkPayload::totalBytes,
            ByteBufCodecs.byteArray(64 * 1024), UploadAudioChunkPayload::data,
            UploadAudioChunkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
