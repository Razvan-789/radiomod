package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.menu.DrmTransmitterMenu;
import com.radiomod.server.TransmitterChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DrmTransmitterBlock extends BaseEntityBlock {
    public static final MapCodec<DrmTransmitterBlock> CODEC = simpleCodec(DrmTransmitterBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public DrmTransmitterBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DrmTransmitterBlockEntity(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DrmTransmitterBlockEntity tx) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new DrmTransmitterMenu(id, inventory, tx),
                    Component.translatable("container.radio.drm_transmitter")));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!oldState.is(newState.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof DrmTransmitterBlockEntity tx
                    && tx.getAntennaPos() != null
                    && level.getBlockEntity(tx.getAntennaPos()) instanceof AntennaBlockEntity antenna
                    && pos.equals(antenna.getLinkedTransmitterPos())) antenna.setLinkedTransmitterPos(null);
            TransmitterChunkLoading.releaseChunkIfUnused(level, pos);
        }
        super.onRemove(oldState, level, pos, newState, moved);
    }
}
