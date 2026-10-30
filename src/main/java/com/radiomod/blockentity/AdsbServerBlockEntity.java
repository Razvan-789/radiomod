package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.server.TransmitterChunkLoading;
import com.radiomod.signal.AdsbServerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A remote endpoint for exactly one placed ADS-B receiver. */
public class AdsbServerBlockEntity extends BlockEntity {
    @Nullable private BlockPos receiverPos;
    public AdsbServerBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.ADSB_SERVER_BLOCK_ENTITY.get(), pos, state);
    }
    @Nullable public BlockPos receiverPos() { return receiverPos; }
    @Nullable public AdsbReceiverBlockEntity receiver() {
        return receiverPos != null && level != null
                && level.getBlockEntity(receiverPos) instanceof AdsbReceiverBlockEntity receiver ? receiver : null;
    }
    public void setReceiverPos(@Nullable BlockPos pos) {
        BlockPos old = receiverPos;
        receiverPos = pos == null ? null : pos.immutable();
        setChanged();
        if (level != null && !level.isClientSide()) {
            if (receiverPos != null) TransmitterChunkLoading.forceChunkForTransmitter(level, receiverPos);
            if (old != null && !old.equals(receiverPos)) TransmitterChunkLoading.releaseChunkIfUnused(level, old);
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            AdsbServerNetwork.add(level, worldPosition);
            TransmitterChunkLoading.forceChunkForTransmitter(level, worldPosition);
            if (receiverPos != null) TransmitterChunkLoading.forceChunkForTransmitter(level, receiverPos);
        }
    }
    @Override public void setRemoved() {
        if (level != null && !level.isClientSide()) AdsbServerNetwork.remove(level, worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (receiverPos != null) tag.putLong("ReceiverPos", receiverPos.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        receiverPos = tag.contains("ReceiverPos") ? BlockPos.of(tag.getLong("ReceiverPos")) : null;
    }
}
