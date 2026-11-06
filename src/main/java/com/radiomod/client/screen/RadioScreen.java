package com.radiomod.client.screen;

import com.radiomod.Radio;
import com.radiomod.menu.RadioMenu;
import com.radiomod.item.RadioItem;
import com.radiomod.signal.SignalMath;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class RadioScreen extends AbstractContainerScreen<RadioMenu> {
    private static final int DIGITAL_BASE_WIDTH = 480;
    private static final int DIGITAL_BASE_HEIGHT = 264;
    private static final float DIGITAL_MAX_SCALE = 1.35f;
    private static final int COLOR_HOUSING = 0xFF2B2B2E;
    private static final int COLOR_HOUSING_INNER = 0xFF1E1E20;
    private static final int COLOR_PANEL_BLUE = 0xFF0D4676;
    private static final int COLOR_PANEL_BLUE_DARK = 0xFF093456;
    private static final int COLOR_DISPLAY_BG = 0xFF02070D;
    private static final int COLOR_DISPLAY_BORDER = 0xFF0CA8E2;
    private static final int COLOR_TEXT_WHITE = 0xFFE6ECF2;
    private static final int COLOR_TEXT_YELLOW = 0xFFFFFF33;
    private static final int COLOR_BAR_OFF = 0xFF23404F;
    private static final int COLOR_DIGITAL_FRAME = 0xFFDBDEDC;
    private static final int COLOR_DIGITAL_PANEL = 0xFFC6CCCD;
    private static final int COLOR_DIGITAL_INNER = 0xFF77858C;
    private static final int COLOR_DIGITAL_SCREEN = 0xFFC6DFE6;
    private static final int COLOR_DIGITAL_BORDER = 0xFF607C8B;
    private static final int COLOR_DIGITAL_TEXT = 0xFF263945;
    private static final int COLOR_DIGITAL_ACCENT = 0xFF164967;
    private static final int COLOR_DIGITAL_ACCENT_DARK = 0xFF7895A1;
    private static final int COLOR_BLACK_FRAME = 0xFF111317;
    private static final int COLOR_BLACK_PANEL = 0xFF252A30;
    private static final int COLOR_BLACK_INNER = 0xFF3D4750;
    private static final int COLOR_BLACK_SCREEN = 0xFF111F27;
    private static final int COLOR_BLACK_BORDER = 0xFF5C7785;
    private static final int COLOR_BLACK_TEXT = 0xFFBBD5DB;
    private static final int COLOR_BLACK_ACCENT = 0xFF7BC5DF;
    private static final int COLOR_BLACK_DIM = 0xFF718C97;
    private static final int COLOR_DIGITAL_RDS_ACTIVE = 0xFF142C38;
    private static final int COLOR_BLACK_RDS_ACTIVE = 0xFF5D9CB4;
    private static final int COLOR_STATUS_ON = 0xFF64D66B;
    private static final int COLOR_STATUS_OFF = 0xFFE15353;

    private enum Tab {
        FM,
        DAB
    }

    private Tab activeTab = Tab.FM;
    private EditBox frequencyInput;
    private EditBox dabChannelInput;
    private Button fmTabButton;
    private Button dabTabButton;
    private Button fmDownButton;
    private Button fmUpButton;
    private Button fmSetButton;
    private Button dabDownButton;
    private Button dabUpButton;
    private Button dabSetButton;
    private Button muteButton;
    private Button afButton;
    private Button pinHudButton;
    private final Button[] keypadDigitButtons = new Button[10];
    private Button keypadEnterButton;
    private Button keypadCloseButton;
    private String keypadDigits = "";
    private String keypadMessage = "";
    private boolean keypadTuneComplete;
    private boolean dabSupported = true;
    private boolean digitalStyle = false;
    private boolean digitalLayout;
    private boolean blackDigitalStyle;
    private float digitalScale = 1.0f;
    private final boolean forceDigitalStyle;

    public RadioScreen(RadioMenu menu, Inventory playerInventory, Component title) {
        this(menu, playerInventory, title, false);
    }

    public RadioScreen(RadioMenu menu, Inventory playerInventory, Component title, boolean forceDigitalStyle) {
        super(menu, playerInventory, title);
        imageWidth = forceDigitalStyle ? 480 : 328;
        imageHeight = forceDigitalStyle ? 264 : 210;
        this.forceDigitalStyle = forceDigitalStyle;
    }

    @Override
    protected void init() {
        // The digital item can open the shared radio menu, so resolve its layout before
        // the container screen computes leftPos and topPos from imageWidth/imageHeight.
        digitalLayout = forceDigitalStyle || isHoldingDigitalRadio() || menu.isDigitalRadioStyle();
        blackDigitalStyle = isHoldingBlackDigitalRadio();
        if (digitalLayout) {
            digitalScale = Math.min(DIGITAL_MAX_SCALE,
                    Math.min((width - 12.0f) / DIGITAL_BASE_WIDTH, (height - 12.0f) / DIGITAL_BASE_HEIGHT));
            imageWidth = Math.round(DIGITAL_BASE_WIDTH * digitalScale);
            imageHeight = Math.round(DIGITAL_BASE_HEIGHT * digitalScale);
        } else {
            digitalScale = 1.0f;
            imageWidth = 328;
            imageHeight = 210;
        }
        super.init();
        int left = leftPos;
        int top = topPos;
        dabSupported = menu.isDabSupported();
        digitalStyle = digitalLayout;
        if (menu.isDigitalRadioStyle()) {
            blackDigitalStyle = menu.isBlackDigitalRadioStyle();
        }
        if (digitalLayout) {
            dabSupported = false;
        }
        if (minecraft != null && minecraft.player != null) {
            boolean playerWantsDab = RadioItem.getMode(minecraft.player) == RadioItem.MODE_DAB;
            activeTab = dabSupported && playerWantsDab ? Tab.DAB : Tab.FM;
            if (!dabSupported) {
                RadioItem.setMode(minecraft.player, RadioItem.MODE_FM);
            }
        }

        fmTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.tab.fm"), button -> setActiveTab(Tab.FM))
                .bounds(left + 14, top + 12, 66, 20)
                .build());
        dabTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.tab.dab"), button -> setActiveTab(Tab.DAB))
                .bounds(left + 84, top + 12, 66, 20)
                .build());

        fmDownButton = addRenderableWidget(Button.builder(Component.literal("-10"), button -> {
                    clearKeypad();
                    sendButton(RadioMenu.BUTTON_FREQUENCY_DOWN);
                })
                .bounds(left + 14, top + 38, 44, 20)
                .build());
        fmUpButton = addRenderableWidget(Button.builder(Component.literal("+10"), button -> {
                    clearKeypad();
                    sendButton(RadioMenu.BUTTON_FREQUENCY_UP);
                })
                .bounds(left + 62, top + 38, 44, 20)
                .build());
        frequencyInput = new EditBox(font, left + 112, top + 40, 120, 16, Component.translatable("screen.radio.frequency_input"));
        frequencyInput.setFilter(this::isNumericInput);
        frequencyInput.setValue(Integer.toString(menu.getFrequencyKhz()));
        addRenderableWidget(frequencyInput);
        fmSetButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), button -> onFmSetPressed())
                .bounds(left + 236, top + 38, 50, 20)
                .build());

        dabDownButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.channel.prev"), button -> sendButton(RadioMenu.BUTTON_DAB_CHANNEL_DOWN))
                .bounds(left + 14, top + 38, 66, 20)
                .build());
        dabUpButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.channel.next"), button -> sendButton(RadioMenu.BUTTON_DAB_CHANNEL_UP))
                .bounds(left + 84, top + 38, 66, 20)
                .build());
        dabChannelInput = new EditBox(font, left + 156, top + 40, 76, 16, Component.translatable("screen.radio.dab_channel_input"));
        dabChannelInput.setFilter(this::isNumericInput);
        dabChannelInput.setValue(Integer.toString(menu.getDabChannelIndex() + 1));
        addRenderableWidget(dabChannelInput);
        dabSetButton = addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), button -> applyDabChannelInput())
                .bounds(left + 236, top + 38, 50, 20)
                .build());
        muteButton = addRenderableWidget(Button.builder(muteLabel(), button -> toggleMute())
                .bounds(left + imageWidth - 84, top + 12, 70, 20)
                .build());
        afButton = addRenderableWidget(Button.builder(afLabel(), button -> toggleAf())
                .bounds(left + imageWidth - 84, top + 36, 70, 20)
                .build());
        pinHudButton = addRenderableWidget(Button.builder(pinHudLabel(), button -> togglePinHud())
                .bounds(left + imageWidth - 160, top + 12, 72, 20)
                .build());
        if (digitalStyle) {
            int keyX = 200;
            int keyY = 184;
            int keyWidth = 44;
            int keyGap = 6;
            int rowGap = 22;
            int keyHeight = 16;
            setDigitalBounds(pinHudButton, keyX, keyY, keyWidth, keyHeight);
            setDigitalBounds(fmSetButton, keyX + 4 * (keyWidth + keyGap), keyY, keyWidth, keyHeight);
            fmSetButton.setMessage(Component.literal("STEP"));
            setDigitalBounds(fmUpButton, keyX, keyY + rowGap, keyWidth, keyHeight);
            fmUpButton.setMessage(Component.literal("+"));
            setDigitalBounds(fmDownButton, keyX, keyY + 2 * rowGap, keyWidth, keyHeight);
            fmDownButton.setMessage(Component.literal("-"));
            setDigitalBounds(muteButton, 40, 192, 84, 20);
            muteButton.setTooltip(Tooltip.create(Component.translatable("screen.radio.dp666.power_hint")));
            setDigitalBounds(afButton, 40, 226, 84, 18);

            int[] digits = {1, 2, 3, 4, 5, 6, 7, 8, 9};
            for (int index = 0; index < digits.length; index++) {
                int digit = digits[index];
                int row = index / 3;
                int column = index % 3 + 1;
                keypadDigitButtons[digit] = addRenderableWidget(Button.builder(Component.literal(Integer.toString(digit)), button -> appendKeypadDigit(digit))
                        .bounds(digitalX(keyX + column * (keyWidth + keyGap)), digitalY(keyY + row * rowGap), digitalSize(keyWidth), digitalSize(keyHeight)).build());
            }
            keypadDigitButtons[0] = addRenderableWidget(Button.builder(Component.literal("0"), button -> appendKeypadDigit(0))
                    .bounds(digitalX(keyX + 4 * (keyWidth + keyGap)), digitalY(keyY + rowGap), digitalSize(keyWidth), digitalSize(keyHeight)).build());
            keypadEnterButton = addRenderableWidget(Button.builder(Component.literal("ENTER"), button -> applyKeypadFrequency())
                    .bounds(digitalX(keyX + 4 * (keyWidth + keyGap)), digitalY(keyY + 2 * rowGap), digitalSize(keyWidth), digitalSize(keyHeight)).build());
            keypadCloseButton = addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                    .bounds(digitalX(438), digitalY(20), digitalSize(22), digitalSize(16)).build());
        }

        updateTabVisibility();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        if (digitalStyle) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(left, top, 0);
            guiGraphics.pose().scale(digitalScale, digitalScale, 1.0f);
            renderDigitalBackground(guiGraphics, 0, 0);
            guiGraphics.pose().popPose();
            return;
        }

        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, COLOR_HOUSING);
        guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, COLOR_HOUSING_INNER);
        guiGraphics.fill(left + 10, top + 8, left + imageWidth - 10, top + imageHeight - 8, COLOR_PANEL_BLUE);
        guiGraphics.fill(left + 12, top + 10, left + imageWidth - 12, top + imageHeight - 10, COLOR_PANEL_BLUE_DARK);

        int displayLeft = left + 14;
        int displayTop = top + 66;
        int displayRight = left + imageWidth - 14;
        int displayBottom = top + imageHeight - 14;

        guiGraphics.fill(displayLeft, displayTop, displayRight, displayBottom, COLOR_DISPLAY_BG);
        drawBorder(guiGraphics, displayLeft, displayTop, displayRight, displayBottom, COLOR_DISPLAY_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (digitalStyle) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(digitalScale, digitalScale, 1.0f);
            renderDigitalLabels(guiGraphics);
            guiGraphics.pose().popPose();
            return;
        }
        guiGraphics.drawString(font, title, 14, 2, COLOR_TEXT_WHITE, false);

        if (activeTab == Tab.FM) {
            renderFmDisplay(guiGraphics);
        } else {
            renderDabDisplay(guiGraphics);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncInput(frequencyInput, Integer.toString(menu.getFrequencyKhz()));
        syncInput(dabChannelInput, Integer.toString(menu.getDabChannelIndex() + 1));
        dabSupported = menu.isDabSupported();
        digitalStyle = digitalLayout;
        if (menu.isDigitalRadioStyle()) {
            blackDigitalStyle = menu.isBlackDigitalRadioStyle();
        }
        if (digitalLayout) {
            dabSupported = false;
        }
        if (!dabSupported && activeTab != Tab.FM) {
            activeTab = Tab.FM;
            updateTabVisibility();
        }
        if (minecraft != null && minecraft.player != null) {
            RadioItem.setFrequencyKhz(minecraft.player, menu.getFrequencyKhz());
            RadioItem.setDabChannelIndex(minecraft.player, menu.getDabChannelIndex());
            RadioItem.setMode(minecraft.player, dabSupported && activeTab == Tab.DAB ? RadioItem.MODE_DAB : RadioItem.MODE_FM);
        }
        if (muteButton != null) {
            muteButton.setMessage(muteLabel());
        }
        if (afButton != null) {
            afButton.setMessage(afLabel());
        }
        if (pinHudButton != null) {
            pinHudButton.setMessage(pinHudLabel());
        }
        if (digitalStyle && fmSetButton != null) {
            fmSetButton.setMessage(Component.literal("STEP"));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (digitalStyle) {
            if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                appendKeypadDigit(keyCode - GLFW.GLFW_KEY_0);
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
                appendKeypadDigit(keyCode - GLFW.GLFW_KEY_KP_0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (keypadTuneComplete) {
                    clearKeypad();
                } else if (!keypadDigits.isEmpty()) {
                    keypadDigits = keypadDigits.substring(0, keypadDigits.length() - 1);
                }
                keypadMessage = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyKeypadFrequency();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (activeTab == Tab.DAB && dabChannelInput != null && dabChannelInput.isFocused()) {
                applyDabChannelInput();
                return true;
            }
            if (activeTab == Tab.FM && frequencyInput != null && frequencyInput.isFocused()) {
                applyFrequencyInput();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderFmDisplay(GuiGraphics guiGraphics) {
        int displayX = 18;
        int displayY = 72;
        guiGraphics.drawString(font, Component.translatable("screen.radio.mode_fm"), displayX, displayY, COLOR_TEXT_WHITE, false);
        String mhz = String.format(Locale.ROOT, "%.2f", menu.getFrequencyKhz() / 1000.0);
        guiGraphics.drawString(font, Component.literal(mhz + " MHz"), displayX + 56, displayY, COLOR_TEXT_YELLOW, false);

        guiGraphics.drawString(font, Component.literal(String.format(Locale.ROOT, "Signal: %.1f dBuV", menu.getSignalDbuv())), displayX, displayY + 20, COLOR_TEXT_WHITE, false);
        drawIndicatorBar(guiGraphics, displayX + 104, displayY + 20, 170, 10, menu.getSignalDbuv(), -10.0, 70.0);

        guiGraphics.drawString(font, Component.literal(String.format(Locale.ROOT, "SNR: %.1f dB", menu.getSnrDb())), displayX, displayY + 38, COLOR_TEXT_WHITE, false);
        drawIndicatorBar(guiGraphics, displayX + 104, displayY + 38, 170, 10, menu.getSnrDb(), -5.0, 40.0);

        String ps = menu.getRdsPs().isBlank() ? "----" : menu.getRdsPs();
        String rt = menu.getRdsRt().isBlank() ? "----" : menu.getRdsRt();
        String pi = menu.getRdsPi().isBlank() ? "----" : menu.getRdsPi();
        String group = menu.getRdsGroupCode().isBlank() ? "--" : menu.getRdsGroupCode();
        guiGraphics.drawString(font, Component.translatable("screen.radio.rds_group", group), displayX, displayY + 60, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(
                font,
                Component.translatable("screen.radio.rds_ber", menu.getRdsBerPercent()),
                displayX + 178,
                displayY + 60,
                berColor(menu.getRdsBerPercent()),
                false
        );
        guiGraphics.drawString(font, Component.translatable("screen.radio.rds_ps", ps), displayX, displayY + 74, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.rds_pi", pi), displayX, displayY + 88, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.rds_pty", menu.getRdsPty(), ptyName(menu.getRdsPty())), displayX + 120, displayY + 88, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.rds_tp", menu.isRdsTp() ? "ON" : "OFF"), displayX + 240, displayY + 88, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(font, Component.literal(scrollingRtText(rt, 48)), displayX, displayY + 102, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(font, Component.literal(scrollingMetaRdsText(48)), displayX, displayY + 116, COLOR_TEXT_YELLOW, false);
    }

    private void renderDigitalLabels(GuiGraphics guiGraphics) {
        guiGraphics.drawString(font, Component.literal("DEEPELEC"), 22, 20, digitalText(), false);
        guiGraphics.drawString(font, Component.literal("DP-666"), 100, 20, digitalText(), false);
        guiGraphics.drawString(font, Component.literal("FM/LW/MW/SW DSP RECEIVER"), 20, 36, digitalAccent(), false);
        guiGraphics.drawString(font, Component.literal("R-D-S"), 55, 173, digitalText(), false);
        renderDigitalFmDisplay(guiGraphics);
    }

    private void renderDigitalFmDisplay(GuiGraphics guiGraphics) {
        boolean muted = minecraft != null && minecraft.player != null && RadioItem.isMuted(minecraft.player);
        guiGraphics.drawString(font, Component.literal(muted ? "MUTE" : "VOL"), 184, 19, digitalText(), false);
        if (menu.isRdsDecoding() && menu.isRdsCtEnabled() && minecraft != null && minecraft.level != null) {
            long dayTime = minecraft.level.getDayTime();
            String clock = String.format(Locale.ROOT, "%02d:%02d", (dayTime / 1000L + 6L) % 24L, (dayTime % 1000L) * 60L / 1000L);
            guiGraphics.drawString(font, Component.literal(clock), 250, 19, digitalRdsColor(), false);
        }
        guiGraphics.drawString(font, Component.literal("STEP " + fmStepModeShortLabel()), 365, 19, digitalText(), false);

        guiGraphics.drawString(font, Component.literal("MAN"), 185, 39, digitalText(), false);
        guiGraphics.drawString(font, Component.literal("AUTO"), 185, 54, digitalDim(), false);
        guiGraphics.drawString(font, Component.literal("MEM"), 185, 69, digitalDim(), false);
        guiGraphics.drawString(font, Component.literal("FM"), 235, 39, digitalText(), false);
        String mhz = String.format(Locale.ROOT, "%.2f", menu.getFrequencyKhz() / 1000.0);
        drawScaledCenteredString(guiGraphics, Component.literal(mhz), 325, 47, digitalAccent(), 2.35f);
        guiGraphics.drawString(font, Component.literal("MHz"), 419, 76, digitalText(), false);
        String directEntry = !keypadMessage.isBlank() ? keypadMessage
                : keypadDigits.isBlank() ? "DIRECT: 650-1080"
                : keypadTuneComplete ? "DIRECT " + keypadDigits + " > "
                        + String.format(Locale.ROOT, "%.1f MHz", Integer.parseInt(keypadDigits) / 10.0)
                : "DIRECT " + keypadDigits + "_";
        guiGraphics.drawString(font, Component.literal(directEntry), 325 - font.width(directEntry) / 2, 82, digitalText(), false);
        guiGraphics.drawString(font, Component.literal("AUTO BW"), 417, 38, digitalText(), false);
        if (menu.isRdsDecoding()) {
            guiGraphics.drawString(font, Component.literal("RDS"), 431, 53, digitalRdsColor(), false);
        }

        guiGraphics.drawString(font, Component.literal("S"), 184, 99, digitalText(), false);
        drawDigitalMeter(guiGraphics, 201, 100, menu.getSignalDbuv(), -5.0, 65.0);
        guiGraphics.drawString(font, Component.literal(String.format(Locale.ROOT, "%.0f dBuV", menu.getSignalDbuv())), 354, 99, digitalText(), false);
        guiGraphics.drawString(font, Component.literal("A"), 184, 115, digitalText(), false);
        drawDigitalMeter(guiGraphics, 201, 116, menu.getSnrDb(), 0.0, 40.0);
        guiGraphics.drawString(font, Component.literal(String.format(Locale.ROOT, "%.0f dB", menu.getSnrDb())), 354, 115, digitalText(), false);

        String ps = menu.getRdsPs().isBlank() ? "----" : menu.getRdsPs();
        String pi = menu.getRdsPi().isBlank() ? "----" : menu.getRdsPi();
        String rt = menu.getRdsRt().isBlank() ? "----" : menu.getRdsRt();
        guiGraphics.drawString(font, Component.literal("PTY: " + ptyName(menu.getRdsPty())), 184, 132, digitalRdsColor(), false);
        guiGraphics.drawString(font, Component.literal("BER: " + menu.getRdsBerPercent() + "%"), 379, 132, digitalRdsColor(), false);
        guiGraphics.drawString(font, Component.literal("PS: " + ps), 184, 145, digitalRdsColor(), false);
        guiGraphics.drawString(font, Component.literal("PI: " + pi), 378, 145, digitalRdsColor(), false);
        guiGraphics.drawString(font, Component.literal("RT: " + scrollingRtText(rt, 37)), 184, 158, digitalRdsColor(), false);
    }

    private void drawDigitalMeter(GuiGraphics guiGraphics, int x, int y, double value, double min, double max) {
        int lit = (int) Math.round(Mth.clamp((value - min) / (max - min), 0.0, 1.0) * 20);
        for (int segment = 0; segment < 20; segment++) {
            int segmentX = x + segment * 7;
            guiGraphics.fill(segmentX, y, segmentX + 5, y + 5,
                    segment < lit ? digitalAccent() : digitalDim());
        }
    }

    private void renderDabDisplay(GuiGraphics guiGraphics) {
        int displayX = 18;
        int displayY = 72;
        guiGraphics.drawString(font, Component.translatable("screen.radio.mode_dab"), displayX, displayY, COLOR_TEXT_WHITE, false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.channel", menu.getDabChannelLabel()), displayX + 56, displayY, COLOR_TEXT_YELLOW, false);

        guiGraphics.drawString(font, Component.literal(String.format(Locale.ROOT, "Signal: %.1f dBuV", menu.getDabSignalDbuv())), displayX, displayY + 20, COLOR_TEXT_WHITE, false);
        drawIndicatorBar(guiGraphics, displayX + 104, displayY + 20, 170, 10, menu.getDabSignalDbuv(), -10.0, 70.0);

        guiGraphics.drawString(font, Component.literal(String.format(Locale.ROOT, "SNR: %.1f dB", menu.getDabSnrDb())), displayX, displayY + 38, COLOR_TEXT_WHITE, false);
        drawIndicatorBar(guiGraphics, displayX + 104, displayY + 38, 170, 10, menu.getDabSnrDb(), -5.0, 40.0);

        guiGraphics.drawString(font, Component.translatable("screen.radio.dab_fic", signalStateLabel(menu.getDabFicState())), displayX, displayY + 62, signalStateColor(menu.getDabFicState()), false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.dab_msc", signalStateLabel(menu.getDabMscState())), displayX, displayY + 78, signalStateColor(menu.getDabMscState()), false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.dab_frame_sync", signalStateLabel(menu.getDabFrameSyncState())), displayX, displayY + 94, signalStateColor(menu.getDabFrameSyncState()), false);
    }

    private void drawIndicatorBar(GuiGraphics guiGraphics, int x, int y, int width, int height, double value, double minValue, double maxValue) {
        int segments = 28;
        int gap = 1;
        int segmentWidth = Math.max(2, (width - ((segments - 1) * gap)) / segments);
        int usedWidth = segments * segmentWidth + (segments - 1) * gap;
        int startX = x + (width - usedWidth) / 2;
        double normalized = Mth.clamp((value - minValue) / (maxValue - minValue), 0.0, 1.0);
        int litSegments = (int) Math.round(normalized * segments);

        for (int i = 0; i < segments; i++) {
            int sx = startX + (i * (segmentWidth + gap));
            int color = i < litSegments ? indicatorColorForRatio((i + 1.0) / segments) : COLOR_BAR_OFF;
            guiGraphics.fill(sx, y, sx + segmentWidth, y + height, color);
        }
        drawBorder(guiGraphics, x - 2, y - 2, x + width + 2, y + height + 2,
                digitalStyle ? COLOR_DIGITAL_BORDER : COLOR_DISPLAY_BORDER);
    }

    private int indicatorColorForRatio(double ratio) {
        if (ratio < 0.45) {
            return 0xFF4FC3F7;
        }
        if (ratio < 0.75) {
            return 0xFFFFEB3B;
        }
        return 0xFF8BC34A;
    }

    private void drawBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top, left + 1, bottom, color);
        guiGraphics.fill(right - 1, top, right, bottom, color);
    }

    private void renderDigitalBackground(GuiGraphics guiGraphics, int left, int top) {
        int frame = blackDigitalStyle ? COLOR_BLACK_FRAME : COLOR_DIGITAL_FRAME;
        int panel = blackDigitalStyle ? COLOR_BLACK_PANEL : COLOR_DIGITAL_PANEL;
        int inner = blackDigitalStyle ? COLOR_BLACK_INNER : COLOR_DIGITAL_INNER;
        int screen = blackDigitalStyle ? COLOR_BLACK_SCREEN : COLOR_DIGITAL_SCREEN;
        int border = blackDigitalStyle ? COLOR_BLACK_BORDER : COLOR_DIGITAL_BORDER;
        guiGraphics.fill(left, top, left + DIGITAL_BASE_WIDTH, top + DIGITAL_BASE_HEIGHT, frame);
        guiGraphics.fill(left + 4, top + 4, left + DIGITAL_BASE_WIDTH - 4, top + DIGITAL_BASE_HEIGHT - 4, panel);
        guiGraphics.fill(left + 16, top + 48, left + 160, top + 168, inner);
        drawBorder(guiGraphics, left + 16, top + 48, left + 160, top + 168, border);
        for (int y = top + 56; y <= top + 156; y += 9) {
            for (int x = left + 24; x <= left + 148; x += 9) {
                guiGraphics.fill(x, y, x + 3, y + 3, blackDigitalStyle ? COLOR_BLACK_FRAME : COLOR_DIGITAL_TEXT);
            }
        }
        int screenLeft = left + 176;
        int screenTop = top + 14;
        int screenRight = left + DIGITAL_BASE_WIDTH - 14;
        int screenBottom = top + 174;
        guiGraphics.fill(screenLeft - 5, screenTop - 5, screenRight + 5, screenBottom + 5, inner);
        guiGraphics.fill(screenLeft, screenTop, screenRight, screenBottom, screen);
        drawBorder(guiGraphics, screenLeft, screenTop, screenRight, screenBottom, border);
        guiGraphics.fill(screenLeft + 2, screenTop + 15, screenRight - 2, screenTop + 16, border);
        guiGraphics.fill(screenLeft + 2, screenTop + 77, screenRight - 2, screenTop + 78, border);
        guiGraphics.fill(screenLeft + 2, screenTop + 112, screenRight - 2, screenTop + 113, border);
        guiGraphics.fill(screenLeft + 16, screenTop + 21, screenLeft + 43, screenTop + 34, blackDigitalStyle ? COLOR_BLACK_INNER : COLOR_DIGITAL_INNER);
        guiGraphics.fill(left + 16, top + 184, left + 166, top + 252, blackDigitalStyle ? COLOR_BLACK_INNER : 0xFFB3BABC);
        drawBorder(guiGraphics, left + 16, top + 184, left + 166, top + 252, border);
    }

    private int digitalText() {
        return blackDigitalStyle ? COLOR_BLACK_TEXT : COLOR_DIGITAL_TEXT;
    }

    private int digitalAccent() {
        return blackDigitalStyle ? COLOR_BLACK_ACCENT : COLOR_DIGITAL_ACCENT;
    }

    private int digitalDim() {
        return blackDigitalStyle ? COLOR_BLACK_DIM : COLOR_DIGITAL_ACCENT_DARK;
    }

    private int digitalRdsColor() {
        if (menu.isRdsDecoding()) {
            return blackDigitalStyle ? COLOR_BLACK_RDS_ACTIVE : COLOR_DIGITAL_RDS_ACTIVE;
        }
        return digitalText();
    }

    private void drawScaledCenteredString(GuiGraphics guiGraphics, Component text, int centerX, int y, int color, float scale) {
        int width = Math.round(font.width(text) * scale);
        drawScaledString(guiGraphics, text, centerX - width / 2, y, color, scale);
    }

    private void drawScaledString(GuiGraphics guiGraphics, Component text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(font, text, Math.round(x / scale), Math.round(y / scale), color, false);
        guiGraphics.pose().popPose();
    }

    private void setActiveTab(Tab tab) {
        if (!dabSupported && tab == Tab.DAB) {
            tab = Tab.FM;
        }
        activeTab = tab;
        if (minecraft != null && minecraft.player != null) {
            RadioItem.setMode(minecraft.player, dabSupported && tab == Tab.DAB ? RadioItem.MODE_DAB : RadioItem.MODE_FM);
        }
        sendButton(tab == Tab.DAB ? RadioMenu.BUTTON_MODE_DAB : RadioMenu.BUTTON_MODE_FM);
        updateTabVisibility();
    }

    private void updateTabVisibility() {
        boolean fm = activeTab == Tab.FM;
        setControlVisibility(fmDownButton, fm);
        setControlVisibility(fmUpButton, fm);
        setControlVisibility(fmSetButton, fm);
        setControlVisibility(frequencyInput, fm && !digitalStyle);

        boolean dab = activeTab == Tab.DAB;
        setControlVisibility(dabDownButton, dab);
        setControlVisibility(dabUpButton, dab);
        setControlVisibility(dabSetButton, dab);
        setControlVisibility(dabChannelInput, dab);

        if (digitalStyle) {
            setControlVisibility(fmTabButton, false);
            setControlVisibility(dabTabButton, false);
        } else {
            fmTabButton.active = !fm;
            setControlVisibility(dabTabButton, dabSupported);
            dabTabButton.active = dabSupported && !dab;
        }
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void onFmSetPressed() {
        if (digitalStyle) {
            sendButton(RadioMenu.BUTTON_STEP_MODE_TOGGLE);
            return;
        }
        applyFrequencyInput();
    }

    private void appendKeypadDigit(int digit) {
        if (!digitalStyle) {
            return;
        }
        if (keypadTuneComplete || !keypadMessage.isBlank()) {
            clearKeypad();
        }
        if (keypadDigits.length() >= 4) {
            return;
        }
        keypadDigits += digit;
        if (keypadDigits.length() >= 3) {
            int tenthsMhz = Integer.parseInt(keypadDigits);
            if (tenthsMhz >= 650 && tenthsMhz <= 1080) {
                applyKeypadFrequency();
            } else if (keypadDigits.length() == 4) {
                keypadMessage = "FM RANGE: 65.0-108.0 MHz";
            }
        }
    }

    private void clearKeypad() {
        keypadDigits = "";
        keypadMessage = "";
        keypadTuneComplete = false;
    }

    private void applyKeypadFrequency() {
        if (!digitalStyle) {
            return;
        }
        if (keypadTuneComplete) {
            clearKeypad();
            return;
        }
        if (keypadDigits.length() < 3) {
            keypadMessage = "ENTER 650-1080";
            return;
        }
        int frequencyKhz = Integer.parseInt(keypadDigits) * 100;
        if (!SignalMath.isFmBandFrequency(frequencyKhz)) {
            keypadMessage = "FM RANGE: 65.0-108.0 MHz";
            return;
        }
        sendButton(RadioMenu.encodeSetFrequencyButtonId(frequencyKhz));
        if (minecraft != null && minecraft.player != null) {
            RadioItem.setFrequencyKhz(minecraft.player, frequencyKhz);
        }
        keypadMessage = "";
        keypadTuneComplete = true;
    }

    private void applyFrequencyInput() {
        if (minecraft == null || minecraft.gameMode == null || frequencyInput == null || frequencyInput.getValue().isEmpty()) {
            return;
        }
        try {
            int frequencyKhz = Integer.parseInt(frequencyInput.getValue());
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RadioMenu.encodeSetFrequencyButtonId(frequencyKhz));
            if (minecraft.player != null) {
                RadioItem.setFrequencyKhz(minecraft.player, frequencyKhz);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyDabChannelInput() {
        if (minecraft == null || minecraft.gameMode == null || dabChannelInput == null || dabChannelInput.getValue().isEmpty()) {
            return;
        }
        try {
            int oneBasedIndex = Integer.parseInt(dabChannelInput.getValue());
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RadioMenu.encodeSetDabChannelButtonId(oneBasedIndex - 1));
            if (minecraft.player != null) {
                RadioItem.setDabChannelIndex(minecraft.player, oneBasedIndex - 1);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private Component muteLabel() {
        boolean muted = minecraft != null && minecraft.player != null && RadioItem.isMuted(minecraft.player);
        if (digitalStyle) {
            return Component.literal("POWER").withStyle(style -> style.withColor(muted ? COLOR_STATUS_OFF : COLOR_STATUS_ON));
        }
        return Component.translatable("screen.radio.mute", Component.translatable(muted ? "screen.radio.on" : "screen.radio.off"));
    }

    private Component afLabel() {
        if (digitalStyle) {
            return Component.literal("AF").withStyle(style -> style.withColor(menu.isAfEnabled() ? COLOR_STATUS_ON : COLOR_STATUS_OFF));
        }
        return Component.translatable("screen.radio.af", Component.translatable(menu.isAfEnabled() ? "screen.radio.on" : "screen.radio.off"));
    }

    private Component pinHudLabel() {
        boolean pinned = minecraft != null && minecraft.player != null && RadioItem.isHudPinned(minecraft.player);
        if (digitalStyle) {
            return Component.literal("PIN").withStyle(style -> style.withColor(pinned ? COLOR_STATUS_ON : COLOR_STATUS_OFF));
        }
        return Component.translatable("screen.radio.pin_hud", Component.translatable(pinned ? "screen.radio.on" : "screen.radio.off"));
    }

    private void toggleMute() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        RadioItem.setMuted(minecraft.player, !RadioItem.isMuted(minecraft.player));
    }

    private void toggleAf() {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RadioMenu.BUTTON_AF_TOGGLE);
    }

    private void togglePinHud() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        RadioItem.setHudPinned(minecraft.player, !RadioItem.isHudPinned(minecraft.player));
    }

    private String fmStepModeShortLabel() {
        return switch (menu.getFmStepMode()) {
            case RadioItem.FM_STEP_MODE_0_5_MHZ -> "0.5";
            case RadioItem.FM_STEP_MODE_1_0_MHZ -> "1.0";
            case RadioItem.FM_STEP_MODE_SEEK -> "SEEK";
            default -> "0.1";
        };
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

    private void syncInput(EditBox input, String value) {
        if (input != null && !input.isFocused() && !value.equals(input.getValue())) {
            input.setValue(value);
        }
    }

    private String signalStateLabel(int state) {
        return switch (state) {
            case 2 -> "OK";
            case 1 -> "Correctable";
            default -> "Error";
        };
    }

    private int signalStateColor(int state) {
        return switch (state) {
            case 2 -> 0xFF8BC34A;
            case 1 -> 0xFFFFEB3B;
            default -> 0xFFFF5252;
        };
    }

    private int berColor(int berPercent) {
        if (berPercent <= 8) {
            return 0xFF8BC34A;
        }
        if (berPercent <= 25) {
            return 0xFFFFEB3B;
        }
        if (berPercent <= 60) {
            return 0xFFFFA726;
        }
        return 0xFFFF5252;
    }

    private String ptyName(int pty) {
        return switch (Math.max(0, Math.min(31, pty))) {
            case 1 -> "News";
            case 2 -> "Current Affairs";
            case 3 -> "Information";
            case 4 -> "Sport";
            case 5 -> "Education";
            case 6 -> "Drama";
            case 7 -> "Culture";
            case 8 -> "Science";
            case 9 -> "Varied";
            case 10 -> "Pop Music";
            case 11 -> "Rock Music";
            case 12 -> "Easy Listening";
            case 13 -> "Light Classics";
            case 14 -> "Serious Classics";
            case 15 -> "Other Music";
            case 16 -> "Weather";
            case 17 -> "Finance";
            case 18 -> "Children";
            case 19 -> "Social Affairs";
            case 20 -> "Religion";
            case 21 -> "Phone In";
            case 22 -> "Travel";
            case 23 -> "Leisure";
            case 24 -> "Jazz Music";
            case 25 -> "Country Music";
            case 26 -> "National Music";
            case 27 -> "Oldies Music";
            case 28 -> "Folk Music";
            case 29 -> "Documentary";
            case 30 -> "Alarm Test";
            case 31 -> "Alarm";
            default -> "None";
        };
    }

    private String scrollingRtText(String rt, int visibleChars) {
        String full = "RT: " + rt;
        if (full.length() <= visibleChars) {
            return full;
        }
        long tick = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        int offset = (int) ((tick / 3L) % (full.length() + 6));
        String loop = full + "      " + full;
        if (offset + visibleChars > loop.length()) {
            offset = 0;
        }
        return loop.substring(offset, offset + visibleChars);
    }

    private String scrollingMetaRdsText(int visibleChars) {
        String af = menu.getRdsAf().isBlank() ? "-" : menu.getRdsAf();
        String ecc = menu.getRdsEcc().isBlank() ? "-" : menu.getRdsEcc();
        String lic = menu.getRdsLic().isBlank() ? "-" : menu.getRdsLic();
        String pin = menu.getRdsPin().isBlank() ? "-" : menu.getRdsPin();
        String ptyn = menu.getRdsPtyn().isBlank() ? "-" : menu.getRdsPtyn();
        String lps = menu.getRdsLongPs().isBlank() ? "-" : menu.getRdsLongPs();
        String oda = menu.getRdsOdaGroup().isBlank() ? "-" : menu.getRdsOdaGroup();
        String meta = switch ((int) (((minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L) / 60L) % 4L)) {
            case 1 -> "ECC:" + ecc + " LIC:" + lic + " PIN:" + pin;
            case 2 -> "ODA RT+:" + (menu.isRdsOdaRtPlusEnabled() ? "ON" : "OFF") + " GRP:" + oda + " CT:" + (menu.isRdsCtEnabled() ? "ON" : "OFF");
            case 3 -> "Long PS: " + lps;
            default -> "AF:" + af + " PTYN:" + ptyn;
        };

        if (meta.length() <= visibleChars) {
            return meta;
        }
        long tick = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        int offset = (int) ((tick / 4L) % (meta.length() + 6));
        String loop = meta + "      " + meta;
        if (offset + visibleChars > loop.length()) {
            offset = 0;
        }
        return loop.substring(offset, offset + visibleChars);
    }

    private void setControlVisibility(AbstractWidget widget, boolean visible) {
        widget.visible = visible;
        widget.active = visible;
    }

    private int digitalX(int baseX) {
        return leftPos + Math.round(baseX * digitalScale);
    }

    private int digitalY(int baseY) {
        return topPos + Math.round(baseY * digitalScale);
    }

    private int digitalSize(int baseSize) {
        return Math.max(1, Math.round(baseSize * digitalScale));
    }

    private void setDigitalBounds(AbstractWidget widget, int x, int y, int width, int height) {
        widget.setX(digitalX(x));
        widget.setY(digitalY(y));
        widget.setWidth(digitalSize(width));
        widget.setHeight(digitalSize(height));
    }

    private boolean isHoldingDigitalRadio() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        return minecraft.player.getMainHandItem().is(Radio.DIGITAL_RADIO_ITEM.get())
                || minecraft.player.getOffhandItem().is(Radio.DIGITAL_RADIO_ITEM.get())
                || isHoldingBlackDigitalRadio();
    }

    private boolean isHoldingBlackDigitalRadio() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        return minecraft.player.getMainHandItem().is(Radio.BLACK_DIGITAL_RADIO_ITEM.get())
                || minecraft.player.getOffhandItem().is(Radio.BLACK_DIGITAL_RADIO_ITEM.get());
    }
}
