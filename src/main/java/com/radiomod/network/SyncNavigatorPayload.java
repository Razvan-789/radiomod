package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SyncNavigatorPayload(int containerId, int frequency1Khz, int frequency2Khz, double kilometersPerBlock,
                                   boolean pinned, List<Transponder> transponders, List<VorEntry> registry,
                                   @Nullable Fix vor1, @Nullable Fix vor2) implements CustomPacketPayload {
    public record Transponder(String identifier, String operator, int x, int y, int z,
                              float distanceKm, float snrDb, int headingDegrees, float speedKmH) { }
    public record VorEntry(String dimension, long pos, String identifier, int frequencyKhz,
                           boolean dme, boolean connected) { }
    public record Fix(String identifier, int radialDegrees, float distanceKm, float dmeBlocks,
                      float snrDb, boolean dme, int x, int y, int z) { }
    public static final Type<SyncNavigatorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_navigator"));
    public static final StreamCodec<ByteBuf, SyncNavigatorPayload> STREAM_CODEC = StreamCodec.of(
            SyncNavigatorPayload::write, SyncNavigatorPayload::read);
    private static void write(ByteBuf b, SyncNavigatorPayload p) {
        b.writeInt(p.containerId); b.writeInt(p.frequency1Khz); b.writeInt(p.frequency2Khz); b.writeDouble(p.kilometersPerBlock); b.writeBoolean(p.pinned);
        b.writeByte(p.transponders.size());
        for (Transponder contact : p.transponders) {
            ByteBufCodecs.stringUtf8(24).encode(b, contact.identifier);
            ByteBufCodecs.stringUtf8(32).encode(b, contact.operator);
            b.writeInt(contact.x); b.writeInt(contact.y); b.writeInt(contact.z);
            b.writeFloat(contact.distanceKm); b.writeFloat(contact.snrDb);
            b.writeShort(contact.headingDegrees); b.writeFloat(contact.speedKmH);
        }
        b.writeShort(p.registry.size());
        for (VorEntry vor : p.registry) {
            ByteBufCodecs.stringUtf8(128).encode(b, vor.dimension);
            b.writeLong(vor.pos);
            ByteBufCodecs.stringUtf8(4).encode(b, vor.identifier);
            b.writeInt(vor.frequencyKhz); b.writeBoolean(vor.dme); b.writeBoolean(vor.connected);
        }
        writeFix(b, p.vor1); writeFix(b, p.vor2);
    }
    private static void writeFix(ByteBuf b, @Nullable Fix fix) {
        b.writeBoolean(fix != null);
        if (fix == null) return;
        ByteBufCodecs.stringUtf8(4).encode(b, fix.identifier);
        b.writeShort(fix.radialDegrees); b.writeFloat(fix.distanceKm); b.writeFloat(fix.dmeBlocks);
        b.writeFloat(fix.snrDb); b.writeBoolean(fix.dme);
        b.writeInt(fix.x); b.writeInt(fix.y); b.writeInt(fix.z);
    }
    private static SyncNavigatorPayload read(ByteBuf b) {
        int id = b.readInt(), freq1 = b.readInt(), freq2 = b.readInt();
        double scale = b.readDouble();
        boolean pinned = b.readBoolean();
        int contactCount = b.readUnsignedByte();
        if (contactCount > 64) throw new IllegalArgumentException("Too many ADS-B contacts");
        List<Transponder> contacts = new ArrayList<>(contactCount);
        for (int i = 0; i < contactCount; i++) contacts.add(new Transponder(
                ByteBufCodecs.stringUtf8(24).decode(b), ByteBufCodecs.stringUtf8(32).decode(b),
                b.readInt(), b.readInt(), b.readInt(), b.readFloat(), b.readFloat(), b.readUnsignedShort(), b.readFloat()));
        int registryCount = b.readUnsignedShort();
        if (registryCount > 512) throw new IllegalArgumentException("Too many VORs");
        List<VorEntry> registry = new ArrayList<>(registryCount);
        for (int i = 0; i < registryCount; i++) registry.add(new VorEntry(
                ByteBufCodecs.stringUtf8(128).decode(b), b.readLong(), ByteBufCodecs.stringUtf8(4).decode(b),
                b.readInt(), b.readBoolean(), b.readBoolean()));
        return new SyncNavigatorPayload(id, freq1, freq2, scale, pinned, List.copyOf(contacts), List.copyOf(registry),
                readFix(b), readFix(b));
    }
    @Nullable private static Fix readFix(ByteBuf b) {
        if (!b.readBoolean()) return null;
        return new Fix(ByteBufCodecs.stringUtf8(4).decode(b), b.readUnsignedShort(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readInt(), b.readInt(), b.readInt());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
