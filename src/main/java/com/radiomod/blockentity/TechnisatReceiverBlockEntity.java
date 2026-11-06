package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.network.Dx3Station;
import com.radiomod.item.RadioItem;
import com.radiomod.signal.DrmSignal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fixed-position Technisat receiver; the scanned DAB list survives reopening and world saves. */
public class TechnisatReceiverBlockEntity extends Dx3ReceiverBlockEntity {
    private List<Dx3Station> scanned = List.of();
    private List<Dx3Station> scannedDrm = List.of();
    private boolean hasScan;
    private boolean hasDrmScan;
    private int mode = RadioItem.MODE_FM;
    private int drmFrequencyKhz = 1000;

    public TechnisatReceiverBlockEntity(BlockPos pos, BlockState state) {
        this(Radio.TECHNISAT_RECEIVER_BLOCK_ENTITY.get(), pos, state);
    }

    protected TechnisatReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public List<Dx3Station> scannedStations() { return scanned; }
    public boolean hasScan() { return hasScan; }
    public List<Dx3Station> scannedDrmStations() { return scannedDrm; }
    public boolean hasDrmScan() { return hasDrmScan; }
    public int getRadioMode() { return mode; }
    public void setRadioMode(int value) {
        mode = value == RadioItem.MODE_DAB ? RadioItem.MODE_DAB
                : value == RadioItem.MODE_DRM ? RadioItem.MODE_DRM : RadioItem.MODE_FM;
        setChanged();
    }
    public int getDrmFrequencyKhz() { return drmFrequencyKhz; }
    public void setDrmFrequencyKhz(int khz) { drmFrequencyKhz = DrmSignal.clampFrequency(khz); setChanged(); }

    public void storeScan(List<Dx3Station> stations) {
        scanned = stations.stream().filter(Dx3Station::dab).limit(128).toList();
        hasScan = true;
        setChanged();
    }
    public void storeDrmScan(List<Dx3Station> stations) {
        scannedDrm = stations.stream().filter(station -> !station.dab()).limit(128).toList();
        hasDrmScan = true;
        setChanged();
    }

    @Override public double receiverLossDb() { return 0.0; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("HasDabScan", hasScan);
        tag.putBoolean("HasDrmScan", hasDrmScan);
        tag.putInt("RadioMode", mode);
        tag.putInt("DrmFrequencyKhz", drmFrequencyKhz);
        ListTag list = new ListTag();
        for (Dx3Station station : scanned) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Channel", station.tuning());
            entry.putString("Sid", station.sid());
            entry.putString("Mux", station.mux());
            entry.putString("Label", station.label());
            entry.putString("Detail", station.detail());
            list.add(entry);
        }
        tag.put("ScannedDab", list);
        ListTag drmList = new ListTag();
        for (Dx3Station station : scannedDrm) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Frequency", station.tuning());
            entry.putString("ShortLabel", station.sid());
            entry.putString("ServiceLabel", station.mux());
            entry.putString("Label", station.label());
            entry.putString("Detail", station.detail());
            drmList.add(entry);
        }
        tag.put("ScannedDrm", drmList);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.contains("RadioMode") ? Math.max(RadioItem.MODE_FM,
                Math.min(RadioItem.MODE_DRM, tag.getInt("RadioMode")))
                : isDab() ? RadioItem.MODE_DAB : RadioItem.MODE_FM;
        drmFrequencyKhz = tag.contains("DrmFrequencyKhz")
                ? DrmSignal.clampFrequency(tag.getInt("DrmFrequencyKhz")) : 1000;
        hasScan = tag.getBoolean("HasDabScan");
        hasDrmScan = tag.getBoolean("HasDrmScan");
        List<Dx3Station> restored = new ArrayList<>();
        ListTag list = tag.getList("ScannedDab", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < 128; i++) {
            CompoundTag entry = list.getCompound(i);
            String sid = limit(entry.getString("Sid"), 16);
            String mux = limit(entry.getString("Mux"), 64);
            String label = limit(entry.getString("Label"), 96);
            String detail = limit(entry.getString("Detail"), 96);
            restored.add(new Dx3Station(true,
                    com.radiomod.signal.DabChannel.clampIndex(entry.getInt("Channel")), sid, mux,
                    label, label, detail, null));
        }
        scanned = hasScan ? List.copyOf(restored) : List.of();
        List<Dx3Station> restoredDrm = new ArrayList<>();
        ListTag drmList = tag.getList("ScannedDrm", Tag.TAG_COMPOUND);
        for (int i = 0; i < drmList.size() && i < 128; i++) {
            CompoundTag entry = drmList.getCompound(i);
            String shortLabel = limit(entry.getString("ShortLabel"), 16);
            String serviceLabel = limit(entry.getString("ServiceLabel"), 64);
            String label = limit(entry.getString("Label"), 96);
            String detail = limit(entry.getString("Detail"), 96);
            restoredDrm.add(new Dx3Station(false, DrmSignal.clampFrequency(entry.getInt("Frequency")),
                    shortLabel, serviceLabel, label, label, detail, null));
        }
        scannedDrm = hasDrmScan ? List.copyOf(restoredDrm) : List.of();
    }

    private static String limit(String text, int max) {
        return text.substring(0, Math.min(max, text.length()));
    }
}
