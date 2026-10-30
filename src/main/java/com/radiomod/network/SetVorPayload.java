package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetVorPayload(int containerId, int frequencyKhz, String identifier) implements CustomPacketPayload {
    public static final Type<SetVorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_vor"));
    public static final StreamCodec<ByteBuf, SetVorPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeInt(p.containerId); b.writeInt(p.frequencyKhz);
                ByteBufCodecs.stringUtf8(4).encode(b, p.identifier); },
            b -> new SetVorPayload(b.readInt(), b.readInt(), ByteBufCodecs.stringUtf8(4).decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
