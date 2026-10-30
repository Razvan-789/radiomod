package com.radiomod.client.screen;

import com.radiomod.menu.DrmRadioMenu;
import com.radiomod.signal.DrmSignal;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class DrmRadioScreen extends AbstractContainerScreen<DrmRadioMenu> {
    private EditBox frequency;
    private Button mute;
    public DrmRadioScreen(DrmRadioMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 390; imageHeight = 284;
    }
    @Override protected void init() {
        imageWidth = Math.min(390, Math.max(310, width - 12));
        super.init(); int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose()).bounds(x + imageWidth - 30, y + 8, 18, 18).build());
        frequency = new EditBox(font, x + 104, y + 45, imageWidth - 230, 18, Component.literal("Frequency in kHz"));
        frequency.setMaxLength(5); frequency.setFilter(v -> v.matches("[0-9]{0,5}"));
        frequency.setHint(Component.literal("140–30000 kHz"));
        frequency.setTooltip(Tooltip.create(Component.literal("Tune 140–30000 kHz")));
        frequency.setValue(Integer.toString(menu.frequency())); addRenderableWidget(frequency);
        addRenderableWidget(Button.builder(Component.literal("−"), b -> send(DrmRadioMenu.DOWN)).bounds(x + imageWidth - 122, y + 43, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(DrmRadioMenu.UP)).bounds(x + imageWidth - 94, y + 43, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Tune"), b -> tune()).bounds(x + imageWidth - 66, y + 43, 56, 20).build());
        mute = addRenderableWidget(Button.builder(Component.literal("Mute"), b -> send(DrmRadioMenu.MUTE))
                .bounds(x + imageWidth - 95, y + 235, 76, 20).build());
    }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void tune() { try { if (!frequency.getValue().isBlank()) send(DrmRadioMenu.SET_FREQ + Integer.parseInt(frequency.getValue())); }
        catch (NumberFormatException ignored) { } frequency.setFocused(false); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (frequency.isFocused() && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true;
        if (frequency.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) { tune(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override protected void containerTick() {
        super.containerTick(); if (!frequency.isFocused()) frequency.setValue(Integer.toString(menu.frequency()));
        mute.setMessage(Component.literal(menu.muted() ? "Unmute" : "Mute"));
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        TransmitterUi.frame(g, leftPos, topPos, imageWidth, imageHeight);
        TransmitterUi.card(g, leftPos + 12, topPos + 75, imageWidth - 24, 149);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "DRM RADIO", 14, 12, TransmitterUi.TEXT, false);
        g.drawString(font, "TUNE kHz", 17, 49, TransmitterUi.ACCENT, false);
        g.drawString(font, DrmSignal.band(menu.frequency()) + "  " + menu.frequency() + " kHz", 17, 83, TransmitterUi.ACCENT, false);
        if (!menu.connected()) {
            g.drawString(font, "Connect a loop or Yagi antenna with the linking tool", 17, 112, TransmitterUi.BAD, false);
            return;
        }
        if (!menu.decoded()) {
            g.drawString(font, "NO DRM SERVICE", 17, 112, TransmitterUi.BAD, false);
            g.drawString(font, "Try another frequency or a stronger signal", 17, 134, TransmitterUi.MUTED, false);
            return;
        }
        DrmImageCache.draw(g, menu.image(), 17, 105, 72);
        g.drawString(font, clipped(menu.shortName(), imageWidth - 115), 103, 103, TransmitterUi.TEXT, false);
        g.drawString(font, clipped(menu.service(), imageWidth - 115), 103, 119, TransmitterUi.ACCENT, false);
        g.drawString(font, "MODE " + "ABCD".charAt(Math.max(0, Math.min(3, menu.mode()))) + "  "
                + menu.bandwidth() + " kHz  Opus " + menu.opusBitrate() + "k", 103, 135, TransmitterUi.MUTED, false);
        g.drawString(font, "MP3 output " + menu.mp3Bitrate() + " kbps", 103, 147, TransmitterUi.MUTED, false);
        g.drawString(font, String.format(Locale.ROOT, "SNR %.1f dB  |  RF %.1f dBuV", menu.snr(), menu.signal()),
                103, 159, TransmitterUi.TEXT, false);
        g.drawString(font, "TX POWER " + menu.power() + " W", 103, 171, TransmitterUi.MUTED, false);
        g.drawString(font, "TEXT", 17, 188, TransmitterUi.ACCENT, false);
        var lines = font.split(Component.literal(menu.message()), imageWidth - 125);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), 52, 188 + i * 12, TransmitterUi.TEXT, false);
        TransmitterUi.status(g, font, "Loop linked / DRM decoded", 17, 243, true);
    }
    private String clipped(String text, int maxWidth) { return font.plainSubstrByWidth(text, maxWidth); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
    }
}
