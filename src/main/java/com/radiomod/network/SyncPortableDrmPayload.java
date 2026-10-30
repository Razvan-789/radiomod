package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncPortableDrmPayload(int frequencyKhz, boolean active, boolean muted, boolean pinned,
                                     String shortLabel, String serviceLabel,
                                     String message, String imageUrl, int mode, int bandwidthKhz,
                                     int opusBitrateKbps, int mp3BitrateKbps, int signalTenths, int snrTenths,
                                     int powerW, String streamUrl, String sourceUrl) implements CustomPacketPayload {
    public static final Type<SyncPortableDrmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_portable_drm"));
    public static final StreamCodec<ByteBuf, SyncPortableDrmPayload> STREAM_CODEC =
            StreamCodec.of(SyncPortableDrmPayload::write, SyncPortableDrmPayload::read);
    private static void write(ByteBuf b, SyncPortableDrmPayload p) {
        b.writeInt(p.frequencyKhz); b.writeBoolean(p.active); b.writeBoolean(p.muted); b.writeBoolean(p.pinned);
        ByteBufCodecs.stringUtf8(16).encode(b, p.shortLabel);
        ByteBufCodecs.stringUtf8(64).encode(b, p.serviceLabel);
        ByteBufCodecs.stringUtf8(256).encode(b, p.message);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.imageUrl);
        b.writeInt(p.mode); b.writeInt(p.bandwidthKhz); b.writeInt(p.opusBitrateKbps); b.writeInt(p.mp3BitrateKbps);
        b.writeInt(p.signalTenths); b.writeInt(p.snrTenths); b.writeInt(p.powerW);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.streamUrl);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.sourceUrl);
    }
    private static SyncPortableDrmPayload read(ByteBuf b) {
        return new SyncPortableDrmPayload(b.readInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                ByteBufCodecs.stringUtf8(16).decode(b), ByteBufCodecs.stringUtf8(64).decode(b),
                ByteBufCodecs.stringUtf8(256).decode(b), ByteBufCodecs.stringUtf8(1024).decode(b),
                b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(),
                ByteBufCodecs.stringUtf8(1024).decode(b), ByteBufCodecs.stringUtf8(1024).decode(b));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
