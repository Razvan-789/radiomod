package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncWebTabletPayload(int containerId, boolean localConfig, List<Entry> servers,
                                   String selectedDimension, long selectedPos, boolean selected,
                                   String location, String backgroundUrl, int themeColor,
                                   boolean connected, boolean drm, int frequencyKhz,
                                   boolean signal, int signalTenths, int snrTenths, int ber,
                                   String ps, String pi, String country, String rt,
                                   boolean identified, String stationName,
                                   int txX, int txY, int txZ, int powerW, int bearing, int distance,
                                   int[] logo, int[] history, String streamUrl, String sourceUrl,
                                   List<Integer> afFrequenciesKhz)
        implements CustomPacketPayload {
    public record Entry(String dimension, long pos, String location) { }
    public static final Type<SyncWebTabletPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_web_tablet"));
    public static final StreamCodec<ByteBuf, SyncWebTabletPayload> STREAM_CODEC = StreamCodec.of(
            SyncWebTabletPayload::write, SyncWebTabletPayload::read);
    private static void write(ByteBuf b, SyncWebTabletPayload p) {
        b.writeInt(p.containerId); b.writeBoolean(p.localConfig);
        b.writeShort(p.servers.size());
        for (Entry entry : p.servers) {
            string(b, entry.dimension, 128); b.writeLong(entry.pos); string(b, entry.location, 256);
        }
        string(b, p.selectedDimension, 128); b.writeLong(p.selectedPos); b.writeBoolean(p.selected);
        string(b, p.location, 256); string(b, p.backgroundUrl, 2048); b.writeInt(p.themeColor);
        b.writeBoolean(p.connected); b.writeBoolean(p.drm); b.writeInt(p.frequencyKhz);
        b.writeBoolean(p.signal); b.writeInt(p.signalTenths); b.writeInt(p.snrTenths); b.writeByte(p.ber);
        string(b, p.ps, 128); string(b, p.pi, 16); string(b, p.country, 256); string(b, p.rt, 256);
        b.writeBoolean(p.identified); string(b, p.stationName, 128);
        b.writeInt(p.txX); b.writeInt(p.txY); b.writeInt(p.txZ); b.writeInt(p.powerW);
        b.writeInt(p.bearing); b.writeInt(p.distance);
        b.writeBoolean(p.logo != null && p.logo.length == 4096);
        if (p.logo != null && p.logo.length == 4096) for (int pixel : p.logo) b.writeInt(pixel);
        for (int i = 0; i < 60; i++) b.writeShort(p.history[i]);
        string(b, p.streamUrl, 2048); string(b, p.sourceUrl, 2048);
        b.writeByte(Math.min(32, p.afFrequenciesKhz.size()));
        for (int i = 0; i < Math.min(32, p.afFrequenciesKhz.size()); i++) b.writeInt(p.afFrequenciesKhz.get(i));
    }
    private static SyncWebTabletPayload read(ByteBuf b) {
        int id = b.readInt(); boolean local = b.readBoolean();
        int count = b.readUnsignedShort();
        if (count > 1024) throw new IllegalArgumentException("Too many webservers");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            entries.add(new Entry(readString(b, 128), b.readLong(), readString(b, 256)));
        String dimension = readString(b, 128); long pos = b.readLong(); boolean selected = b.readBoolean();
        String location = readString(b, 256), background = readString(b, 2048); int color = b.readInt();
        boolean connected = b.readBoolean(), drm = b.readBoolean(); int frequency = b.readInt();
        boolean signal = b.readBoolean(); int strength = b.readInt(), snr = b.readInt(), ber = b.readUnsignedByte();
        String ps = readString(b, 128), pi = readString(b, 16), country = readString(b, 256), rt = readString(b, 256);
        boolean identified = b.readBoolean(); String name = readString(b, 128);
        int x = b.readInt(), y = b.readInt(), z = b.readInt(), power = b.readInt();
        int bearing = b.readInt(), distance = b.readInt();
        int[] logo = null;
        if (b.readBoolean()) { logo = new int[4096]; for (int i = 0; i < logo.length; i++) logo[i] = b.readInt(); }
        int[] history = new int[60]; for (int i = 0; i < 60; i++) history[i] = b.readShort();
        String stream = readString(b, 2048), source = readString(b, 2048);
        int afCount = b.readUnsignedByte();
        if (afCount > 32) throw new IllegalArgumentException("Too many AF frequencies");
        List<Integer> af = new ArrayList<>(afCount);
        for (int i = 0; i < afCount; i++) af.add(b.readInt());
        return new SyncWebTabletPayload(id, local, List.copyOf(entries), dimension, pos, selected,
                location, background, color, connected, drm, frequency, signal, strength, snr, ber,
                ps, pi, country, rt, identified, name, x, y, z, power, bearing, distance,
                logo, history, stream, source, List.copyOf(af));
    }
    private static void string(ByteBuf b, String value, int max) { ByteBufCodecs.stringUtf8(max).encode(b, value); }
    private static String readString(ByteBuf b, int max) { return ByteBufCodecs.stringUtf8(max).decode(b); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
