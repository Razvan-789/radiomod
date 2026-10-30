package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.block.YagiAntennaBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Forward gain grows and the receive beam narrows with element count. */
public class YagiAntennaBlockEntity extends AntennaBlockEntity {
    public YagiAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.YAGI_ANTENNA_BLOCK_ENTITY.get(), pos, state);
    }

    public double gainToward(Vec3 source) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof YagiAntennaBlock yagi)) return 0;
        Direction facing = state.getValue(YagiAntennaBlock.FACING);
        Vec3 center = Vec3.atCenterOf(getBlockPos());
        double dx = source.x - center.x, dz = source.z - center.z;
        double distance = Math.hypot(dx, dz);
        if (distance < 0.001) return 0;
        double alignment = (dx * facing.getStepX() + dz * facing.getStepZ()) / distance;
        int count = yagi.elements();
        double forward = switch (count) { case 3 -> 5; case 5 -> 7; case 7 -> 9; default -> 12; };
        double sideAndRear = -4.0 - (count - 3) * 0.7;
        double beam = Math.pow(Math.max(0.0, alignment), 1.0 + (count - 3) * 0.25);
        return sideAndRear + (forward - sideAndRear) * beam;
    }
}
