package com.radiomod.block;

import com.mojang.serialization.MapCodec;
import com.radiomod.blockentity.DirectionalAntennaBlockEntity;
import com.radiomod.menu.DirectionalAntennaMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DirectionalAntennaBlock extends AntennaBlock {
    public static final MapCodec<DirectionalAntennaBlock> CODEC = simpleCodec(DirectionalAntennaBlock::new);

    public DirectionalAntennaBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DirectionalAntennaBlockEntity(pos, state);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DirectionalAntennaBlockEntity)) return InteractionResult.PASS;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                new DirectionalAntennaMenu(id, inventory, pos),
                Component.translatable("container.radio.directional_antenna")),
                buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
}
