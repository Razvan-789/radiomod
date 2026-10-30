package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncAdsbTabletPayload(int containerId, List<Entry> servers,
                                    String selectedDimension, long selectedPos, boolean selected,
                                    boolean connected, int centerX, int centerY, int centerZ,
                                    int rangeKm, double kilometersPerBlock, List<VorMarker> vors,
                                    List<SyncAdsbRadarPayload.Contact> contacts)
        implements CustomPacketPayload {
    public record Entry(String dimension, long pos, String name) { }
    public record VorMarker(long pos, String identifier, int frequencyKhz, boolean dme, boolean connected) { }
    public static final Type<SyncAdsbTabletPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_adsb_tablet"));
    public static final StreamCodec<ByteBuf, SyncAdsbTabletPayload> STREAM_CODEC = StreamCodec.of(
            SyncAdsbTabletPayload::write, SyncAdsbTabletPayload::read);
    private static void write(ByteBuf b, SyncAdsbTabletPayload p) {
        b.writeInt(p.containerId);
        b.writeShort(p.servers.size());
        for (Entry entry : p.servers) {
            ByteBufCodecs.stringUtf8(128).encode(b, entry.dimension);
            b.writeLong(entry.pos);
            ByteBufCodecs.stringUtf8(64).encode(b, entry.name);
        }
        ByteBufCodecs.stringUtf8(128).encode(b, p.selectedDimension);
        b.writeLong(p.selectedPos); b.writeBoolean(p.selected); b.writeBoolean(p.connected);
        b.writeInt(p.centerX); b.writeInt(p.centerY); b.writeInt(p.centerZ); b.writeInt(p.rangeKm);
        b.writeDouble(p.kilometersPerBlock);
        b.writeShort(p.vors.size());
        for (VorMarker vor : p.vors) {
            b.writeLong(vor.pos);
            ByteBufCodecs.stringUtf8(4).encode(b, vor.identifier);
            b.writeInt(vor.frequencyKhz);
            b.writeBoolean(vor.dme); b.writeBoolean(vor.connected);
        }
        b.writeByte(p.contacts.size());
        for (SyncAdsbRadarPayload.Contact contact : p.contacts) SyncAdsbRadarPayload.writeContact(b, contact);
    }
    private static SyncAdsbTabletPayload read(ByteBuf b) {
        int id = b.readInt(), count = b.readUnsignedShort();
        if (count > 512) throw new IllegalArgumentException("Too many ADS-B servers");
        List<Entry> servers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) servers.add(new Entry(ByteBufCodecs.stringUtf8(128).decode(b),
                b.readLong(), ByteBufCodecs.stringUtf8(64).decode(b)));
        String dimension = ByteBufCodecs.stringUtf8(128).decode(b);
        long pos = b.readLong(); boolean selected = b.readBoolean(), connected = b.readBoolean();
        int x = b.readInt(), y = b.readInt(), z = b.readInt(), rangeKm = b.readInt();
        double scale = b.readDouble();
        int vorCount = b.readUnsignedShort();
        if (vorCount > 512) throw new IllegalArgumentException("Too many VORs");
        List<VorMarker> vors = new ArrayList<>(vorCount);
        for (int i = 0; i < vorCount; i++) vors.add(new VorMarker(b.readLong(),
                ByteBufCodecs.stringUtf8(4).decode(b), b.readInt(), b.readBoolean(), b.readBoolean()));
        int contactCount = b.readUnsignedByte();
        if (contactCount > 128) throw new IllegalArgumentException("Too many ADS-B contacts");
        List<SyncAdsbRadarPayload.Contact> contacts = new ArrayList<>(contactCount);
        for (int i = 0; i < contactCount; i++) contacts.add(SyncAdsbRadarPayload.readContact(b));
        return new SyncAdsbTabletPayload(id, List.copyOf(servers), dimension, pos, selected, connected,
                x, y, z, rangeKm, scale, List.copyOf(vors), List.copyOf(contacts));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
