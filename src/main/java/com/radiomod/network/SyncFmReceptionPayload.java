package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncFmReceptionPayload(
        boolean active,
        int signalDbuvTimes10,
        int snrDbTimes10,
        int rdsBerPercent,
        String streamUrl,
        String ps
)
        implements CustomPacketPayload {
    public static final Type<SyncFmReceptionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_fm_reception"));
    public static final StreamCodec<ByteBuf, SyncFmReceptionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SyncFmReceptionPayload::active,
            ByteBufCodecs.VAR_INT,
            SyncFmReceptionPayload::signalDbuvTimes10,
            ByteBufCodecs.VAR_INT,
            SyncFmReceptionPayload::snrDbTimes10,
            ByteBufCodecs.VAR_INT,
            SyncFmReceptionPayload::rdsBerPercent,
            ByteBufCodecs.stringUtf8(1024),
            SyncFmReceptionPayload::streamUrl,
            ByteBufCodecs.stringUtf8(128),
            SyncFmReceptionPayload::ps,
            SyncFmReceptionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
