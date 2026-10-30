package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectDx3StationPayload(int containerId, int stationIndex, boolean dab, int tuning,
                                      String sid, String mux) implements CustomPacketPayload {
    public static final Type<SelectDx3StationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "select_dx3_station"));
    public static final StreamCodec<ByteBuf, SelectDx3StationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectDx3StationPayload::containerId,
            ByteBufCodecs.VAR_INT, SelectDx3StationPayload::stationIndex,
            ByteBufCodecs.BOOL, SelectDx3StationPayload::dab,
            ByteBufCodecs.VAR_INT, SelectDx3StationPayload::tuning,
            ByteBufCodecs.stringUtf8(16), SelectDx3StationPayload::sid,
            ByteBufCodecs.stringUtf8(64), SelectDx3StationPayload::mux,
            SelectDx3StationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
