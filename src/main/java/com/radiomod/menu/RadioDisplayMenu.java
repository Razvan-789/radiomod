package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.RadioDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class RadioDisplayMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final BlockPos pos;

    public RadioDisplayMenu(int id, Inventory inventory, BlockPos pos) {
        super(Radio.RADIO_DISPLAY_MENU.get(), id);
        this.inventory = inventory;
        this.pos = pos;
    }

    public RadioDisplayMenu(int id, Inventory inventory, RadioDisplayBlockEntity display) {
        this(id, inventory, display.getBlockPos());
    }

    public BlockPos pos() { return pos; }
    public RadioDisplayBlockEntity display() {
        return inventory.player.level().getBlockEntity(pos) instanceof RadioDisplayBlockEntity display ? display : null;
    }

    @Override public boolean stillValid(Player player) {
        return display() != null && player.distanceToSqr(com.radiomod.signal.MovingRadioPositions.world(
                player.level(), pos)) < 64;
    }

    @Override public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int slot) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
