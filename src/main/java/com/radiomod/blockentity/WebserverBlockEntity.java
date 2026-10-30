package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.server.TransmitterChunkLoading;
import com.radiomod.signal.DrmSignal;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.WebserverNetwork;
import com.radiomod.signal.WebserverReception;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One shared receiver and tuning state for all tablet viewers. */
public class WebserverBlockEntity extends BlockEntity {
    private String location = "New webserver";
    private String backgroundUrl = "";
    private int themeColor = 0x8BD15B;
    private boolean drm;
    private int fmFrequencyKhz = 101_700, drmFrequencyKhz = 1_000;
    @Nullable private BlockPos antennaPos;
    private final int[] history = new int[60];
    private int historyCount, historyHead;
    private WebserverReception.Sample sample = WebserverReception.Sample.EMPTY;

    public WebserverBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.WEBSERVER_BLOCK_ENTITY.get(), pos, state);
        Arrays.fill(history, -1_000);
    }
    public String location() { return location; }
    public String backgroundUrl() { return backgroundUrl; }
    public int themeColor() { return themeColor; }
    public boolean drm() { return drm; }
    public int frequencyKhz() { return drm ? drmFrequencyKhz : fmFrequencyKhz; }
    @Nullable public BlockPos antennaPos() { return antennaPos; }
    public void setAntennaPos(@Nullable BlockPos pos) { antennaPos = pos == null ? null : pos.immutable(); setChanged(); }
    public boolean hasAntennaConnected() {
        return antennaPos != null && level != null
                && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna
                && !(antenna instanceof LoopAntennaBlockEntity)
                && worldPosition.equals(antenna.getLinkedTransmitterPos());
    }
    public BlockPos receivePos() { return hasAntennaConnected() ? antennaPos : worldPosition; }
    public void setMode(boolean drm) {
        if (this.drm != drm) {
            this.drm = drm; sample = WebserverReception.Sample.EMPTY;
            WebserverReception.clear(this); setChanged();
        }
    }
    public void setFrequencyKhz(int frequency) {
        int previous = frequencyKhz();
        if (drm) drmFrequencyKhz = Math.max(150, Math.min(DrmSignal.MAX_KHZ, frequency));
        else fmFrequencyKhz = SignalMath.clampFmFrequencyKhz(frequency);
        if (frequencyKhz() != previous) {
            sample = WebserverReception.Sample.EMPTY;
            WebserverReception.clear(this); setChanged();
        }
    }
    public void setLocation(String value) {
        location = clean(value, 64);
        if (location.isBlank()) location = "New webserver";
        setChanged();
    }
    public void setBackgroundUrl(String value) {
        String cleaned = clean(value, 512);
        backgroundUrl = cleaned.startsWith("https://") || cleaned.startsWith("http://") ? cleaned : "";
        setChanged();
    }
    public void setThemeColor(int rgb) { themeColor = rgb & 0xFFFFFF; setChanged(); }
    public WebserverReception.Sample sample() { return sample; }
    public int[] history() {
        int[] ordered = new int[60];
        Arrays.fill(ordered, -1_000);
        for (int i = 0; i < historyCount; i++)
            ordered[60 - historyCount + i] = history[(historyHead - historyCount + i + 60) % 60];
        return ordered;
    }
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            WebserverBlockEntity webserver) {
        if (!level.isClientSide() && level.getGameTime() % 4 == 0) {
            webserver.sample = WebserverReception.sample(webserver);
            if (level.getGameTime() % 20 == 0) {
                webserver.history[webserver.historyHead] = (int) Math.round(webserver.sample.signalDbuv() * 10);
                webserver.historyHead = (webserver.historyHead + 1) % 60;
                webserver.historyCount = Math.min(60, webserver.historyCount + 1);
            }
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            WebserverNetwork.add(level, worldPosition);
            TransmitterChunkLoading.forceChunkForTransmitter(level, worldPosition);
        }
    }
    @Override public void setRemoved() {
        if (level != null && !level.isClientSide()) WebserverNetwork.remove(level, worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Location", location); tag.putString("BackgroundUrl", backgroundUrl);
        tag.putInt("ThemeColor", themeColor); tag.putBoolean("Drm", drm);
        tag.putInt("FmFrequencyKhz", fmFrequencyKhz); tag.putInt("DrmFrequencyKhz", drmFrequencyKhz);
        if (antennaPos != null) tag.putLong("AntennaPos", antennaPos.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        location = clean(tag.getString("Location"), 64);
        if (location.isBlank()) location = "New webserver";
        setBackgroundUrl(tag.getString("BackgroundUrl"));
        themeColor = tag.contains("ThemeColor") ? tag.getInt("ThemeColor") & 0xFFFFFF : 0x8BD15B;
        drm = tag.getBoolean("Drm");
        fmFrequencyKhz = SignalMath.clampFmFrequencyKhz(tag.getInt("FmFrequencyKhz"));
        drmFrequencyKhz = Math.max(150, Math.min(DrmSignal.MAX_KHZ, tag.getInt("DrmFrequencyKhz")));
        antennaPos = tag.contains("AntennaPos") ? BlockPos.of(tag.getLong("AntennaPos")) : null;
    }
    private static String clean(String value, int max) {
        if (value == null) return "";
        String cleaned = value.strip().replaceAll("[\\p{Cntrl}]", "");
        return cleaned.substring(0, Math.min(max, cleaned.length()));
    }
}
