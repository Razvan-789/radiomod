package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DrmTransmitterMenu extends AbstractContainerMenu {
    public static final int FREQ_DOWN = 0, FREQ_UP = 1, POWER_DOWN = 2, POWER_UP = 3, MODE = 4, BANDWIDTH = 5;
    public static final int SET_FREQ = 100_000, SET_POWER = 200_000;
    @Nullable private final DrmTransmitterBlockEntity tx;
    private final DataSlot frequency = addDataSlot(DataSlot.standalone());
    private final DataSlot powerLow = addDataSlot(DataSlot.standalone());
    private final DataSlot powerHigh = addDataSlot(DataSlot.standalone());
    private final DataSlot mode = addDataSlot(DataSlot.standalone());
    private final DataSlot bandwidth = addDataSlot(DataSlot.standalone());
    private final DataSlot mp3Bitrate = addDataSlot(DataSlot.standalone());
    private final DataSlot antenna = addDataSlot(DataSlot.standalone());
    private final TextSlots service = new TextSlots(64), shortName = new TextSlots(16), message = new TextSlots(256),
            stream = new TextSlots(1024), image = new TextSlots(1024);

    public DrmTransmitterMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public DrmTransmitterMenu(int id, Inventory inventory, @Nullable DrmTransmitterBlockEntity tx) {
        super(Radio.DRM_TRANSMITTER_MENU.get(), id);
        this.tx = tx;
        sync();
    }
    private void sync() {
        if (tx == null) return;
        frequency.set(tx.getFrequencyKhz());
        powerLow.set(tx.getPowerW() & 0xFFFF); powerHigh.set(tx.getPowerW() >>> 16);
        mode.set(tx.getMode()); bandwidth.set(tx.getBandwidthKhz()); mp3Bitrate.set(tx.getMp3BitrateKbps());
        antenna.set(tx.hasAntennaConnected() ? 1 : 0);
        service.set(tx.getServiceLabel()); shortName.set(tx.getShortLabel()); message.set(tx.getTextMessage());
        stream.set(tx.getStreamUrl()); image.set(tx.getImageUrl());
    }
    @Override public void broadcastChanges() { sync(); super.broadcastChanges(); }
    public int frequency() { return frequency.get(); }
    public int power() { return (powerHigh.get() << 16) | (powerLow.get() & 0xFFFF); }
    public int mode() { return mode.get(); }
    public int bandwidth() { return bandwidth.get(); }
    public boolean hasAntenna() { return antenna.get() != 0; }
    public String service() { return service.get(); }
    public String shortName() { return shortName.get(); }
    public String message() { return message.get(); }
    public String stream() { return stream.get(); }
    public String image() { return image.get(); }
    public int opusBitrate() { return new int[]{24, 20, 16, 12}[Math.max(0, Math.min(3, mode()))] * bandwidth() / 5; }
    public int mp3Bitrate() { return mp3Bitrate.get(); }
    public double minimumSnr() { return new int[]{15, 12, 9, 6}[Math.max(0, Math.min(3, mode()))]
            + (bandwidth() == 20 ? 3 : bandwidth() == 5 ? -1 : 0); }
    public void setText(String field, String value) { if (tx != null) { tx.setText(field, value); broadcastChanges(); } }
    public void setUploadedAudioSource(String source) { setText("stream", source); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (tx == null || !stillValid(player)) return false;
        if (id >= SET_POWER) tx.setPowerW(id - SET_POWER);
        else if (id >= SET_FREQ) tx.setFrequencyKhz(id - SET_FREQ);
        else switch (id) {
            case FREQ_DOWN -> tx.setFrequencyKhz(tx.getFrequencyKhz() - 1);
            case FREQ_UP -> tx.setFrequencyKhz(tx.getFrequencyKhz() + 1);
            case POWER_DOWN -> tx.setPowerW(tx.getPowerW() - 10);
            case POWER_UP -> tx.setPowerW(tx.getPowerW() + 10);
            case MODE -> tx.cycleMode();
            case BANDWIDTH -> tx.cycleBandwidth();
            default -> { return false; }
        }
        broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return tx == null || tx.getLevel() != null && tx.getLevel().getBlockEntity(tx.getBlockPos()) == tx
                && player.distanceToSqr(tx.getBlockPos().getX() + .5, tx.getBlockPos().getY() + .5,
                tx.getBlockPos().getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    private final class TextSlots {
        private final DataSlot length = addDataSlot(DataSlot.standalone());
        private final DataSlot[] chars;
        private TextSlots(int max) {
            chars = new DataSlot[max];
            for (int i = 0; i < max; i++) chars[i] = addDataSlot(DataSlot.standalone());
        }
        private void set(String text) {
            int count = Math.min(chars.length, text.length());
            length.set(count);
            for (int i = 0; i < count; i++) chars[i].set(text.charAt(i));
        }
        private String get() {
            int count = Math.max(0, Math.min(chars.length, length.get()));
            StringBuilder out = new StringBuilder(count);
            for (int i = 0; i < count; i++) out.append((char) chars[i].get());
            return out.toString();
        }
    }
}
