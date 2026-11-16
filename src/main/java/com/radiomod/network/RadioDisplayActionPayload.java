package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadioDisplayActionPayload(int containerId, int action) implements CustomPacketPayload {
    public static final Type<RadioDisplayActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "radio_display_action"));
    public static final StreamCodec<ByteBuf, RadioDisplayActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeInt(p.containerId); b.writeByte(p.action); },
            b -> new RadioDisplayActionPayload(b.readInt(), b.readUnsignedByte()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
