package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.DabErrorCorrection;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.DabVersion;
import com.radiomod.server.TransmitterPresets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DabTransmitterMenu extends AbstractContainerMenu {
    public static final int BUTTON_FREQUENCY_DOWN = 0;
    public static final int BUTTON_FREQUENCY_UP = 1;
    public static final int BUTTON_POWER_DOWN = 2;
    public static final int BUTTON_POWER_UP = 3;
    public static final int BUTTON_ERROR_CORRECTION_NEXT = 4;
    public static final int BUTTON_CHANNEL_DOWN = 5;
    public static final int BUTTON_CHANNEL_UP = 6;
    public static final int BUTTON_DAB_VERSION_NEXT = 7;
    public static final int BUTTON_DIRECTIONAL_TOGGLE = 8;
    public static final int BUTTON_MUX_PREV = 9;
    public static final int BUTTON_MUX_NEXT = 10;
    public static final int BUTTON_SERVER_PREV = 11;
    public static final int BUTTON_SERVER_NEXT = 12;
    public static final int BUTTON_SERVER_UNLINK = 13;
    public static final int BUTTON_PRESET_SAVE_BASE = 100;
    public static final int BUTTON_PRESET_LOAD_BASE = 110;
    private static final int BUTTON_SET_FREQUENCY_BASE = 1_000_000;
    private static final int BUTTON_SET_POWER_BASE = 2_000_000;
    private static final int BUTTON_SET_ENSEMBLE_ID_BASE = 3_000_000;
    private static final int BUTTON_SET_TII_BASE = 4_000_000;
    private static final int ENSEMBLE_NAME_MAX_LENGTH = 32;
    private static final int SERVER_LABEL_MAX_LENGTH = 40;

    @Nullable
    private final DabTransmitterBlockEntity blockEntity;
    private final Player owner;
    private final DataSlot presetMaskSlot;
    private final DataSlot frequencyKhzSlot;
    private final DataSlot powerWSlot;
    private final DataSlot antennaConnectedSlot;
    private final DataSlot errorCorrectionSlot;
    private final DataSlot ensembleIdSlot;
    private final DataSlot dabVersionSlot;
    private final DataSlot directionalEnabledSlot;
    private final DataSlot directionDegreesSlot;
    private final DataSlot serverLinkedSlot;
    private final DataSlot serverLinkPresentSlot;
    private final DataSlot serverCountSlot;
    private final DataSlot serverLabelLengthSlot;
    private final DataSlot[] serverLabelCharSlots = new DataSlot[SERVER_LABEL_MAX_LENGTH];
    private final DataSlot ensembleNameLengthSlot;
    private final DataSlot[] ensembleNameCharSlots = new DataSlot[ENSEMBLE_NAME_MAX_LENGTH];
    private final DataSlot[] tiiCharSlots = new DataSlot[4];

    public DabTransmitterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public DabTransmitterMenu(int containerId, Inventory inventory, @Nullable DabTransmitterBlockEntity blockEntity) {
        super(Radio.DAB_TRANSMITTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.owner = inventory.player;
        presetMaskSlot = addDataSlot(DataSlot.standalone());
        presetMaskSlot.set(TransmitterPresets.occupiedMask(owner, "dab"));

        frequencyKhzSlot = addDataSlot(DataSlot.standalone());
        powerWSlot = addDataSlot(DataSlot.standalone());
        antennaConnectedSlot = addDataSlot(DataSlot.standalone());
        errorCorrectionSlot = addDataSlot(DataSlot.standalone());
        ensembleIdSlot = addDataSlot(DataSlot.standalone());
        dabVersionSlot = addDataSlot(DataSlot.standalone());
        directionalEnabledSlot = addDataSlot(DataSlot.standalone());
        directionDegreesSlot = addDataSlot(DataSlot.standalone());
        serverLinkedSlot = addDataSlot(DataSlot.standalone());
        serverLinkPresentSlot = addDataSlot(DataSlot.standalone());
        serverCountSlot = addDataSlot(DataSlot.standalone());
        serverLabelLengthSlot = addDataSlot(DataSlot.standalone());
        ensembleNameLengthSlot = addDataSlot(DataSlot.standalone());
        for (int i = 0; i < ENSEMBLE_NAME_MAX_LENGTH; i++) {
            ensembleNameCharSlots[i] = addDataSlot(DataSlot.standalone());
        }
        for (int i = 0; i < SERVER_LABEL_MAX_LENGTH; i++) {
            serverLabelCharSlots[i] = addDataSlot(DataSlot.standalone());
        }
        for (int i = 0; i < tiiCharSlots.length; i++) {
            tiiCharSlots[i] = addDataSlot(DataSlot.standalone());
        }

        if (blockEntity != null) {
            frequencyKhzSlot.set(blockEntity.getFrequencyKhz());
            powerWSlot.set(blockEntity.getPowerW());
            antennaConnectedSlot.set(blockEntity.hasAntennaConnected() ? 1 : 0);
            errorCorrectionSlot.set(blockEntity.getErrorCorrection().ordinal());
            ensembleIdSlot.set(blockEntity.getEnsembleId());
            dabVersionSlot.set(blockEntity.getDabVersion().ordinal());
            directionalEnabledSlot.set(blockEntity.isDirectionalEnabled() ? 1 : 0);
            directionDegreesSlot.set(blockEntity.getDirectionDegrees());
            serverLinkedSlot.set(blockEntity.isServerLinked() ? 1 : 0);
            serverLinkPresentSlot.set(blockEntity.hasServerLink() ? 1 : 0);
            serverCountSlot.set(blockEntity.getAvailableServerCount());
            syncEnsembleName(blockEntity.getEnsembleName());
            syncServerLabel(blockEntity.getLinkedServerLabel());
            syncTiiCode();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) {
            return false;
        }

        if (id >= BUTTON_PRESET_SAVE_BASE && id < BUTTON_PRESET_SAVE_BASE + TransmitterPresets.SLOT_COUNT) {
            TransmitterPresets.save(player, "dab", id - BUTTON_PRESET_SAVE_BASE,
                    blockEntity.createPreset(player.level().registryAccess()));
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_PRESET_LOAD_BASE && id < BUTTON_PRESET_LOAD_BASE + TransmitterPresets.SLOT_COUNT) {
            CompoundTag preset = TransmitterPresets.load(player, "dab", id - BUTTON_PRESET_LOAD_BASE);
            if (preset != null) {
                blockEntity.applyPreset(preset, player.level().registryAccess());
                broadcastChanges();
            }
            return true;
        }

        if (id >= BUTTON_SET_TII_BASE) {
            int code = id - BUTTON_SET_TII_BASE;
            if (code <= 0xFFFF) {
                blockEntity.setTiiCode(String.format(java.util.Locale.ROOT, "%04X", code));
            }
            broadcastChanges();
            return true;
        }

        if (id >= BUTTON_SET_ENSEMBLE_ID_BASE) {
            blockEntity.setEnsembleId(id - BUTTON_SET_ENSEMBLE_ID_BASE);
            broadcastChanges();
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

        switch (id) {
            case BUTTON_FREQUENCY_DOWN -> blockEntity.setFrequencyKhz(blockEntity.getFrequencyKhz() - 10);
            case BUTTON_FREQUENCY_UP -> blockEntity.setFrequencyKhz(blockEntity.getFrequencyKhz() + 10);
            case BUTTON_POWER_DOWN -> blockEntity.setPowerW(blockEntity.getPowerW() - 1);
            case BUTTON_POWER_UP -> blockEntity.setPowerW(blockEntity.getPowerW() + 1);
            case BUTTON_ERROR_CORRECTION_NEXT -> {
                DabErrorCorrection candidate = blockEntity.getErrorCorrection();
                for (int attempt = 0; attempt < DabErrorCorrection.values().length; attempt++) {
                    candidate = candidate.next();
                    blockEntity.setErrorCorrection(candidate);
                    if (blockEntity.getErrorCorrection() == candidate) {
                        break;
                    }
                }
            }
            case BUTTON_CHANNEL_DOWN -> {
                int currentIndex = DabChannel.nearestIndexForFrequencyKhz(blockEntity.getFrequencyKhz());
                int nextIndex = DabChannel.clampIndex(currentIndex - 1);
                blockEntity.setFrequencyKhz(DabChannel.byIndex(nextIndex).frequencyKhz());
            }
            case BUTTON_CHANNEL_UP -> {
                int currentIndex = DabChannel.nearestIndexForFrequencyKhz(blockEntity.getFrequencyKhz());
                int nextIndex = DabChannel.clampIndex(currentIndex + 1);
                blockEntity.setFrequencyKhz(DabChannel.byIndex(nextIndex).frequencyKhz());
            }
            case BUTTON_DAB_VERSION_NEXT -> blockEntity.setDabVersion(blockEntity.getDabVersion().next());
            case BUTTON_DIRECTIONAL_TOGGLE -> blockEntity.setDirectionalEnabled(!blockEntity.isDirectionalEnabled());
            case BUTTON_MUX_PREV -> blockEntity.cycleLinkedMux(-1);
            case BUTTON_MUX_NEXT -> blockEntity.cycleLinkedMux(1);
            case BUTTON_SERVER_PREV -> blockEntity.cycleServerLink(-1);
            case BUTTON_SERVER_NEXT -> blockEntity.cycleServerLink(1);
            case BUTTON_SERVER_UNLINK -> blockEntity.unlinkServer();
            default -> {
                return false;
            }
        }

        broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        presetMaskSlot.set(TransmitterPresets.occupiedMask(owner, "dab"));
        if (blockEntity != null) {
            frequencyKhzSlot.set(SignalMath.clampFrequencyKhz(blockEntity.getFrequencyKhz()));
            powerWSlot.set(SignalMath.clampPowerW(blockEntity.getPowerW()));
            antennaConnectedSlot.set(blockEntity.hasAntennaConnected() ? 1 : 0);
            errorCorrectionSlot.set(blockEntity.getErrorCorrection().ordinal());
            ensembleIdSlot.set(blockEntity.getEnsembleId());
            dabVersionSlot.set(blockEntity.getDabVersion().ordinal());
            directionalEnabledSlot.set(blockEntity.isDirectionalEnabled() ? 1 : 0);
            directionDegreesSlot.set(blockEntity.getDirectionDegrees());
            serverLinkedSlot.set(blockEntity.isServerLinked() ? 1 : 0);
            serverLinkPresentSlot.set(blockEntity.hasServerLink() ? 1 : 0);
            serverCountSlot.set(blockEntity.getAvailableServerCount());
            syncEnsembleName(blockEntity.getEnsembleName());
            syncServerLabel(blockEntity.getLinkedServerLabel());
            syncTiiCode();
        }
        super.broadcastChanges();
    }

    public static int encodeSetFrequencyButtonId(int frequencyKhz) {
        return BUTTON_SET_FREQUENCY_BASE + frequencyKhz;
    }

    public static int encodeSetPowerButtonId(int powerW) {
        return BUTTON_SET_POWER_BASE + powerW;
    }

    public static int encodeSetEnsembleIdButtonId(int ensembleId) {
        return BUTTON_SET_ENSEMBLE_ID_BASE + ensembleId;
    }

    public static int encodeSetTiiButtonId(int code) {
        return BUTTON_SET_TII_BASE + code;
    }

    private void syncTiiCode() {
        String code = blockEntity.getTiiCode();
        for (int i = 0; i < tiiCharSlots.length; i++) {
            tiiCharSlots[i].set(code.charAt(i));
        }
    }

    public String getTiiCode() {
        StringBuilder code = new StringBuilder(4);
        for (DataSlot slot : tiiCharSlots) {
            code.append((char) slot.get());
        }
        return code.toString().matches("[0-9A-F]{4}") ? code.toString() : "0000";
    }

    public int getFrequencyKhz() {
        return frequencyKhzSlot.get();
    }

    public boolean hasPreset(int slot) {
        return slot >= 0 && slot < TransmitterPresets.SLOT_COUNT
                && (presetMaskSlot.get() & (1 << slot)) != 0;
    }

    public String getChannelLabel() {
        return DabChannel.byIndex(DabChannel.nearestIndexForFrequencyKhz(getFrequencyKhz())).label();
    }

    public int getPowerW() {
        return powerWSlot.get();
    }

    public boolean hasAntennaConnected() {
        return antennaConnectedSlot.get() > 0;
    }

    public DabErrorCorrection getErrorCorrection() {
        int index = Math.max(0, Math.min(DabErrorCorrection.values().length - 1, errorCorrectionSlot.get()));
        return DabErrorCorrection.values()[index];
    }

    public int getEnsembleId() {
        return ensembleIdSlot.get();
    }

    public DabVersion getDabVersion() {
        int index = Math.max(0, Math.min(DabVersion.values().length - 1, dabVersionSlot.get()));
        return DabVersion.values()[index];
    }

    public boolean isDirectionalEnabled() {
        return directionalEnabledSlot.get() > 0;
    }

    public int getDirectionDegrees() {
        return Math.floorMod(directionDegreesSlot.get(), 360);
    }

    public boolean isServerLinked() {
        return serverLinkedSlot.get() > 0;
    }

    public boolean hasServerLink() {
        return serverLinkPresentSlot.get() > 0;
    }

    public int getAvailableServerCount() {
        return Math.max(0, serverCountSlot.get());
    }

    public String getLinkedServerLabel() {
        int length = Math.max(0, Math.min(SERVER_LABEL_MAX_LENGTH, serverLabelLengthSlot.get()));
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) serverLabelCharSlots[i].get());
        }
        return builder.toString();
    }

    public String getEnsembleName() {
        int length = Math.max(0, Math.min(ENSEMBLE_NAME_MAX_LENGTH, ensembleNameLengthSlot.get()));
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ensembleNameCharSlots[i].get());
        }
        String value = builder.toString().trim();
        return value.isEmpty() ? "BLANK MUX A" : value;
    }

    public void setEnsembleNameFromClient(String value) {
        if (blockEntity == null) {
            return;
        }
        blockEntity.setEnsembleName(value);
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void syncEnsembleName(String value) {
        String sanitized = sanitizeEnsembleName(value);
        int length = Math.min(ENSEMBLE_NAME_MAX_LENGTH, sanitized.length());
        ensembleNameLengthSlot.set(length);
        for (int i = 0; i < ENSEMBLE_NAME_MAX_LENGTH; i++) {
            ensembleNameCharSlots[i].set(i < length ? sanitized.charAt(i) : 0);
        }
    }

    private void syncServerLabel(String value) {
        int length = Math.min(SERVER_LABEL_MAX_LENGTH, value.length());
        serverLabelLengthSlot.set(length);
        for (int i = 0; i < SERVER_LABEL_MAX_LENGTH; i++) {
            serverLabelCharSlots[i].set(i < length ? value.charAt(i) : 0);
        }
    }

    private static String sanitizeEnsembleName(String value) {
        if (value == null) {
            return "BLANK MUX A";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "BLANK MUX A";
        }
        if (trimmed.length() > ENSEMBLE_NAME_MAX_LENGTH) {
            return trimmed.substring(0, ENSEMBLE_NAME_MAX_LENGTH);
        }
        return trimmed;
    }
}
