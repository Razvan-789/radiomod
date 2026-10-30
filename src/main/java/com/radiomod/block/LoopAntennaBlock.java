package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.LoopAntennaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LoopAntennaBlock extends AntennaBlock {
    public static final MapCodec<LoopAntennaBlock> CODEC = simpleCodec(LoopAntennaBlock::new);
    public LoopAntennaBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoopAntennaBlockEntity(pos, state);
    }
}
