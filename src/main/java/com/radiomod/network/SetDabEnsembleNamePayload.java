package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDabEnsembleNamePayload(int containerId, String ensembleName) implements CustomPacketPayload {
    public static final Type<SetDabEnsembleNamePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_dab_ensemble_name"));
    public static final StreamCodec<ByteBuf, SetDabEnsembleNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SetDabEnsembleNamePayload::containerId,
            ByteBufCodecs.stringUtf8(64),
            SetDabEnsembleNamePayload::ensembleName,
            SetDabEnsembleNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
