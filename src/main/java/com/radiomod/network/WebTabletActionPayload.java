package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WebTabletActionPayload(int containerId, String action, String dimension,
                                     long position, String value, int number) implements CustomPacketPayload {
    public static final Type<WebTabletActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "web_tablet_action"));
    public static final StreamCodec<ByteBuf, WebTabletActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.containerId); ByteBufCodecs.stringUtf8(16).encode(b, p.action);
                ByteBufCodecs.stringUtf8(128).encode(b, p.dimension); b.writeLong(p.position);
                ByteBufCodecs.stringUtf8(2048).encode(b, p.value); b.writeInt(p.number);
            },
            b -> new WebTabletActionPayload(b.readInt(), ByteBufCodecs.stringUtf8(16).decode(b),
                    ByteBufCodecs.stringUtf8(128).decode(b), b.readLong(),
                    ByteBufCodecs.stringUtf8(2048).decode(b), b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
