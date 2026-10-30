package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.menu.Dx3Menu;
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

public class Dx3ReceiverBlock extends BaseEntityBlock {
    public static final MapCodec<Dx3ReceiverBlock> CODEC = simpleCodec(Dx3ReceiverBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public Dx3ReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof Dx3ReceiverBlockEntity receiver) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new Dx3Menu(id, inventory, owner, receiver),
                    Component.translatable("container.radio.dx3_receiver")),
                    buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Dx3ReceiverBlockEntity(pos, state);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                                   boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof Dx3ReceiverBlockEntity receiver
                && receiver.getAntennaPos() != null
                && level.getBlockEntity(receiver.getAntennaPos()) instanceof AntennaBlockEntity antenna
                && pos.equals(antenna.getLinkedTransmitterPos())) {
            antenna.setLinkedTransmitterPos(null);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
