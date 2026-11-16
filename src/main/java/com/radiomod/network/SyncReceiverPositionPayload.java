package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Small position-only update for an already streaming speaker on a moving contraption. */
public record SyncReceiverPositionPayload(BlockPos pos, Vec3 worldPos) implements CustomPacketPayload {
    public static final Type<SyncReceiverPositionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_receiver_position"));
    public static final StreamCodec<ByteBuf, SyncReceiverPositionPayload> STREAM_CODEC =
            StreamCodec.of(SyncReceiverPositionPayload::write, SyncReceiverPositionPayload::read);

    private static void write(ByteBuf b, SyncReceiverPositionPayload p) {
        b.writeLong(p.pos.asLong());
        b.writeDouble(p.worldPos.x); b.writeDouble(p.worldPos.y); b.writeDouble(p.worldPos.z);
    }

    private static SyncReceiverPositionPayload read(ByteBuf b) {
        return new SyncReceiverPositionPayload(BlockPos.of(b.readLong()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
