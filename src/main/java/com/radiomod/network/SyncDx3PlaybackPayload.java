package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDx3PlaybackPayload(int channelIndex, String sid, String mux, String streamUrl, String sourceUrl,
                                     int signalTimes10, int snrTimes10) implements CustomPacketPayload {
    public static final Type<SyncDx3PlaybackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_playback"));
    public static final StreamCodec<ByteBuf, SyncDx3PlaybackPayload> STREAM_CODEC = StreamCodec.of(
            SyncDx3PlaybackPayload::write, SyncDx3PlaybackPayload::read);

    private static void write(ByteBuf buffer, SyncDx3PlaybackPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.channelIndex);
        ByteBufCodecs.stringUtf8(16).encode(buffer, payload.sid);
        ByteBufCodecs.stringUtf8(64).encode(buffer, payload.mux);
        ByteBufCodecs.stringUtf8(1024).encode(buffer, payload.streamUrl);
        ByteBufCodecs.stringUtf8(1024).encode(buffer, payload.sourceUrl);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.signalTimes10);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.snrTimes10);
    }

    private static SyncDx3PlaybackPayload read(ByteBuf buffer) {
        return new SyncDx3PlaybackPayload(ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(16).decode(buffer), ByteBufCodecs.stringUtf8(64).decode(buffer),
                ByteBufCodecs.stringUtf8(1024).decode(buffer), ByteBufCodecs.stringUtf8(1024).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
