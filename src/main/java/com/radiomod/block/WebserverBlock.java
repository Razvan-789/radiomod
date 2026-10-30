package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.menu.WebTabletMenu;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class WebserverBlock extends BaseEntityBlock {
    public static final MapCodec<WebserverBlock> CODEC = simpleCodec(WebserverBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public WebserverBlock(Properties properties) {
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
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WebserverBlockEntity(pos, state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                       BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,
                com.radiomod.Radio.WEBSERVER_BLOCK_ENTITY.get(), WebserverBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof WebserverBlockEntity) {
            player.openMenu(new SimpleMenuProvider((id, inv, owner) -> new WebTabletMenu(id, inv, pos),
                    Component.translatable("container.radio.webserver")), buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof WebserverBlockEntity webserver
                    && webserver.antennaPos() != null
                    && level.getBlockEntity(webserver.antennaPos()) instanceof AntennaBlockEntity antenna
                    && pos.equals(antenna.getLinkedTransmitterPos())) antenna.setLinkedTransmitterPos(null);
            TransmitterChunkLoading.releaseChunkIfUnused(level, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
