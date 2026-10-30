package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import com.radiomod.menu.VorTransmitterMenu;
import com.radiomod.server.TransmitterChunkLoading;
import com.radiomod.signal.VorNetwork;
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

public class VorTransmitterBlock extends BaseEntityBlock {
    public static final MapCodec<VorTransmitterBlock> CODEC = simpleCodec(VorTransmitterBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public VorTransmitterBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VorTransmitterBlockEntity(pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                           Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new VorTransmitterMenu(id, inventory, pos),
                Component.translatable("container.radio.vor_transmitter")), buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor
                    && vor.antennaPos() != null
                    && level.getBlockEntity(vor.antennaPos()) instanceof AntennaBlockEntity antenna
                    && pos.equals(antenna.getLinkedTransmitterPos())) antenna.setLinkedTransmitterPos(null);
            VorNetwork.remove(level, pos);
            TransmitterChunkLoading.releaseChunkIfUnused(level, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
