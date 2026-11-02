package com.radiomod.block;

import com.radiomod.Radio;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class DmeModuleBlock extends Block {
    public static final BooleanProperty ATTACHED = BooleanProperty.create("attached");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public DmeModuleBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(ATTACHED, false)
                .setValue(FACING, Direction.NORTH));
    }
    public static boolean hasAdjacentVor(Level level, BlockPos pos) {
        for (Direction direction : Direction.values())
            if (level.getBlockState(pos.relative(direction)).is(Radio.VOR_TRANSMITTER_BLOCK.get())) return true;
        return false;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ATTACHED, hasAdjacentVor(context.getLevel(), context.getClickedPos()))
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                          BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide()) {
            boolean attached = hasAdjacentVor(level, pos);
            if (state.getValue(ATTACHED) != attached) level.setBlock(pos, state.setValue(ATTACHED, attached), 3);
        }
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                           Player player, BlockHitResult hit) {
        if (!level.isClientSide()) player.displayClientMessage(Component.literal(state.getValue(ATTACHED)
                ? "DME attached to VOR" : "Place this beside a VOR transmitter"), true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACHED, FACING);
    }
}
