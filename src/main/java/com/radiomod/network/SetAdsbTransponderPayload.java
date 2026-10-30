package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetAdsbTransponderPayload(int containerId, String identifier, String operator)
        implements CustomPacketPayload {
    public static final Type<SetAdsbTransponderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_adsb_transponder"));
    public static final StreamCodec<ByteBuf, SetAdsbTransponderPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.containerId);
                ByteBufCodecs.stringUtf8(24).encode(b, p.identifier);
                ByteBufCodecs.stringUtf8(32).encode(b, p.operator);
            }, b -> new SetAdsbTransponderPayload(b.readInt(),
                    ByteBufCodecs.stringUtf8(24).decode(b), ByteBufCodecs.stringUtf8(32).decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
