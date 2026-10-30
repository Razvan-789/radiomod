package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDx3ReceiverStatePayload(int containerId, boolean dab, int frequencyKhz, int channelIndex,
        String sid, String mux, String pi, int linkMode, boolean muted, boolean antenna) implements CustomPacketPayload {
    public static final Type<SyncDx3ReceiverStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_receiver_state"));
    public static final StreamCodec<ByteBuf, SyncDx3ReceiverStatePayload> STREAM_CODEC =
            StreamCodec.of(SyncDx3ReceiverStatePayload::write, SyncDx3ReceiverStatePayload::read);

    private static void write(ByteBuf b, SyncDx3ReceiverStatePayload p) {
        b.writeInt(p.containerId); b.writeBoolean(p.dab); b.writeInt(p.frequencyKhz); b.writeInt(p.channelIndex);
        ByteBufCodecs.stringUtf8(16).encode(b, p.sid);
        ByteBufCodecs.stringUtf8(64).encode(b, p.mux);
        ByteBufCodecs.stringUtf8(16).encode(b, p.pi);
        b.writeByte(p.linkMode); b.writeBoolean(p.muted); b.writeBoolean(p.antenna);
    }

    private static SyncDx3ReceiverStatePayload read(ByteBuf b) {
        return new SyncDx3ReceiverStatePayload(b.readInt(), b.readBoolean(), b.readInt(), b.readInt(),
                ByteBufCodecs.stringUtf8(16).decode(b), ByteBufCodecs.stringUtf8(64).decode(b),
                ByteBufCodecs.stringUtf8(16).decode(b), b.readUnsignedByte(), b.readBoolean(), b.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
