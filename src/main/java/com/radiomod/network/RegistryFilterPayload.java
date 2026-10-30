package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Applies all registry filters together before server-side sorting and pagination. */
public record RegistryFilterPayload(int containerId, String query, int minFrequencyKhz,
                                    int maxFrequencyKhz, int minPowerW, int maxPowerW, int mode)
        implements CustomPacketPayload {
    public static final Type<RegistryFilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "registry_filter"));
    public static final StreamCodec<ByteBuf, RegistryFilterPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.containerId);
                ByteBufCodecs.stringUtf8(320).encode(b, p.query);
                b.writeInt(p.minFrequencyKhz);
                b.writeInt(p.maxFrequencyKhz);
                b.writeInt(p.minPowerW);
                b.writeInt(p.maxPowerW);
                b.writeInt(p.mode);
            },
            b -> new RegistryFilterPayload(b.readInt(), ByteBufCodecs.stringUtf8(320).decode(b),
                    b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
