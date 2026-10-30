package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDrmTextPayload(int containerId, String field, String value) implements CustomPacketPayload {
    public static final Type<SetDrmTextPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "set_drm_text"));
    public static final StreamCodec<ByteBuf, SetDrmTextPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.containerId);
                ByteBufCodecs.stringUtf8(16).encode(buffer, payload.field);
                ByteBufCodecs.stringUtf8(1024).encode(buffer, payload.value);
            },
            buffer -> new SetDrmTextPayload(buffer.readInt(), ByteBufCodecs.stringUtf8(16).decode(buffer),
                    ByteBufCodecs.stringUtf8(1024).decode(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
