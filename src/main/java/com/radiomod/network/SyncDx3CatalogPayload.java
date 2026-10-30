package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDx3CatalogPayload(int containerId, List<Dx3Station> stations) implements CustomPacketPayload {
    public static final Type<SyncDx3CatalogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_dx3_catalog"));
    public static final StreamCodec<ByteBuf, SyncDx3CatalogPayload> STREAM_CODEC = StreamCodec.of(
            SyncDx3CatalogPayload::write, SyncDx3CatalogPayload::read);
    private static final int MAX_STATIONS = 128;
    private static final int LOGO_PIXELS = 16 * 16;

    private static void write(ByteBuf buffer, SyncDx3CatalogPayload payload) {
        buffer.writeInt(payload.containerId);
        buffer.writeShort(payload.stations.size());
        for (Dx3Station station : payload.stations) {
            buffer.writeBoolean(station.dab());
            buffer.writeInt(station.tuning());
            writeString(buffer, station.sid(), 16);
            writeString(buffer, station.mux(), 64);
            writeString(buffer, station.label(), 96);
            writeString(buffer, station.sortName(), 96);
            writeString(buffer, station.detail(), 96);
            int[] logo = station.logoPixels();
            buffer.writeBoolean(logo != null && logo.length == LOGO_PIXELS);
            if (logo != null && logo.length == LOGO_PIXELS) {
                for (int pixel : logo) {
                    buffer.writeInt(pixel);
                }
            }
        }
    }

    private static SyncDx3CatalogPayload read(ByteBuf buffer) {
        int containerId = buffer.readInt();
        int count = buffer.readUnsignedShort();
        if (count > MAX_STATIONS) {
            throw new IllegalArgumentException("Too many DX3 stations");
        }
        List<Dx3Station> stations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            boolean dab = buffer.readBoolean();
            int tuning = buffer.readInt();
            String sid = readString(buffer, 16);
            String mux = readString(buffer, 64);
            String label = readString(buffer, 96);
            String sortName = readString(buffer, 96);
            String detail = readString(buffer, 96);
            int[] logo = null;
            if (buffer.readBoolean()) {
                logo = new int[LOGO_PIXELS];
                for (int pixel = 0; pixel < LOGO_PIXELS; pixel++) {
                    logo[pixel] = buffer.readInt();
                }
            }
            stations.add(new Dx3Station(dab, tuning, sid, mux, label, sortName, detail, logo));
        }
        return new SyncDx3CatalogPayload(containerId, List.copyOf(stations));
    }

    private static void writeString(ByteBuf buffer, String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("DX3 catalog text is too long");
        }
        buffer.writeByte(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static String readString(ByteBuf buffer, int maxBytes) {
        int length = buffer.readUnsignedByte();
        if (length > maxBytes) {
            throw new IllegalArgumentException("DX3 catalog text is too long");
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
