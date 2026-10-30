package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AudioLibraryActionPayload(int containerId, String action, String id, String name, int page)
        implements CustomPacketPayload {
    public static final Type<AudioLibraryActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "audio_library_action"));
    public static final StreamCodec<ByteBuf, AudioLibraryActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AudioLibraryActionPayload::containerId,
            ByteBufCodecs.stringUtf8(16), AudioLibraryActionPayload::action,
            ByteBufCodecs.stringUtf8(36), AudioLibraryActionPayload::id,
            ByteBufCodecs.stringUtf8(80), AudioLibraryActionPayload::name,
            ByteBufCodecs.VAR_INT, AudioLibraryActionPayload::page,
            AudioLibraryActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
