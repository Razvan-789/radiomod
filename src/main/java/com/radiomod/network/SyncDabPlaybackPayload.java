package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDabPlaybackPayload(int channelIndex, String streamUrl, String sourceUrl,
                                     int signalTimes10, int snrTimes10) implements CustomPacketPayload {
    public static final Type<SyncDabPlaybackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dab_playback"));
    public static final StreamCodec<ByteBuf, SyncDabPlaybackPayload> STREAM_CODEC = StreamCodec.of(
            SyncDabPlaybackPayload::write, SyncDabPlaybackPayload::read);

    private static void write(ByteBuf buffer, SyncDabPlaybackPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.channelIndex);
        ByteBufCodecs.stringUtf8(1024).encode(buffer, payload.streamUrl);
        ByteBufCodecs.stringUtf8(1024).encode(buffer, payload.sourceUrl);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.signalTimes10);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.snrTimes10);
    }

    private static SyncDabPlaybackPayload read(ByteBuf buffer) {
        return new SyncDabPlaybackPayload(ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(1024).decode(buffer), ByteBufCodecs.stringUtf8(1024).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
