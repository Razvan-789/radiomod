package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDx3PlayerPayload(int containerId, boolean active, String ps, String longPs,
                                   String rt, int pty, int signalBars, boolean ct, String time,
                                   int bitrateKbps, String codec, boolean rds, String lic,
                                   String title, String artist) implements CustomPacketPayload {
    public SyncDx3PlayerPayload(int containerId, boolean active, String ps, String longPs,
                                String rt, int pty, int signalBars, boolean ct, String time,
                                int bitrateKbps, String codec, boolean rds, String lic) {
        this(containerId, active, ps, longPs, rt, pty, signalBars, ct, time, bitrateKbps, codec,
                rds, lic, "", "");
    }
    public SyncDx3PlayerPayload(int containerId, boolean active, String ps, String longPs,
                                String rt, int pty, int signalBars, boolean ct, String time,
                                int bitrateKbps, String codec, boolean rds) {
        this(containerId, active, ps, longPs, rt, pty, signalBars, ct, time, bitrateKbps, codec, rds, "");
    }
    public SyncDx3PlayerPayload(int containerId, boolean active, String ps, String longPs,
                                String rt, int pty, int signalBars, boolean ct, String time,
                                int bitrateKbps, String codec) {
        this(containerId, active, ps, longPs, rt, pty, signalBars, ct, time, bitrateKbps, codec, false);
    }
    public static final Type<SyncDx3PlayerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_player"));
    public static final StreamCodec<ByteBuf, SyncDx3PlayerPayload> STREAM_CODEC = StreamCodec.of(
            SyncDx3PlayerPayload::write, SyncDx3PlayerPayload::read);

    private static void write(ByteBuf buffer, SyncDx3PlayerPayload payload) {
        buffer.writeInt(payload.containerId);
        buffer.writeBoolean(payload.active);
        writeString(buffer, payload.ps, 96);
        writeString(buffer, payload.longPs, 96);
        writeString(buffer, payload.rt, 256);
        buffer.writeByte(payload.pty);
        buffer.writeByte(payload.signalBars);
        buffer.writeBoolean(payload.ct);
        writeString(buffer, payload.time, 8);
        buffer.writeShort(payload.bitrateKbps);
        writeString(buffer, payload.codec, 40);
        buffer.writeBoolean(payload.rds);
        writeString(buffer, payload.lic, 64);
        writeString(buffer, payload.title, 512);
        writeString(buffer, payload.artist, 512);
    }

    private static SyncDx3PlayerPayload read(ByteBuf buffer) {
        return new SyncDx3PlayerPayload(buffer.readInt(), buffer.readBoolean(), readString(buffer, 96),
                readString(buffer, 96), readString(buffer, 256), buffer.readUnsignedByte(),
                buffer.readUnsignedByte(), buffer.readBoolean(), readString(buffer, 8),
                buffer.readUnsignedShort(), readString(buffer, 40), buffer.readBoolean(), readString(buffer, 64),
                readString(buffer, 512), readString(buffer, 512));
    }

    private static void writeString(ByteBuf buffer, String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("DX3 player text is too long");
        }
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static String readString(ByteBuf buffer, int maxBytes) {
        int length = buffer.readUnsignedShort();
        if (length > maxBytes) {
            throw new IllegalArgumentException("DX3 player text is too long");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
