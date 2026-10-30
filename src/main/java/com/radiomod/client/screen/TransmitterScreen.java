package com.radiomod.client.screen;

import com.radiomod.menu.TransmitterMenu;
import com.radiomod.network.SetFmRdsConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class TransmitterScreen extends AbstractContainerScreen<TransmitterMenu> {
    private EditBox frequencyInput;
    private EditBox powerInput;
    private EditBox streamUrlInput;
    private Button directionalButton;
    private String lastFrequencyText;
    private String lastPowerText;
    private String lastStreamUrl;

    public TransmitterScreen(TransmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 380;
        imageHeight = 232;
    }

    @Override
    protected void init() {
        imageWidth = Math.min(380, Math.max(300, width - 12));
        super.init();
        int left = leftPos;
        int top = topPos;
        int applyX = left + imageWidth - 68;
        int inputWidth = imageWidth - 228;

        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(left + imageWidth - 29, top + 8, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("PRESETS"), button -> openPresets())
                .bounds(left + imageWidth - 131, top + 7, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-10 kHz"), button -> sendButton(TransmitterMenu.BUTTON_FREQUENCY_DOWN))
                .bounds(left + 16, top + 78, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10 kHz"), button -> sendButton(TransmitterMenu.BUTTON_FREQUENCY_UP))
                .bounds(left + 84, top + 78, 64, 20).build());

        frequencyInput = new EditBox(font, left + 152, top + 80, inputWidth, 16, Component.translatable("screen.radio.frequency_input"));
        frequencyInput.setFilter(this::isNumericInput);
        frequencyInput.setHint(Component.literal("kHz (65000–108000)"));
        frequencyInput.setValue(Integer.toString(menu.getFrequencyKhz()));
        addRenderableWidget(frequencyInput);
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyFrequencyInput())
                .bounds(applyX, top + 78, 52, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-1 W"), button -> sendButton(TransmitterMenu.BUTTON_POWER_DOWN))
                .bounds(left + 16, top + 126, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1 W"), button -> sendButton(TransmitterMenu.BUTTON_POWER_UP))
                .bounds(left + 84, top + 126, 64, 20).build());

        powerInput = new EditBox(font, left + 152, top + 128, inputWidth, 16, Component.translatable("screen.radio.power_input"));
        powerInput.setFilter(this::isNumericInput);
        powerInput.setHint(Component.literal("Power in W"));
        powerInput.setValue(Integer.toString(menu.getPowerW()));
        addRenderableWidget(powerInput);
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyPowerInput())
                .bounds(applyX, top + 126, 52, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.radio.rds.config"), button -> openRdsConfig())
                .bounds(left + 16, top + 204, 136, 20).build());
        directionalButton = addRenderableWidget(Button.builder(directionalityLabel(), button -> sendButton(TransmitterMenu.BUTTON_DIRECTIONAL_TOGGLE))
                .bounds(left + 160, top + 204, imageWidth - 176, 20).build());

        streamUrlInput = new EditBox(font, left + 16, top + 176, imageWidth - 92, 16, Component.translatable("screen.radio.stream_url"));
        streamUrlInput.setHint(Component.translatable("screen.radio.stream_url_hint"));
        streamUrlInput.setMaxLength(1024);
        streamUrlInput.setValue(menu.getStreamUrl());
        addRenderableWidget(streamUrlInput);
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyStreamUrlInput())
                .bounds(applyX, top + 174, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("LIBRARY"), button -> {
                    saveStreamIfEdited();
                    minecraft.setScreen(new AudioLibraryScreen(this, menu.containerId, false));
                })
                .bounds(left + imageWidth - 106, top + 155, 90, 17).build());
        lastFrequencyText = frequencyInput.getValue();
        lastPowerText = powerInput.getValue();
        lastStreamUrl = streamUrlInput.getValue();
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        TransmitterUi.frame(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
        TransmitterUi.card(guiGraphics, leftPos + 12, topPos + 58, imageWidth - 24, 44);
        TransmitterUi.card(guiGraphics, leftPos + 12, topPos + 106, imageWidth - 24, 44);
        TransmitterUi.card(guiGraphics, leftPos + 12, topPos + 154, imageWidth - 24, 44);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "FM TRANSMITTER", 13, 13, TransmitterUi.TEXT, false);
        TransmitterUi.status(guiGraphics, font,
                menu.hasAntennaConnected() ? "Antenna connected" : "Antenna missing", 16, 38,
                menu.hasAntennaConnected());
        TransmitterUi.status(guiGraphics, font, menu.isRdsEnabled() ? "RDS on" : "RDS off",
                imageWidth - 88, 38, menu.isRdsEnabled());
        drawCardLabel(guiGraphics, "FREQUENCY", String.format(java.util.Locale.ROOT,
                "%.3f MHz", menu.getFrequencyKhz() / 1000.0), 63);
        drawCardLabel(guiGraphics, "TRANSMIT POWER", menu.getPowerW() + " W", 111);
        drawCardLabel(guiGraphics, "AUDIO SOURCE", "", 159);
    }

    private void drawCardLabel(GuiGraphics graphics, String label, String value, int y) {
        graphics.drawString(font, label, 16, y, TransmitterUi.ACCENT, false);
        graphics.drawString(font, value, imageWidth - 16 - font.width(value), y, TransmitterUi.MUTED, false);
    }

    private void openRdsConfig() {
        if (minecraft == null) {
            return;
        }
        saveStreamIfEdited();
        minecraft.setScreen(new RdsConfigScreen(this, menu));
    }

    @Override public void onClose() {
        if (!frequencyInput.getValue().equals(lastFrequencyText)) applyFrequencyInput();
        if (!powerInput.getValue().equals(lastPowerText)) applyPowerInput();
        saveStreamIfEdited();
        super.onClose();
    }

    private void openPresets() {
        if (minecraft != null) {
            saveStreamIfEdited();
            minecraft.setScreen(new TransmitterPresetScreen(this, "FM", menu::hasPreset,
                    this::sendButton, this::resetStreamAfterPresetLoad,
                    TransmitterMenu.BUTTON_PRESET_SAVE_BASE, TransmitterMenu.BUTTON_PRESET_LOAD_BASE));
        }
    }

    private void resetStreamAfterPresetLoad() {
        streamUrlInput.setValue(menu.getStreamUrl());
        lastStreamUrl = streamUrlInput.getValue();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        lastFrequencyText = syncInput(frequencyInput, lastFrequencyText, Integer.toString(menu.getFrequencyKhz()));
        lastPowerText = syncInput(powerInput, lastPowerText, Integer.toString(menu.getPowerW()));
        lastStreamUrl = syncInput(streamUrlInput, lastStreamUrl, menu.getStreamUrl());
        if (directionalButton != null) {
            directionalButton.setMessage(directionalityLabel());
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTextInputFocused() && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (frequencyInput != null && frequencyInput.isFocused()) {
                applyFrequencyInput();
                return true;
            }
            if (powerInput != null && powerInput.isFocused()) {
                applyPowerInput();
                return true;
            }
            if (streamUrlInput != null && streamUrlInput.isFocused()) {
                applyStreamUrlInput();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isTextInputFocused() {
        return frequencyInput != null && frequencyInput.isFocused()
                || powerInput != null && powerInput.isFocused()
                || streamUrlInput != null && streamUrlInput.isFocused();
    }

    private void applyFrequencyInput() {
        if (minecraft == null || minecraft.gameMode == null || frequencyInput == null || frequencyInput.getValue().isEmpty()) {
            return;
        }
        try {
            int frequencyKhz = Integer.parseInt(frequencyInput.getValue());
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TransmitterMenu.encodeSetFrequencyButtonId(frequencyKhz));
        } catch (NumberFormatException ignored) {
        }
    }

    private String syncInput(EditBox input, String previousServerValue, String serverValue) {
        if (input != null && !input.isFocused() && input.getValue().equals(previousServerValue)
                && !serverValue.equals(previousServerValue)) {
            input.setValue(serverValue);
        }
        return serverValue;
    }

    private void applyPowerInput() {
        if (minecraft == null || minecraft.gameMode == null || powerInput == null || powerInput.getValue().isEmpty()) {
            return;
        }
        try {
            int powerW = Integer.parseInt(powerInput.getValue());
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TransmitterMenu.encodeSetPowerButtonId(powerW));
        } catch (NumberFormatException ignored) {
        }
    }

    private boolean isNumericInput(String value) {
        if (value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return value.length() <= 6;
    }

    private Component directionalityLabel() {
        return Component.literal(menu.isDirectionalEnabled() ? "DIR ON  " + menu.getDirectionDegrees() + "°" : "DIR OFF");
    }

    private void applyStreamUrlInput() {
        if (streamUrlInput == null) {
            return;
        }
        PacketDistributor.sendToServer(new SetFmRdsConfigPayload(menu.containerId, "set_stream_url", streamUrlInput.getValue()));
    }

    private void saveStreamIfEdited() {
        if (streamUrlInput != null && !streamUrlInput.getValue().equals(lastStreamUrl))
            applyStreamUrlInput();
    }
}
