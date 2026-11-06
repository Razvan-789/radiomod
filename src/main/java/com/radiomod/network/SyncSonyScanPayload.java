package com.radiomod.network;

import com.radiomod.Radio;
import com.radiomod.item.SonyRadioItem;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Current Sony DAB scan list; progress itself uses menu data slots. */
public record SyncSonyScanPayload(int containerId, boolean scanned,
                                  List<SonyRadioItem.Station> stations) implements CustomPacketPayload {
    public static final Type<SyncSonyScanPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Radio.MODID, "sync_sony_scan"));
    public static final StreamCodec<ByteBuf, SyncSonyScanPayload> STREAM_CODEC = StreamCodec.of(
            SyncSonyScanPayload::write, SyncSonyScanPayload::read);

    private static void write(ByteBuf buffer, SyncSonyScanPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.containerId());
        buffer.writeBoolean(payload.scanned());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.stations().size());
        for (var station : payload.stations()) {
            ByteBufCodecs.VAR_INT.encode(buffer, station.channelIndex());
            ByteBufCodecs.stringUtf8(64).encode(buffer, station.mux());
            ByteBufCodecs.stringUtf8(16).encode(buffer, station.sid());
            ByteBufCodecs.stringUtf8(96).encode(buffer, station.label());
        }
    }

    private static SyncSonyScanPayload read(ByteBuf buffer) {
        int id = ByteBufCodecs.VAR_INT.decode(buffer);
        boolean scanned = buffer.readBoolean();
        int size = ByteBufCodecs.VAR_INT.decode(buffer);
        if (size < 0 || size > 128) throw new IllegalArgumentException("Too many Sony DAB services");
        List<SonyRadioItem.Station> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(new SonyRadioItem.Station(
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(64).decode(buffer),
                ByteBufCodecs.stringUtf8(16).decode(buffer),
                ByteBufCodecs.stringUtf8(96).decode(buffer)));
        return new SyncSonyScanPayload(id, scanned, List.copyOf(entries));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
