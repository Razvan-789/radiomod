package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DirectionalAntennaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DirectionalAntennaMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Level level;
    private final DataSlot[] gains = new DataSlot[DirectionalAntennaBlockEntity.SECTORS];

    public DirectionalAntennaMenu(int id, Inventory inventory, BlockPos pos) {
        super(Radio.DIRECTIONAL_ANTENNA_MENU.get(), id);
        this.pos = pos;
        this.level = inventory.player.level();
        for (int i = 0; i < gains.length; i++) gains[i] = addDataSlot(DataSlot.standalone());
        broadcastChanges();
    }

    public int gainAt(int sector) { return gains[sector].get(); }

    @Override public void broadcastChanges() {
        DirectionalAntennaBlockEntity antenna = getAntenna();
        if (antenna != null && !level.isClientSide()) {
            for (int i = 0; i < gains.length; i++) gains[i].set(antenna.gainAt(i));
        }
        super.broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= gains.length * 2 || !stillValid(player)) return false;
        DirectionalAntennaBlockEntity antenna = getAntenna();
        if (!player.level().isClientSide() && antenna != null) {
            int sector = id / 2;
            antenna.adjustGain(sector, id % 2 == 0 ? -1 : 1);
            broadcastChanges();
        }
        return true;
    }

    private DirectionalAntennaBlockEntity getAntenna() {
        return level.getBlockEntity(pos) instanceof DirectionalAntennaBlockEntity antenna ? antenna : null;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof DirectionalAntennaBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
