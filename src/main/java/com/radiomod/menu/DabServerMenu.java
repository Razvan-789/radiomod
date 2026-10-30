package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabServerBlockEntity.DabEnsembleDefinition;
import com.radiomod.blockentity.DabServerBlockEntity.DabStationDefinition;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DabServerMenu extends AbstractContainerMenu {
    public static final int MODE_ENSEMBLES = 0;
    public static final int MODE_STATIONS = 1;
    public static final int MODE_STATION_DETAIL = 2;

    public static final int BUTTON_ENSEMBLE_PREV = 0;
    public static final int BUTTON_ENSEMBLE_NEXT = 1;
    public static final int BUTTON_ENSEMBLE_ADD = 2;
    public static final int BUTTON_ENSEMBLE_REMOVE = 3;
    public static final int BUTTON_ENSEMBLE_OPEN = 4;
    public static final int BUTTON_BACK = 5;
    public static final int BUTTON_STATION_PREV = 6;
    public static final int BUTTON_STATION_NEXT = 7;
    public static final int BUTTON_STATION_ADD = 8;
    public static final int BUTTON_STATION_REMOVE = 9;
    public static final int BUTTON_STATION_OPEN = 10;
    public static final int BUTTON_STATION_DLS_MODE_TOGGLE = 11;
    public static final int BUTTON_EDIT_ENSEMBLE_NAME = 12;
    private static final int BUTTON_SELECT_ENSEMBLE_BASE = 10_000;
    private static final int BUTTON_SELECT_STATION_BASE = 20_000;

    private static final int ENSEMBLE_NAME_MAX = 32;
    private static final int SID_MAX = 8;
    private static final int LABEL_MAX = 32;
    private static final int DLS_TEXT_MAX = 128;
    private static final int URL_MAX = 200;
    private static final int BITRATE_MAX = 4;
    private static final int STATUS_MAX = 64;
    private static final int ENSEMBLE_LIST_SIZE = 6;
    private static final int STATION_LIST_SIZE = 8;
    private static final int LIST_ENTRY_MAX = 24;

    @Nullable
    private final DabServerBlockEntity blockEntity;
    private final DataSlot modeSlot;
    private final DataSlot selectedEnsembleSlot;
    private final DataSlot selectedStationSlot;
    private final DataSlot ensembleCountSlot;
    private final DataSlot stationCountSlot;
    private final DataSlot stationDlsUrlModeSlot;
    private final DataSlot stationSaveRevisionSlot;
    private final DataSlot stationPtySlot;
    private final DataSlot muxCapacitySlot;
    private final DataSlot muxUsedSlot;
    private final DataSlot muxCorrectionSlot;
    private final DataSlot[] ensembleNameSlots;
    private final DataSlot ensembleNameLengthSlot;
    private final DataSlot[] stationSidSlots;
    private final DataSlot stationSidLengthSlot;
    private final DataSlot[] stationLabelSlots;
    private final DataSlot stationLabelLengthSlot;
    private final DataSlot[] stationDlsTextSlots;
    private final DataSlot stationDlsTextLengthSlot;
    private final DataSlot[] stationDlsUrlSlots;
    private final DataSlot stationDlsUrlLengthSlot;
    private final DataSlot[] stationMotUrlSlots;
    private final DataSlot stationMotUrlLengthSlot;
    private final DataSlot[] stationStreamUrlSlots;
    private final DataSlot stationStreamUrlLengthSlot;
    private final DataSlot[] stationBitrateSlots;
    private final DataSlot stationBitrateLengthSlot;
    private final DataSlot[] statusSlots;
    private final DataSlot statusLengthSlot;
    private final DataSlot[] ensembleListLengthSlots;
    private final DataSlot[][] ensembleListCharSlots;
    private final DataSlot[] stationListLengthSlots;
    private final DataSlot[][] stationListCharSlots;
    private String statusMessage = "";

    public DabServerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public DabServerMenu(int containerId, Inventory inventory, @Nullable DabServerBlockEntity blockEntity) {
        super(Radio.DAB_SERVER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        modeSlot = addDataSlot(DataSlot.standalone());
        selectedEnsembleSlot = addDataSlot(DataSlot.standalone());
        selectedStationSlot = addDataSlot(DataSlot.standalone());
        ensembleCountSlot = addDataSlot(DataSlot.standalone());
        stationCountSlot = addDataSlot(DataSlot.standalone());
        stationDlsUrlModeSlot = addDataSlot(DataSlot.standalone());
        stationSaveRevisionSlot = addDataSlot(DataSlot.standalone());
        stationPtySlot = addDataSlot(DataSlot.standalone());
        muxCapacitySlot = addDataSlot(DataSlot.standalone());
        muxUsedSlot = addDataSlot(DataSlot.standalone());
        muxCorrectionSlot = addDataSlot(DataSlot.standalone());
        ensembleNameLengthSlot = addDataSlot(DataSlot.standalone());
        ensembleNameSlots = addCharSlots(ENSEMBLE_NAME_MAX);
        stationSidLengthSlot = addDataSlot(DataSlot.standalone());
        stationSidSlots = addCharSlots(SID_MAX);
        stationLabelLengthSlot = addDataSlot(DataSlot.standalone());
        stationLabelSlots = addCharSlots(LABEL_MAX);
        stationDlsTextLengthSlot = addDataSlot(DataSlot.standalone());
        stationDlsTextSlots = addCharSlots(DLS_TEXT_MAX);
        stationDlsUrlLengthSlot = addDataSlot(DataSlot.standalone());
        stationDlsUrlSlots = addCharSlots(URL_MAX);
        stationMotUrlLengthSlot = addDataSlot(DataSlot.standalone());
        stationMotUrlSlots = addCharSlots(URL_MAX);
        stationStreamUrlLengthSlot = addDataSlot(DataSlot.standalone());
        stationStreamUrlSlots = addCharSlots(URL_MAX);
        stationBitrateLengthSlot = addDataSlot(DataSlot.standalone());
        stationBitrateSlots = addCharSlots(BITRATE_MAX);
        statusLengthSlot = addDataSlot(DataSlot.standalone());
        statusSlots = addCharSlots(STATUS_MAX);
        ensembleListLengthSlots = new DataSlot[ENSEMBLE_LIST_SIZE];
        ensembleListCharSlots = new DataSlot[ENSEMBLE_LIST_SIZE][];
        for (int i = 0; i < ENSEMBLE_LIST_SIZE; i++) {
            ensembleListLengthSlots[i] = addDataSlot(DataSlot.standalone());
            ensembleListCharSlots[i] = addCharSlots(LIST_ENTRY_MAX);
        }
        stationListLengthSlots = new DataSlot[STATION_LIST_SIZE];
        stationListCharSlots = new DataSlot[STATION_LIST_SIZE][];
        for (int i = 0; i < STATION_LIST_SIZE; i++) {
            stationListLengthSlots[i] = addDataSlot(DataSlot.standalone());
            stationListCharSlots[i] = addCharSlots(LIST_ENTRY_MAX);
        }

        modeSlot.set(MODE_ENSEMBLES);
        selectedEnsembleSlot.set(0);
        selectedStationSlot.set(0);
        stationDlsUrlModeSlot.set(0);
        stationSaveRevisionSlot.set(0);
        syncData();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) {
            return false;
        }
        if (id >= BUTTON_SELECT_STATION_BASE) {
            int stationIndex = id - BUTTON_SELECT_STATION_BASE;
            if (stationIndex >= 0 && stationIndex < stationCountSlot.get()) {
                selectedStationSlot.set(stationIndex);
                modeSlot.set(MODE_STATION_DETAIL);
            }
            syncData();
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_SELECT_ENSEMBLE_BASE) {
            int ensembleIndex = id - BUTTON_SELECT_ENSEMBLE_BASE;
            if (ensembleIndex >= 0 && ensembleIndex < ensembleCountSlot.get()) {
                selectedEnsembleSlot.set(ensembleIndex);
                selectedStationSlot.set(0);
                modeSlot.set(MODE_STATIONS);
            }
            syncData();
            broadcastChanges();
            return true;
        }
        switch (id) {
            case BUTTON_ENSEMBLE_PREV -> selectedEnsembleSlot.set(Math.max(0, getEnsemblePageStart() - ENSEMBLE_LIST_SIZE));
            case BUTTON_ENSEMBLE_NEXT -> selectedEnsembleSlot.set(Math.min(Math.max(0, ensembleCountSlot.get() - 1), getEnsemblePageStart() + ENSEMBLE_LIST_SIZE));
            case BUTTON_ENSEMBLE_ADD -> {
                blockEntity.addEnsemble();
                selectedEnsembleSlot.set(Math.max(0, blockEntity.getEnsembleCount() - 1));
            }
            case BUTTON_ENSEMBLE_REMOVE -> {
                blockEntity.removeEnsemble(selectedEnsembleSlot.get());
                selectedEnsembleSlot.set(Math.max(0, Math.min(selectedEnsembleSlot.get(), Math.max(0, blockEntity.getEnsembleCount() - 1))));
            }
            case BUTTON_ENSEMBLE_OPEN -> modeSlot.set(MODE_STATIONS);
            case BUTTON_EDIT_ENSEMBLE_NAME -> modeSlot.set(MODE_ENSEMBLES);
            case BUTTON_BACK -> {
                if (modeSlot.get() == MODE_STATION_DETAIL) {
                    modeSlot.set(MODE_STATIONS);
                } else if (modeSlot.get() == MODE_STATIONS) {
                    modeSlot.set(MODE_ENSEMBLES);
                }
            }
            case BUTTON_STATION_PREV -> selectedStationSlot.set(Math.max(0, getStationPageStart() - STATION_LIST_SIZE));
            case BUTTON_STATION_NEXT -> selectedStationSlot.set(Math.min(Math.max(0, stationCountSlot.get() - 1), getStationPageStart() + STATION_LIST_SIZE));
            case BUTTON_STATION_ADD -> {
                int before = blockEntity.getStationCount(selectedEnsembleSlot.get());
                blockEntity.addStation(selectedEnsembleSlot.get());
                selectedStationSlot.set(Math.max(0, blockEntity.getStationCount(selectedEnsembleSlot.get()) - 1));
                if (blockEntity.getStationCount(selectedEnsembleSlot.get()) == before) {
                    statusMessage = "Station limit or MUX capacity reached";
                }
            }
            case BUTTON_STATION_REMOVE -> {
                blockEntity.removeStation(selectedEnsembleSlot.get(), selectedStationSlot.get());
                selectedStationSlot.set(Math.max(0, Math.min(selectedStationSlot.get(), Math.max(0, blockEntity.getStationCount(selectedEnsembleSlot.get()) - 1))));
            }
            case BUTTON_STATION_OPEN -> {
                if (stationCountSlot.get() > 0) {
                    modeSlot.set(MODE_STATION_DETAIL);
                }
            }
            case BUTTON_STATION_DLS_MODE_TOGGLE -> {
                if (stationCountSlot.get() > 0) {
                    DabStationDefinition station = blockEntity.getStation(selectedEnsembleSlot.get(), selectedStationSlot.get());
                    blockEntity.updateStation(selectedEnsembleSlot.get(), selectedStationSlot.get(), station.sid(), station.label(),
                            !station.dlsUrlMode(), station.dlsText(), station.dlsUrl(), station.motUrl(), station.streamUrl(), station.bitrateKbps(), station.pty());
                }
            }
            default -> {
                return false;
            }
        }
        syncData();
        broadcastChanges();
        return true;
    }

    public void applyAction(String action, String value) {
        if (blockEntity == null) {
            return;
        }
        int ensembleIndex = selectedEnsembleSlot.get();
        if ("set_ensemble_name".equals(action)) {
            blockEntity.renameEnsemble(ensembleIndex, value);
            syncData();
            broadcastChanges();
            return;
        }

        if (modeSlot.get() != MODE_STATION_DETAIL || blockEntity.getStationCount(ensembleIndex) == 0) {
            return;
        }
        int stationIndex = selectedStationSlot.get();
        DabStationDefinition station = blockEntity.getStation(ensembleIndex, stationIndex);
        String sid = station.sid();
        String label = station.label();
        String dlsText = station.dlsText();
        String dlsUrl = station.dlsUrl();
        String motUrl = station.motUrl();
        String streamUrl = station.streamUrl();
        int bitrateKbps = station.bitrateKbps();
        int pty = station.pty();
        boolean dlsUrlMode = stationDlsUrlModeSlot.get() > 0;

        switch (action) {
            case "set_sid" -> sid = value;
            case "set_label" -> label = value;
            case "set_dls_text" -> dlsText = value;
            case "set_dls_url" -> dlsUrl = value;
            case "set_mot_url" -> motUrl = value;
            case "set_stream_url" -> streamUrl = value;
            case "set_bitrate_kbps" -> {
                try {
                    bitrateKbps = Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    statusMessage = "Invalid bitrate";
                }
            }
            case "set_pty" -> {
                try {
                    pty = Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    statusMessage = "Invalid PTY";
                }
            }
            case "save_station" -> {
                boolean saved = blockEntity.updateStation(ensembleIndex, stationIndex, sid, label, dlsUrlMode,
                        dlsText, dlsUrl, motUrl, streamUrl, bitrateKbps, pty);
                statusMessage = saved ? "Saved station profile" : "MUX full or invalid bitrate/PTY";
                syncData();
                broadcastChanges();
                return;
            }
            case "save_station_form" -> {
                String[] parts = value.split("\u001F", -1);
                if (parts.length >= 8) {
                    sid = parts[0];
                    label = parts[1];
                    dlsText = parts[2];
                    dlsUrl = parts[3];
                    motUrl = parts[4];
                    streamUrl = parts[5];
                    try {
                        bitrateKbps = Integer.parseInt(parts[6].trim());
                    } catch (NumberFormatException ignored) {
                        bitrateKbps = station.bitrateKbps();
                    }
                    try {
                        pty = Integer.parseInt(parts[7].trim());
                    } catch (NumberFormatException ignored) {
                        pty = station.pty();
                    }
                }
                boolean saved = blockEntity.updateStation(ensembleIndex, stationIndex, sid, label, dlsUrlMode,
                        dlsText, dlsUrl, motUrl, streamUrl, bitrateKbps, pty);
                stationSaveRevisionSlot.set(stationSaveRevisionSlot.get() + 1);
                statusMessage = saved ? "Saved station profile" : "MUX full or invalid bitrate/PTY";
                syncData();
                broadcastChanges();
                return;
            }
            default -> {
                return;
            }
        }
        boolean saved = blockEntity.updateStation(ensembleIndex, stationIndex, sid, label, dlsUrlMode,
                dlsText, dlsUrl, motUrl, streamUrl, bitrateKbps, pty);
        statusMessage = saved ? "Updated " + action : "MUX full or invalid bitrate/PTY";
        syncData();
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        syncData();
        super.broadcastChanges();
    }

    public int getMode() {
        return modeSlot.get();
    }

    public int getSelectedEnsembleIndex() {
        return selectedEnsembleSlot.get();
    }

    public int getSelectedStationIndex() {
        return selectedStationSlot.get();
    }

    public boolean matchesUploadTarget(int ensembleIndex, int stationIndex, String sid) {
        return blockEntity != null && getMode() == MODE_STATION_DETAIL
                && selectedEnsembleSlot.get() == ensembleIndex && selectedStationSlot.get() == stationIndex
                && ensembleIndex >= 0 && ensembleIndex < blockEntity.getEnsembleCount()
                && stationIndex >= 0 && stationIndex < blockEntity.getStationCount(ensembleIndex)
                && blockEntity.getStation(ensembleIndex, stationIndex).sid().equals(sid);
    }

    public void setUploadedAudioSource(String source) {
        if (blockEntity == null || getMode() != MODE_STATION_DETAIL) {
            return;
        }
        int ensembleIndex = selectedEnsembleSlot.get();
        int stationIndex = selectedStationSlot.get();
        if (ensembleIndex < 0 || ensembleIndex >= blockEntity.getEnsembleCount()
                || stationIndex < 0 || stationIndex >= blockEntity.getStationCount(ensembleIndex)) {
            return;
        }
        DabStationDefinition station = blockEntity.getStation(ensembleIndex, stationIndex);
        if (blockEntity.updateStation(ensembleIndex, stationIndex, station.sid(), station.label(),
                station.dlsUrlMode(), station.dlsText(), station.dlsUrl(), station.motUrl(),
                source, station.bitrateKbps(), station.pty())) {
            statusMessage = "Library audio loaded";
            stationSaveRevisionSlot.set(stationSaveRevisionSlot.get() + 1);
            syncData();
            broadcastChanges();
        }
    }

    public int getEnsembleCount() {
        return ensembleCountSlot.get();
    }

    public int getStationCount() {
        return stationCountSlot.get();
    }

    public int getStationSaveRevision() {
        return stationSaveRevisionSlot.get();
    }

    public int getEnsemblePageStart() {
        return (Math.max(0, selectedEnsembleSlot.get()) / ENSEMBLE_LIST_SIZE) * ENSEMBLE_LIST_SIZE;
    }

    public int getStationPageStart() {
        return (Math.max(0, selectedStationSlot.get()) / STATION_LIST_SIZE) * STATION_LIST_SIZE;
    }

    public String getEnsembleName() {
        return readString(ensembleNameLengthSlot, ensembleNameSlots);
    }

    public String getStationSid() {
        return readString(stationSidLengthSlot, stationSidSlots);
    }

    public String getStationLabel() {
        return readString(stationLabelLengthSlot, stationLabelSlots);
    }

    public String getStationDlsText() {
        return readString(stationDlsTextLengthSlot, stationDlsTextSlots);
    }

    public String getStationDlsUrl() {
        return readString(stationDlsUrlLengthSlot, stationDlsUrlSlots);
    }

    public String getStationMotUrl() {
        return readString(stationMotUrlLengthSlot, stationMotUrlSlots);
    }

    public String getStationStreamUrl() {
        return readString(stationStreamUrlLengthSlot, stationStreamUrlSlots);
    }

    public String getStationBitrateKbps() {
        return readString(stationBitrateLengthSlot, stationBitrateSlots);
    }

    public int getStationPty() {
        return stationPtySlot.get();
    }

    public int getMuxCapacityKbps() {
        return muxCapacitySlot.get();
    }

    public int getMuxUsedKbps() {
        return muxUsedSlot.get();
    }

    public String getMuxErrorCorrectionLabel() {
        int index = Math.max(0, Math.min(com.radiomod.signal.DabErrorCorrection.values().length - 1,
                muxCorrectionSlot.get()));
        return com.radiomod.signal.DabErrorCorrection.values()[index].getLabel();
    }

    public boolean isStationDlsUrlMode() {
        return stationDlsUrlModeSlot.get() > 0;
    }

    public String getStatusMessage() {
        return readString(statusLengthSlot, statusSlots);
    }

    public String getEnsembleListEntry(int index) {
        if (index < 0 || index >= ENSEMBLE_LIST_SIZE) {
            return "";
        }
        return readString(ensembleListLengthSlots[index], ensembleListCharSlots[index]);
    }

    public String getStationListEntry(int index) {
        if (index < 0 || index >= STATION_LIST_SIZE) {
            return "";
        }
        return readString(stationListLengthSlots[index], stationListCharSlots[index]);
    }

    public static int encodeSelectEnsembleButtonId(int index) {
        return BUTTON_SELECT_ENSEMBLE_BASE + Math.max(0, index);
    }

    public static int encodeSelectStationButtonId(int index) {
        return BUTTON_SELECT_STATION_BASE + Math.max(0, index);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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

    private void syncData() {
        if (blockEntity == null) {
            stationPtySlot.set(0);
            muxCapacitySlot.set(0);
            muxUsedSlot.set(0);
            muxCorrectionSlot.set(0);
            setStringSlots("", ensembleNameLengthSlot, ensembleNameSlots, ENSEMBLE_NAME_MAX);
            setStringSlots("", stationSidLengthSlot, stationSidSlots, SID_MAX);
            setStringSlots("", stationLabelLengthSlot, stationLabelSlots, LABEL_MAX);
            setStringSlots("", stationDlsTextLengthSlot, stationDlsTextSlots, DLS_TEXT_MAX);
            setStringSlots("", stationDlsUrlLengthSlot, stationDlsUrlSlots, URL_MAX);
            setStringSlots("", stationMotUrlLengthSlot, stationMotUrlSlots, URL_MAX);
            setStringSlots("", stationStreamUrlLengthSlot, stationStreamUrlSlots, URL_MAX);
            setStringSlots("", stationBitrateLengthSlot, stationBitrateSlots, BITRATE_MAX);
            setStringSlots("", statusLengthSlot, statusSlots, STATUS_MAX);
            syncListSlots(0, 0);
            return;
        }

        int ensembleCount = blockEntity.getEnsembleCount();
        ensembleCountSlot.set(ensembleCount);
        if (ensembleCount <= 0) {
            stationPtySlot.set(0);
            muxCapacitySlot.set(0);
            muxUsedSlot.set(0);
            muxCorrectionSlot.set(0);
            selectedEnsembleSlot.set(0);
            stationCountSlot.set(0);
            selectedStationSlot.set(0);
            setStringSlots("", ensembleNameLengthSlot, ensembleNameSlots, ENSEMBLE_NAME_MAX);
            setStringSlots("", stationSidLengthSlot, stationSidSlots, SID_MAX);
            setStringSlots("", stationLabelLengthSlot, stationLabelSlots, LABEL_MAX);
            setStringSlots("", stationDlsTextLengthSlot, stationDlsTextSlots, DLS_TEXT_MAX);
            setStringSlots("", stationDlsUrlLengthSlot, stationDlsUrlSlots, URL_MAX);
            setStringSlots("", stationMotUrlLengthSlot, stationMotUrlSlots, URL_MAX);
            setStringSlots("", stationStreamUrlLengthSlot, stationStreamUrlSlots, URL_MAX);
            setStringSlots("", stationBitrateLengthSlot, stationBitrateSlots, BITRATE_MAX);
            setStringSlots(statusMessage, statusLengthSlot, statusSlots, STATUS_MAX);
            syncListSlots(0, 0);
            return;
        }

        int ensembleIndex = Math.max(0, Math.min(ensembleCount - 1, selectedEnsembleSlot.get()));
        selectedEnsembleSlot.set(ensembleIndex);
        DabEnsembleDefinition ensemble = blockEntity.getEnsemble(ensembleIndex);
        muxCapacitySlot.set(blockEntity.getMuxCapacityKbps(ensembleIndex));
        muxUsedSlot.set(blockEntity.getMuxUsedKbps(ensembleIndex));
        muxCorrectionSlot.set(blockEntity.getMuxErrorCorrection(ensembleIndex).ordinal());
        setStringSlots(ensemble.name(), ensembleNameLengthSlot, ensembleNameSlots, ENSEMBLE_NAME_MAX);

        int stationCount = ensemble.stations().size();
        stationCountSlot.set(stationCount);
        if (stationCount <= 0) {
            stationPtySlot.set(0);
            selectedStationSlot.set(0);
            stationDlsUrlModeSlot.set(0);
            setStringSlots("", stationSidLengthSlot, stationSidSlots, SID_MAX);
            setStringSlots("", stationLabelLengthSlot, stationLabelSlots, LABEL_MAX);
            setStringSlots("", stationDlsTextLengthSlot, stationDlsTextSlots, DLS_TEXT_MAX);
            setStringSlots("", stationDlsUrlLengthSlot, stationDlsUrlSlots, URL_MAX);
            setStringSlots("", stationMotUrlLengthSlot, stationMotUrlSlots, URL_MAX);
            setStringSlots("", stationStreamUrlLengthSlot, stationStreamUrlSlots, URL_MAX);
            setStringSlots("", stationBitrateLengthSlot, stationBitrateSlots, BITRATE_MAX);
        } else {
            int stationIndex = Math.max(0, Math.min(stationCount - 1, selectedStationSlot.get()));
            selectedStationSlot.set(stationIndex);
            DabStationDefinition station = blockEntity.getStation(ensembleIndex, stationIndex);
            stationPtySlot.set(station.pty());
            stationDlsUrlModeSlot.set(station.dlsUrlMode() ? 1 : 0);
            setStringSlots(station.sid(), stationSidLengthSlot, stationSidSlots, SID_MAX);
            setStringSlots(station.label(), stationLabelLengthSlot, stationLabelSlots, LABEL_MAX);
            setStringSlots(station.dlsText(), stationDlsTextLengthSlot, stationDlsTextSlots, DLS_TEXT_MAX);
            setStringSlots(station.dlsUrl(), stationDlsUrlLengthSlot, stationDlsUrlSlots, URL_MAX);
            setStringSlots(station.motUrl(), stationMotUrlLengthSlot, stationMotUrlSlots, URL_MAX);
            setStringSlots(station.streamUrl(), stationStreamUrlLengthSlot, stationStreamUrlSlots, URL_MAX);
            setStringSlots(Integer.toString(station.bitrateKbps()), stationBitrateLengthSlot, stationBitrateSlots, BITRATE_MAX);
        }
        setStringSlots(statusMessage, statusLengthSlot, statusSlots, STATUS_MAX);
        syncListSlots(ensembleIndex, stationCount);
    }

    private void syncListSlots(int selectedEnsembleIndex, int stationCount) {
        if (blockEntity == null) {
            for (int i = 0; i < ENSEMBLE_LIST_SIZE; i++) {
                setStringSlots("", ensembleListLengthSlots[i], ensembleListCharSlots[i], LIST_ENTRY_MAX);
            }
            for (int i = 0; i < STATION_LIST_SIZE; i++) {
                setStringSlots("", stationListLengthSlots[i], stationListCharSlots[i], LIST_ENTRY_MAX);
            }
            return;
        }
        for (int i = 0; i < ENSEMBLE_LIST_SIZE; i++) {
            int index = getEnsemblePageStart() + i;
            if (index < blockEntity.getEnsembleCount()) {
                String name = blockEntity.getEnsemble(index).name();
                setStringSlots((index + 1) + ". " + name, ensembleListLengthSlots[i], ensembleListCharSlots[i], LIST_ENTRY_MAX);
            } else {
                setStringSlots("", ensembleListLengthSlots[i], ensembleListCharSlots[i], LIST_ENTRY_MAX);
            }
        }
        for (int i = 0; i < STATION_LIST_SIZE; i++) {
            int index = getStationPageStart() + i;
            if (index < stationCount) {
                DabStationDefinition station = blockEntity.getStation(selectedEnsembleIndex, index);
                String label = station.label().isBlank() ? station.sid() : station.label();
                setStringSlots((index + 1) + ". " + label, stationListLengthSlots[i], stationListCharSlots[i], LIST_ENTRY_MAX);
            } else {
                setStringSlots("", stationListLengthSlots[i], stationListCharSlots[i], LIST_ENTRY_MAX);
            }
        }
    }
}
