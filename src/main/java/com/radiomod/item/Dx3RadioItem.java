package com.radiomod.item;

import com.radiomod.menu.Dx3Menu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class Dx3RadioItem extends RadioItem {
    private static final Component TITLE = Component.translatable("container.radio.dx3");

    public Dx3RadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        PortableDrmRadioItem.setSelected(player, false);
        RadioItem.setTechnisatSelected(player, false);
        RadioItem.setSonySelected(player, false);
        if (RadioItem.getMode(player) == RadioItem.MODE_DRM) RadioItem.setMode(player, RadioItem.MODE_FM);
        if (!level.isClientSide()) {
            MenuProvider provider = new SimpleMenuProvider((containerId, inventory, menuPlayer) ->
                    new Dx3Menu(containerId, inventory, menuPlayer), TITLE);
            player.openMenu(provider);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
