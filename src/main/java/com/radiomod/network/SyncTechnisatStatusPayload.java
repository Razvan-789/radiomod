package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncTechnisatStatusPayload(int containerId, int mode, int frequencyKhz,
                                         int channelIndex, boolean scanned, boolean muted,
                                         boolean scanning, int scanChannelIndex, int scanStationCount,
                                         String artworkUrl)
        implements CustomPacketPayload {
    public boolean dab() { return mode == 1; }
    public boolean drm() { return mode == 2; }
    public static final Type<SyncTechnisatStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_technisat_status"));
    public static final StreamCodec<ByteBuf, SyncTechnisatStatusPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.containerId); b.writeByte(p.mode); b.writeInt(p.frequencyKhz);
                b.writeInt(p.channelIndex); b.writeBoolean(p.scanned); b.writeBoolean(p.muted);
                b.writeBoolean(p.scanning); b.writeByte(p.scanChannelIndex); b.writeShort(p.scanStationCount);
                net.minecraft.network.codec.ByteBufCodecs.stringUtf8(1024).encode(b, p.artworkUrl);
            },
            b -> new SyncTechnisatStatusPayload(b.readInt(), b.readUnsignedByte(), b.readInt(),
                    b.readInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                    b.readUnsignedByte(), b.readUnsignedShort(),
                    net.minecraft.network.codec.ByteBufCodecs.stringUtf8(1024).decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
