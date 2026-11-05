package com.radiomod.client.screen;

import com.radiomod.menu.TransmitterMenu;
import com.radiomod.network.SetFmRdsConfigPayload;
import com.radiomod.signal.RdsGroups;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class RdsConfigScreen extends Screen {
    private enum Page {
        GROUP_LIST,
        GROUP_DETAIL
    }

    private final Screen parent;
    private final TransmitterMenu menu;
    private final List<Button> groupButtons = new ArrayList<>();
    private Page page = Page.GROUP_LIST;
    private int selectedGroupIndex = 0;
    private EditBox fieldA;
    private EditBox fieldB;
    private EditBox fieldC;
    private EditBox fieldD;
    private final Map<String, EditBox> textFields = new HashMap<>();
    private final Map<String, String> lastSyncedText = new HashMap<>();

    protected RdsConfigScreen(Screen parent, TransmitterMenu menu) {
        super(Component.translatable("screen.radio.rds.config.title"));
        this.parent = parent;
        this.menu = menu;
    }

    @Override
    protected void init() {
        super.init();
        rebuildRdsWidgets();
    }

    private void rebuildRdsWidgets() {
        saveEditedFields();
        clearWidgets();
        groupButtons.clear();
        textFields.clear();
        lastSyncedText.clear();
        fieldA = null;
        fieldB = null;
        fieldC = null;
        fieldD = null;

        if (page == Page.GROUP_LIST) {
            addRenderableWidget(Button.builder(Component.translatable("screen.radio.back"), b -> onClose())
                    .bounds(10, 10, 90, 20)
                    .build());
            buildGroupButtons();
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.radio.back_to_groups"), b -> {
                        page = Page.GROUP_LIST;
                        rebuildRdsWidgets();
                    })
                    .bounds(10, 10, 120, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("screen.radio.back"), b -> onClose())
                    .bounds(width - 100, 10, 90, 20)
                    .build());
            buildDetailWidgets();
        }
    }

    private void buildGroupButtons() {
        int startX = 10;
        int startY = 40;
        int buttonWidth = 52;
        int buttonHeight = 18;
        int spacingX = 6;
        int spacingY = 6;
        for (int i = 0; i < RdsGroups.size(); i++) {
            int col = i % 6;
            int row = i / 6;
            int x = startX + col * (buttonWidth + spacingX);
            int y = startY + row * (buttonHeight + spacingY);
            int groupIndex = i;
            boolean enabled = menu.isRdsGroupEnabled(i);
            Component label = enabled
                    ? Component.literal(RdsGroups.codeByIndex(i)).withStyle(ChatFormatting.GREEN)
                    : Component.literal(RdsGroups.codeByIndex(i));
            Button button = addRenderableWidget(Button.builder(label, b -> {
                        selectedGroupIndex = groupIndex;
                        page = Page.GROUP_DETAIL;
                        rebuildRdsWidgets();
                    })
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build());
            groupButtons.add(button);
        }
    }

    private void buildDetailWidgets() {
        int x = 12;
        int y = 48;
        int setWidth = 48;
        int contentRight = width - 14;
        int rowWidth = contentRight - x;
        int setX = contentRight - setWidth;

        addRenderableWidget(Button.builder(Component.translatable("screen.radio.rds.group.toggle"), b ->
                        sendAction("set_group_enabled", selectedGroupIndex + ":" + !menu.isRdsGroupEnabled(selectedGroupIndex)))
                .bounds(x, y, 150, 20).build());

        String group = RdsGroups.codeByIndex(selectedGroupIndex);
        switch (group) {
            case "0A" -> {
                fieldA = addEditBox(x, y + 28, 64, "PI (hex, 4)", 4);
                fieldB = addEditBox(x + 70, y + 28, Math.max(88, rowWidth - 70 - setWidth - 8), "PS (dynamic ASCII)", 512);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> {
                            sendAction("set_pi", fieldA.getValue());
                            sendAction("set_ps", fieldB.getValue());
                        })
                        .bounds(setX, y + 26, setWidth, 20).build());
                fieldC = addEditBox(x, y + 54, 84, "PTY", 2);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_pty", fieldC.getValue()))
                        .bounds(x + 88, y + 52, 52, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.rds.tp.toggle"), b ->
                                sendAction("set_tp", Boolean.toString(!menu.isRdsTpEnabled())))
                        .bounds(x + 146, y + 52, 140, 20).build());
                fieldD = addEditBox(x, y + 80, Math.max(120, rowWidth - setWidth - 4), "AF list (e.g. 87.5 87.6)", 128);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_af", fieldD.getValue()))
                        .bounds(setX, y + 78, setWidth, 20).build());
                bind("pi", fieldA); bind("ps", fieldB); bind("pty", fieldC); bind("af", fieldD);
            }
            case "1A" -> {
                int half = Math.max(90, (rowWidth - 6) / 2);
                fieldA = addEditBox(x, y + 28, half, "ECC country", 24);
                fieldB = addEditBox(x + half + 6, y + 28, half, "LIC language", 24);
                fieldC = addEditBox(x, y + 54, Math.max(120, rowWidth - setWidth - 4), "PIN 01 10:00", 16);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> {
                            sendAction("set_ecc", fieldA.getValue());
                            sendAction("set_lic", fieldB.getValue());
                            sendAction("set_pin", fieldC.getValue());
                        })
                        .bounds(setX, y + 52, setWidth, 20).build());
                bind("ecc", fieldA); bind("lic", fieldB); bind("pin", fieldC);
            }
            case "2A" -> {
                fieldA = addEditBox(x, y + 28, Math.max(120, rowWidth - setWidth - 4), "RT text (64 ASCII)", 64);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_rt", fieldA.getValue()))
                        .bounds(setX, y + 26, setWidth, 20).build());
                fieldB = addEditBox(x, y + 54, Math.max(120, rowWidth - setWidth - 4), "RT URL or TuneIn link", 1024);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_rt_url", fieldB.getValue()))
                        .bounds(setX, y + 52, setWidth, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.rds.url_mode.toggle"), b ->
                                sendAction("set_rt_url_mode", Boolean.toString(!menu.isRdsRtUrlModeEnabled())))
                        .bounds(x, y + 80, 170, 20).build());
                bind("rt", fieldA); bind("rt_url", fieldB);
            }
            case "3A" -> {
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.rds.rtplus.toggle"), b ->
                                sendAction("set_oda_rtplus", Boolean.toString(!menu.isRdsOdaRtPlusEnabled())))
                        .bounds(x, y + 28, 170, 20).build());
                fieldA = addEditBox(x, y + 54, Math.max(120, rowWidth - setWidth - 4), "ODA group", 3);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_oda_group", fieldA.getValue()))
                        .bounds(setX, y + 52, setWidth, 20).build());
                bind("oda_group", fieldA);
            }
            case "4A" -> addRenderableWidget(Button.builder(Component.translatable("screen.radio.rds.ct.toggle"), b ->
                            sendAction("set_ct_enabled", Boolean.toString(!menu.isRdsCtEnabled())))
                    .bounds(x, y + 28, 170, 20).build());
            case "10A" -> {
                fieldA = addEditBox(x, y + 28, Math.max(100, rowWidth - setWidth - 4), "PTYN (8 ASCII)", 8);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_ptyn", fieldA.getValue()))
                        .bounds(setX, y + 26, setWidth, 20).build());
                bind("ptyn", fieldA);
            }
            case "15A" -> {
                fieldA = addEditBox(x, y + 28, Math.max(120, rowWidth - setWidth - 4), "Long PS (32 UTF-8)", 32);
                addRenderableWidget(Button.builder(Component.translatable("screen.radio.set"), b -> sendAction("set_long_ps", fieldA.getValue()))
                        .bounds(setX, y + 26, setWidth, 20).build());
                bind("long_ps", fieldA);
            }
            case "14A" -> {
            }
            default -> {
            }
        }
    }

    private EditBox addEditBox(int x, int y, int width, String hint, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, 16, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maxLength);
        addRenderableWidget(box);
        return box;
    }

    private void bind(String field, EditBox box) {
        String value = menu.getRdsText(field);
        box.setValue(value);
        textFields.put(field, box);
        lastSyncedText.put(field, value);
    }

    @Override public void tick() {
        super.tick();
        for (var entry : textFields.entrySet()) {
            String field = entry.getKey();
            EditBox box = entry.getValue();
            String previous = lastSyncedText.get(field);
            String current = menu.getRdsText(field);
            if (!box.isFocused() && box.getValue().equals(previous) && !current.equals(previous))
                box.setValue(current);
            lastSyncedText.put(field, current);
        }
    }

    private void saveEditedFields() {
        for (var entry : textFields.entrySet()) {
            String value = entry.getValue().getValue();
            if (!value.equals(lastSyncedText.get(entry.getKey())))
                sendAction("set_" + entry.getKey(), value);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (page == Page.GROUP_DETAIL) {
                page = Page.GROUP_LIST;
                rebuildRdsWidgets();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(4, 4, width - 4, height - 4, 0xEE111218);
        guiGraphics.drawString(font, title, 10, 16, 0xFFFFFF, false);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (page == Page.GROUP_DETAIL) {
            renderDetailStatusOverlay(guiGraphics);
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.radio.rds.groups_hint"), 10, 28, 0xCCCCCC, false);
        }
    }

    private void renderDetailStatusOverlay(GuiGraphics guiGraphics) {
        String group = RdsGroups.codeByIndex(selectedGroupIndex);
        int overlayX = 12;
        int overlayW = Math.min(width - 24, 520);
        int overlayH = 92;
        int overlayY = height - overlayH - 12;
        guiGraphics.fill(overlayX, overlayY, overlayX + overlayW, overlayY + overlayH, 0xFF0F1520);
        guiGraphics.fill(overlayX, overlayY, overlayX + overlayW, overlayY + 1, 0xFF32577A);
        guiGraphics.fill(overlayX, overlayY + overlayH - 1, overlayX + overlayW, overlayY + overlayH, 0xFF32577A);
        guiGraphics.fill(overlayX, overlayY, overlayX + 1, overlayY + overlayH, 0xFF32577A);
        guiGraphics.fill(overlayX + overlayW - 1, overlayY, overlayX + overlayW, overlayY + overlayH, 0xFF32577A);

        guiGraphics.drawString(font, Component.translatable("screen.radio.rds.group", group), overlayX + 8, overlayY + 8, 0xFFFF66, false);
        guiGraphics.drawString(font, Component.translatable("screen.radio.rds.group_status",
                        menu.isRdsGroupEnabled(selectedGroupIndex)
                                ? Component.translatable("screen.radio.enabled")
                                : Component.translatable("screen.radio.disabled")),
                overlayX + 8, overlayY + 22, menu.isRdsGroupEnabled(selectedGroupIndex) ? 0x55FF55 : 0xFF5555, false);

        if ("0A".equals(group)) {
            drawStatus(guiGraphics, overlayX + 8, overlayY + 40, Component.translatable("screen.radio.rds.tp_status"), menu.isRdsTpEnabled());
        } else if ("2A".equals(group)) {
            drawStatus(guiGraphics, overlayX + 8, overlayY + 40, Component.translatable("screen.radio.rds.url_mode_status"), menu.isRdsRtUrlModeEnabled());
        } else if ("3A".equals(group)) {
            drawStatus(guiGraphics, overlayX + 8, overlayY + 40, Component.translatable("screen.radio.rds.rtplus_status"), menu.isRdsOdaRtPlusEnabled());
        } else if ("4A".equals(group)) {
            drawStatus(guiGraphics, overlayX + 8, overlayY + 40, Component.translatable("screen.radio.rds.ct_status"), menu.isRdsCtEnabled());
        } else if ("14A".equals(group)) {
            guiGraphics.drawString(font, Component.translatable("screen.radio.rds.eon_skipped"), overlayX + 8, overlayY + 40, 0xCCCCCC, false);
        }
    }

    private void drawStatus(GuiGraphics guiGraphics, int x, int y, Component label, boolean enabled) {
        guiGraphics.drawString(
                font,
                Component.translatable("screen.radio.rds.status", label, enabled ? Component.translatable("screen.radio.enabled") : Component.translatable("screen.radio.disabled")),
                x,
                y,
                enabled ? 0x55FF55 : 0xFF5555,
                false
        );
    }

    @Override
    public void onClose() {
        saveEditedFields();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void sendAction(String action, String value) {
        PacketDistributor.sendToServer(new SetFmRdsConfigPayload(menu.containerId, action, value));
    }
}
