package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SiteNameActionPayload(int containerId, boolean save, String name) implements CustomPacketPayload {
    public static final Type<SiteNameActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "site_name_action"));
    public static final StreamCodec<ByteBuf, SiteNameActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.containerId); buffer.writeBoolean(payload.save);
                ByteBufCodecs.stringUtf8(64).encode(buffer, payload.name);
            }, buffer -> new SiteNameActionPayload(buffer.readInt(), buffer.readBoolean(),
                    ByteBufCodecs.stringUtf8(64).decode(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
