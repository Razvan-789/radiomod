package com.radiomod.item;

import com.radiomod.signal.MeteorScatter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A handheld readout of the current dimension's meteor rate and FM opening forecast. */
public class MeteorForecasterItem extends Item {
    public MeteorForecasterItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            player.displayClientMessage(Component.literal(MeteorScatter.status(server)), false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
