package com.radiomod.item;

import com.radiomod.menu.RadioMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DigitalRadioItem extends RadioItem {
    private static final Component TITLE = Component.translatable("container.radio.radio");

    public DigitalRadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean supportsDab() {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        setDx3Selected(player, false);
        setTechnisatSelected(player, false);
        PortableDrmRadioItem.setSelected(player, false);
        if (!level.isClientSide()) {
            setMode(player, MODE_FM);
            MenuProvider provider = new SimpleMenuProvider((containerId, inventory, menuPlayer) ->
                    new RadioMenu(containerId, inventory, player, stack), TITLE);
            player.openMenu(provider);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
