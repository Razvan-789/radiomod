package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncSiteNamePayload(int containerId, String name) implements CustomPacketPayload {
    public static final Type<SyncSiteNamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_site_name"));
    public static final StreamCodec<ByteBuf, SyncSiteNamePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.containerId);
                ByteBufCodecs.stringUtf8(64).encode(buffer, payload.name);
            }, buffer -> new SyncSiteNamePayload(buffer.readInt(), ByteBufCodecs.stringUtf8(64).decode(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
