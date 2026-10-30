package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.signal.SignalMath;
import com.radiomod.server.TransmitterPresets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class TransmitterMenu extends AbstractContainerMenu {
    public static final int BUTTON_FREQUENCY_DOWN = 0;
    public static final int BUTTON_FREQUENCY_UP = 1;
    public static final int BUTTON_POWER_DOWN = 2;
    public static final int BUTTON_POWER_UP = 3;
    public static final int BUTTON_DIRECTIONAL_TOGGLE = 4;
    public static final int BUTTON_PRESET_SAVE_BASE = 100;
    public static final int BUTTON_PRESET_LOAD_BASE = 110;
    private static final int BUTTON_SET_FREQUENCY_BASE = 1_000_000;
    private static final int BUTTON_SET_POWER_BASE = 2_000_000;
    private static final int STREAM_URL_MAX = 1024;

    @Nullable
    private final TransmitterBlockEntity blockEntity;
    private final Player owner;
    private final DataSlot presetMaskSlot;
    private final DataSlot frequencyKhzSlot;
    private final DataSlot powerWSlot;
    private final DataSlot antennaConnectedSlot;
    private final DataSlot rdsEnabledSlot;
    private final DataSlot rdsMaskLowSlot;
    private final DataSlot rdsMaskHighSlot;
    private final DataSlot rdsTpSlot;
    private final DataSlot rdsRtUrlModeSlot;
    private final DataSlot rdsOdaRtPlusSlot;
    private final DataSlot rdsCtEnabledSlot;
    private final DataSlot directionalEnabledSlot;
    private final DataSlot directionDegreesSlot;
    private final DataSlot streamUrlLengthSlot;
    private final DataSlot[] streamUrlSlots;
    private final DataSlot rdsPtySlot = addDataSlot(DataSlot.standalone());
    private final TextSlots rdsPi = new TextSlots(4), rdsPs = new TextSlots(512), rdsAf = new TextSlots(64),
            rdsEcc = new TextSlots(24), rdsLic = new TextSlots(24), rdsPin = new TextSlots(16),
            rdsRt = new TextSlots(64), rdsRtUrl = new TextSlots(200), rdsOdaGroup = new TextSlots(3),
            rdsPtyn = new TextSlots(8), rdsLongPs = new TextSlots(32);

    public TransmitterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public TransmitterMenu(int containerId, Inventory inventory, @Nullable TransmitterBlockEntity blockEntity) {
        super(Radio.TRANSMITTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.owner = inventory.player;
        presetMaskSlot = addDataSlot(DataSlot.standalone());
        presetMaskSlot.set(TransmitterPresets.occupiedMask(owner, "fm"));

        frequencyKhzSlot = addDataSlot(DataSlot.standalone());
        powerWSlot = addDataSlot(DataSlot.standalone());
        antennaConnectedSlot = addDataSlot(DataSlot.standalone());
        rdsEnabledSlot = addDataSlot(DataSlot.standalone());
        rdsMaskLowSlot = addDataSlot(DataSlot.standalone());
        rdsMaskHighSlot = addDataSlot(DataSlot.standalone());
        rdsTpSlot = addDataSlot(DataSlot.standalone());
        rdsRtUrlModeSlot = addDataSlot(DataSlot.standalone());
        rdsOdaRtPlusSlot = addDataSlot(DataSlot.standalone());
        rdsCtEnabledSlot = addDataSlot(DataSlot.standalone());
        directionalEnabledSlot = addDataSlot(DataSlot.standalone());
        directionDegreesSlot = addDataSlot(DataSlot.standalone());
        streamUrlLengthSlot = addDataSlot(DataSlot.standalone());
        streamUrlSlots = addCharSlots(STREAM_URL_MAX);

        if (blockEntity != null) {
            frequencyKhzSlot.set(blockEntity.getFrequencyKhz());
            powerWSlot.set(blockEntity.getPowerW());
            antennaConnectedSlot.set(blockEntity.hasAntennaConnected() ? 1 : 0);
            rdsEnabledSlot.set(blockEntity.isRdsEnabled() ? 1 : 0);
            rdsMaskLowSlot.set(blockEntity.getRdsMaskLow());
            rdsMaskHighSlot.set(blockEntity.getRdsMaskHigh());
            rdsTpSlot.set(blockEntity.isRdsTp() ? 1 : 0);
            rdsRtUrlModeSlot.set(blockEntity.isRdsRtUrlMode() ? 1 : 0);
            rdsOdaRtPlusSlot.set(blockEntity.isRdsOdaRtPlus() ? 1 : 0);
            rdsCtEnabledSlot.set(blockEntity.isRdsCtEnabled() ? 1 : 0);
            directionalEnabledSlot.set(blockEntity.isDirectionalEnabled() ? 1 : 0);
            directionDegreesSlot.set(blockEntity.getDirectionDegrees());
            setStringSlots(blockEntity.getStreamUrl(), streamUrlLengthSlot, streamUrlSlots, STREAM_URL_MAX);
            syncRdsText();
        } else {
            setStringSlots("", streamUrlLengthSlot, streamUrlSlots, STREAM_URL_MAX);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) {
            return false;
        }

        if (id >= BUTTON_PRESET_SAVE_BASE && id < BUTTON_PRESET_SAVE_BASE + TransmitterPresets.SLOT_COUNT) {
            TransmitterPresets.save(player, "fm", id - BUTTON_PRESET_SAVE_BASE,
                    blockEntity.createPreset(player.level().registryAccess()));
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_PRESET_LOAD_BASE && id < BUTTON_PRESET_LOAD_BASE + TransmitterPresets.SLOT_COUNT) {
            CompoundTag preset = TransmitterPresets.load(player, "fm", id - BUTTON_PRESET_LOAD_BASE);
            if (preset != null) {
                blockEntity.applyPreset(preset, player.level().registryAccess());
                broadcastChanges();
            }
            return true;
        }

        if (id >= BUTTON_SET_POWER_BASE) {
            blockEntity.setPowerW(id - BUTTON_SET_POWER_BASE);
            broadcastChanges();
            return true;
        }

        if (id >= BUTTON_SET_FREQUENCY_BASE) {
            blockEntity.setFrequencyKhz(id - BUTTON_SET_FREQUENCY_BASE);
            broadcastChanges();
            return true;
        }

        return switch (id) {
            case BUTTON_FREQUENCY_DOWN -> {
                blockEntity.setFrequencyKhz(blockEntity.getFrequencyKhz() - 10);
                broadcastChanges();
                yield true;
            }
            case BUTTON_FREQUENCY_UP -> {
                blockEntity.setFrequencyKhz(blockEntity.getFrequencyKhz() + 10);
                broadcastChanges();
                yield true;
            }
            case BUTTON_POWER_DOWN -> {
                blockEntity.setPowerW(blockEntity.getPowerW() - 1);
                broadcastChanges();
                yield true;
            }
            case BUTTON_POWER_UP -> {
                blockEntity.setPowerW(blockEntity.getPowerW() + 1);
                broadcastChanges();
                yield true;
            }
            case BUTTON_DIRECTIONAL_TOGGLE -> {
                blockEntity.setDirectionalEnabled(!blockEntity.isDirectionalEnabled());
                broadcastChanges();
                yield true;
            }
            default -> false;
        };
    }

    public static int encodeSetFrequencyButtonId(int frequencyKhz) {
        return BUTTON_SET_FREQUENCY_BASE + frequencyKhz;
    }

    public static int encodeSetPowerButtonId(int powerW) {
        return BUTTON_SET_POWER_BASE + powerW;
    }

    @Override
    public void broadcastChanges() {
        presetMaskSlot.set(TransmitterPresets.occupiedMask(owner, "fm"));
        if (blockEntity != null) {
            frequencyKhzSlot.set(SignalMath.clampFmFrequencyKhz(blockEntity.getFrequencyKhz()));
            powerWSlot.set(SignalMath.clampPowerW(blockEntity.getPowerW()));
            antennaConnectedSlot.set(blockEntity.hasAntennaConnected() ? 1 : 0);
            rdsEnabledSlot.set(blockEntity.isRdsEnabled() ? 1 : 0);
            rdsMaskLowSlot.set(blockEntity.getRdsMaskLow());
            rdsMaskHighSlot.set(blockEntity.getRdsMaskHigh());
            rdsTpSlot.set(blockEntity.isRdsTp() ? 1 : 0);
            rdsRtUrlModeSlot.set(blockEntity.isRdsRtUrlMode() ? 1 : 0);
            rdsOdaRtPlusSlot.set(blockEntity.isRdsOdaRtPlus() ? 1 : 0);
            rdsCtEnabledSlot.set(blockEntity.isRdsCtEnabled() ? 1 : 0);
            directionalEnabledSlot.set(blockEntity.isDirectionalEnabled() ? 1 : 0);
            directionDegreesSlot.set(blockEntity.getDirectionDegrees());
            setStringSlots(blockEntity.getStreamUrl(), streamUrlLengthSlot, streamUrlSlots, STREAM_URL_MAX);
            syncRdsText();
        }
        super.broadcastChanges();
    }

    public int getFrequencyKhz() {
        return frequencyKhzSlot.get();
    }

    public boolean hasPreset(int slot) {
        return slot >= 0 && slot < TransmitterPresets.SLOT_COUNT
                && (presetMaskSlot.get() & (1 << slot)) != 0;
    }

    public int getPowerW() {
        return powerWSlot.get();
    }

    public boolean hasAntennaConnected() {
        return antennaConnectedSlot.get() > 0;
    }

    public boolean isRdsEnabled() {
        return rdsEnabledSlot.get() > 0;
    }

    public boolean isRdsGroupEnabled(int index) {
        int clamped = Math.max(0, Math.min(31, index));
        if (clamped < 16) {
            return (rdsMaskLowSlot.get() & (1 << clamped)) != 0;
        }
        return (rdsMaskHighSlot.get() & (1 << (clamped - 16))) != 0;
    }

    public boolean isRdsTpEnabled() {
        return rdsTpSlot.get() > 0;
    }

    public boolean isRdsRtUrlModeEnabled() {
        return rdsRtUrlModeSlot.get() > 0;
    }

    public boolean isRdsOdaRtPlusEnabled() {
        return rdsOdaRtPlusSlot.get() > 0;
    }

    public boolean isRdsCtEnabled() {
        return rdsCtEnabledSlot.get() > 0;
    }

    public boolean isDirectionalEnabled() {
        return directionalEnabledSlot.get() > 0;
    }

    public int getDirectionDegrees() {
        return Math.floorMod(directionDegreesSlot.get(), 360);
    }

    public String getStreamUrl() {
        return readString(streamUrlLengthSlot, streamUrlSlots);
    }

    public String getRdsText(String field) {
        return switch (field) {
            case "pi" -> rdsPi.get();
            case "ps" -> rdsPs.get();
            case "af" -> rdsAf.get();
            case "ecc" -> rdsEcc.get();
            case "lic" -> rdsLic.get();
            case "pin" -> rdsPin.get();
            case "rt" -> rdsRt.get();
            case "rt_url" -> rdsRtUrl.get();
            case "oda_group" -> rdsOdaGroup.get();
            case "ptyn" -> rdsPtyn.get();
            case "long_ps" -> rdsLongPs.get();
            case "pty" -> Integer.toString(rdsPtySlot.get());
            default -> "";
        };
    }

    private void syncRdsText() {
        rdsPtySlot.set(blockEntity.getRdsPty());
        rdsPi.set(blockEntity.getRdsPi()); rdsPs.set(blockEntity.getRdsPs());
        rdsAf.set(blockEntity.getRdsAf()); rdsEcc.set(blockEntity.getRdsEcc());
        rdsLic.set(blockEntity.getRdsLic()); rdsPin.set(blockEntity.getRdsPin());
        rdsRt.set(blockEntity.getRdsRt()); rdsRtUrl.set(blockEntity.getRdsRtUrl());
        rdsOdaGroup.set(blockEntity.getRdsOdaGroup()); rdsPtyn.set(blockEntity.getRdsPtyn());
        rdsLongPs.set(blockEntity.getRdsLongPs());
    }

    private final class TextSlots {
        private final DataSlot length = addDataSlot(DataSlot.standalone());
        private final DataSlot[] chars;

        private TextSlots(int max) { chars = addCharSlots(max); }
        private void set(String text) { setStringSlots(text, length, chars, chars.length); }
        private String get() { return readString(length, chars); }
    }

    public void applyRdsConfigAction(String action, String value) {
        if (blockEntity == null) {
            return;
        }
        blockEntity.applyRdsConfigAction(action, value);
        broadcastChanges();
    }

    public void setUploadedAudioSource(String source) {
        applyRdsConfigAction("set_stream_url", source);
    }

    private DataSlot[] addCharSlots(int size) {
        DataSlot[] slots = new DataSlot[size];
        for (int i = 0; i < size; i++) {
            slots[i] = addDataSlot(DataSlot.standalone());
        }
        return slots;
    }

    private void setStringSlots(String value, DataSlot lengthSlot, DataSlot[] slots, int maxLength) {
        String safe = value == null ? "" : value;
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        lengthSlot.set(safe.length());
        for (int i = 0; i < slots.length; i++) {
            slots[i].set(i < safe.length() ? safe.charAt(i) : 0);
        }
    }

    private String readString(DataSlot lengthSlot, DataSlot[] slots) {
        int length = Math.max(0, Math.min(slots.length, lengthSlot.get()));
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) slots[i].get());
        }
        return builder.toString();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
