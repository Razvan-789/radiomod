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
            case 1, 2 -> {
                int direction = id == 1 ? -1 : 1;
                int next = band == 1 ? DabChannel.nearestIndexForFrequencyKhz(s.centers[band]) + direction : 0;
                int khz = band == 1 && next >= 0 && next < DabChannel.size()
                        ? DabChannel.byIndex(next).frequencyKhz()
                        : s.centers[band] + direction * SpectrumAnalyserState.tuneStep(band);
                if (khz < SpectrumAnalyserState.min(band)) khz = switch (band) {
                    case 0 -> 30_000; case 1 -> 117_950; case 3 -> 107_900;
                    case 4 -> 239_200; default -> SpectrumAnalyserState.min(band);
                };
                if (khz > SpectrumAnalyserState.max(band)) khz = switch (band) {
                    case 2 -> 65_000; case 0 -> 108_000; case 3 -> 174_928;
                    case 1 -> 1_090_000; default -> SpectrumAnalyserState.max(band);
                };
                SpectrumAnalyserState.tune(s, khz);
            }
            case 3, 4 -> s.spans[band] = Math.clamp(s.spans[band]
                    + (id == 3 ? -1 : 1) * SpectrumAnalyserState.minSpan(band) / 2,
                    SpectrumAnalyserState.minSpan(band), SpectrumAnalyserState.maxSpan(band));
            case 5, 6 -> s.bandwidths[band] = Math.clamp(s.bandwidths[band]
                    + (id == 5 ? -1 : 1) * SpectrumAnalyserState.bandwidthStep(band),
                    SpectrumAnalyserState.minBandwidth(band), SpectrumAnalyserState.maxBandwidth(band));
            default -> {
                if (id >= 2_000_000 && id < 2_000_032) {
                    s.selectedService = id - 2_000_000;
                    return true;
                }
                if (id < 1_000_000 || id > 2_091_000) return false;
                int khz = id - 1_000_000;
                if (SpectrumAnalyserState.bandFor(khz) < 0) return false;
                SpectrumAnalyserState.tune(s, khz);
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
