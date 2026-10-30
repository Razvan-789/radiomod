package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDabServerConfigPayload(int containerId, String action, String value) implements CustomPacketPayload {
    public static final Type<SetDabServerConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_dab_server_config"));
    public static final StreamCodec<ByteBuf, SetDabServerConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SetDabServerConfigPayload::containerId,
            ByteBufCodecs.stringUtf8(64),
            SetDabServerConfigPayload::action,
            ByteBufCodecs.stringUtf8(1024),
            SetDabServerConfigPayload::value,
            SetDabServerConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
