package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDx3LinkModePayload(int containerId, int mode) implements CustomPacketPayload {
    public static final Type<SetDx3LinkModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_dx3_link_mode"));
    public static final StreamCodec<ByteBuf, SetDx3LinkModePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetDx3LinkModePayload::containerId,
            ByteBufCodecs.VAR_INT, SetDx3LinkModePayload::mode,
            SetDx3LinkModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
