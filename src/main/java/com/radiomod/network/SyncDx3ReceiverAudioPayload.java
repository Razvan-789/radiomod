package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Stream choice for one world speaker. Clients apply distance falloff at this position. */
public record SyncDx3ReceiverAudioPayload(BlockPos pos, String url, String fallbackUrl, boolean dab) implements CustomPacketPayload {
    public static final Type<SyncDx3ReceiverAudioPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_receiver_audio"));
    public static final StreamCodec<ByteBuf, SyncDx3ReceiverAudioPayload> STREAM_CODEC =
            StreamCodec.of(SyncDx3ReceiverAudioPayload::write, SyncDx3ReceiverAudioPayload::read);

    private static void write(ByteBuf b, SyncDx3ReceiverAudioPayload p) {
        b.writeLong(p.pos.asLong());
        ByteBufCodecs.stringUtf8(1024).encode(b, p.url);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.fallbackUrl);
        b.writeBoolean(p.dab);
    }

    private static SyncDx3ReceiverAudioPayload read(ByteBuf b) {
        return new SyncDx3ReceiverAudioPayload(BlockPos.of(b.readLong()),
                ByteBufCodecs.stringUtf8(1024).decode(b), ByteBufCodecs.stringUtf8(1024).decode(b), b.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
