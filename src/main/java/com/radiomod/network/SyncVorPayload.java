package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncVorPayload(int containerId, int frequencyKhz, String identifier,
                             boolean antennaConnected, boolean dmeAttached) implements CustomPacketPayload {
    public static final Type<SyncVorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_vor"));
    public static final StreamCodec<ByteBuf, SyncVorPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.containerId); b.writeInt(p.frequencyKhz);
                ByteBufCodecs.stringUtf8(4).encode(b, p.identifier);
                b.writeBoolean(p.antennaConnected); b.writeBoolean(p.dmeAttached);
            }, b -> new SyncVorPayload(b.readInt(), b.readInt(), ByteBufCodecs.stringUtf8(4).decode(b),
                    b.readBoolean(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
