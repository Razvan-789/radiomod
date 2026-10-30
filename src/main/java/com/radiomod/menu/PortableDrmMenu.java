package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.item.PortableDrmRadioItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class PortableDrmMenu extends AbstractContainerMenu {
    public static final int DOWN = 0, UP = 1, MUTE = 2, PIN = 3, SET_FREQ = 100_000;
    private final Player owner;
    private final DataSlot frequency = addDataSlot(DataSlot.standalone());
    private final DataSlot muted = addDataSlot(DataSlot.standalone());
    private final DataSlot pinned = addDataSlot(DataSlot.standalone());
    public PortableDrmMenu(int id, Inventory inventory) {
        super(Radio.PORTABLE_DRM_MENU.get(), id); owner = inventory.player; sync();
    }
    private void sync() {
        frequency.set(PortableDrmRadioItem.frequency(owner));
        muted.set(PortableDrmRadioItem.muted(owner) ? 1 : 0);
        pinned.set(PortableDrmRadioItem.pinned(owner) ? 1 : 0);
    }
    @Override public void broadcastChanges() { sync(); super.broadcastChanges(); }
    public int frequency() { return frequency.get(); }
    public boolean muted() { return muted.get() != 0; }
    public boolean pinned() { return pinned.get() != 0; }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) return false;
        if (id >= SET_FREQ) PortableDrmRadioItem.setFrequency(player, id - SET_FREQ);
        else switch (id) {
            case DOWN -> PortableDrmRadioItem.setFrequency(player, PortableDrmRadioItem.frequency(player) - 1);
            case UP -> PortableDrmRadioItem.setFrequency(player, PortableDrmRadioItem.frequency(player) + 1);
            case MUTE -> PortableDrmRadioItem.setMuted(player, !PortableDrmRadioItem.muted(player));
            case PIN -> PortableDrmRadioItem.setPinned(player, !PortableDrmRadioItem.pinned(player));
            default -> { return false; }
        }
        broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) { return PortableDrmRadioItem.hasInInventory(player); }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
