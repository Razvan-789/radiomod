package com.radiomod.network;

import com.radiomod.Radio;
import com.radiomod.signal.SpectrumAnalyserState;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact RF readings; the spectrum shape and waterfall are generated locally. */
public record SyncSpectrumPayload(int band, int centerKhz, int spanKhz, int bandwidthKhz,
        int noiseTenths, int capturedTenths, int overheadBlocks, List<Carrier> carriers,
        boolean signalPresent, int tunedSignalTenths, int snrTenths,
        String audioUrl, String sourceUrl, RdsReading rds) implements CustomPacketPayload {
    public record Carrier(int frequencyKhz, int widthKhz, int signalTenths, int kind) { }
    public record RdsReading(boolean present, boolean decoded, int berPercent,
            String pi, String ps, String rt, int pty, String group, boolean tp) {
        public static final RdsReading EMPTY = new RdsReading(false, false, 100,
                "", "", "", 0, "", false);
    }
    public static final Type<SyncSpectrumPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_spectrum"));
    public static final StreamCodec<ByteBuf, SyncSpectrumPayload> STREAM_CODEC =
            StreamCodec.of(SyncSpectrumPayload::write, SyncSpectrumPayload::read);

    private static void write(ByteBuf b, SyncSpectrumPayload p) {
        b.writeByte(p.band); b.writeInt(p.centerKhz); b.writeInt(p.spanKhz);
        b.writeInt(p.bandwidthKhz); b.writeInt(p.noiseTenths); b.writeInt(p.capturedTenths);
        b.writeByte(Math.clamp(p.overheadBlocks, 0, 64));
        b.writeByte(Math.min(64, p.carriers.size()));
        for (int i = 0; i < Math.min(64, p.carriers.size()); i++) {
            Carrier c = p.carriers.get(i);
            b.writeInt(c.frequencyKhz); b.writeShort(c.widthKhz); b.writeShort(c.signalTenths); b.writeByte(c.kind);
        }
        b.writeBoolean(p.signalPresent); b.writeShort(p.tunedSignalTenths); b.writeShort(p.snrTenths);
        ByteBufCodecs.stringUtf8(2048).encode(b, p.audioUrl);
        ByteBufCodecs.stringUtf8(1024).encode(b, p.sourceUrl);
        b.writeBoolean(p.rds.present); b.writeBoolean(p.rds.decoded);
        b.writeByte(p.rds.berPercent); b.writeByte(p.rds.pty); b.writeBoolean(p.rds.tp);
        ByteBufCodecs.stringUtf8(8).encode(b, p.rds.pi);
        ByteBufCodecs.stringUtf8(16).encode(b, p.rds.ps);
        ByteBufCodecs.stringUtf8(256).encode(b, p.rds.rt);
        ByteBufCodecs.stringUtf8(4).encode(b, p.rds.group);
    }

    private static SyncSpectrumPayload read(ByteBuf b) {
        int band = b.readUnsignedByte(), center = b.readInt(), span = b.readInt();
        int bandwidth = b.readInt(), noise = b.readInt(), captured = b.readInt();
        int overhead = b.readUnsignedByte();
        int count = b.readUnsignedByte();
        if (band > 2 || overhead > 64 || count > 64) throw new IllegalArgumentException("Invalid spectrum packet");
        List<Carrier> carriers = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            carriers.add(new Carrier(b.readInt(), b.readUnsignedShort(), b.readShort(), b.readUnsignedByte()));
        boolean signal = b.readBoolean(); int tuned = b.readShort(); int snr = b.readShort();
        String audio = ByteBufCodecs.stringUtf8(2048).decode(b);
        String source = ByteBufCodecs.stringUtf8(1024).decode(b);
        boolean rdsPresent = b.readBoolean(), rdsDecoded = b.readBoolean();
        int ber = b.readUnsignedByte(), pty = b.readUnsignedByte();
        boolean tp = b.readBoolean();
        RdsReading rds = new RdsReading(rdsPresent, rdsDecoded, ber,
                ByteBufCodecs.stringUtf8(8).decode(b), ByteBufCodecs.stringUtf8(16).decode(b),
                ByteBufCodecs.stringUtf8(256).decode(b), pty,
                ByteBufCodecs.stringUtf8(4).decode(b), tp);
        return new SyncSpectrumPayload(band, center, span, bandwidth, noise, captured, overhead,
                List.copyOf(carriers), signal, tuned, snr, audio, source, rds);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
