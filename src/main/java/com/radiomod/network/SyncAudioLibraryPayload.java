package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncAudioLibraryPayload(int containerId, int page, int pageCount, String entries)
        implements CustomPacketPayload {
    public static final Type<SyncAudioLibraryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_audio_library"));
    public static final StreamCodec<ByteBuf, SyncAudioLibraryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncAudioLibraryPayload::containerId,
            ByteBufCodecs.VAR_INT, SyncAudioLibraryPayload::page,
            ByteBufCodecs.VAR_INT, SyncAudioLibraryPayload::pageCount,
            ByteBufCodecs.stringUtf8(4096), SyncAudioLibraryPayload::entries,
            SyncAudioLibraryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
