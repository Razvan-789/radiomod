package com.radiomod.menu;

import com.radiomod.Radio;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RecorderMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public RecorderMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(Radio.RECORDER_MENU.get(), containerId);
        this.pos = pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(Radio.RECORDER_BLOCK.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
