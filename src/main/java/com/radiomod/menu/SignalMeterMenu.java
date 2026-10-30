package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.signal.SignalMeterState;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class SignalMeterMenu extends AbstractContainerMenu {
    private final Player owner;

    public SignalMeterMenu(int id, Inventory inventory) {
        super(Radio.SIGNAL_METER_MENU.get(), id);
        owner = inventory.player;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player != owner || !SignalMeterState.hasMeter(player)) return false;
        SignalMeterState.Settings state = SignalMeterState.settings(player);
        if (id == 0) state.dab = !state.dab;
        else if (id == 1 || id == 2) {
            int direction = id == 1 ? -1 : 1;
            if (state.dab) state.channelIndex = com.radiomod.signal.DabChannel.clampIndex(state.channelIndex + direction);
            else state.frequencyKhz = com.radiomod.signal.SignalMath.clampFmFrequencyKhz(state.frequencyKhz + direction * 100);
            state.decodedGroups.clear();
        } else if (id == 3) state.pinned = !state.pinned;
        else if (id >= 1_000_000 && id <= 1_108_000) {
            state.frequencyKhz = com.radiomod.signal.SignalMath.clampFmFrequencyKhz(id - 1_000_000);
            state.decodedGroups.clear();
        } else return false;
        return true;
    }

    @Override public boolean stillValid(Player player) { return SignalMeterState.hasMeter(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
