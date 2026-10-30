package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens one uploaded-audio stream over the existing Minecraft connection. */
public record RequestAudioRelayPayload(long sessionId, String token) implements CustomPacketPayload {
    public static final Type<RequestAudioRelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "request_audio_relay"));
    public static final StreamCodec<ByteBuf, RequestAudioRelayPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeLong(p.sessionId); ByteBufCodecs.stringUtf8(2048).encode(b, p.token); },
            b -> new RequestAudioRelayPayload(b.readLong(), ByteBufCodecs.stringUtf8(2048).decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
