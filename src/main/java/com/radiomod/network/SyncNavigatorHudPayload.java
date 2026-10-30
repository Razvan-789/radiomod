package com.radiomod.network;

import com.radiomod.Radio;
import com.radiomod.signal.VorReception;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SyncNavigatorHudPayload(int frequency1Khz, int frequency2Khz,
                                      @Nullable Fix vor1, @Nullable Fix vor2) implements CustomPacketPayload {
    public record Fix(String identifier, int radialDegrees, float snrDb, boolean dme, float dmeBlocks) { }
    public static final Type<SyncNavigatorHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_navigator_hud"));
    public static final StreamCodec<ByteBuf, SyncNavigatorHudPayload> STREAM_CODEC = StreamCodec.of(
            SyncNavigatorHudPayload::write, SyncNavigatorHudPayload::read);
    public static SyncNavigatorHudPayload sample(int frequency1, int frequency2,
                                                 VorReception.Fix first, VorReception.Fix second) {
        return new SyncNavigatorHudPayload(frequency1, frequency2, of(first), of(second));
    }
    @Nullable private static Fix of(@Nullable VorReception.Fix fix) {
        return fix == null ? null : new Fix(fix.identifier(), fix.radialDegrees(),
                (float) fix.snrDb(), fix.dme(), (float) fix.dmeBlocks());
    }
    private static void write(ByteBuf b, SyncNavigatorHudPayload payload) {
        b.writeInt(payload.frequency1Khz); b.writeInt(payload.frequency2Khz);
        writeFix(b, payload.vor1); writeFix(b, payload.vor2);
    }
    private static void writeFix(ByteBuf b, @Nullable Fix fix) {
        b.writeBoolean(fix != null);
        if (fix == null) return;
        ByteBufCodecs.stringUtf8(4).encode(b, fix.identifier);
        b.writeShort(fix.radialDegrees); b.writeFloat(fix.snrDb);
        b.writeBoolean(fix.dme); b.writeFloat(fix.dmeBlocks);
    }
    private static SyncNavigatorHudPayload read(ByteBuf b) {
        return new SyncNavigatorHudPayload(b.readInt(), b.readInt(), readFix(b), readFix(b));
    }
    @Nullable private static Fix readFix(ByteBuf b) {
        if (!b.readBoolean()) return null;
        return new Fix(ByteBufCodecs.stringUtf8(4).decode(b), b.readUnsignedShort(),
                b.readFloat(), b.readBoolean(), b.readFloat());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
