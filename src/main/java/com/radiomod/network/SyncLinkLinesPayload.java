package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Nearby, server-confirmed links for the linking tool's world overlay. */
public record SyncLinkLinesPayload(String dimension, List<Link> links) implements CustomPacketPayload {
    public record Link(BlockPos from, BlockPos to, byte kind) { }

    public static final byte ANTENNA = 0;
    public static final byte DAB_SERVER = 1;
    public static final byte ADSB_SERVER = 2;
    public static final int MAX_LINKS = 256;
    public static final Type<SyncLinkLinesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_link_lines"));
    public static final StreamCodec<ByteBuf, SyncLinkLinesPayload> STREAM_CODEC = StreamCodec.of(
            SyncLinkLinesPayload::write, SyncLinkLinesPayload::read);

    private static void write(ByteBuf buffer, SyncLinkLinesPayload payload) {
        ByteBufCodecs.stringUtf8(128).encode(buffer, payload.dimension);
        buffer.writeShort(payload.links.size());
        for (Link link : payload.links) {
            buffer.writeLong(link.from.asLong());
            buffer.writeLong(link.to.asLong());
            buffer.writeByte(link.kind);
        }
    }

    private static SyncLinkLinesPayload read(ByteBuf buffer) {
        String dimension = ByteBufCodecs.stringUtf8(128).decode(buffer);
        int count = buffer.readUnsignedShort();
        if (count > MAX_LINKS) throw new IllegalArgumentException("Too many link lines");
        List<Link> links = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            links.add(new Link(BlockPos.of(buffer.readLong()), BlockPos.of(buffer.readLong()), buffer.readByte()));
        }
        return new SyncLinkLinesPayload(dimension, List.copyOf(links));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
