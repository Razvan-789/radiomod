package com.radiomod.client.screen;

import com.radiomod.item.RadioItem;
import com.radiomod.menu.RadioMenu;
import com.radiomod.signal.DabChannel;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact front panel inspired by the XDR-S41D's speaker, blue LCD and controls. */
public class SonyRadioScreen extends AbstractContainerScreen<RadioMenu> {
    private static final int WIDTH = 470, HEIGHT = 260;
    private int view; // 0 player, 1 information, 2 DAB list, 3 scanning
    private int listScroll;
    private Button modeButton;
    private Button muteButton;

    public SonyRadioScreen(RadioMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = 1000;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("MENU"), b -> view = dab() ? 2 : view == 1 ? 0 : 1)
                .bounds(x + 258, y + 174, 57, 18).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> view = 0)
                .bounds(x + 258, y + 198, 57, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> step(1))
                .bounds(x + 322, y + 174, 55, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> step(-1))
                .bounds(x + 322, y + 198, 55, 18).build());
        modeButton = addRenderableWidget(Button.builder(Component.literal("DAB/FM"), b -> changeMode())
                .bounds(x + 384, y + 174, 68, 18).build());
        addRenderableWidget(Button.builder(Component.literal("AUTO TUNE"), b -> autoTune())
                .bounds(x + 384, y + 198, 68, 18).build());
        muteButton = addRenderableWidget(Button.builder(Component.literal("VOL"), b -> toggleMute())
                .bounds(x + 398, y + 228, 55, 20).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(x + WIDTH - 21, y + 5, 16, 16).build());
    }

    private boolean dab() {
        return minecraft != null && minecraft.player != null
                && RadioItem.getMode(minecraft.player) == RadioItem.MODE_DAB;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void changeMode() {
        if (minecraft == null || minecraft.player == null) return;
        int mode = dab() ? RadioItem.MODE_FM : RadioItem.MODE_DAB;
        RadioItem.setMode(minecraft.player, mode);
        send(mode == RadioItem.MODE_DAB ? RadioMenu.BUTTON_MODE_DAB : RadioMenu.BUTTON_MODE_FM);
        view = 0;
    }

    private void step(int direction) {
        if (minecraft == null || minecraft.player == null) return;
        if (dab() && view == 2 && !menu.getSonyStations().isEmpty()) {
            int current = selectedIndex();
            select(Math.floorMod(current + direction, menu.getSonyStations().size()));
            return;
        }
        if (dab()) {
            int index = Math.max(0, Math.min(com.radiomod.signal.DabChannel.size() - 1,
                    menu.getDabChannelIndex() + direction));
            RadioItem.setDabChannelIndex(minecraft.player, index);
            RadioItem.setDabStation(minecraft.player, "", "");
            send(direction > 0 ? RadioMenu.BUTTON_DAB_CHANNEL_UP : RadioMenu.BUTTON_DAB_CHANNEL_DOWN);
        } else {
            int frequency = menu.getFrequencyKhz() + direction * 100;
            RadioItem.setFrequencyKhz(minecraft.player, frequency);
            send(RadioMenu.encodeSetFrequencyButtonId(frequency));
        }
    }

    private void toggleMute() {
        if (minecraft != null && minecraft.player != null)
            RadioItem.setMuted(minecraft.player, !RadioItem.isMuted(minecraft.player));
    }

    private void autoTune() {
        if (dab()) {
            view = 3;
            listScroll = 0;
            send(RadioMenu.BUTTON_SONY_SCAN);
        } else send(RadioMenu.BUTTON_AUTO_TUNE);
    }

    private int selectedIndex() {
        if (minecraft == null || minecraft.player == null) return -1;
        for (int i = 0; i < menu.getSonyStations().size(); i++) {
            var station = menu.getSonyStations().get(i);
            if (station.channelIndex() == RadioItem.getDabChannelIndex(minecraft.player)
                    && station.mux().equals(RadioItem.getDabStationMux(minecraft.player))
                    && station.sid().equals(RadioItem.getDabStationSid(minecraft.player))) return i;
        }
        return -1;
    }

    private void select(int index) {
        if (!menu.hasSonyScan() || index < 0 || index >= menu.getSonyStations().size()) return;
        var station = menu.getSonyStations().get(index);
        send(RadioMenu.encodeSonySelectButtonId(index));
        if (minecraft != null && minecraft.player != null) {
            RadioItem.setMode(minecraft.player, RadioItem.MODE_DAB);
            RadioItem.setDabChannelIndex(minecraft.player, station.channelIndex());
            RadioItem.setDabStation(minecraft.player, station.mux(), station.sid());
        }
        view = 0;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dab() && view == 2 && menu.hasSonyScan()) {
            int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
            if (x >= 263 && x < 447 && y >= 75 && y < 135) {
                select(listScroll + (y - 75) / 20);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dab() && view == 2 && mouseX >= leftPos + 260 && mouseX < leftPos + 450
                && mouseY >= topPos + 72 && mouseY < topPos + 150) {
            listScroll = Math.max(0, Math.min(Math.max(0, menu.getSonyStations().size() - 3),
                    listScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        boolean black = menu.isBlackSonyStyle();
        int edge = black ? 0xFF090B0E : 0xFFBFC4C8;
        int frame = black ? 0xFF24292F : 0xFFF0F1F0;
        int grille = black ? 0xFF111519 : 0xFFD6D9DA;
        int hole = black ? 0xFF050709 : 0xFF343C40;
        g.fill(x, y, x + WIDTH, y + HEIGHT, edge);
        g.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, frame);
        g.fill(x + 15, y + 35, x + 240, y + 225, grille);
        for (int row = 0; row < 23; row++) {
            for (int col = 0; col < 28; col++) {
                int gx = x + 20 + col * 8 + (row % 2) * 3;
                int gy = y + 41 + row * 8;
                if (gx + 3 < x + 238 && gy + 3 < y + 223)
                    g.fill(gx, gy, gx + 3, gy + 3, hole);
            }
        }
        g.fill(x + 254, y + 37, x + 456, y + 159, black ? 0xFF080B10 : 0xFFA1A8AD);
        g.fill(x + 258, y + 41, x + 452, y + 155, black ? 0xFF182A36 : 0xFFC8E5F4);
        g.fill(x + 260, y + 43, x + 450, y + 153, black ? 0xFF203849 : 0xFFD5EEFC);
        g.fill(x + 260, y + 68, x + 450, y + 69, black ? 0xFF54778A : 0xFF8CB4C8);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        boolean black = menu.isBlackSonyStyle();
        int caseText = black ? 0xFFD6DADE : 0xFF737A7E;
        int lcdText = black ? 0xFFD1EDFA : 0xFF1C3A4D;
        int lcdDim = black ? 0xFF88AABA : 0xFF52788D;
        g.drawString(font, "SONY", 260, 16, caseText, false);
        g.drawString(font, "XDR-S41D", 315, 17, caseText, false);
        g.drawString(font, "DAB   DAB+   RDS", 259, 240, caseText, false);
        g.drawString(font, dab() ? "DAB+  " + menu.getDabChannelLabel() : "FM", 265, 49, lcdText, false);
        if (minecraft != null && minecraft.level != null
                && (dab() ? menu.getDabMscState() > 0 : menu.isRdsCtEnabled())) {
            long day = Math.floorMod(minecraft.level.getDayTime(), 24000L);
            String clock = String.format(Locale.ROOT, "%02d:%02d",
                    (day / 1000 + 6) % 24, (day % 1000) * 60 / 1000);
            g.drawString(font, clock, 407, 49, lcdText, false);
        }
        if (dab() && (view == 3 || menu.isSonyScanning())) {
            if (!menu.isSonyScanning() && view == 3) view = 2;
            else {
                g.drawString(font, "Full Scan", 266, 78, lcdText, false);
                g.drawString(font, "Scanning " + DabChannel.byIndex(menu.getSonyScanChannel()).label() + "...", 266, 97, lcdText, false);
                g.drawString(font, "Stations: " + menu.getSonyScanCount(), 266, 117, lcdDim, false);
                g.fill(265, 138, 444, 144, lcdDim);
                g.fill(266, 139, 266 + menu.getSonyScanChannel() * 177 / DabChannel.size(), 143, lcdText);
                return;
            }
        }
        if (dab() && view == 2) {
            if (!menu.hasSonyScan()) {
                g.drawString(font, "Full Scan required", 266, 86, lcdText, false);
                g.drawString(font, "Press AUTO TUNE", 266, 108, lcdDim, false);
            } else if (menu.getSonyStations().isEmpty()) {
                g.drawString(font, "No DAB services found", 266, 96, lcdText, false);
            } else {
                listScroll = Math.min(listScroll, Math.max(0, menu.getSonyStations().size() - 3));
                for (int row = 0; row < 3 && row + listScroll < menu.getSonyStations().size(); row++) {
                    int index = row + listScroll;
                    var station = menu.getSonyStations().get(index);
                    int y = 76 + row * 20;
                    if (index == selectedIndex()) g.fill(263, y - 2, 446, y + 16,
                            black ? 0xFF365A6B : 0xFFA4CDDE);
                    g.drawString(font, font.plainSubstrByWidth(station.label(), 135), 266, y, lcdText, false);
                    g.drawString(font, DabChannel.byIndex(station.channelIndex()).label(), 418, y, lcdDim, false);
                }
                g.drawString(font, (listScroll + 1) + "-" + Math.min(listScroll + 3, menu.getSonyStations().size())
                        + "/" + menu.getSonyStations().size(), 265, 139, lcdDim, false);
            }
            return;
        }
        if (view == 1) {
            g.drawString(font, dab() ? "DIGITAL RADIO" : "FM RADIO", 266, 78, lcdText, false);
            g.drawString(font, dab() ? String.format(Locale.ROOT, "%.1f dBuV  SNR %.1f", menu.getDabSignalDbuv(), menu.getDabSnrDb())
                    : String.format(Locale.ROOT, "%.1f dBuV  SNR %.1f", menu.getSignalDbuv(), menu.getSnrDb()),
                    266, 98, lcdText, false);
            g.drawString(font, dab() ? "FIC " + state(menu.getDabFicState()) + "  MSC " + state(menu.getDabMscState())
                    : "RDS BER " + menu.getRdsBerPercent() + "%", 266, 119, lcdDim, false);
        } else if (dab()) {
            String label = menu.getDabLabel().isBlank() ? "No DAB service" : menu.getDabLabel();
            g.drawString(font, font.plainSubstrByWidth(label, 174), 266, 78, lcdText, false);
            String dls = menu.getDabDls().isBlank() ? "Channel " + menu.getDabChannelLabel() : menu.getDabDls();
            g.drawString(font, font.plainSubstrByWidth(dls, 174), 266, 99, lcdDim, false);
            g.drawString(font, menu.getDabMscState() > 0 ? "DAB LOCK" : "NO SIGNAL", 266, 126, lcdText, false);
        } else {
            String ps = menu.isRdsDecoding() ? menu.getRdsPs().trim() : "";
            String title = ps.isBlank() || ps.equals("--------")
                    ? String.format(Locale.ROOT, "%.2f MHz", menu.getFrequencyKhz() / 1000.0) : ps;
            g.drawString(font, font.plainSubstrByWidth(title, 174), 266, 78, lcdText, false);
            String rt = menu.getRdsRt().isBlank() ? String.format(Locale.ROOT, "%.2f MHz", menu.getFrequencyKhz() / 1000.0) : menu.getRdsRt();
            g.drawString(font, font.plainSubstrByWidth(rt, 174), 266, 99, lcdDim, false);
            if (menu.isRdsDecoding()) g.drawString(font, "RDS", 266, 126, lcdText, false);
        }
        int bars = (int) Math.max(0, Math.min(5, Math.round(((dab() ? menu.getDabSignalDbuv() : menu.getSignalDbuv()) + 5) / 13.0)));
        for (int i = 0; i < 5; i++) g.fill(408 + i * 8, 143 - i * 2, 413 + i * 8, 148,
                i < bars ? lcdText : lcdDim);
        if (muteButton != null && minecraft != null && minecraft.player != null)
            muteButton.setMessage(Component.literal(RadioItem.isMuted(minecraft.player) ? "MUTED" : "VOL"));
        if (modeButton != null) modeButton.setMessage(Component.literal(dab() ? "FM MODE" : "DAB MODE"));
    }

    private static String state(int value) { return value > 0 ? "OK" : "--"; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g, mouseX, mouseY, tick);
        super.render(g, mouseX, mouseY, tick);
        renderTooltip(g, mouseX, mouseY);
    }
}
