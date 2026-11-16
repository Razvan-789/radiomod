package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Stream choice for one world speaker. Clients apply distance falloff at this position. */
public record SyncDx3ReceiverAudioPayload(BlockPos pos, Vec3 worldPos, String url,
                                          String fallbackUrl, boolean dab, float fmNoise) implements CustomPacketPayload {
    public static final Type<SyncDx3ReceiverAudioPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_receiver_audio"));
    public static final StreamCodec<ByteBuf, SyncDx3ReceiverAudioPayload> STREAM_CODEC =
            StreamCodec.of(SyncDx3ReceiverAudioPayload::write, SyncDx3ReceiverAudioPayload::read);

    private static void write(ByteBuf b, SyncDx3ReceiverAudioPayload p) {
        b.writeLong(p.pos.asLong());
        b.writeDouble(p.worldPos.x); b.writeDouble(p.worldPos.y); b.writeDouble(p.worldPos.z);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.url);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.fallbackUrl);
        b.writeBoolean(p.dab);
        b.writeFloat(p.fmNoise);
    }

    private static SyncDx3ReceiverAudioPayload read(ByteBuf b) {
        BlockPos pos = BlockPos.of(b.readLong());
        Vec3 worldPos = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        return new SyncDx3ReceiverAudioPayload(pos, worldPos,
                ByteBufCodecs.stringUtf8(1024).decode(b), ByteBufCodecs.stringUtf8(1024).decode(b),
                b.readBoolean(), b.readFloat());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
