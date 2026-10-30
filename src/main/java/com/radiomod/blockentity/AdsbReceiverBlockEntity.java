package com.radiomod.blockentity;

import com.radiomod.Radio;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class AdsbReceiverBlockEntity extends BlockEntity {
    @Nullable private BlockPos antennaPos;
    public AdsbReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.ADSB_RECEIVER_BLOCK_ENTITY.get(), pos, state);
    }
    @Nullable public BlockPos getAntennaPos() { return antennaPos; }
    public void setAntennaPos(@Nullable BlockPos value) {
        antennaPos = value == null ? null : value.immutable(); setChanged();
    }
    public boolean hasAntennaConnected() {
        return antennaPos != null && level != null
                && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna
                && worldPosition.equals(antenna.getLinkedTransmitterPos());
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (antennaPos != null) tag.putLong("AntennaPos", antennaPos.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        antennaPos = tag.contains("AntennaPos") ? BlockPos.of(tag.getLong("AntennaPos")) : null;
    }
}
