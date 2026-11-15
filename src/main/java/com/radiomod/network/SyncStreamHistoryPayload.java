package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncStreamHistoryPayload(int containerId, int page, int pageCount, String json)
        implements CustomPacketPayload {
    public static final Type<SyncStreamHistoryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_stream_history"));
    public static final StreamCodec<ByteBuf, SyncStreamHistoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncStreamHistoryPayload::containerId,
            ByteBufCodecs.VAR_INT, SyncStreamHistoryPayload::page,
            ByteBufCodecs.VAR_INT, SyncStreamHistoryPayload::pageCount,
            ByteBufCodecs.stringUtf8(16384), SyncStreamHistoryPayload::json,
            SyncStreamHistoryPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
