package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDx3LogoPayload(int containerId, int[] pixels) implements CustomPacketPayload {
    public static final Type<SyncDx3LogoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_logo"));
    public static final StreamCodec<ByteBuf, SyncDx3LogoPayload> STREAM_CODEC = StreamCodec.of(
            SyncDx3LogoPayload::write, SyncDx3LogoPayload::read);
    private static final int PIXELS = 64 * 64;

    private static void write(ByteBuf buffer, SyncDx3LogoPayload payload) {
        buffer.writeInt(payload.containerId);
        buffer.writeBoolean(payload.pixels != null && payload.pixels.length == PIXELS);
        if (payload.pixels != null && payload.pixels.length == PIXELS) {
            for (int pixel : payload.pixels) {
                buffer.writeInt(pixel);
            }
        }
    }

    private static SyncDx3LogoPayload read(ByteBuf buffer) {
        int id = buffer.readInt();
        if (!buffer.readBoolean()) {
            return new SyncDx3LogoPayload(id, null);
        }
        int[] pixels = new int[PIXELS];
        for (int index = 0; index < PIXELS; index++) {
            pixels[index] = buffer.readInt();
        }
        return new SyncDx3LogoPayload(id, pixels);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
