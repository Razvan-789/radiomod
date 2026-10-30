package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.signal.DrmSignal;
import com.radiomod.signal.Dx3ReceiverNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DrmRadioBlockEntity extends BlockEntity {
    private int frequencyKhz = 1000;
    @Nullable private BlockPos antennaPos;
    private boolean muted;
    public DrmRadioBlockEntity(BlockPos pos, BlockState state) { super(Radio.DRM_RADIO_BLOCK_ENTITY.get(), pos, state); }
    public int getFrequencyKhz() { return frequencyKhz; }
    public void setFrequencyKhz(int value) { frequencyKhz = DrmSignal.clampFrequency(value); setChanged(); }
    @Nullable public BlockPos getAntennaPos() { return antennaPos; }
    public void setAntennaPos(@Nullable BlockPos pos) { antennaPos = pos == null ? null : pos.immutable(); setChanged(); }
    public boolean hasReceiveAntennaConnected() {
        return antennaPos != null && level != null
                && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna
                && (antenna instanceof LoopAntennaBlockEntity || antenna instanceof YagiAntennaBlockEntity)
                && worldPosition.equals(antenna.getLinkedTransmitterPos());
    }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean value) { muted = value; setChanged(); }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) Dx3ReceiverNetwork.add(level, worldPosition);
    }
    @Override public void setRemoved() {
        if (level != null && !level.isClientSide()) Dx3ReceiverNetwork.remove(level, worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FrequencyKhz", frequencyKhz);
        if (antennaPos != null) tag.putLong("AntennaPos", antennaPos.asLong());
        tag.putBoolean("Muted", muted);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequencyKhz = DrmSignal.clampFrequency(tag.getInt("FrequencyKhz"));
        antennaPos = tag.contains("AntennaPos") ? BlockPos.of(tag.getLong("AntennaPos")) : null;
        muted = tag.getBoolean("Muted");
    }
}
