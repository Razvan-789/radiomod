package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.item.RadioItem;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.signal.DrmReception;
import com.radiomod.signal.Dx3ReceiverCatalog;
import com.radiomod.signal.PtyNames;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A radio-fed display. Only changed, compact display data is sent to clients. */
public class RadioDisplayBlockEntity extends BlockEntity {
    public static final int LOGO = 1, NAME = 2, TEXT = 4, TAGS = 8, SIGNAL = 16, PTY = 32;
    public static final int ALL = LOGO | NAME | TEXT | TAGS | SIGNAL | PTY;
    @Nullable private BlockPos radioPos;
    private int options = ALL;
    private int singleMode;
    private DisplayData data = DisplayData.EMPTY;

    public RadioDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.RADIO_DISPLAY_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable public BlockPos radioPos() { return radioPos; }
    public int options() { return options; }
    public int singleMode() { return singleMode; }
    public DisplayData data() { return data; }

    public void link(BlockPos pos) {
        if (pos.equals(worldPosition)) return;
        radioPos = pos.immutable();
        if (level == null || !level.isClientSide()) sync();
        refresh();
    }

    public void action(int action) {
        if (action == 0) singleMode = (singleMode + 1) % 3;
        else if (action >= 1 && action <= 6) options ^= 1 << (action - 1);
        else return;
        if (level == null || !level.isClientSide()) sync();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadioDisplayBlockEntity display) {
        if (!level.isClientSide() && level.getGameTime() % 20 == Math.floorMod(pos.asLong(), 20))
            display.refresh();
    }

    private void refresh() {
        if (level == null || level.isClientSide()) return;
        DisplayData next = DisplayData.EMPTY;
        if (radioPos != null && level.getBlockEntity(radioPos) instanceof TechnisatReceiverBlockEntity technisat
                && technisat.getRadioMode() == RadioItem.MODE_DRM) {
            DrmReception.Sample sample = DrmReception.sampleReceiver(technisat);
            if (sample.decoded()) next = drmData(sample);
        } else if (radioPos != null && level.getBlockEntity(radioPos) instanceof Dx3ReceiverBlockEntity radio) {
            Dx3ReceiverCatalog.Sample sample = Dx3ReceiverCatalog.sample(radio);
            SyncDx3PlayerPayload info = sample.info();
            if (info.active()) {
                String name = !info.longPs().isBlank() ? info.longPs() : info.ps();
                if (name.isBlank()) name = radio.isDab() ? radio.getMux()
                        : String.format(Locale.ROOT, "%.1f MHz", radio.getFrequencyKhz() / 1000.0);
                int[] logo = compactLogo(sample.logo());
                if (logo.length == 0 && !radio.isDab() && !radio.getPi().isBlank()) {
                    for (var station : Dx3ReceiverCatalog.scan(radio)) {
                        if (!station.dab() && radio.getPi().equalsIgnoreCase(station.sid())) {
                            logo = compactLogo(station.logoPixels());
                            break;
                        }
                    }
                }
                next = new DisplayData(name, info.rt(), info.title(), info.artist(),
                        radio.isDab() || info.rds() ? PtyNames.name(info.pty()) : "",
                        Math.round(sample.signal() * 10) / 10.0,
                        info.signalBars(), logo);
            }
        } else if (radioPos != null && level.getBlockEntity(radioPos) instanceof DrmRadioBlockEntity radio) {
            DrmReception.Sample sample = DrmReception.sample(radio);
            if (sample.decoded()) next = drmData(sample);
        }
        if (!data.same(next)) { data = next; sync(); }
    }

    private static DisplayData drmData(DrmReception.Sample sample) {
        return new DisplayData(sample.transmitter().getServiceLabel(),
                sample.transmitter().getTextMessage(), "", "", "DRM",
                Math.round(sample.signalDbuv() * 10) / 10.0,
                Math.max(0, Math.min(5, (int) ((sample.signalDbuv() - 5) / 12) + 1)), new int[0]);
    }

    private static int[] compactLogo(int[] pixels) {
        if (pixels == null || pixels.length < 256) return new int[0];
        int side = (int) Math.sqrt(pixels.length);
        if (side * side != pixels.length) return new int[0];
        int[] small = new int[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            small[y * 16 + x] = pixels[(y * side / 16) * side + x * side / 16];
        return small;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (radioPos != null) tag.putLong("RadioPos", radioPos.asLong());
        tag.putInt("Options", options);
        tag.putInt("SingleMode", singleMode);
        tag.putString("Name", data.name());
        tag.putString("Text", data.text());
        tag.putString("Title", data.title());
        tag.putString("Artist", data.artist());
        tag.putString("Pty", data.pty());
        tag.putDouble("Signal", data.signal());
        tag.putInt("Bars", data.bars());
        tag.putIntArray("Logo", data.logo());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        radioPos = tag.contains("RadioPos") ? BlockPos.of(tag.getLong("RadioPos")) : null;
        options = tag.contains("Options") ? tag.getInt("Options") & ALL : ALL;
        singleMode = Math.floorMod(tag.getInt("SingleMode"), 3);
        int[] logo = tag.getIntArray("Logo");
        data = new DisplayData(tag.getString("Name"), tag.getString("Text"), tag.getString("Title"),
                tag.getString("Artist"), tag.getString("Pty"), tag.getDouble("Signal"),
                tag.getInt("Bars"), logo.length == 256 ? logo : new int[0]);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public record DisplayData(String name, String text, String title, String artist,
                              String pty, double signal, int bars, int[] logo) {
        public static final DisplayData EMPTY = new DisplayData("", "", "", "", "", 0, 0, new int[0]);
        public boolean same(DisplayData other) {
            return Objects.equals(name, other.name) && Objects.equals(text, other.text)
                    && Objects.equals(title, other.title) && Objects.equals(artist, other.artist)
                    && Objects.equals(pty, other.pty) && signal == other.signal && bars == other.bars
                    && Arrays.equals(logo, other.logo);
        }
    }
}
