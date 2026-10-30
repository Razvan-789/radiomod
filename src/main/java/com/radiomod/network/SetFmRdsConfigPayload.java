package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetFmRdsConfigPayload(int containerId, String action, String value) implements CustomPacketPayload {
    public static final Type<SetFmRdsConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_fm_rds_config"));
    public static final StreamCodec<ByteBuf, SetFmRdsConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SetFmRdsConfigPayload::containerId,
            ByteBufCodecs.stringUtf8(64),
            SetFmRdsConfigPayload::action,
            ByteBufCodecs.stringUtf8(2048),
            SetFmRdsConfigPayload::value,
            SetFmRdsConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
