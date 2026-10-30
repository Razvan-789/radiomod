package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Button input for the Technisat portable radio or placed receiver. */
public record TechnisatActionPayload(int containerId, int action) implements CustomPacketPayload {
    public static final Type<TechnisatActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "technisat_action"));
    public static final StreamCodec<ByteBuf, TechnisatActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeInt(p.containerId); b.writeByte(p.action); },
            b -> new TechnisatActionPayload(b.readInt(), b.readUnsignedByte()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
