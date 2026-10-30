package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Current programme information for a nearby placed Technisat's PIN display. */
public record SyncTechnisatReceiverHudPayload(BlockPos pos, int mode, int frequencyKhz,
                                              boolean active, String label, String text,
                                              int bars, boolean rds, boolean ct, String time)
        implements CustomPacketPayload {
    public static final Type<SyncTechnisatReceiverHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_technisat_receiver_hud"));
    public static final StreamCodec<ByteBuf, SyncTechnisatReceiverHudPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.pos.asLong()); b.writeByte(p.mode); b.writeInt(p.frequencyKhz);
                b.writeBoolean(p.active);
                ByteBufCodecs.stringUtf8(288).encode(b, p.label);
                ByteBufCodecs.stringUtf8(768).encode(b, p.text);
                b.writeByte(p.bars); b.writeBoolean(p.rds); b.writeBoolean(p.ct);
                ByteBufCodecs.stringUtf8(8).encode(b, p.time);
            },
            b -> new SyncTechnisatReceiverHudPayload(BlockPos.of(b.readLong()), b.readUnsignedByte(),
                    b.readInt(), b.readBoolean(), ByteBufCodecs.stringUtf8(288).decode(b),
                    ByteBufCodecs.stringUtf8(768).decode(b), b.readUnsignedByte(), b.readBoolean(),
                    b.readBoolean(), ByteBufCodecs.stringUtf8(8).decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
