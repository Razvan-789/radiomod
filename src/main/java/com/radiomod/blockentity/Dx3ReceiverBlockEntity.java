package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.item.RadioItem;
import com.radiomod.signal.Dx3ReceiverNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared station selection for a jukebox-style DX3 speaker. */
public class Dx3ReceiverBlockEntity extends BlockEntity {
    @Nullable private BlockPos antennaPos;
    private boolean dab;
    private int frequencyKhz = 101_700;
    private int channelIndex = com.radiomod.signal.DabChannel.nearestIndexForFrequencyKhz(220_352);
    private String sid = "";
    private String mux = "";
    private String pi = "";
    private String linkId = "";
    private int linkMode = RadioItem.DX3_BALANCED;
    private boolean muted;

    public Dx3ReceiverBlockEntity(BlockPos pos, BlockState state) {
        this(Radio.DX3_RECEIVER_BLOCK_ENTITY.get(), pos, state);
    }

    protected Dx3ReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean hasAntennaConnected() {
        return antennaPos != null && level != null
                && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna
                && getBlockPos().equals(antenna.getLinkedTransmitterPos());
    }

    @Nullable public BlockPos getAntennaPos() { return antennaPos; }
    public void setAntennaPos(@Nullable BlockPos pos) { antennaPos = pos == null ? null : pos.immutable(); setChanged(); }
    public Vec3 receptionPoint() { return Vec3.atCenterOf(hasAntennaConnected() ? antennaPos : worldPosition); }
    public double receiverLossDb() { return hasAntennaConnected() ? 0.0 : 22.0; }
    public double antennaGainToward(Vec3 transmitter) {
        return com.radiomod.signal.SignalMath.linkedAntennaGainDb(level, antennaPos, getBlockPos(), transmitter);
    }
    public boolean isDab() { return dab; }
    public int getFrequencyKhz() { return frequencyKhz; }
    public int getChannelIndex() { return channelIndex; }
    public String getSid() { return sid; }
    public String getMux() { return mux; }
    public String getPi() { return pi; }
    public String getLinkId() { return linkId; }
    public int getLinkMode() { return linkMode; }
    public boolean isMuted() { return muted; }

    public void select(boolean dab, int tuning, String sid, String mux) {
        this.dab = dab;
        if (dab) {
            channelIndex = com.radiomod.signal.DabChannel.clampIndex(tuning);
            this.sid = limit(sid, 16);
            this.mux = limit(mux, 64);
            pi = "";
            linkId = this.sid;
        } else {
            frequencyKhz = com.radiomod.signal.SignalMath.clampFmFrequencyKhz(tuning);
            pi = limit(sid, 16);
            this.sid = "";
            this.mux = "";
            linkId = pi;
        }
        setChanged();
    }

    public void retune(boolean dab, int tuning, String sid, String mux) {
        String preservedLink = linkId;
        select(dab, tuning, sid, mux);
        linkId = preservedLink;
    }

    public void setLinkMode(int mode) {
        if (mode >= RadioItem.DX3_PREFER_DAB && mode <= RadioItem.DX3_PREFER_QUALITY) {
            linkMode = mode; setChanged();
        }
    }

    public void setMuted(boolean value) { muted = value; setChanged(); }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null) Dx3ReceiverNetwork.add(level, worldPosition);
    }

    @Override public void setRemoved() {
        if (level != null) Dx3ReceiverNetwork.remove(level, worldPosition);
        super.setRemoved();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (antennaPos != null) tag.putLong("AntennaPos", antennaPos.asLong());
        tag.putBoolean("Dab", dab);
        tag.putInt("FrequencyKhz", frequencyKhz);
        tag.putInt("ChannelIndex", channelIndex);
        tag.putString("Sid", sid);
        tag.putString("Mux", mux);
        tag.putString("Pi", pi);
        tag.putString("LinkId", linkId);
        tag.putInt("LinkMode", linkMode);
        tag.putBoolean("Muted", muted);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        antennaPos = tag.contains("AntennaPos") ? BlockPos.of(tag.getLong("AntennaPos")) : null;
        dab = tag.getBoolean("Dab");
        frequencyKhz = com.radiomod.signal.SignalMath.clampFmFrequencyKhz(tag.getInt("FrequencyKhz"));
        channelIndex = com.radiomod.signal.DabChannel.clampIndex(tag.getInt("ChannelIndex"));
        sid = limit(tag.getString("Sid"), 16);
        mux = limit(tag.getString("Mux"), 64);
        pi = limit(tag.getString("Pi"), 16);
        linkId = limit(tag.getString("LinkId"), 16);
        linkMode = Math.max(RadioItem.DX3_PREFER_DAB, Math.min(RadioItem.DX3_PREFER_QUALITY, tag.getInt("LinkMode")));
        muted = tag.getBoolean("Muted");
    }

    private static String limit(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
}
