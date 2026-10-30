package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncAdsbRadarPayload(int containerId, boolean connected, int receiverX, int receiverY, int receiverZ,
                                   List<Contact> contacts) implements CustomPacketPayload {
    public record Contact(String identifier, String operator, String carrier, int x, int y, int z,
                          float eastKm, float southKm, float rangeKm, float distanceKm, float snrDb,
                          int headingDegrees, float speedKmH) { }
    public static final Type<SyncAdsbRadarPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_adsb_radar"));
    public static final StreamCodec<ByteBuf, SyncAdsbRadarPayload> STREAM_CODEC = StreamCodec.of(
            SyncAdsbRadarPayload::write, SyncAdsbRadarPayload::read);
    private static void write(ByteBuf b, SyncAdsbRadarPayload p) {
        b.writeInt(p.containerId); b.writeBoolean(p.connected);
        b.writeInt(p.receiverX); b.writeInt(p.receiverY); b.writeInt(p.receiverZ);
        b.writeByte(p.contacts.size());
        for (Contact contact : p.contacts) writeContact(b, contact);
    }
    static void writeContact(ByteBuf b, Contact contact) {
            ByteBufCodecs.stringUtf8(24).encode(b, contact.identifier);
            ByteBufCodecs.stringUtf8(32).encode(b, contact.operator);
            ByteBufCodecs.stringUtf8(64).encode(b, contact.carrier);
            b.writeInt(contact.x); b.writeInt(contact.y); b.writeInt(contact.z);
            b.writeFloat(contact.eastKm); b.writeFloat(contact.southKm);
            b.writeFloat(contact.rangeKm); b.writeFloat(contact.distanceKm); b.writeFloat(contact.snrDb);
            b.writeShort(contact.headingDegrees); b.writeFloat(contact.speedKmH);
    }
    private static SyncAdsbRadarPayload read(ByteBuf b) {
        int id = b.readInt(); boolean connected = b.readBoolean();
        int x = b.readInt(), y = b.readInt(), z = b.readInt();
        int size = b.readUnsignedByte();
        if (size > 128) throw new IllegalArgumentException("Too many ADS-B contacts");
        List<Contact> contacts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) contacts.add(readContact(b));
        return new SyncAdsbRadarPayload(id, connected, x, y, z, List.copyOf(contacts));
    }
    static Contact readContact(ByteBuf b) {
        return new Contact(
                ByteBufCodecs.stringUtf8(24).decode(b), ByteBufCodecs.stringUtf8(32).decode(b),
                ByteBufCodecs.stringUtf8(64).decode(b), b.readInt(), b.readInt(), b.readInt(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readUnsignedShort(), b.readFloat());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
