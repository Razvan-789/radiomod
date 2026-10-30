package com.radiomod.client.screen;

import com.radiomod.menu.DabServerMenu;
import com.radiomod.network.SetDabServerConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class DabServerScreen extends AbstractContainerScreen<DabServerMenu> {
    private static final int ENSEMBLE_LIST_ROWS = 6;
    private static final int STATION_LIST_ROWS = 8;
    private static final int PANEL = 0xFF17212C;
    private static final int PANEL_BORDER = 0xFF42627D;
    private static final int TEXT = 0xFFE7F0F8;
    private static final int MUTED_TEXT = 0xFFAFC4D5;

    private final Button[] ensembleButtons = new Button[ENSEMBLE_LIST_ROWS];
    private final Button[] stationButtons = new Button[STATION_LIST_ROWS];
    private Button addEnsembleButton;
    private Button removeEnsembleButton;
    private Button addStationButton;
    private Button removeStationButton;
    private Button ensemblePrevButton;
    private Button ensembleNextButton;
    private Button stationPrevButton;
    private Button stationNextButton;
    private Button backButton;
    private Button renameMuxButton;
    private Button renameStationButton;
    private Button dlsModeButton;
    private Button saveButton;
    private Button uploadAudioButton;
    private EditBox ensembleNameInput;
    private EditBox sidInput;
    private EditBox labelInput;
    private EditBox dlsTextInput;
    private EditBox dlsUrlInput;
    private EditBox motUrlInput;
    private EditBox streamUrlInput;
    private EditBox bitrateInput;
    private EditBox ptyInput;
    private int previousMode = -1;
    private int previousEnsembleIndex = -1;
    private int previousStationIndex = -1;
    private boolean previousDlsUrlMode;
    private boolean stationDirty;
    private boolean syncingInputs;
    private boolean focusMuxNameOnReturn;
    private int pendingStationRevision = -1;

    public DabServerScreen(DabServerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 500;
        imageHeight = 310;
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos;
        int top = topPos;

        backButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.back"), button -> click(DabServerMenu.BUTTON_BACK))
                .bounds(left + 12, top + 8, 60, 20).build());
        renameMuxButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.dab_server.rename_mux"), button -> {
                    focusMuxNameOnReturn = true;
                    click(DabServerMenu.BUTTON_EDIT_ENSEMBLE_NAME);
                }).bounds(left + 390, top + 8, 98, 20).build());

        for (int i = 0; i < ENSEMBLE_LIST_ROWS; i++) {
            final int row = i;
            ensembleButtons[i] = addRenderableWidget(Button.builder(Component.empty(), button ->
                            click(DabServerMenu.encodeSelectEnsembleButtonId(menu.getEnsemblePageStart() + row)))
                    .bounds(left + 16, top + 66 + i * 26, 164, 22).build());
        }
        for (int i = 0; i < STATION_LIST_ROWS; i++) {
            final int row = i;
            stationButtons[i] = addRenderableWidget(Button.builder(Component.empty(), button ->
                            click(DabServerMenu.encodeSelectStationButtonId(menu.getStationPageStart() + row)))
                    .bounds(left + 204, top + 62 + i * 22, 280, 20).build());
        }

        ensemblePrevButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> click(DabServerMenu.BUTTON_ENSEMBLE_PREV))
                .bounds(left + 16, top + 238, 28, 20).build());
        ensembleNextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> click(DabServerMenu.BUTTON_ENSEMBLE_NEXT))
                .bounds(left + 152, top + 238, 28, 20).build());
        stationPrevButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> click(DabServerMenu.BUTTON_STATION_PREV))
                .bounds(left + 204, top + 240, 28, 20).build());
        stationNextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> click(DabServerMenu.BUTTON_STATION_NEXT))
                .bounds(left + 456, top + 240, 28, 20).build());

        addEnsembleButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.dab_server.add_mux"), button -> click(DabServerMenu.BUTTON_ENSEMBLE_ADD))
                .bounds(left + 16, top + 268, 80, 20).build());
        removeEnsembleButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.dab_server.remove_mux"), button -> click(DabServerMenu.BUTTON_ENSEMBLE_REMOVE))
                .bounds(left + 100, top + 268, 80, 20).build());
        addStationButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.dab_server.add_station"), button -> click(DabServerMenu.BUTTON_STATION_ADD))
                .bounds(left + 204, top + 268, 130, 20).build());
        removeStationButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.dab_server.remove_station"), button -> click(DabServerMenu.BUTTON_STATION_REMOVE))
                .bounds(left + 338, top + 268, 146, 20).build());

        ensembleNameInput = addInput(left + 204, top + 94, 280, 32, "screen.radio.dab_server.ensemble_name", "screen.radio.dab_server.ensemble_name_hint");
        sidInput = addInput(left + 294, top + 62, 190, 8, "screen.radio.dab_server.sid", "screen.radio.dab_server.sid_hint");
        labelInput = addInput(left + 294, top + 86, 190, 32, "screen.radio.dab_server.label", "screen.radio.dab_server.label_hint");
        ptyInput = addInput(left + 294, top + 110, 190, 2, "screen.radio.dab_server.pty", "screen.radio.dab_server.pty_hint");
        ptyInput.setFilter(value -> value.length() <= 2 && isNumericInput(value));
        dlsTextInput = addInput(left + 294, top + 134, 190, 128, "screen.radio.dab_server.dls_text", "screen.radio.dab_server.dls_text_hint");
        dlsUrlInput = addInput(left + 294, top + 134, 190, 200, "screen.radio.dab_server.dls_url", "screen.radio.dab_server.dls_url_hint");
        motUrlInput = addInput(left + 294, top + 158, 190, 200, "screen.radio.dab_server.mot_url", "screen.radio.dab_server.mot_url_hint");
        streamUrlInput = addInput(left + 294, top + 182, 190, 200, "screen.radio.dab_server.stream_url", "screen.radio.dab_server.stream_url_hint");
        bitrateInput = addInput(left + 294, top + 206, 190, 4, "screen.radio.dab_server.bitrate", "screen.radio.dab_server.bitrate_hint");
        bitrateInput.setFilter(this::isNumericInput);

        dlsModeButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(DabServerMenu.BUTTON_STATION_DLS_MODE_TOGGLE))
                .bounds(left + 204, top + 232, 130, 22).build());
        renameStationButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.dab_server.rename_station"), button -> saveStationForm())
                .bounds(left + 204, top + 264, 154, 24).build());
        renameStationButton.setTooltip(Tooltip.create(Component.translatable("screen.radio.dab_server.rename_station_hint")));
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.save"), button -> applyCurrentInputs())
                .bounds(left + 378, top + 264, 106, 24).build());
        uploadAudioButton = addRenderableWidget(Button.builder(Component.literal("LIBRARY"), button -> {
                    if (stationDirty) {
                        saveStationForm();
                    }
                    minecraft.setScreen(new AudioLibraryScreen(this, menu.containerId, false));
                })
                .bounds(left + 204, top + 182, 84, 20).build());
        uploadAudioButton.setTooltip(Tooltip.create(Component.literal("Choose audio uploaded with a Recorder")));
        for (EditBox input : stationInputs()) {
            input.setResponder(value -> {
                if (!syncingInputs) {
                    stationDirty = true;
                }
            });
        }
        previousMode = -1;
        previousEnsembleIndex = -1;
        previousStationIndex = -1;
        stationDirty = false;
        pendingStationRevision = -1;
        refreshWidgets();
    }

    private EditBox addInput(int x, int y, int width, int maxLength, String labelKey, String hintKey) {
        EditBox input = new EditBox(font, x, y, width, 20, Component.translatable(labelKey));
        input.setMaxLength(maxLength);
        input.setHint(Component.translatable(hintKey));
        input.setTooltip(Tooltip.create(Component.translatable(hintKey)));
        return addRenderableWidget(input);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (previousMode != menu.getMode()
                || previousEnsembleIndex != menu.getSelectedEnsembleIndex()
                || previousStationIndex != menu.getSelectedStationIndex()
                || previousDlsUrlMode != menu.isStationDlsUrlMode()) {
            for (EditBox input : allInputs()) {
                input.setFocused(false);
            }
            previousMode = menu.getMode();
            previousEnsembleIndex = menu.getSelectedEnsembleIndex();
            previousStationIndex = menu.getSelectedStationIndex();
            previousDlsUrlMode = menu.isStationDlsUrlMode();
            stationDirty = false;
            pendingStationRevision = -1;
        }
        if (pendingStationRevision >= 0 && menu.getStationSaveRevision() >= pendingStationRevision) {
            pendingStationRevision = -1;
        }
        if (menu.getMode() == DabServerMenu.MODE_STATION_DETAIL && stationDirty && !isAnyStationInputFocused()) {
            saveStationForm();
        }
        syncInput(ensembleNameInput, menu.getEnsembleName());
        if (!stationDirty && pendingStationRevision < 0) {
            syncInput(sidInput, menu.getStationSid());
            syncInput(labelInput, menu.getStationLabel());
            syncInput(ptyInput, Integer.toString(menu.getStationPty()));
            syncInput(dlsTextInput, menu.getStationDlsText());
            syncInput(dlsUrlInput, menu.getStationDlsUrl());
            syncInput(motUrlInput, menu.getStationMotUrl());
            syncInput(streamUrlInput, menu.getStationStreamUrl());
            syncInput(bitrateInput, menu.getStationBitrateKbps());
        }
        refreshWidgets();
        if (focusMuxNameOnReturn && menu.getMode() == DabServerMenu.MODE_ENSEMBLES) {
            ensembleNameInput.setFocused(true);
            focusMuxNameOnReturn = false;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isAnyInputFocused() && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && isAnyInputFocused()) {
            applyCurrentInputs();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (stationDirty) {
            saveStationForm();
        }
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (stationDirty && menu.getMode() == DabServerMenu.MODE_STATION_DETAIL
                && !saveButton.isMouseOver(mouseX, mouseY)
                && !renameStationButton.isMouseOver(mouseX, mouseY)) {
            for (EditBox input : stationInputs()) {
                if (input.visible && input.isFocused() && !input.isMouseOver(mouseX, mouseY)) {
                    saveStationForm();
                    input.setFocused(false);
                    break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF0D1721);
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 36, 0xFF20374B);
        drawPanel(guiGraphics, leftPos + 8, topPos + 42, leftPos + 188, topPos + 262);
        drawPanel(guiGraphics, leftPos + 196, topPos + 42, leftPos + 492, topPos + 262);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        guiGraphics.fill(x1, y1, x2, y2, PANEL_BORDER);
        guiGraphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int mode = menu.getMode();
        Component heading = switch (mode) {
            case DabServerMenu.MODE_STATIONS -> Component.translatable("screen.radio.dab_server.mode_stations", menu.getEnsembleName());
            case DabServerMenu.MODE_STATION_DETAIL -> Component.translatable("screen.radio.dab_server.mode_station_detail", menu.getStationLabel());
            default -> Component.translatable("screen.radio.dab_server.mode_ensembles");
        };
        guiGraphics.drawString(font, heading, 84, 14, TEXT, false);
        guiGraphics.drawString(font, Component.translatable(mode == DabServerMenu.MODE_STATION_DETAIL
                ? "screen.radio.dab_server.station_list" : "screen.radio.dab_server.mux_list"), 16, 50, TEXT, false);
        guiGraphics.drawString(font, Component.translatable(mode == DabServerMenu.MODE_STATIONS
                ? "screen.radio.dab_server.station_list" : mode == DabServerMenu.MODE_STATION_DETAIL
                ? "screen.radio.dab_server.station_detail" : "screen.radio.dab_server.ensemble_name"), 204, 50, TEXT, false);

        if (mode == DabServerMenu.MODE_ENSEMBLES) {
            guiGraphics.drawString(font, Component.translatable("screen.radio.dab_server.mux_help_1"), 204, 142, MUTED_TEXT, false);
            guiGraphics.drawString(font, Component.translatable("screen.radio.dab_server.mux_help_2"), 204, 156, MUTED_TEXT, false);
        } else if (mode == DabServerMenu.MODE_STATION_DETAIL) {
            drawFieldLabels(guiGraphics);
        }

        int count = mode == DabServerMenu.MODE_STATION_DETAIL ? menu.getStationCount() : menu.getEnsembleCount();
        int pageStart = mode == DabServerMenu.MODE_STATION_DETAIL ? menu.getStationPageStart() : menu.getEnsemblePageStart();
        int pageSize = mode == DabServerMenu.MODE_STATION_DETAIL ? STATION_LIST_ROWS : ENSEMBLE_LIST_ROWS;
        guiGraphics.drawString(font, Component.translatable("screen.radio.dab_server.page", count == 0 ? 0 : pageStart / pageSize + 1,
                (count + pageSize - 1) / pageSize), 64, 244, MUTED_TEXT, false);
        if (mode == DabServerMenu.MODE_STATIONS) {
            guiGraphics.drawString(font, Component.translatable("screen.radio.dab_server.page", menu.getStationCount() == 0 ? 0 : menu.getStationPageStart() / STATION_LIST_ROWS + 1,
                    (menu.getStationCount() + STATION_LIST_ROWS - 1) / STATION_LIST_ROWS), 326, 246, MUTED_TEXT, false);
        }
        if (!menu.getStatusMessage().isBlank()) {
            guiGraphics.drawString(font, menu.getStatusMessage(), 204, 296, 0xFF9FE29F, false);
        }
        if (menu.getEnsembleCount() > 0) {
            int used = menu.getMuxUsedKbps();
            int capacity = menu.getMuxCapacityKbps();
            guiGraphics.drawString(font, "MUX " + used + "/" + capacity + " kbps  "
                    + menu.getMuxErrorCorrectionLabel(), 16, 296,
                    used > capacity ? 0xFFFF7777 : MUTED_TEXT, false);
        }
    }

    private void drawFieldLabels(GuiGraphics guiGraphics) {
        String[] keys = {
                "screen.radio.dab_server.sid", "screen.radio.dab_server.label", "screen.radio.dab_server.pty",
                menu.isStationDlsUrlMode() ? "screen.radio.dab_server.dls_url" : "screen.radio.dab_server.dls_text",
                "screen.radio.dab_server.mot_url", "screen.radio.dab_server.stream_url", "screen.radio.dab_server.bitrate"
        };
        for (int i = 0; i < keys.length; i++) {
            if (i == 5) {
                continue;
            }
            guiGraphics.drawString(font, Component.translatable(keys[i]), 204, 68 + i * 24, MUTED_TEXT, false);
        }
    }

    private void applyCurrentInputs() {
        if (menu.getMode() == DabServerMenu.MODE_ENSEMBLES && menu.getEnsembleCount() > 0) {
            applyAction("set_ensemble_name", ensembleNameInput.getValue());
        } else if (menu.getMode() == DabServerMenu.MODE_STATION_DETAIL) {
            saveStationForm();
        }
    }

    private void saveStationForm() {
        if (menu.getMode() != DabServerMenu.MODE_STATION_DETAIL) {
            return;
        }
        String packed = String.join("\u001F", sidInput.getValue(), labelInput.getValue(), dlsTextInput.getValue(),
                dlsUrlInput.getValue(), motUrlInput.getValue(), streamUrlInput.getValue(), bitrateInput.getValue(),
                ptyInput.getValue());
        pendingStationRevision = Math.max(pendingStationRevision, menu.getStationSaveRevision()) + 1;
        stationDirty = false;
        applyAction("save_station_form", packed);
    }

    private void refreshWidgets() {
        int mode = menu.getMode();
        boolean ensembles = mode == DabServerMenu.MODE_ENSEMBLES;
        boolean stations = mode == DabServerMenu.MODE_STATIONS;
        boolean detail = mode == DabServerMenu.MODE_STATION_DETAIL;
        backButton.visible = !ensembles;
        renameMuxButton.visible = stations || detail;
        renameStationButton.visible = detail;
        for (int i = 0; i < ENSEMBLE_LIST_ROWS; i++) {
            String label = menu.getEnsembleListEntry(i);
            ensembleButtons[i].visible = !detail;
            ensembleButtons[i].active = !label.isBlank();
            ensembleButtons[i].setMessage(Component.literal((menu.getEnsemblePageStart() + i == menu.getSelectedEnsembleIndex() ? "> " : "  ") + label));
        }
        for (int i = 0; i < STATION_LIST_ROWS; i++) {
            String label = menu.getStationListEntry(i);
            stationButtons[i].visible = stations || detail;
            stationButtons[i].active = !label.isBlank();
            stationButtons[i].setMessage(Component.literal((menu.getStationPageStart() + i == menu.getSelectedStationIndex() ? "> " : "  ") + label));
            stationButtons[i].setX(leftPos + (detail ? 16 : 204));
            stationButtons[i].setWidth(detail ? 164 : 280);
        }
        ensemblePrevButton.visible = !detail;
        ensembleNextButton.visible = !detail;
        ensemblePrevButton.active = menu.getEnsemblePageStart() > 0;
        ensembleNextButton.active = menu.getEnsemblePageStart() + ENSEMBLE_LIST_ROWS < menu.getEnsembleCount();
        stationPrevButton.visible = stations || detail;
        stationNextButton.visible = stations || detail;
        stationPrevButton.setX(leftPos + (detail ? 16 : 204));
        stationNextButton.setX(leftPos + (detail ? 152 : 456));
        stationPrevButton.active = menu.getStationPageStart() > 0;
        stationNextButton.active = menu.getStationPageStart() + STATION_LIST_ROWS < menu.getStationCount();
        addEnsembleButton.visible = ensembles;
        removeEnsembleButton.visible = ensembles;
        removeEnsembleButton.active = menu.getEnsembleCount() > 0;
        addStationButton.visible = stations;
        removeStationButton.visible = stations;
        addStationButton.active = menu.getEnsembleCount() > 0;
        removeStationButton.active = menu.getStationCount() > 0;
        ensembleNameInput.visible = ensembles;
        ensembleNameInput.setEditable(ensembles && menu.getEnsembleCount() > 0);
        sidInput.visible = detail;
        labelInput.visible = detail;
        ptyInput.visible = detail;
        dlsTextInput.visible = detail && !menu.isStationDlsUrlMode();
        dlsUrlInput.visible = detail && menu.isStationDlsUrlMode();
        motUrlInput.visible = detail;
        streamUrlInput.visible = detail;
        uploadAudioButton.visible = detail;
        bitrateInput.visible = detail;
        dlsModeButton.visible = detail;
        dlsModeButton.setMessage(Component.translatable(menu.isStationDlsUrlMode()
                ? "screen.radio.dab_server.dls_url_mode" : "screen.radio.dab_server.dls_text_mode"));
        saveButton.visible = ensembles || detail;
        saveButton.active = detail || menu.getEnsembleCount() > 0;
        saveButton.setMessage(Component.translatable(detail ? "screen.radio.dab_server.save_station" : "screen.radio.dab_server.save_mux"));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            if (stationDirty && menu.getMode() == DabServerMenu.MODE_STATION_DETAIL) {
                saveStationForm();
            }
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void applyAction(String action, String value) {
        PacketDistributor.sendToServer(new SetDabServerConfigPayload(menu.containerId, action, value));
    }

    private void syncInput(EditBox input, String value) {
        if (input != null && !input.isFocused() && !value.equals(input.getValue())) {
            syncingInputs = true;
            try {
                input.setValue(value);
            } finally {
                syncingInputs = false;
            }
        }
    }

    private boolean isNumericInput(String value) {
        if (value.length() > 4) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private EditBox[] allInputs() {
        return new EditBox[]{ensembleNameInput, sidInput, labelInput, ptyInput, dlsTextInput, dlsUrlInput,
                motUrlInput, streamUrlInput, bitrateInput};
    }

    private EditBox[] stationInputs() {
        return new EditBox[]{sidInput, labelInput, ptyInput, dlsTextInput, dlsUrlInput, motUrlInput, streamUrlInput, bitrateInput};
    }

    private boolean isAnyStationInputFocused() {
        for (EditBox input : stationInputs()) {
            if (input.isFocused() && input.visible) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyInputFocused() {
        for (EditBox input : allInputs()) {
            if (input != null && input.isFocused() && input.visible) {
                return true;
            }
        }
        return false;
    }
}
