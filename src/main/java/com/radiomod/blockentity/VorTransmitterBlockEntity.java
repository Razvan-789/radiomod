package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.server.TransmitterChunkLoading;
import com.radiomod.signal.VorNetwork;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class VorTransmitterBlockEntity extends BlockEntity {
    private int frequencyKhz = 113_000;
    private String identifier = "VOR";
    @Nullable private BlockPos antennaPos;

    public VorTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.VOR_TRANSMITTER_BLOCK_ENTITY.get(), pos, state);
    }
    public int frequencyKhz() { return frequencyKhz; }
    public String identifier() { return identifier; }
    @Nullable public BlockPos antennaPos() { return antennaPos; }
    public void setFrequencyKhz(int value) {
        frequencyKhz = Math.clamp(Math.round(value / 50.0f) * 50, 108_000, 117_950);
        setChanged();
    }
    public void setIdentifier(String value) {
        String cleaned = value == null ? "" : value.strip().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        identifier = cleaned.isBlank() ? "VOR" : cleaned.substring(0, Math.min(4, cleaned.length()));
        setChanged();
    }
    public void setAntennaPos(@Nullable BlockPos value) {
        antennaPos = value == null ? null : value.immutable();
        setChanged();
    }
    public boolean hasAntennaConnected() {
        return antennaPos != null && level != null
                && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna
                && !(antenna instanceof LoopAntennaBlockEntity)
                && !(antenna instanceof DirectionalAntennaBlockEntity)
                && !(antenna instanceof YagiAntennaBlockEntity)
                && worldPosition.equals(antenna.getLinkedTransmitterPos());
    }
    public boolean hasDmeAttached() {
        if (level == null) return false;
        for (Direction direction : Direction.values())
            if (level.getBlockState(worldPosition.relative(direction)).is(Radio.DME_MODULE_BLOCK.get())) return true;
        return false;
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            VorNetwork.add(level, worldPosition);
            TransmitterChunkLoading.forceChunkForTransmitter(level, worldPosition);
        }
    }
    @Override public void setRemoved() {
        if (level != null && !level.isClientSide()) VorNetwork.remove(level, worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FrequencyKhz", frequencyKhz);
        tag.putString("Identifier", identifier);
        if (antennaPos != null) tag.putLong("AntennaPos", antennaPos.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FrequencyKhz")) setFrequencyKhz(tag.getInt("FrequencyKhz"));
        if (tag.contains("Identifier")) setIdentifier(tag.getString("Identifier"));
        antennaPos = tag.contains("AntennaPos") ? BlockPos.of(tag.getLong("AntennaPos")) : null;
    }
}
