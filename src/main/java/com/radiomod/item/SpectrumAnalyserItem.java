package com.radiomod.item;

import com.radiomod.menu.SpectrumAnalyserMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SpectrumAnalyserItem extends Item {
    public SpectrumAnalyserItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new SpectrumAnalyserMenu(id, inventory),
                Component.translatable("container.radio.spectrum_analyser")));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
