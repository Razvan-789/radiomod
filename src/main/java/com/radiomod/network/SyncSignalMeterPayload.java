package com.radiomod.network;

import com.radiomod.Radio;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A receive-only technical sample. No audio URL is carried by this packet. */
public record SyncSignalMeterPayload(boolean dab, int frequencyKhz, int channelIndex, boolean pinned,
        boolean active, int signalTenths, int snrTenths, int berPercent, String pi, String ps,
        String currentGroup, String decodedGroups, String mux, String correction,
        int fic, int msc, int fs, List<String> stations, List<TiiReading> tii) implements CustomPacketPayload {
    public static final Type<SyncSignalMeterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_signal_meter"));
    public static final StreamCodec<ByteBuf, SyncSignalMeterPayload> STREAM_CODEC =
            StreamCodec.of(SyncSignalMeterPayload::write, SyncSignalMeterPayload::read);

    public record TiiReading(String code, int relativeTenths) {
    }

    private static void write(ByteBuf b, SyncSignalMeterPayload p) {
        b.writeBoolean(p.dab); b.writeInt(p.frequencyKhz); b.writeInt(p.channelIndex);
        b.writeBoolean(p.pinned); b.writeBoolean(p.active); b.writeInt(p.signalTenths);
        b.writeInt(p.snrTenths); b.writeInt(p.berPercent);
        string(b, p.pi); string(b, p.ps); string(b, p.currentGroup); string(b, p.decodedGroups);
        string(b, p.mux); string(b, p.correction);
        b.writeByte(p.fic); b.writeByte(p.msc); b.writeByte(p.fs);
        b.writeByte(Math.min(64, p.stations.size()));
        for (int i = 0; i < Math.min(64, p.stations.size()); i++) string(b, p.stations.get(i));
        b.writeByte(Math.min(64, p.tii.size()));
        for (int i = 0; i < Math.min(64, p.tii.size()); i++) {
            string(b, p.tii.get(i).code()); b.writeInt(p.tii.get(i).relativeTenths());
        }
    }

    private static SyncSignalMeterPayload read(ByteBuf b) {
        boolean dab = b.readBoolean(); int frequency = b.readInt(); int channel = b.readInt();
        boolean pinned = b.readBoolean(); boolean active = b.readBoolean();
        int signal = b.readInt(); int snr = b.readInt(); int ber = b.readInt();
        String pi = string(b); String ps = string(b); String group = string(b);
        String groups = string(b); String mux = string(b); String correction = string(b);
        int fic = b.readUnsignedByte(); int msc = b.readUnsignedByte(); int fs = b.readUnsignedByte();
        int count = b.readUnsignedByte();
        if (count > 64) throw new IllegalArgumentException("Too many meter stations");
        List<String> stations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) stations.add(string(b));
        count = b.readUnsignedByte();
        if (count > 64) throw new IllegalArgumentException("Too many TII readings");
        List<TiiReading> tii = new ArrayList<>(count);
        for (int i = 0; i < count; i++) tii.add(new TiiReading(string(b), b.readInt()));
        return new SyncSignalMeterPayload(dab, frequency, channel, pinned, active, signal, snr, ber,
                pi, ps, group, groups, mux, correction, fic, msc, fs, List.copyOf(stations), List.copyOf(tii));
    }

    private static void string(ByteBuf b, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(255, bytes.length);
        b.writeByte(length); b.writeBytes(bytes, 0, length);
    }

    private static String string(ByteBuf b) {
        byte[] bytes = new byte[b.readUnsignedByte()];
        b.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
