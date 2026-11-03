package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.SpectrumAnalyserState;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class SpectrumAnalyserMenu extends AbstractContainerMenu {
    private final Player owner;

    public SpectrumAnalyserMenu(int id, Inventory inventory) {
        super(Radio.SPECTRUM_ANALYSER_MENU.get(), id);
        owner = inventory.player;
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (player != owner || !SpectrumAnalyserState.hasAnalyser(player)) return false;
        SpectrumAnalyserState.Settings s = SpectrumAnalyserState.settings(player);
        int band = s.band;
        switch (id) {
            case 0 -> s.band = (band + 1) % 3;
            case 1, 2 -> {
                int direction = id == 1 ? -1 : 1;
                s.centers[band] = band == 1
                        ? DabChannel.byIndex(DabChannel.clampIndex(
                                DabChannel.nearestIndexForFrequencyKhz(s.centers[band]) + direction)).frequencyKhz()
                        : Math.clamp(s.centers[band] + direction * SpectrumAnalyserState.tuneStep(band),
                                SpectrumAnalyserState.min(band), SpectrumAnalyserState.max(band));
            }
            case 3, 4 -> s.spans[band] = Math.clamp(s.spans[band]
                    + (id == 3 ? -1 : 1) * SpectrumAnalyserState.minSpan(band) / 2,
                    SpectrumAnalyserState.minSpan(band), SpectrumAnalyserState.maxSpan(band));
            case 5, 6 -> s.bandwidths[band] = Math.clamp(s.bandwidths[band]
                    + (id == 5 ? -1 : 1) * SpectrumAnalyserState.bandwidthStep(band),
                    SpectrumAnalyserState.minBandwidth(band), SpectrumAnalyserState.maxBandwidth(band));
            default -> {
                if (id < 1_000_000 || id > 1_240_000) return false;
                int khz = id - 1_000_000;
                if (khz < SpectrumAnalyserState.min(band) || khz > SpectrumAnalyserState.max(band)) return false;
                s.centers[band] = khz;
            }
        }
        return true;
    }

    @Override public boolean stillValid(Player player) { return SpectrumAnalyserState.hasAnalyser(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) SpectrumAnalyserState.clearRds(player);
    }
}
