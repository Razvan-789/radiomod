package com.radiomod.client.screen;

import com.radiomod.menu.SignalMeterMenu;
import com.radiomod.network.SyncSignalMeterPayload;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.SignalMeterState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class SignalMeterScreen extends AbstractContainerScreen<SignalMeterMenu> {
    private EditBox fmInput;
    private Button modeButton;
    private Button pinButton;
    private Button tuneButton;
    private int scroll;

    public SignalMeterScreen(SignalMeterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 440;
        imageHeight = 300;
    }

    @Override protected void init() {
        imageWidth = Math.min(440, Math.max(320, width - 12));
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(x + imageWidth - 27, y + 8, 18, 18).build());
        modeButton = addRenderableWidget(Button.builder(Component.literal("MODE: FM"), button -> { click(0); scroll = 0; })
                .bounds(x + 12, y + 33, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { click(1); scroll = 0; })
                .bounds(x + 98, y + 33, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { click(2); scroll = 0; })
                .bounds(x + 126, y + 33, 24, 20).build());
        fmInput = new EditBox(font, x + 157, y + 35, Math.min(110, imageWidth - 283), 16,
                Component.literal("FM frequency"));
        fmInput.setHint(Component.literal("MHz, e.g. 101.7"));
        fmInput.setMaxLength(7);
        fmInput.setFilter(value -> value.matches("[0-9.]*"));
        addRenderableWidget(fmInput);
        tuneButton = addRenderableWidget(Button.builder(Component.literal("TUNE"), button -> tuneFm())
                .bounds(x + imageWidth - 120, y + 33, 50, 20).build());
        pinButton = addRenderableWidget(Button.builder(Component.literal("PIN"), button -> click(3))
                .bounds(x + imageWidth - 62, y + 33, 50, 20).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void tuneFm() {
        try {
            int khz = (int) Math.round(Double.parseDouble(fmInput.getValue()) * 1000.0);
            if (khz >= 65000 && khz <= 108000) click(1_000_000 + khz);
        } catch (NumberFormatException ignored) { }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fmInput.isFocused() && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        if (fmInput.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            tuneFm(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF17242B);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF233740);
        g.fill(x + 10, y + 60, x + imageWidth - 10, y + imageHeight - 10, 0xFF10212A);
        g.fill(x + 10, y + 60, x + imageWidth - 10, y + 62, 0xFF51C6C1);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "SIGNAL METER", 13, 13, 0xFFE8F6F6, false);
        SyncSignalMeterPayload p = SignalMeterState.freshClient();
        if (p == null) {
            g.drawString(font, "Waiting for signal sample...", 20, 75, 0xFF9BB5BE, false);
            return;
        }
        modeButton.setMessage(Component.literal(p.dab() ? "MODE: DAB" : "MODE: FM"));
        pinButton.setMessage(Component.literal(p.pinned() ? "UNPIN" : "PIN"));
        fmInput.visible = !p.dab();
        tuneButton.visible = !p.dab();
        g.drawString(font, p.dab() ? "DAB " + DabChannel.byIndex(p.channelIndex()).label()
                + String.format(Locale.ROOT, "  %.3f MHz", DabChannel.byIndex(p.channelIndex()).frequencyKhz() / 1000.0)
                : String.format(Locale.ROOT, "FM %.2f MHz", p.frequencyKhz() / 1000.0),
                20, 72, 0xFF78E2D7, false);
        g.drawString(font, p.active() ? "SIGNAL RECEIVED" : "NO SIGNAL", imageWidth - 115, 72,
                p.active() ? 0xFF8BE4A9 : 0xFFF2A9A0, false);
        if (p.active()) {
            g.drawString(font, String.format(Locale.ROOT, "Strength  %.1f dBuV", p.signalTenths() / 10.0),
                    20, 91, 0xFFF1F8F8, false);
            g.drawString(font, String.format(Locale.ROOT, "SNR  %.1f dB", p.snrTenths() / 10.0),
                    imageWidth / 2, 91, 0xFFF1F8F8, false);
        }
        if (p.dab()) renderDab(g, p); else renderFm(g, p);
        g.drawString(font, "Receive only • mouse wheel scrolls the list", 20, imageHeight - 24, 0xFF829EA6, false);
    }

    private void renderFm(GuiGraphics g, SyncSignalMeterPayload p) {
        g.drawString(font, "RDS BER  " + (p.active() ? p.berPercent() + "%" : "--"), 20, 119, 0xFF78E2D7, false);
        g.drawString(font, "PI  " + (p.pi().isBlank() ? "----" : p.pi()), imageWidth / 2, 119, 0xFFF1F8F8, false);
        g.drawString(font, "PS  " + (p.ps().isBlank() ? "--------" : p.ps()), 20, 141, 0xFFF1F8F8, false);
        g.drawString(font, "Current group  " + (p.currentGroup().isBlank() ? "--" : p.currentGroup()), 20, 165, 0xFFF1F8F8, false);
        g.drawString(font, "Decoded RDS groups", 20, 189, 0xFF78E2D7, false);
        List<String> groups = wrap(p.decodedGroups(), imageWidth - 42);
        for (int i = 0; i < Math.min(4, groups.size()); i++)
            g.drawString(font, groups.get(i), 20, 207 + 13 * i, 0xFFD4E8EB, false);
    }

    private void renderDab(GuiGraphics g, SyncSignalMeterPayload p) {
        g.drawString(font, "MUX  " + (p.mux().isBlank() ? "--" : p.mux()), 20, 116, 0xFFF1F8F8, false);
        g.drawString(font, "Protection  " + (p.correction().isBlank() ? "--" : p.correction()), 20, 137, 0xFF78E2D7, false);
        g.drawString(font, "FIC " + status(p.fic()) + "     MSC " + status(p.msc())
                + "     FS " + status(p.fs()), 20, 156, 0xFFF1F8F8, false);
        int divider = imageWidth / 2;
        g.fill(divider, 181, divider + 1, imageHeight - 34, 0xFF466572);
        g.drawString(font, "STATIONS (" + p.stations().size() + ")", 20, 184, 0xFF78E2D7, false);
        g.drawString(font, "TII (" + p.tii().size() + " TX)", divider + 12, 184, 0xFF78E2D7, false);
        int rows = Math.max(p.stations().size(), p.tii().size());
        int maxRows = 6;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - maxRows)));
        for (int i = 0; i < maxRows; i++) {
            int index = scroll + i, y = 203 + i * 12;
            if (index < p.stations().size())
                g.drawString(font, font.plainSubstrByWidth(p.stations().get(index), divider - 34),
                        20, y, 0xFFD4E8EB, false);
            if (index < p.tii().size()) {
                SyncSignalMeterPayload.TiiReading reading = p.tii().get(index);
                g.drawString(font, String.format(Locale.ROOT, "%s   %.1f dB", reading.code(), reading.relativeTenths() / 10.0),
                        divider + 12, y, 0xFFD4E8EB, false);
            }
        }
    }

    private String status(int value) { return value == 2 ? "OK" : value == 1 ? "ERR" : "LOST"; }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isBlank()) { lines.add("No groups decoded"); return lines; }
        StringBuilder line = new StringBuilder();
        for (String token : text.split(" ")) {
            if (!line.isEmpty() && font.width(line + " " + token) > maxWidth) {
                lines.add(line.toString()); line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(token);
        }
        lines.add(line.toString());
        return lines;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
