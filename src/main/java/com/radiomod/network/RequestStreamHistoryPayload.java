package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestStreamHistoryPayload(int containerId, int page) implements CustomPacketPayload {
    public static final Type<RequestStreamHistoryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "request_stream_history"));
    public static final StreamCodec<ByteBuf, RequestStreamHistoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestStreamHistoryPayload::containerId,
            ByteBufCodecs.VAR_INT, RequestStreamHistoryPayload::page,
            RequestStreamHistoryPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
