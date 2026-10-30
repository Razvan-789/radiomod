package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StopAudioRelayPayload(long sessionId) implements CustomPacketPayload {
    public static final Type<StopAudioRelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "stop_audio_relay"));
    public static final StreamCodec<ByteBuf, StopAudioRelayPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> b.writeLong(p.sessionId), b -> new StopAudioRelayPayload(b.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
