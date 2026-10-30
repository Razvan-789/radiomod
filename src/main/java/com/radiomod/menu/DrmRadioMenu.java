package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.signal.DrmReception;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DrmRadioMenu extends AbstractContainerMenu {
    public static final int DOWN = 0, UP = 1, MUTE = 2, SET_FREQ = 100_000;
    @Nullable private final DrmRadioBlockEntity radio;
    private final DataSlot frequency = addDataSlot(DataSlot.standalone());
    private final DataSlot connected = addDataSlot(DataSlot.standalone());
    private final DataSlot muted = addDataSlot(DataSlot.standalone());
    private final DataSlot decoded = addDataSlot(DataSlot.standalone());
    private final DataSlot signalTenths = addDataSlot(DataSlot.standalone());
    private final DataSlot snrTenths = addDataSlot(DataSlot.standalone());
    private final DataSlot powerLow = addDataSlot(DataSlot.standalone());
    private final DataSlot powerHigh = addDataSlot(DataSlot.standalone());
    private final DataSlot mode = addDataSlot(DataSlot.standalone());
    private final DataSlot bandwidth = addDataSlot(DataSlot.standalone());
    private final DataSlot bitrate = addDataSlot(DataSlot.standalone());
    private final DataSlot mp3Bitrate = addDataSlot(DataSlot.standalone());
    private final TextSlots service = new TextSlots(64), shortName = new TextSlots(16), message = new TextSlots(256),
            image = new TextSlots(1024);
    public DrmRadioMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public DrmRadioMenu(int id, Inventory inventory, @Nullable DrmRadioBlockEntity radio) {
        super(Radio.DRM_RADIO_MENU.get(), id); this.radio = radio; sync();
    }
    private void sync() {
        if (radio == null) return;
        frequency.set(radio.getFrequencyKhz()); connected.set(radio.hasReceiveAntennaConnected() ? 1 : 0);
        muted.set(radio.isMuted() ? 1 : 0);
        DrmReception.Sample sample = DrmReception.sample(radio);
        decoded.set(sample.decoded() ? 1 : 0);
        signalTenths.set((int) Math.round(sample.signalDbuv() * 10));
        snrTenths.set((int) Math.round(sample.snrDb() * 10));
        if (sample.transmitter() instanceof DrmTransmitterBlockEntity tx) {
            powerLow.set(tx.getPowerW() & 0xFFFF); powerHigh.set(tx.getPowerW() >>> 16);
            mode.set(tx.getMode()); bandwidth.set(tx.getBandwidthKhz()); bitrate.set(tx.getOpusBitrateKbps());
            mp3Bitrate.set(tx.getMp3BitrateKbps());
            service.set(tx.getServiceLabel()); shortName.set(tx.getShortLabel()); message.set(tx.getTextMessage()); image.set(tx.getImageUrl());
        } else {
            powerLow.set(0); powerHigh.set(0); mode.set(0); bandwidth.set(0); bitrate.set(0); mp3Bitrate.set(0);
            service.set(""); shortName.set(""); message.set(""); image.set("");
        }
    }
    @Override public void broadcastChanges() { sync(); super.broadcastChanges(); }
    public int frequency() { return frequency.get(); }
    public boolean connected() { return connected.get() != 0; }
    public boolean muted() { return muted.get() != 0; }
    public boolean decoded() { return decoded.get() != 0; }
    public double signal() { return signalTenths.get() / 10.0; }
    public double snr() { return snrTenths.get() / 10.0; }
    public int power() { return (powerHigh.get() << 16) | (powerLow.get() & 0xFFFF); }
    public int mode() { return mode.get(); }
    public int bandwidth() { return bandwidth.get(); }
    public int opusBitrate() { return bitrate.get(); }
    public int mp3Bitrate() { return mp3Bitrate.get(); }
    public String service() { return service.get(); }
    public String shortName() { return shortName.get(); }
    public String message() { return message.get(); }
    public String image() { return image.get(); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (radio == null || !stillValid(player)) return false;
        if (id >= SET_FREQ) radio.setFrequencyKhz(id - SET_FREQ);
        else switch (id) {
            case DOWN -> radio.setFrequencyKhz(radio.getFrequencyKhz() - 1);
            case UP -> radio.setFrequencyKhz(radio.getFrequencyKhz() + 1);
            case MUTE -> radio.setMuted(!radio.isMuted());
            default -> { return false; }
        }
        broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return radio == null || radio.getLevel() != null && radio.getLevel().getBlockEntity(radio.getBlockPos()) == radio
                && player.distanceToSqr(radio.getBlockPos().getX() + .5, radio.getBlockPos().getY() + .5,
                radio.getBlockPos().getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    private final class TextSlots {
        private final DataSlot length = addDataSlot(DataSlot.standalone());
        private final DataSlot[] chars;
        private TextSlots(int max) { chars = new DataSlot[max]; for (int i = 0; i < max; i++) chars[i] = addDataSlot(DataSlot.standalone()); }
        private void set(String text) { int count = Math.min(chars.length, text.length()); length.set(count); for (int i = 0; i < count; i++) chars[i].set(text.charAt(i)); }
        private String get() { int count = Math.max(0, Math.min(chars.length, length.get())); StringBuilder out = new StringBuilder(count); for (int i = 0; i < count; i++) out.append((char) chars[i].get()); return out.toString(); }
    }
}
