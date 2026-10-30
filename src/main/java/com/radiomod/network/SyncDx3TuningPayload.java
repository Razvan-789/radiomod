package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDx3TuningPayload(int mode, int frequencyKhz, int channelIndex, String sid,
                                   String mux, String pi, String linkId, int linkMode) implements CustomPacketPayload {
    public static final Type<SyncDx3TuningPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_tuning"));
    public static final StreamCodec<ByteBuf, SyncDx3TuningPayload> STREAM_CODEC = StreamCodec.of(
            SyncDx3TuningPayload::write, SyncDx3TuningPayload::read);

    private static void write(ByteBuf buffer, SyncDx3TuningPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.mode);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.frequencyKhz);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.channelIndex);
        ByteBufCodecs.stringUtf8(16).encode(buffer, payload.sid);
        ByteBufCodecs.stringUtf8(64).encode(buffer, payload.mux);
        ByteBufCodecs.stringUtf8(16).encode(buffer, payload.pi);
        ByteBufCodecs.stringUtf8(16).encode(buffer, payload.linkId);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.linkMode);
    }

    private static SyncDx3TuningPayload read(ByteBuf buffer) {
        return new SyncDx3TuningPayload(ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(16).decode(buffer), ByteBufCodecs.stringUtf8(64).decode(buffer),
                ByteBufCodecs.stringUtf8(16).decode(buffer), ByteBufCodecs.stringUtf8(16).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
