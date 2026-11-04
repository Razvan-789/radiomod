package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.server.TransmitterChunkLoading;
import com.radiomod.signal.DrmSignal;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.SignalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class DrmTransmitterBlockEntity extends BlockEntity {
    @Override public void setChanged() {
        super.setChanged();
        SignalNetwork.markChanged(level);
    }
    private int frequencyKhz = 1000, powerW = 50, mode, bandwidthKhz = 10;
    private String streamUrl = "", imageUrl = "", serviceLabel = "DRM Service", shortLabel = "DRM", textMessage = "";
    @Nullable private BlockPos antennaPos;

    public DrmTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.DRM_TRANSMITTER_BLOCK_ENTITY.get(), pos, state);
    }
    public int getFrequencyKhz() { return frequencyKhz; }
    public int getPowerW() { return powerW; }
    public int getMode() { return mode; }
    public String getModeLabel() { return "ABCD".substring(mode, mode + 1); }
    public int getBandwidthKhz() { return bandwidthKhz; }
    public String getStreamUrl() { return streamUrl; }
    public String getImageUrl() { return imageUrl; }
    public String getServiceLabel() { return serviceLabel; }
    public String getShortLabel() { return shortLabel; }
    public String getTextMessage() { return textMessage; }
    @Nullable public BlockPos getAntennaPos() { return antennaPos; }
    public void setAntennaPos(@Nullable BlockPos pos) { antennaPos = pos == null ? null : pos.immutable(); setChanged(); }
    public boolean hasAntennaConnected() {
        return antennaPos != null && level != null && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna
                && !(antenna instanceof LoopAntennaBlockEntity)
                && !(antenna instanceof YagiAntennaBlockEntity)
                && worldPosition.equals(antenna.getLinkedTransmitterPos());
    }
    public double antennaGainToward(Vec3 target) {
        return SignalMath.linkedAntennaGainDb(level, antennaPos, worldPosition, target);
    }
    public int getOpusBitrateKbps() {
        int[] perFiveKhz = {24, 20, 16, 12};
        return perFiveKhz[mode] * bandwidthKhz / 5;
    }
    public int getMp3BitrateKbps() {
        return com.radiomod.server.ServerAudioProxy.dabMp3BitrateKbps(true, getOpusBitrateKbps());
    }
    public double getMinimumSnrDb() { return new double[]{15, 12, 9, 6}[mode] + (bandwidthKhz == 20 ? 3 : bandwidthKhz == 5 ? -1 : 0); }
    public void setFrequencyKhz(int value) { frequencyKhz = DrmSignal.clampFrequency(value); setChanged(); }
    public void setPowerW(int value) { powerW = SignalMath.clampPowerW(value); setChanged(); }
    public void cycleMode() { mode = (mode + 1) % 4; setChanged(); }
    public void cycleBandwidth() { bandwidthKhz = bandwidthKhz == 5 ? 10 : bandwidthKhz == 10 ? 20 : 5; setChanged(); }
    public void setText(String field, String value) {
        if (value == null) return;
        String text = value.strip();
        switch (field) {
            case "stream" -> streamUrl = allowedUrl(text) ? limit(text, 1024) : "";
            case "image" -> imageUrl = allowedImageUrl(text) ? limit(text, 1024) : "";
            case "service" -> serviceLabel = limit(text, 64);
            case "short" -> shortLabel = limit(text, 16);
            case "message" -> textMessage = limit(text, 256);
            default -> { return; }
        }
        setChanged();
    }
    private static boolean allowedUrl(String value) {
        return value.isBlank() || value.startsWith("https://") || value.startsWith("http://") || value.startsWith("radio-file://");
    }
    private static boolean allowedImageUrl(String value) {
        return value.isBlank() || value.startsWith("https://") || value.startsWith("http://");
    }
    private static String limit(String text, int max) { return text.substring(0, Math.min(max, text.length())); }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            SignalNetwork.registerTransmitter(level, worldPosition);
            TransmitterChunkLoading.forceChunkForTransmitter(level, worldPosition);
        }
    }
    @Override public void setRemoved() {
        if (level != null && !level.isClientSide()) SignalNetwork.unregisterTransmitter(level, worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FrequencyKhz", frequencyKhz); tag.putInt("PowerW", powerW);
        tag.putInt("Mode", mode); tag.putInt("BandwidthKhz", bandwidthKhz);
        tag.putString("StreamUrl", streamUrl); tag.putString("ImageUrl", imageUrl);
        tag.putString("ServiceLabel", serviceLabel); tag.putString("ShortLabel", shortLabel);
        tag.putString("TextMessage", textMessage);
        if (antennaPos != null) tag.putLong("AntennaPos", antennaPos.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequencyKhz = DrmSignal.clampFrequency(tag.getInt("FrequencyKhz"));
        powerW = SignalMath.clampPowerW(tag.getInt("PowerW"));
        mode = Math.max(0, Math.min(3, tag.getInt("Mode")));
        int savedBandwidth = tag.getInt("BandwidthKhz");
        bandwidthKhz = savedBandwidth == 5 || savedBandwidth == 20 ? savedBandwidth : 10;
        setText("stream", tag.getString("StreamUrl")); setText("image", tag.getString("ImageUrl"));
        serviceLabel = limit(tag.getString("ServiceLabel"), 64);
        shortLabel = limit(tag.getString("ShortLabel"), 16);
        textMessage = limit(tag.getString("TextMessage"), 256);
        antennaPos = tag.contains("AntennaPos") ? BlockPos.of(tag.getLong("AntennaPos")) : null;
    }
}
