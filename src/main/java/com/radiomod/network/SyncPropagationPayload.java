package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncPropagationPayload(int containerId, String meteor, String sporadic,
                                     String tropo, String aurora) implements CustomPacketPayload {
    public static final Type<SyncPropagationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_propagation"));
    public static final StreamCodec<ByteBuf, SyncPropagationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                net.minecraft.network.FriendlyByteBuf b = new net.minecraft.network.FriendlyByteBuf(buffer);
                b.writeVarInt(payload.containerId);
                b.writeUtf(payload.meteor, 256); b.writeUtf(payload.sporadic, 256);
                b.writeUtf(payload.tropo, 256); b.writeUtf(payload.aurora, 256);
            }, buffer -> {
                net.minecraft.network.FriendlyByteBuf b = new net.minecraft.network.FriendlyByteBuf(buffer);
                return new SyncPropagationPayload(b.readVarInt(), b.readUtf(256), b.readUtf(256),
                        b.readUtf(256), b.readUtf(256));
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
