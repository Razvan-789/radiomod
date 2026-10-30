package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NavigatorActionPayload(int containerId, int channel, int frequencyKhz)
        implements CustomPacketPayload {
    public static final Type<NavigatorActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "navigator_action"));
    public static final StreamCodec<ByteBuf, NavigatorActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeInt(p.containerId); b.writeByte(p.channel); b.writeInt(p.frequencyKhz); },
            b -> new NavigatorActionPayload(b.readInt(), b.readUnsignedByte(), b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
