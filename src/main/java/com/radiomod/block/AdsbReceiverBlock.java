package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import com.radiomod.signal.AdsbServerNetwork;
import com.radiomod.menu.AdsbRadarMenu;
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

public class AdsbReceiverBlock extends BaseEntityBlock {
    public static final MapCodec<AdsbReceiverBlock> CODEC = simpleCodec(AdsbReceiverBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public AdsbReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return new AdsbReceiverBlockEntity(pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                           Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof AdsbReceiverBlockEntity)
            player.openMenu(new SimpleMenuProvider((id, inv, owner) -> new AdsbRadarMenu(id, inv, pos),
                    Component.translatable("container.radio.adsb_receiver")), buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof AdsbReceiverBlockEntity receiver
                    && receiver.getAntennaPos() != null
                    && level.getBlockEntity(receiver.getAntennaPos()) instanceof AntennaBlockEntity antenna
                    && pos.equals(antenna.getLinkedTransmitterPos())) antenna.setLinkedTransmitterPos(null);
            for (BlockPos serverPos : AdsbServerNetwork.get(level))
                if (level.getBlockEntity(serverPos) instanceof AdsbServerBlockEntity server
                        && pos.equals(server.receiverPos())) server.setReceiverPos(null);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
