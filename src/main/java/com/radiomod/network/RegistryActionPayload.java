package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RegistryActionPayload(int containerId, String action, int value, String text)
        implements CustomPacketPayload {
    public static final Type<RegistryActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "registry_action"));
    public static final StreamCodec<ByteBuf, RegistryActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.containerId);
                ByteBufCodecs.stringUtf8(24).encode(b, p.action);
                b.writeInt(p.value);
                ByteBufCodecs.stringUtf8(160).encode(b, p.text);
            },
            b -> new RegistryActionPayload(b.readInt(), ByteBufCodecs.stringUtf8(24).decode(b),
                    b.readInt(), ByteBufCodecs.stringUtf8(160).decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
