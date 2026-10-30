package com.radiomod.blockentity;

import com.radiomod.Radio;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class LoopAntennaBlockEntity extends AntennaBlockEntity {
    public LoopAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.LOOP_ANTENNA_BLOCK_ENTITY.get(), pos, state);
    }
}
