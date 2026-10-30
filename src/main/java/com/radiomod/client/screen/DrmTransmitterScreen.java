package com.radiomod.client.screen;

import com.radiomod.menu.DrmTransmitterMenu;
import com.radiomod.network.SetDrmTextPayload;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class DrmTransmitterScreen extends AbstractContainerScreen<DrmTransmitterMenu> {
    private final Map<String, EditBox> fields = new LinkedHashMap<>();
    private final Map<String, String> lastSynced = new LinkedHashMap<>();
    private EditBox frequency, power;
    private String lastFrequency, lastPower;
    private boolean servicePage;
    private Button rfTab, serviceTab;
    public DrmTransmitterScreen(DrmTransmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 420; imageHeight = 318;
    }
    @Override protected void init() {
        imageWidth = Math.min(420, Math.max(320, width - 12));
        super.init(); fields.clear(); lastSynced.clear();
        int x = leftPos, y = topPos, w = imageWidth;
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose()).bounds(x + w - 30, y + 8, 18, 18).build());
        rfTab = addRenderableWidget(Button.builder(Component.literal("RF / PROPAGATION"), b -> { commit(); servicePage = false; page(); })
                .bounds(x + 12, y + 42, (w - 28) / 2, 20).build());
        serviceTab = addRenderableWidget(Button.builder(Component.literal("SERVICE / AUDIO"), b -> { commit(); servicePage = true; page(); })
                .bounds(x + 16 + (w - 28) / 2, y + 42, (w - 28) / 2, 20).build());
        frequency = input(x + 126, y + 89, w - 250, "Frequency in kHz (140–30000)", Integer.toString(menu.frequency()), 6);
        frequency.setFilter(v -> v.matches("[0-9]{0,5}"));
        addRenderableWidget(Button.builder(Component.literal("−"), b -> send(DrmTransmitterMenu.FREQ_DOWN))
                .bounds(x + w - 118, y + 86, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(DrmTransmitterMenu.FREQ_UP))
                .bounds(x + w - 90, y + 86, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Set"), b -> applyNumber(frequency, DrmTransmitterMenu.SET_FREQ))
                .bounds(x + w - 62, y + 86, 48, 20).build());
        power = input(x + 126, y + 124, w - 250, "Transmit power in watts", Integer.toString(menu.power()), 7);
        power.setFilter(v -> v.matches("[0-9]{0,6}"));
        lastFrequency = frequency.getValue(); lastPower = power.getValue();
        addRenderableWidget(Button.builder(Component.literal("−"), b -> send(DrmTransmitterMenu.POWER_DOWN))
                .bounds(x + w - 118, y + 121, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(DrmTransmitterMenu.POWER_UP))
                .bounds(x + w - 90, y + 121, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Set"), b -> applyNumber(power, DrmTransmitterMenu.SET_POWER))
                .bounds(x + w - 62, y + 121, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Change mode"), b -> send(DrmTransmitterMenu.MODE))
                .bounds(x + 16, y + 173, 116, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Change bandwidth"), b -> send(DrmTransmitterMenu.BANDWIDTH))
                .bounds(x + 16, y + 214, 140, 20).build());
        field("service", menu.service(), "Full service label (64 characters)", y + 85, 64);
        field("short", menu.shortName(), "Short label (16 characters)", y + 119, 16);
        field("message", menu.message(), "Text message (256 characters)", y + 153, 256);
        field("stream", menu.stream(), "https:// stream or uploaded audio", y + 187, 1024);
        field("image", menu.image(), "https:// artwork image URL", y + 221, 1024);
        addRenderableWidget(Button.builder(Component.literal("Save service"), b -> commit())
                .bounds(x + w - 133, y + 264, 117, 20).build());
        addRenderableWidget(Button.builder(Component.literal("LIBRARY"), b -> {
            commit();
            minecraft.setScreen(new AudioLibraryScreen(this, menu.containerId, false));
        }).bounds(x + 16, y + 264, 90, 20).build());
        page();
    }
    private EditBox input(int x, int y, int width, String hint, String value, int max) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setMaxLength(max); box.setHint(Component.literal(hint)); box.setTooltip(Tooltip.create(Component.literal(hint)));
        box.setValue(value); addRenderableWidget(box); return box;
    }
    private void field(String key, String value, String hint, int y, int max) {
        fields.put(key, input(leftPos + 126, y, imageWidth - 144, hint, value, max));
        lastSynced.put(key, value);
    }
    private void page() {
        boolean rf = !servicePage;
        frequency.visible = power.visible = rf;
        // Buttons are kept in a stable order between the two tab buttons and the text fields.
        for (var widget : renderables) if (widget instanceof Button button) {
            int relY = button.getY() - topPos;
            if (relY == 86 || relY == 121 || relY == 173 || relY == 214) button.visible = rf;
            if (relY == 264) button.visible = servicePage;
        }
        for (EditBox box : fields.values()) box.visible = servicePage;
        rfTab.active = servicePage; serviceTab.active = !servicePage;
    }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void applyNumber(EditBox box, int base) {
        try { if (!box.getValue().isBlank()) send(base + Integer.parseInt(box.getValue())); }
        catch (NumberFormatException ignored) { }
        box.setFocused(false);
    }
    private void commit() {
        for (var entry : fields.entrySet()) {
            String value = entry.getValue().getValue();
            String saved = lastSynced.get(entry.getKey());
            if (!value.equals(saved)) {
                PacketDistributor.sendToServer(new SetDrmTextPayload(menu.containerId, entry.getKey(), value));
            }
        }
    }
    @Override public void onClose() {
        if (!frequency.getValue().equals(lastFrequency)) applyNumber(frequency, DrmTransmitterMenu.SET_FREQ);
        if (!power.getValue().equals(lastPower)) applyNumber(power, DrmTransmitterMenu.SET_POWER);
        commit();
        super.onClose();
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (focusedInput() && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (frequency.isFocused()) { applyNumber(frequency, DrmTransmitterMenu.SET_FREQ); return true; }
            if (power.isFocused()) { applyNumber(power, DrmTransmitterMenu.SET_POWER); return true; }
            for (EditBox box : fields.values()) if (box.isFocused()) { commit(); box.setFocused(false); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }
    private boolean focusedInput() {
        if (frequency.isFocused() || power.isFocused()) return true;
        for (EditBox box : fields.values()) if (box.isFocused()) return true;
        return false;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean focused = false;
        for (EditBox box : fields.values()) focused |= box.isFocused();
        boolean result = super.mouseClicked(x, y, button);
        if (focused) commit();
        return result;
    }
    @Override protected void containerTick() {
        super.containerTick();
        String serverFrequency = Integer.toString(menu.frequency());
        String serverPower = Integer.toString(menu.power());
        if (!frequency.isFocused() && frequency.getValue().equals(lastFrequency)) frequency.setValue(serverFrequency);
        if (!power.isFocused() && power.getValue().equals(lastPower)) power.setValue(serverPower);
        lastFrequency = serverFrequency; lastPower = serverPower;
        Map<String, String> serverValues = Map.of("service", menu.service(), "short", menu.shortName(),
                "message", menu.message(), "stream", menu.stream(), "image", menu.image());
        for (var entry : fields.entrySet()) {
            String key = entry.getKey();
            EditBox box = entry.getValue();
            String previous = lastSynced.get(key);
            String current = serverValues.get(key);
            if (!box.isFocused() && box.getValue().equals(previous) && !current.equals(previous))
                box.setValue(current);
            lastSynced.put(key, current);
        }
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        TransmitterUi.frame(g, leftPos, topPos, imageWidth, imageHeight);
        TransmitterUi.card(g, leftPos + 12, topPos + 72, imageWidth - 24, 221);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "DRM TRANSMITTER", 14, 12, TransmitterUi.TEXT, false);
        TransmitterUi.status(g, font, menu.hasAntenna() ? "TX antenna linked" : "Link a TX antenna", 16, 292, menu.hasAntenna());
        if (servicePage) {
            String[] labels = {"SERVICE", "SHORT", "MESSAGE", "STREAM", "IMAGE"};
            for (int i = 0; i < labels.length; i++) g.drawString(font, labels[i], 17, 87 + i * 34, TransmitterUi.ACCENT, false);
        } else {
            g.drawString(font, "FREQUENCY", 17, 93, TransmitterUi.ACCENT, false);
            g.drawString(font, "POWER", 17, 128, TransmitterUi.ACCENT, false);
            g.drawString(font, "BAND " + com.radiomod.signal.DrmSignal.band(menu.frequency()), 17, 158, TransmitterUi.MUTED, false);
            g.drawString(font, "MODE " + "ABCD".charAt(Math.max(0, Math.min(3, menu.mode()))), 172, 177, TransmitterUi.TEXT, false);
            g.drawString(font, "BANDWIDTH " + menu.bandwidth() + " kHz", 172, 218, TransmitterUi.TEXT, false);
            g.drawString(font, String.format(Locale.ROOT, "OPUS %d kbps  |  MP3 %d kbps  |  SNR %.0f dB",
                    menu.opusBitrate(), menu.mp3Bitrate(), menu.minimumSnr()), 17, 258, TransmitterUi.MUTED, false);
        }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
    }
}
