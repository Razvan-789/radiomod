package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncRegistryPayload(int containerId, int page, int pageCount, int total, int sort, boolean descending,
                                  List<Entry> entries, List<String> countries) implements CustomPacketPayload {
    public record Entry(String type, String mode, int frequencyKhz, String dimension, int x, int y, int z,
                        int powerW, String programme, String detail, String code, String country) { }
    public static final Type<SyncRegistryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_registry"));
    public static final StreamCodec<ByteBuf, SyncRegistryPayload> STREAM_CODEC =
            StreamCodec.of(SyncRegistryPayload::write, SyncRegistryPayload::read);

    private static void write(ByteBuf b, SyncRegistryPayload p) {
        b.writeInt(p.containerId); b.writeInt(p.page); b.writeInt(p.pageCount);
        b.writeInt(p.total); b.writeInt(p.sort); b.writeBoolean(p.descending);
        b.writeByte(p.entries.size());
        for (Entry e : p.entries) {
            string(b, e.type, 8); string(b, e.mode, 8); b.writeInt(e.frequencyKhz);
            string(b, e.dimension, 128); b.writeInt(e.x); b.writeInt(e.y); b.writeInt(e.z);
            b.writeInt(e.powerW); string(b, e.programme, 256); string(b, e.detail, 320);
            string(b, e.code, 16); string(b, e.country, 160);
        }
        for (int i = 0; i < 16; i++) string(b, p.countries.get(i), 160);
    }

    private static SyncRegistryPayload read(ByteBuf b) {
        int id = b.readInt(), page = b.readInt(), pages = b.readInt(), total = b.readInt(), sort = b.readInt();
        boolean descending = b.readBoolean();
        int count = b.readUnsignedByte();
        if (count > 8) throw new IllegalArgumentException("Too many registry entries");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(
                readString(b, 8), readString(b, 8), b.readInt(), readString(b, 128), b.readInt(), b.readInt(), b.readInt(),
                b.readInt(), readString(b, 256), readString(b, 320), readString(b, 16), readString(b, 160)));
        List<String> countries = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) countries.add(readString(b, 160));
        return new SyncRegistryPayload(id, page, pages, total, sort, descending,
                List.copyOf(entries), List.copyOf(countries));
    }

    private static void string(ByteBuf b, String value, int max) {
        ByteBufCodecs.stringUtf8(max).encode(b, value);
    }
    private static String readString(ByteBuf b, int max) {
        return ByteBufCodecs.stringUtf8(max).decode(b);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
