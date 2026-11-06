package com.radiomod.client.screen;

import com.radiomod.menu.Dx3Menu;
import com.radiomod.network.Dx3Station;
import com.radiomod.network.RequestDx3CatalogPayload;
import com.radiomod.network.SelectDx3StationPayload;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.network.SyncTechnisatStatusPayload;
import com.radiomod.network.TechnisatActionPayload;
import com.radiomod.signal.DabChannel;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pure The Bug: DAB-only tabletop front panel. */
public class PureBugScreen extends AbstractContainerScreen<Dx3Menu> {
    private static final int WIDTH = 500, HEIGHT = 300;
    private static final int LCD = 0xFF0C142C, INK = 0xFFD9E6FF, DIM = 0xFF9AADD1;
    private int view; // 0 programme, 1 saved list, 2 scan
    private int scroll;
    private int cursor;
    private long scanStarted;

    public PureBugScreen(Dx3Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = 1000;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("SCAN"), b -> scan())
                .bounds(x + 174, y + 242, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("MENU"), b -> { view = 1; scroll = 0; })
                .bounds(x + 227, y + 242, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> view = 0)
                .bounds(x + 280, y + 242, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> move(-1))
                .bounds(x + 223, y + 193, 50, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> move(1))
                .bounds(x + 223, y + 219, 50, 16).build());
        addRenderableWidget(Button.builder(Component.literal("ENTER"), b -> select())
                .bounds(x + 278, y + 207, 55, 18).build());
        addRenderableWidget(Button.builder(Component.literal("MUTE"), b ->
                PacketDistributor.sendToServer(new TechnisatActionPayload(menu.containerId, 3)))
                .bounds(x + 163, y + 207, 55, 18).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(x + WIDTH - 25, y + 6, 18, 18).build());
    }

    private void scan() {
        view = 2;
        scroll = cursor = 0;
        scanStarted = System.currentTimeMillis();
        PacketDistributor.sendToServer(new RequestDx3CatalogPayload(menu.containerId));
    }

    private void move(int direction) {
        if (view == 0) view = 1;
        List<Dx3Station> entries = menu.getStations();
        if (entries.isEmpty()) return;
        cursor = Math.floorMod(cursor + direction, entries.size());
        scroll = Math.max(0, Math.min(cursor, Math.max(0, entries.size() - 3)));
    }

    private void select() {
        List<Dx3Station> entries = menu.getStations();
        if (view != 1 || cursor < 0 || cursor >= entries.size()) return;
        Dx3Station station = entries.get(cursor);
        if (!station.dab()) return;
        PacketDistributor.sendToServer(new SelectDx3StationPayload(menu.containerId, cursor,
                true, station.tuning(), station.sid(), station.mux()));
        view = 0;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && view == 1 && mouseX >= leftPos + 142 && mouseX < leftPos + 358
                && mouseY >= topPos + 70 && mouseY < topPos + 136) {
            cursor = scroll + ((int) mouseY - topPos - 70) / 22;
            select();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (view == 1) {
            scroll = Math.max(0, Math.min(Math.max(0, menu.getStations().size() - 3),
                    scroll - (int) Math.signum(scrollY)));
            cursor = Math.max(scroll, Math.min(scroll + 2, cursor));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        for (int row = -ry; row <= ry; row += 2) {
            double part = 1.0 - (double) row * row / (ry * ry);
            int half = (int) Math.round(rx * Math.sqrt(Math.max(0, part)));
            g.fill(cx - half, cy + row, cx + half + 1, cy + row + 2, color);
        }
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF15181D);
        ellipse(g, x + 250, y + 218, 238, 69, 0xFFCAD0D4);
        ellipse(g, x + 250, y + 208, 232, 62, 0xFFF3F4F3);
        g.fill(x + 240, y + 133, x + 260, y + 190, 0xFF78828C);
        for (int i = 0; i < 7; i++) g.fill(x + 240, y + 137 + i * 7, x + 260, y + 139 + i * 7, 0xFFBBC3C7);
        ellipse(g, x + 250, y + 77, 193, 70, 0xFFC7CDD1);
        ellipse(g, x + 250, y + 72, 191, 66, 0xFFF7F7F5);
        ellipse(g, x + 96, y + 209, 43, 41, 0xFF5A6268);
        ellipse(g, x + 404, y + 209, 43, 41, 0xFF5A6268);
        for (int sy = -33; sy <= 33; sy += 6) for (int sx = -34; sx <= 34; sx += 6)
            if (sx * sx + sy * sy < 34 * 34) {
                g.fill(x + 96 + sx, y + 209 + sy, x + 98 + sx, y + 211 + sy, 0xFF1C2329);
                g.fill(x + 404 + sx, y + 209 + sy, x + 406 + sx, y + 211 + sy, 0xFF1C2329);
            }
        g.fill(x + 131, y + 44, x + 369, y + 152, 0xFF70777F);
        g.fill(x + 136, y + 49, x + 364, y + 147, LCD);
        ellipse(g, x + 248, y + 215, 28, 20, 0xFFB5BCC2);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SyncTechnisatStatusPayload status = menu.getTechnisatStatus();
        SyncDx3PlayerPayload info = menu.getPlayerInfo();
        g.drawCenteredString(font, "PURE", 250, 155, 0xFF1D252B);
        g.drawString(font, "the bug", 312, 268, 0xFF313B42, false);
        g.drawString(font, "DAB Digital Radio", 142, 55, INK, false);
        long day = minecraft != null && minecraft.level != null
                ? Math.floorMod(minecraft.level.getDayTime(), 24000L) : 0;
        String clock = String.format(Locale.ROOT, "%02d:%02d",
                (day / 1000 + 6) % 24, day % 1000 * 60 / 1000);
        g.drawString(font, clock, 330, 55, INK, false);
        for (int bar = 0; bar < 5; bar++) {
            int height = 3 + bar * 2;
            g.fill(271 + bar * 8, 64 - height, 276 + bar * 8, 64,
                    bar < info.signalBars() ? INK : 0xFF394765);
        }
        g.fill(313, 54, 324, 62, INK);
        g.fill(314, 55, 322, 61, LCD);
        g.fill(314, 56, 320, 60, INK);
        g.fill(324, 56, 326, 60, INK);
        if (status != null && status.muted()) g.drawString(font, "M", 344, 137, INK, false);
        if (view == 2 && status != null && !status.scanning()
                && System.currentTimeMillis() - scanStarted > 350) view = 1;
        if (view == 2) {
            g.drawString(font, "Full Scan", 147, 77, INK, false);
            g.drawString(font, "Scanning " + DabChannel.byIndex(status == null ? 0 : status.scanChannelIndex()).label()
                    + "...", 147, 98, INK, false);
            g.drawString(font, "Stations: " + (status == null ? 0 : status.scanStationCount()), 147, 119, DIM, false);
            g.fill(262, 126, 354, 132, 0xFF334466);
            g.fill(263, 127, 263 + (status == null ? 0 : status.scanChannelIndex() * 90 / DabChannel.size()),
                    131, 0xFFDAE8FF);
            return;
        }
        if (view == 1) {
            if (status == null || !status.scanned()) {
                g.drawString(font, "Full Scan required", 147, 90, INK, false);
                g.drawString(font, "Press SCAN", 147, 111, DIM, false);
                return;
            }
            List<Dx3Station> stations = menu.getStations();
            if (stations.isEmpty()) {
                g.drawString(font, "No DAB services", 147, 95, INK, false);
                return;
            }
            scroll = Math.min(scroll, Math.max(0, stations.size() - 3));
            for (int row = 0; row < 3 && scroll + row < stations.size(); row++) {
                int index = scroll + row, yy = 74 + row * 22;
                if (index == cursor) g.fill(142, yy - 3, 358, yy + 14, 0xFF30456D);
                Dx3Station station = stations.get(index);
                g.drawString(font, font.plainSubstrByWidth(station.label(), 166), 146, yy, INK, false);
                g.drawString(font, DabChannel.byIndex(station.tuning()).label(), 330, yy, DIM, false);
            }
            g.drawString(font, (scroll + 1) + "-" + Math.min(scroll + 3, stations.size())
                    + "/" + stations.size(), 144, 135, DIM, false);
            return;
        }
        if (!info.active()) {
            g.drawString(font, "DAB unavailable", 147, 83, INK, false);
            g.drawString(font, "SCAN to find stations", 147, 109, DIM, false);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(info.ps(), 203), 147, 77, INK, false);
        String text = "Now playing: " + (info.rt().isBlank() ? "--" : info.rt());
        g.drawString(font, font.plainSubstrByWidth(text, 203), 147, 100, DIM, false);
        String channel = status == null ? "" : DabChannel.byIndex(status.channelIndex()).label();
        g.drawString(font, channel + "  " + info.bitrateKbps() + " kbps  DAB", 147, 125, INK, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g, mouseX, mouseY, tick);
        super.render(g, mouseX, mouseY, tick);
        renderTooltip(g, mouseX, mouseY);
    }
}
