package com.radiomod.client.screen;

import com.radiomod.item.RadioItem;
import com.radiomod.menu.Dx3Menu;
import com.radiomod.network.Dx3Station;
import com.radiomod.network.RequestDx3CatalogPayload;
import com.radiomod.network.SelectDx3StationPayload;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.network.SyncTechnisatStatusPayload;
import com.radiomod.network.TechnisatActionPayload;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.DrmSignal;
import com.radiomod.signal.PtyNames;
import com.radiomod.signal.TechnisatPinnedState;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dark tabletop radio front panel with FM tuning and separate DAB/DRM scan lists. */
public class TechnisatScreen extends AbstractContainerScreen<Dx3Menu> {
    private static final int WIDTH = 500, HEIGHT = 310;
    private static final int CASE = 0xFF17191D, LCD = 0xFF071431;
    private static final int BLUE = 0xFF2362C2, TEXT = 0xFFE9F1FF, DIM = 0xFF93A7C9;
    private int view; // player, service list, scan, information
    private int listScroll;
    private long scanStarted;
    private Button scanButton;
    private Button presetButton;
    private Button pinButton;
    private Button seekDownButton;
    private Button seekUpButton;

    public TechnisatScreen(Dx3Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH; imageHeight = HEIGHT;
        inventoryLabelY = 1000;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("MODE"), b -> {
            PacketDistributor.sendToServer(new TechnisatActionPayload(menu.containerId, 0));
            view = 0;
        }).bounds(x + 263, y + 231, 58, 22).build());
        addRenderableWidget(Button.builder(Component.literal("INFO"), b -> view = view == 3 ? 0 : 3)
                .bounds(x + 326, y + 231, 58, 22).build());
        pinButton = addRenderableWidget(Button.builder(Component.literal("PIN"), b -> {
            if (menu.isReceiver() && menu.receiverPos() != null)
                TechnisatPinnedState.toggle(menu.receiverPos());
            else if (minecraft != null && minecraft.player != null)
                RadioItem.setDx3Pinned(minecraft.player, !RadioItem.isDx3Pinned(minecraft.player));
        }).bounds(x + 389, y + 231, 58, 22).build());
        presetButton = addRenderableWidget(Button.builder(Component.literal("PRESET"), b -> {
            if (dab() || drm()) view = view == 1 ? 0 : 1;
        }).bounds(x + 210, y + 265, 85, 22).build());
        addRenderableWidget(Button.builder(Component.literal("|◀"), b -> step(-1))
                .bounds(x + 300, y + 265, 55, 22).build());
        addRenderableWidget(Button.builder(Component.literal("▶|"), b -> step(1))
                .bounds(x + 360, y + 265, 55, 22).build());
        seekDownButton = addRenderableWidget(Button.builder(Component.literal("SEEK ◀"), b -> seek(-1))
                .bounds(x + 20, y + 272, 79, 20).build());
        seekUpButton = addRenderableWidget(Button.builder(Component.literal("SEEK ▶"), b -> seek(1))
                .bounds(x + 105, y + 272, 79, 20).build());
        scanButton = addRenderableWidget(Button.builder(Component.literal("FULL SCAN"), b -> fullScan())
                .bounds(x + 20, y + 248, 100, 22).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(x + WIDTH - 24, y + 5, 18, 18).build());
        updateControls();
    }

    private boolean dab() {
        SyncTechnisatStatusPayload state = menu.getTechnisatStatus();
        return state != null && state.dab();
    }

    private boolean drm() {
        SyncTechnisatStatusPayload state = menu.getTechnisatStatus();
        return state != null && state.drm();
    }

    private void fullScan() {
        if (!dab() && !drm()) return;
        view = 2;
        scanStarted = System.currentTimeMillis();
        listScroll = 0;
        PacketDistributor.sendToServer(new RequestDx3CatalogPayload(menu.containerId));
    }

    private void toggleMute() {
        PacketDistributor.sendToServer(new TechnisatActionPayload(menu.containerId, 3));
    }

    private void seek(int direction) {
        PacketDistributor.sendToServer(new TechnisatActionPayload(menu.containerId, direction < 0 ? 4 : 5));
    }

    private void step(int direction) {
        if (!dab() && (!drm() || view != 1)) {
            PacketDistributor.sendToServer(new TechnisatActionPayload(menu.containerId, direction < 0 ? 1 : 2));
            return;
        }
        List<Dx3Station> stations = menu.getStations();
        if (stations.isEmpty() || !scanned()) { view = 1; return; }
        int selected = selectedIndex();
        int next = Math.floorMod(selected + direction, stations.size());
        select(next);
    }

    private boolean scanned() {
        SyncTechnisatStatusPayload status = menu.getTechnisatStatus();
        return status != null && status.scanned();
    }

    private int selectedIndex() {
        SyncTechnisatStatusPayload state = menu.getTechnisatStatus();
        if (state == null || (!state.dab() && !state.drm())) return -1;
        for (int i = 0; i < menu.getStations().size(); i++) {
            Dx3Station entry = menu.getStations().get(i);
            if (state.drm() && entry.tuning() == state.frequencyKhz()) return i;
            if (menu.isReceiver()) {
                var receiver = menu.getReceiverState();
                if (receiver != null && receiver.sid().equals(entry.sid())
                        && receiver.mux().equals(entry.mux())) return i;
            } else if (minecraft != null && minecraft.player != null
                    && RadioItem.getDabStationSid(minecraft.player).equals(entry.sid())
                    && RadioItem.getDabStationMux(minecraft.player).equals(entry.mux())) return i;
        }
        return -1;
    }

    private void select(int index) {
        if (!scanned() || index < 0 || index >= menu.getStations().size()) return;
        Dx3Station station = menu.getStations().get(index);
        if (station.dab() != dab() || (!dab() && !drm())) return;
        PacketDistributor.sendToServer(new SelectDx3StationPayload(menu.containerId, index,
                station.dab(), station.tuning(), station.sid(), station.mux()));
        if (!menu.isReceiver() && minecraft != null && minecraft.player != null) {
            RadioItem.setDx3Selected(minecraft.player, true);
            if (station.dab()) {
                RadioItem.setMode(minecraft.player, RadioItem.MODE_DAB);
                RadioItem.setDabChannelIndex(minecraft.player, station.tuning());
                RadioItem.setDabStation(minecraft.player, station.mux(), station.sid());
            } else {
                RadioItem.setMode(minecraft.player, RadioItem.MODE_DRM);
                com.radiomod.item.TechnisatRadioItem.setDrmFrequency(minecraft.player, station.tuning());
            }
        }
        view = 0;
    }

    private void updateControls() {
        if (scanButton != null) scanButton.visible = dab() || drm();
        if (presetButton != null) presetButton.active = dab() || drm();
        if (seekDownButton != null) seekDownButton.active = !dab() && !drm();
        if (seekUpButton != null) seekUpButton.active = !dab() && !drm();
        if (pinButton != null && minecraft != null && minecraft.player != null)
            pinButton.setMessage(Component.literal(menu.isReceiver() && menu.receiverPos() != null
                    ? TechnisatPinnedState.isPinned(menu.receiverPos()) ? "UNPIN" : "PIN"
                    : RadioItem.isDx3Pinned(minecraft.player) ? "UNPIN" : "PIN"));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 428 && mouseX < leftPos + 487
                && mouseY >= topPos + 257 && mouseY < topPos + 296) {
            toggleMute(); return true;
        }
        if (button == 0 && (dab() || drm()) && view == 1 && scanned()) {
            int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
            if (x >= 217 && x < 467 && y >= 76 && y < 196) {
                int index = listScroll + (y - 76) / 29;
                if (index < menu.getStations().size()) { select(index); return true; }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if ((dab() || drm()) && view == 1 && mouseX >= leftPos + 211 && mouseX < leftPos + 475
                && mouseY >= topPos + 72 && mouseY < topPos + 204) {
            listScroll = Math.max(0, Math.min(Math.max(0, menu.getStations().size() - 4),
                    listScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF070809);
        g.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, CASE);
        g.fill(x + 202, y + 28, x + 480, y + 221, 0xFF02050B);
        g.fill(x + 208, y + 34, x + 474, y + 215, LCD);
        g.fill(x + 211, y + 37, x + 471, y + 61, BLUE);
        g.fill(x + 211, y + 198, x + 471, y + 212, 0xFF101F43);
        for (int row = 0; row < 16; row++) for (int column = 0; column < 13; column++) {
            int gx = x + 20 + column * 13, gy = y + 30 + row * 13;
            g.fill(gx, gy, gx + 5, gy + 5, 0xFF080A0D);
        }
        g.fill(x + 426, y + 258, x + 488, y + 296, 0xFF090A0D);
        g.fill(x + 429, y + 261, x + 485, y + 293, 0xFF292B30);
        g.fill(x + 436, y + 257, x + 478, y + 297, 0xFF080A0D);
        g.fill(x + 440, y + 261, x + 474, y + 293, 0xFF303238);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SyncTechnisatStatusPayload state = menu.getTechnisatStatus();
        SyncDx3PlayerPayload info = menu.getPlayerInfo();
        g.drawString(font, "TechniSat", 18, 286, TEXT, false);
        g.drawString(font, state != null && state.muted() ? "M" : "VOL", 446, 275, DIM, false);
        g.drawString(font, state == null ? "RADIO" : state.dab() ? "DAB+" : state.drm() ? "DRM" : "FM", 216, 43, TEXT, false);
        if (info.ct() && !info.time().isBlank()) g.drawString(font, info.time(), 406, 43, TEXT, false);
        if (state != null && !state.dab() && !state.drm() && info.rds())
            g.drawString(font, "RDS", 219, 202, TEXT, false);
        for (int i = 0; i < 5; i++) {
            int height = 3 + i * 2;
            g.fill(432 + i * 7, 209 - height, 437 + i * 7, 209,
                    i < info.signalBars() ? 0xFFD4E2FF : 0xFF344568);
        }
        if (state == null) {
            g.drawCenteredString(font, "Connecting...", 342, 125, DIM);
        } else if (state.dab() || state.drm()) {
            if (view == 2 && (state.scanning() || System.currentTimeMillis() - scanStarted < (state.drm() ? 1300 : 300)))
                renderScan(g, state);
            else {
                if (view == 2) view = 1;
                if (view == 1) renderServiceList(g, state.drm());
                else if (view == 3) renderInfo(g, info, state);
                else renderPlayer(g, info, state);
            }
        } else {
            if (view == 3) renderInfo(g, info, state);
            else renderPlayer(g, info, state);
        }
        updateControls();
    }

    private void renderScan(GuiGraphics g, SyncTechnisatStatusPayload state) {
        long elapsed = System.currentTimeMillis() - scanStarted;
        g.drawString(font, "Full Scan", 221, 73, 0xFF69C9FF, false);
        g.drawString(font, state.dab() ? "Scanning " + DabChannel.byIndex(state.scanChannelIndex()).label()
                + "..." : "Scanning...", 247, 104, TEXT, false);
        g.drawString(font, "Stations: " + (state.dab() ? state.scanStationCount() : menu.getStations().size()),
                247, 128, TEXT, false);
        g.fill(246, 151, 437, 162, 0xFF2A83D5);
        g.fill(248, 153, 435, 160, LCD);
        int progress = state.dab() ? Math.min(185, state.scanChannelIndex() * 185 / DabChannel.size())
                : (int) Math.min(185, Math.max(0, elapsed * 185 / 1300));
        g.fill(249, 154, 249 + progress, 159, 0xFF299AE8);
    }

    private void renderServiceList(GuiGraphics g, boolean drm) {
        if (!scanned()) {
            g.drawCenteredString(font, "Full Scan required", 341, 109, TEXT);
            g.drawCenteredString(font, drm ? "Search for DRM services" : "Search for DAB services", 341, 130, DIM);
            return;
        }
        List<Dx3Station> entries = menu.getStations();
        if (entries.isEmpty()) {
            g.drawCenteredString(font, "No scanned services receivable", 341, 109, TEXT);
            g.drawCenteredString(font, "Full Scan finds new channels", 341, 130, DIM);
            return;
        }
        listScroll = Math.min(listScroll, Math.max(0, entries.size() - 4));
        for (int row = 0; row < 4 && row + listScroll < entries.size(); row++) {
            int index = row + listScroll;
            Dx3Station station = entries.get(index);
            int y = 76 + row * 29;
            g.fill(216, y, 468, y + 27, index == selectedIndex() ? 0xFF1D4E9C : 0xFF102245);
            if (station.logoPixels() != null) drawLogo(g, station.logoPixels(), 221, y + 4, 20);
            g.drawString(font, fit(station.label(), 192), 248, y + 4, TEXT, false);
            g.drawString(font, fit(station.detail(), 190), 248, y + 16, DIM, false);
        }
        g.drawString(font, (listScroll + 1) + "–" + Math.min(listScroll + 4, entries.size())
                + " / " + entries.size(), 218, 202, DIM, false);
    }

    private void renderPlayer(GuiGraphics g, SyncDx3PlayerPayload info, SyncTechnisatStatusPayload state) {
        boolean dab = state.dab();
        String title = dab ? info.active() && !info.ps().isBlank() ? info.ps() : "DAB Radio"
                : state.drm() ? info.active() && !info.longPs().isBlank() ? info.longPs()
                        : state.frequencyKhz() + " kHz"
                : info.active() && !info.ps().isBlank() ? info.ps()
                        : String.format(Locale.ROOT, "%.2f MHz", state.frequencyKhz() / 1000.0);
        g.drawString(font, fit(title, 244), 218, 70, 0xFFFFC329, false);
        g.fill(217, 92, 285, 160, 0xFFFEFEFE);
        int[] logo = dab ? menu.getPlayerLogo() : null;
        if (logo == null && dab) {
            int selected = selectedIndex();
            if (selected >= 0) logo = menu.getStations().get(selected).logoPixels();
        }
        if (state.drm() && !state.artworkUrl().isBlank())
            DrmImageCache.draw(g, state.artworkUrl(), 219, 94, 64);
        else if (logo != null) drawLogo(g, logo, 219, 94, 64);
        else {
            g.drawCenteredString(font, dab ? "DAB+" : state.drm() ? "DRM" : "FM", 251, 120, BLUE);
            g.drawCenteredString(font, "◉", 251, 136, BLUE);
        }
        String subtitle = info.active() ? info.rt() : "";
        if (subtitle.isBlank()) subtitle = dab || state.drm() ? "No programme text" : "No RadioText";
        for (int i = 0; i < 3; i++) {
            int start = i * 30;
            if (start >= subtitle.length()) break;
            g.drawString(font, fit(subtitle.substring(start, Math.min(subtitle.length(), start + 30)), 172),
                    293, 98 + i * 16, TEXT, false);
        }
        if (dab) {
            int selected = selectedIndex();
            if (selected >= 0) {
                Dx3Station station = menu.getStations().get(selected);
                g.drawString(font, fit(station.mux() + " · " + DabChannel.byIndex(station.tuning()).label(), 247),
                        217, 178, DIM, false);
            } else if (!scanned()) g.drawString(font, "List needs a Full Scan", 217, 178, DIM, false);
        } else if (state.drm()) g.drawString(font,
                DrmSignal.band(state.frequencyKhz()) + "  " + state.frequencyKhz() + " kHz", 217, 178, DIM, false);
        else g.drawString(font, info.rds() && !info.ps().isBlank()
                ? "PTY: " + PtyNames.name(info.pty())
                : String.format(Locale.ROOT, "%.2f MHz", state.frequencyKhz() / 1000.0),
                217, 178, DIM, false);
    }

    private void renderInfo(GuiGraphics g, SyncDx3PlayerPayload info, SyncTechnisatStatusPayload state) {
        g.drawString(font, state.dab() ? "DAB+ INFORMATION" : state.drm() ? "DRM INFORMATION" : "FM INFORMATION",
                220, 72, 0xFF69C9FF, false);
        String tuning = state.dab() ? DabChannel.byIndex(state.channelIndex()).label()
                : state.drm() ? state.frequencyKhz() + " kHz"
                : String.format(Locale.ROOT, "%.2f MHz", state.frequencyKhz() / 1000.0);
        g.drawString(font, "Tuning: " + tuning, 220, 95, TEXT, false);
        g.drawString(font, "PTY: " + PtyNames.name(info.pty()), 220, 112, TEXT, false);
        g.drawString(font, "Signal: " + info.signalBars() + "/5", 220, 129, TEXT, false);
        if (state.dab() || state.drm()) {
            g.drawString(font, "Codec: " + fit(info.codec(), 190), 220, 146, TEXT, false);
            g.drawString(font, "Bitrate: " + info.bitrateKbps() + " kbps", 220, 163, TEXT, false);
        } else g.drawString(font, info.active() && !info.ps().isBlank() ? "RDS: " + info.ps() : "RDS: unavailable",
                220, 146, TEXT, false);
        g.drawString(font, "INFO to return", 220, 185, DIM, false);
    }

    private String fit(String text, int pixels) { return font.plainSubstrByWidth(text, pixels); }

    private static void drawLogo(GuiGraphics g, int[] pixels, int x, int y, int size) {
        int source = pixels.length == 64 * 64 ? 64 : pixels.length == 16 * 16 ? 16 : 0;
        if (source == 0) return;
        for (int py = 0; py < source; py++) for (int px = 0; px < source; px++) {
            int color = pixels[py * source + px];
            if ((color >>> 24) == 0) continue;
            int x0 = x + px * size / source, x1 = x + (px + 1) * size / source;
            int y0 = y + py * size / source, y1 = y + (py + 1) * size / source;
            if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
