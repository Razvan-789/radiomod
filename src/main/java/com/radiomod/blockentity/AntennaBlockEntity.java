package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.signal.SignalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class AntennaBlockEntity extends BlockEntity {
    private static final String KEY_LINKED_TRANSMITTER_POS = "LinkedTransmitterPos";

    @Nullable
    private BlockPos linkedTransmitterPos;

    public AntennaBlockEntity(BlockPos pos, BlockState blockState) {
        this(Radio.ANTENNA_BLOCK_ENTITY.get(), pos, blockState);
    }

    protected AntennaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Nullable
    public BlockPos getLinkedTransmitterPos() {
        return linkedTransmitterPos;
    }

    public void setLinkedTransmitterPos(@Nullable BlockPos linkedTransmitterPos) {
        if (this.linkedTransmitterPos != null) SignalNetwork.markChanged(level);
        this.linkedTransmitterPos = linkedTransmitterPos;
        setChanged();
    }

    @Override public void setChanged() {
        super.setChanged();
        if (linkedTransmitterPos != null) SignalNetwork.markChanged(level);
    }

    @Override public void setRemoved() {
        if (linkedTransmitterPos != null) SignalNetwork.markChanged(level);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkedTransmitterPos != null) {
            tag.putLong(KEY_LINKED_TRANSMITTER_POS, linkedTransmitterPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedTransmitterPos = tag.contains(KEY_LINKED_TRANSMITTER_POS) ? BlockPos.of(tag.getLong(KEY_LINKED_TRANSMITTER_POS)) : null;
    }
}
