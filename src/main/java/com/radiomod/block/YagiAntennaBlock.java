package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.YagiAntennaBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Receive-only Yagi; right-click the block to turn its beam clockwise. */
public class YagiAntennaBlock extends AntennaBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final MapCodec<YagiAntennaBlock> codec;
    private final int elements;

    public YagiAntennaBlock(Properties properties, int elements) {
        super(properties);
        if (elements != 3 && elements != 5 && elements != 7 && elements != 11)
            throw new IllegalArgumentException("Unsupported Yagi element count: " + elements);
        this.elements = elements;
        codec = simpleCodec(p -> new YagiAntennaBlock(p, elements));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public int elements() { return elements; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new YagiAntennaBlockEntity(pos, state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            Direction next = state.getValue(FACING).getClockWise();
            level.setBlock(pos, state.setValue(FACING, next), 3);
            player.displayClientMessage(Component.literal("Yagi pointing " + next.getName().toUpperCase()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof YagiAntennaBlockEntity antenna) {
            BlockPos linked = antenna.getLinkedTransmitterPos();
            if (linked != null && level.getBlockEntity(linked) instanceof Dx3ReceiverBlockEntity receiver
                    && pos.equals(receiver.getAntennaPos())) receiver.setAntennaPos(null);
            else if (linked != null && level.getBlockEntity(linked) instanceof DrmRadioBlockEntity receiver
                    && pos.equals(receiver.getAntennaPos())) receiver.setAntennaPos(null);
            else if (linked != null && level.getBlockEntity(linked) instanceof WebserverBlockEntity webserver
                    && pos.equals(webserver.antennaPos())) webserver.setAntennaPos(null);
            else if (linked != null && level.getBlockEntity(linked) instanceof AdsbReceiverBlockEntity receiver
                    && pos.equals(receiver.getAntennaPos())) receiver.setAntennaPos(null);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
