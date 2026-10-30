package com.radiomod.client.screen;

import com.radiomod.menu.DabTransmitterMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class DabTransmitterScreen extends AbstractContainerScreen<DabTransmitterMenu> {
    private final List<AbstractWidget> rfControls = new ArrayList<>();
    private final List<AbstractWidget> ensembleControls = new ArrayList<>();
    private EditBox frequencyInput;
    private EditBox powerInput;
    private EditBox ensembleIdInput;
    private EditBox tiiInput;
    private Button previousMuxButton;
    private Button nextMuxButton;
    private Button previousServerButton;
    private Button nextServerButton;
    private Button unlinkServerButton;
    private Button directionalButton;
    private Button errorCorrectionButton;
    private Button versionButton;
    private Button rfTab;
    private Button ensembleTab;
    private boolean ensemblePage;
    private String lastFrequencyText;
    private String lastPowerText;
    private String lastEnsembleIdText;
    private String lastTiiText;

    public DabTransmitterScreen(DabTransmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 400;
        imageHeight = 278;
    }

    @Override
    protected void init() {
        imageWidth = Math.min(400, Math.max(308, width - 12));
        super.init();
        rfControls.clear();
        ensembleControls.clear();
        int left = leftPos;
        int top = topPos;
        int applyX = left + imageWidth - 68;
        int inputWidth = imageWidth - 228;
        int halfWidth = (imageWidth - 36) / 2;

        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(left + imageWidth - 29, top + 8, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("PRESETS"), button -> openPresets())
                .bounds(left + imageWidth - 225, top + 34, 88, 20).build());
        directionalButton = addRenderableWidget(Button.builder(directionalityLabel(),
                button -> sendButton(DabTransmitterMenu.BUTTON_DIRECTIONAL_TOGGLE))
                .bounds(left + imageWidth - 132, top + 34, 116, 20).build());
        rfTab = addRenderableWidget(Button.builder(Component.literal("RF / SIGNAL"), button -> setPage(false))
                .bounds(left + 12, top + 59, halfWidth, 20).build());
        ensembleTab = addRenderableWidget(Button.builder(Component.literal("ENSEMBLE"), button -> setPage(true))
                .bounds(left + 24 + halfWidth, top + 59, halfWidth, 20).build());

        rfButton("-10 kHz", left + 16, top + 106, 64, DabTransmitterMenu.BUTTON_FREQUENCY_DOWN);
        rfButton("+10 kHz", left + 84, top + 106, 64, DabTransmitterMenu.BUTTON_FREQUENCY_UP);
        frequencyInput = input(left + 152, top + 108, inputWidth, 16, "Frequency kHz", "kHz (50000–300000)",
                Integer.toString(menu.getFrequencyKhz()), true, true, 6);
        rfControls.add(addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyFrequencyInput())
                .bounds(applyX, top + 106, 52, 20).build()));

        rfButton("Previous channel", left + 16, top + 152, halfWidth,
                DabTransmitterMenu.BUTTON_CHANNEL_DOWN);
        rfButton("Next channel", left + 24 + halfWidth, top + 152, halfWidth,
                DabTransmitterMenu.BUTTON_CHANNEL_UP);

        rfButton("-1 W", left + 16, top + 198, 64, DabTransmitterMenu.BUTTON_POWER_DOWN);
        rfButton("+1 W", left + 84, top + 198, 64, DabTransmitterMenu.BUTTON_POWER_UP);
        powerInput = input(left + 152, top + 200, inputWidth, 16, "Transmit power", "Power in W",
                Integer.toString(menu.getPowerW()), true, true, 6);
        rfControls.add(addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyPowerInput())
                .bounds(applyX, top + 198, 52, 20).build()));

        ensembleIdInput = input(left + 16, top + 108, 86, 16, "Ensemble ID", "Ensemble ID",
                Integer.toString(menu.getEnsembleId()), true, false, 6);
        ensembleControls.add(addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyEnsembleIdInput())
                .bounds(left + 106, top + 106, 48, 20).build()));
        tiiInput = input(left + imageWidth - 142, top + 108, 62, 16, "TII code", "4 hex digits",
                menu.getTiiCode(), false, false, 4);
        tiiInput.setFilter(value -> value.matches("(?i)[0-9a-f]{0,4}"));
        ensembleControls.add(addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyTiiInput())
                .bounds(applyX, top + 106, 52, 20).build()));

        previousMuxButton = addRenderableWidget(Button.builder(Component.literal("Previous MUX"),
                button -> sendButton(DabTransmitterMenu.BUTTON_MUX_PREV))
                .bounds(left + 16, top + 152, halfWidth, 20).build());
        ensembleControls.add(previousMuxButton);
        nextMuxButton = addRenderableWidget(Button.builder(Component.literal("Next MUX"),
                button -> sendButton(DabTransmitterMenu.BUTTON_MUX_NEXT))
                .bounds(left + 24 + halfWidth, top + 152, halfWidth, 20).build());
        ensembleControls.add(nextMuxButton);

        errorCorrectionButton = addRenderableWidget(Button.builder(errorCorrectionLabel(),
                button -> sendButton(DabTransmitterMenu.BUTTON_ERROR_CORRECTION_NEXT))
                .bounds(left + 16, top + 198, halfWidth, 20).build());
        ensembleControls.add(errorCorrectionButton);
        versionButton = addRenderableWidget(Button.builder(versionLabel(),
                button -> sendButton(DabTransmitterMenu.BUTTON_DAB_VERSION_NEXT))
                .bounds(left + 24 + halfWidth, top + 198, halfWidth, 20).build());
        ensembleControls.add(versionButton);
        int serverButtonWidth = (imageWidth - 48) / 3;
        previousServerButton = addRenderableWidget(Button.builder(Component.literal("PREV SERVER"),
                button -> sendButton(DabTransmitterMenu.BUTTON_SERVER_PREV))
                .bounds(left + 16, top + 244, serverButtonWidth, 20).build());
        previousServerButton.setTooltip(Tooltip.create(Component.literal("Link the previous available DAB server")));
        ensembleControls.add(previousServerButton);
        nextServerButton = addRenderableWidget(Button.builder(Component.literal("NEXT SERVER"),
                button -> sendButton(DabTransmitterMenu.BUTTON_SERVER_NEXT))
                .bounds(left + 24 + serverButtonWidth, top + 244, serverButtonWidth, 20).build());
        nextServerButton.setTooltip(Tooltip.create(Component.literal("Link the next available DAB server")));
        ensembleControls.add(nextServerButton);
        unlinkServerButton = addRenderableWidget(Button.builder(Component.literal("UNLINK"),
                button -> sendButton(DabTransmitterMenu.BUTTON_SERVER_UNLINK))
                .bounds(left + 32 + serverButtonWidth * 2, top + 244, serverButtonWidth, 20).build());
        unlinkServerButton.setTooltip(Tooltip.create(Component.literal("Disconnect this transmitter from its DAB server")));
        ensembleControls.add(unlinkServerButton);
        lastFrequencyText = frequencyInput.getValue();
        lastPowerText = powerInput.getValue();
        lastEnsembleIdText = ensembleIdInput.getValue();
        lastTiiText = tiiInput.getValue();
        setPage(ensemblePage);
    }

    private void rfButton(String label, int x, int y, int width, int buttonId) {
        rfControls.add(addRenderableWidget(Button.builder(Component.literal(label), button -> sendButton(buttonId))
                .bounds(x, y, width, 20).build()));
    }

    private EditBox input(int x, int y, int width, int height, String label, String hint,
                          String value, boolean numeric, boolean rf, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, height, Component.literal(label));
        box.setMaxLength(maxLength);
        if (numeric) {
            box.setFilter(this::isNumericInput);
        }
        box.setHint(Component.literal(hint));
        box.setValue(value);
        addRenderableWidget(box);
        (rf ? rfControls : ensembleControls).add(box);
        return box;
    }

    private void setPage(boolean ensemble) {
        ensemblePage = ensemble;
        for (AbstractWidget widget : rfControls) {
            widget.visible = !ensemble;
            if (ensemble && widget instanceof EditBox input) {
                input.setFocused(false);
            }
        }
        for (AbstractWidget widget : ensembleControls) {
            widget.visible = ensemble;
            if (!ensemble && widget instanceof EditBox input) {
                input.setFocused(false);
            }
        }
        rfTab.active = ensemble;
        ensembleTab.active = !ensemble;
        previousMuxButton.active = ensemble && menu.isServerLinked();
        nextMuxButton.active = ensemble && menu.isServerLinked();
        previousServerButton.active = ensemble && menu.getAvailableServerCount() > 0;
        nextServerButton.active = ensemble && menu.getAvailableServerCount() > 0;
        unlinkServerButton.active = ensemble && menu.hasServerLink();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        TransmitterUi.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        TransmitterUi.card(graphics, leftPos + 12, topPos + 86, imageWidth - 24, 42);
        TransmitterUi.card(graphics, leftPos + 12, topPos + 132, imageWidth - 24, 42);
        TransmitterUi.card(graphics, leftPos + 12, topPos + 178, imageWidth - 24, 42);
        TransmitterUi.card(graphics, leftPos + 12, topPos + 224, imageWidth - 24, 42);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "DAB TRANSMITTER", 13, 13, TransmitterUi.TEXT, false);
        TransmitterUi.status(graphics, font,
                menu.hasAntennaConnected() ? "Antenna connected" : "Antenna missing", 16, 40,
                menu.hasAntennaConnected());
        if (ensemblePage) {
            graphics.drawString(font, "ENSEMBLE ID", 16, 91, TransmitterUi.ACCENT, false);
            graphics.drawString(font, "TII CODE", imageWidth - 142, 91, TransmitterUi.ACCENT, false);
            drawCardLabel(graphics, "MULTIPLEX", menu.isServerLinked()
                    ? fit(menu.getEnsembleName(), imageWidth / 2)
                    : menu.hasServerLink() ? "SERVER UNAVAILABLE" : "LINK A DAB SERVER", 137);
            drawCardLabel(graphics, "ERROR CORRECTION / CODEC",
                    menu.getErrorCorrection().getLabel() + " / " + menu.getDabVersion().getCodecLabel(), 183);
            drawCardLabel(graphics, "DAB SERVER", menu.getAvailableServerCount() == 0
                    ? "NO SERVERS WITH A MUX" : fit(menu.getLinkedServerLabel(), imageWidth / 2), 229);
        } else {
            drawCardLabel(graphics, "FREQUENCY", String.format(Locale.ROOT,
                    "%.3f MHz", menu.getFrequencyKhz() / 1000.0), 91);
            drawCardLabel(graphics, "DAB CHANNEL", menu.getChannelLabel(), 137);
            drawCardLabel(graphics, "TRANSMIT POWER", menu.getPowerW() + " W", 183);
            drawCardLabel(graphics, "OUTPUT MUX", menu.isServerLinked()
                    ? fit(menu.getEnsembleName(), imageWidth / 2) : "NO SERVER LINK", 229);
        }
    }

    private void drawCardLabel(GuiGraphics graphics, String label, String value, int y) {
        graphics.drawString(font, label, 16, y, TransmitterUi.ACCENT, false);
        graphics.drawString(font, value, imageWidth - 16 - font.width(value), y, TransmitterUi.MUTED, false);
    }

    private String fit(String value, int maxWidth) {
        String result = value;
        while (!result.isEmpty() && font.width(result) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.equals(value) ? result : result + "…";
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        lastFrequencyText = syncInput(frequencyInput, lastFrequencyText, Integer.toString(menu.getFrequencyKhz()));
        lastPowerText = syncInput(powerInput, lastPowerText, Integer.toString(menu.getPowerW()));
        lastEnsembleIdText = syncInput(ensembleIdInput, lastEnsembleIdText, Integer.toString(menu.getEnsembleId()));
        lastTiiText = syncInput(tiiInput, lastTiiText, menu.getTiiCode());
        previousMuxButton.active = ensemblePage && menu.isServerLinked();
        nextMuxButton.active = ensemblePage && menu.isServerLinked();
        previousServerButton.active = ensemblePage && menu.getAvailableServerCount() > 0;
        nextServerButton.active = ensemblePage && menu.getAvailableServerCount() > 0;
        unlinkServerButton.active = ensemblePage && menu.hasServerLink();
        directionalButton.setMessage(directionalityLabel());
        errorCorrectionButton.setMessage(errorCorrectionLabel());
        versionButton.setMessage(versionLabel());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTextInputFocused() && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (frequencyInput.visible && frequencyInput.isFocused()) {
                applyFrequencyInput();
                return true;
            }
            if (powerInput.visible && powerInput.isFocused()) {
                applyPowerInput();
                return true;
            }
            if (ensembleIdInput.visible && ensembleIdInput.isFocused()) {
                applyEnsembleIdInput();
                return true;
            }
            if (tiiInput.visible && tiiInput.isFocused()) {
                applyTiiInput();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isTextInputFocused() {
        return frequencyInput != null && frequencyInput.isFocused()
                || powerInput != null && powerInput.isFocused()
                || ensembleIdInput != null && ensembleIdInput.isFocused()
                || tiiInput != null && tiiInput.isFocused();
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void openPresets() {
        if (minecraft != null) {
            applyEnsembleIdInput();
            applyTiiInput();
            minecraft.setScreen(new TransmitterPresetScreen(this, "DAB", menu::hasPreset,
                    this::sendButton, this::resetFieldsAfterPresetLoad, DabTransmitterMenu.BUTTON_PRESET_SAVE_BASE,
                    DabTransmitterMenu.BUTTON_PRESET_LOAD_BASE));
        }
    }

    private void resetFieldsAfterPresetLoad() {
        ensembleIdInput.setValue(Integer.toString(menu.getEnsembleId()));
        tiiInput.setValue(menu.getTiiCode());
        lastEnsembleIdText = ensembleIdInput.getValue();
        lastTiiText = tiiInput.getValue();
    }

    private void applyFrequencyInput() {
        if (frequencyInput == null || frequencyInput.getValue().isEmpty()) {
            return;
        }
        try {
            sendButton(DabTransmitterMenu.encodeSetFrequencyButtonId(Integer.parseInt(frequencyInput.getValue())));
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyPowerInput() {
        if (powerInput == null || powerInput.getValue().isEmpty()) {
            return;
        }
        try {
            sendButton(DabTransmitterMenu.encodeSetPowerButtonId(Integer.parseInt(powerInput.getValue())));
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyEnsembleIdInput() {
        if (ensembleIdInput == null || ensembleIdInput.getValue().isEmpty()) {
            return;
        }
        try {
            sendButton(DabTransmitterMenu.encodeSetEnsembleIdButtonId(Integer.parseInt(ensembleIdInput.getValue())));
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyTiiInput() {
        if (tiiInput != null && tiiInput.getValue().matches("(?i)[0-9a-f]{4}")) {
            sendButton(DabTransmitterMenu.encodeSetTiiButtonId(Integer.parseInt(tiiInput.getValue(), 16)));
        }
    }

    @Override public void onClose() {
        if (!frequencyInput.getValue().equals(lastFrequencyText)) applyFrequencyInput();
        if (!powerInput.getValue().equals(lastPowerText)) applyPowerInput();
        if (!ensembleIdInput.getValue().equals(lastEnsembleIdText)) applyEnsembleIdInput();
        if (!tiiInput.getValue().equals(lastTiiText)) applyTiiInput();
        super.onClose();
    }

    private boolean isNumericInput(String value) {
        if (value.length() > 6) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private String syncInput(EditBox input, String previousServerValue, String serverValue) {
        if (input != null && !input.isFocused() && input.getValue().equals(previousServerValue)
                && !serverValue.equals(previousServerValue)) {
            input.setValue(serverValue);
        }
        return serverValue;
    }

    private Component directionalityLabel() {
        return Component.literal(menu.isDirectionalEnabled() ? "DIR ON  " + menu.getDirectionDegrees() + "°" : "DIR OFF");
    }

    private Component errorCorrectionLabel() {
        return Component.literal("EC  " + menu.getErrorCorrection().getLabel());
    }

    private Component versionLabel() {
        return Component.literal(menu.getDabVersion().getLabel() + "  " + menu.getDabVersion().getCodecLabel());
    }
}
