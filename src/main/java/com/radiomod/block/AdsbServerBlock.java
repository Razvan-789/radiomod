package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import com.radiomod.server.TransmitterChunkLoading;
import com.radiomod.signal.AdsbServerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
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

public class AdsbServerBlock extends BaseEntityBlock {
    public static final MapCodec<AdsbServerBlock> CODEC = simpleCodec(AdsbServerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public AdsbServerBlock(Properties properties) {
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
        return new AdsbServerBlockEntity(pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                           Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AdsbServerBlockEntity server)
            player.displayClientMessage(Component.literal(server.receiverPos() == null
                    ? "ADS-B server: select a receiver with the Linking Tool, then click this server"
                    : server.receiver() == null ? "ADS-B server: linked receiver unavailable"
                    : "ADS-B server linked to receiver at " + server.receiverPos().toShortString()), true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof AdsbServerBlockEntity server) server.setReceiverPos(null);
            AdsbServerNetwork.remove(level, pos);
            TransmitterChunkLoading.releaseChunkIfUnused(level, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
