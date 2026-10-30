package com.radiomod.client.screen;

import com.radiomod.item.RadioItem;
import com.radiomod.menu.Dx3Menu;
import com.radiomod.network.Dx3Station;
import com.radiomod.network.SelectDx3StationPayload;
import com.radiomod.network.SetDx3LinkModePayload;
import com.radiomod.network.RequestDx3CatalogPayload;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.network.SyncDx3ReceiverStatePayload;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.PtyNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class Dx3Screen extends AbstractContainerScreen<Dx3Menu> {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 300;
    private static final int ROWS = 6;
    private static final int ROW_TOP = 65;
    private static final int ROW_HEIGHT = 32;
    private static final int PANEL = 0xFF18232C;
    private static final int PANEL_LIGHT = 0xFF223542;
    private static final int ACCENT = 0xFF70C2DC;
    private static final int TEXT = 0xFFF0F5F8;
    private static final int DIM = 0xFFA3BBC7;

    private enum Filter { FM, DAB, ALL, PLAYER }
    private enum Sort { FREQUENCY, NAME, GROUP }

    private Filter filter = Filter.ALL;
    private Sort sort = Sort.NAME;
    private int firstRow;
    private Button fmTab;
    private Button dabTab;
    private Button allTab;
    private Button playerTab;
    private Button previous;
    private Button next;
    private Button mute;
    private Button pin;
    private Button sortButton;
    private Button linkButton;
    private int shownLinkMode = -1;

    public Dx3Screen(Dx3Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        fmTab = addRenderableWidget(Button.builder(Component.literal("FM"), button -> chooseFilter(Filter.FM))
                .bounds(leftPos + 14, topPos + 36, 84, 20).build());
        dabTab = addRenderableWidget(Button.builder(Component.literal("DAB"), button -> chooseFilter(Filter.DAB))
                .bounds(leftPos + 102, topPos + 36, 84, 20).build());
        allTab = addRenderableWidget(Button.builder(Component.literal("FM + DAB"), button -> chooseFilter(Filter.ALL))
                .bounds(leftPos + 190, topPos + 36, 110, 20).build());
        playerTab = addRenderableWidget(Button.builder(Component.literal("PLAYER"), button -> chooseFilter(Filter.PLAYER))
                .bounds(leftPos + 304, topPos + 36, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(leftPos + 408, topPos + 10, 20, 18).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(leftPos + 14, topPos + 269, 32, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(leftPos + 50, topPos + 269, 32, 20).build());
        linkButton = addRenderableWidget(Button.builder(linkLabel(), button -> {
                    if (minecraft != null && minecraft.player != null) {
                        int mode = (linkMode() + 1) % 4;
                        if (!menu.isReceiver()) RadioItem.setDx3LinkMode(minecraft.player, mode);
                        PacketDistributor.sendToServer(new SetDx3LinkModePayload(menu.containerId, mode));
                        updateButtons();
                    }
                }).bounds(leftPos + 86, topPos + 269, 104, 20).build());
        shownLinkMode = -1;
        mute = addRenderableWidget(Button.builder(muteLabel(), button -> {
                    if (minecraft != null && minecraft.player != null) {
                        if (menu.isReceiver()) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
                        else RadioItem.setMuted(minecraft.player, !RadioItem.isMuted(minecraft.player));
                        mute.setMessage(muteLabel());
                    }
                }).bounds(leftPos + 349, topPos + 269, 78, 20).build());
        pin = addRenderableWidget(Button.builder(Component.literal("PIN"), button -> {
                    if (minecraft != null && minecraft.player != null) {
                        RadioItem.setDx3Pinned(minecraft.player,
                                !RadioItem.isDx3Pinned(minecraft.player));
                        updateButtons();
                    }
                }).bounds(leftPos + 234, topPos + 269, 108, 20).build());
        sortButton = addRenderableWidget(Button.builder(sortLabel(), button -> {
                    sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                    firstRow = 0;
                    updateButtons();
                }).bounds(leftPos + 194, topPos + 269, 148, 20).build());
        updateButtons();
        PacketDistributor.sendToServer(new RequestDx3CatalogPayload(menu.containerId));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF10171D);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, PANEL);
        graphics.fill(x + 11, y + 62, x + WIDTH - 11, y + 261, 0xFF0D1921);
        if (filter == Filter.PLAYER) {
            graphics.fill(x + 17, y + 69, x + WIDTH - 17, y + 252, PANEL);
            return;
        }
        for (int row = 0; row < ROWS; row++) {
            int rowY = y + ROW_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x + 14 && mouseX < x + WIDTH - 14
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            graphics.fill(x + 14, rowY, x + WIDTH - 14, rowY + ROW_HEIGHT - 2,
                    hovered ? PANEL_LIGHT : PANEL);
        }
        int count = visibleStations().size();
        if (count > ROWS) {
            firstRow = Math.min(firstRow, count - ROWS);
            int trackTop = y + ROW_TOP;
            int trackHeight = ROWS * ROW_HEIGHT - 2;
            int thumbHeight = Math.max(18, trackHeight * ROWS / count);
            int maxFirst = count - ROWS;
            int thumbTop = trackTop + firstRow * (trackHeight - thumbHeight) / maxFirst;
            graphics.fill(x + 420, trackTop, x + 425, trackTop + trackHeight, 0xFF30434F);
            graphics.fill(x + 420, thumbTop, x + 425, thumbTop + thumbHeight, ACCENT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.literal(menu.isReceiver() ? "DX3 RECEIVER" : "DX3 RADIO"), 15, 13, TEXT, false);
        if (menu.isReceiver()) {
            SyncDx3ReceiverStatePayload state = menu.getReceiverState();
            graphics.drawString(font, state != null && state.antenna() ? "ANTENNA LINKED" : "WEAK / NO ANTENNA",
                    134, 27, state != null && state.antenna() ? ACCENT : 0xFFFFB982, false);
        }
        graphics.drawString(font, Component.literal(filter == Filter.PLAYER ? "PLAYER" : "STATIONS"), 134, 15, ACCENT, false);
        drawStatus(graphics);
        if (filter == Filter.PLAYER) {
            renderPlayer(graphics);
            updateButtons();
            return;
        }
        List<VisibleStation> visible = visibleStations();
        firstRow = Math.min(firstRow, Math.max(0, visible.size() - ROWS));
        if (visible.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No receivable stations"), WIDTH / 2, 150, DIM);
        }
        for (int row = 0; row < ROWS && firstRow + row < visible.size(); row++) {
            VisibleStation listed = visible.get(firstRow + row);
            Dx3Station station = listed.station();
            int y = ROW_TOP + row * ROW_HEIGHT;
            if (isSelected(station)) {
                graphics.fill(15, y + 2, 18, y + ROW_HEIGHT - 4, ACCENT);
            }
            if (station.logoPixels() != null) {
                drawLogo(graphics, station.logoPixels(), 23, y + 7);
            } else {
                graphics.fill(23, y + 7, 39, y + 23, station.dab() ? 0xFF43667B : 0xFF305B67);
                graphics.drawString(font, station.dab() ? "D" : "F", 28, y + 11, TEXT, false);
            }
            graphics.drawString(font, fit(station.label(), 285), 48, y + 4, TEXT, false);
            graphics.drawString(font, fit(station.detail(), 300), 48, y + 17, DIM, false);
            graphics.drawString(font, station.dab() ? "DAB" : "FM", 385, y + 11, ACCENT, false);
        }
        graphics.drawString(font, visible.isEmpty() ? "0 / 0" : (firstRow + 1) + "–"
                + Math.min(firstRow + ROWS, visible.size()) + " / " + visible.size(), 54, 275, DIM, false);
        updateButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        if (filter != Filter.PLAYER && x >= 420 && x < 426 && y >= ROW_TOP
                && y < ROW_TOP + ROWS * ROW_HEIGHT) {
            int maxFirst = Math.max(0, visibleStations().size() - ROWS);
            firstRow = Math.min(maxFirst, Math.max(0, (y - ROW_TOP) * maxFirst / (ROWS * ROW_HEIGHT)));
            updateButtons();
            return true;
        }
        if (filter == Filter.PLAYER || x < 14 || x >= WIDTH - 14 || y < ROW_TOP || y >= ROW_TOP + ROWS * ROW_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int row = (y - ROW_TOP) / ROW_HEIGHT;
        if ((y - ROW_TOP) % ROW_HEIGHT >= ROW_HEIGHT - 2) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        List<VisibleStation> entries = visibleStations();
        int position = firstRow + row;
        if (position >= entries.size()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        select(entries.get(position));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && filter != Filter.PLAYER && mouseX >= leftPos + 14 && mouseX < leftPos + WIDTH - 14
                && mouseY >= topPos + ROW_TOP && mouseY < topPos + ROW_TOP + ROWS * ROW_HEIGHT) {
            int amount = Math.max(1, (int) Math.ceil(Math.abs(scrollY)));
            scrollRows(scrollY > 0 ? -amount : amount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void select(VisibleStation listed) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        Dx3Station station = listed.station();
        if (menu.isReceiver()) {
            PacketDistributor.sendToServer(new SelectDx3StationPayload(menu.containerId, listed.catalogIndex(),
                    station.dab(), station.tuning(), station.sid(), station.mux()));
            return;
        }
        RadioItem.setDx3Selected(minecraft.player, true);
        if (station.dab()) {
            RadioItem.setMode(minecraft.player, RadioItem.MODE_DAB);
            RadioItem.setDabChannelIndex(minecraft.player, station.tuning());
            RadioItem.setDabStation(minecraft.player, station.mux(), station.sid());
            RadioItem.setDx3FmPi(minecraft.player, "");
            RadioItem.setDx3LinkId(minecraft.player, station.sid());
        } else {
            RadioItem.setMode(minecraft.player, RadioItem.MODE_FM);
            RadioItem.setFrequencyKhz(minecraft.player, station.tuning());
            RadioItem.setDx3FmPi(minecraft.player, station.sid());
            RadioItem.setDx3LinkId(minecraft.player, station.sid());
        }
        PacketDistributor.sendToServer(new SelectDx3StationPayload(menu.containerId, listed.catalogIndex(),
                station.dab(), station.tuning(), station.sid(), station.mux()));
    }

    private List<VisibleStation> visibleStations() {
        List<VisibleStation> result = new ArrayList<>();
        List<Dx3Station> catalog = menu.getStations();
        for (int index = 0; index < catalog.size(); index++) {
            Dx3Station station = catalog.get(index);
            if (filter == Filter.ALL || filter == Filter.DAB && station.dab()
                    || filter == Filter.FM && !station.dab()) {
                result.add(new VisibleStation(index, station));
            }
        }
        Comparator<VisibleStation> comparator = switch (sort) {
            case FREQUENCY -> Comparator.comparingInt((VisibleStation entry) -> frequencyKhz(entry.station()))
                    .thenComparing(entry -> entry.station().sortName(), String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing((VisibleStation entry) -> entry.station().sortName(),
                    String.CASE_INSENSITIVE_ORDER).thenComparingInt(entry -> frequencyKhz(entry.station()));
            case GROUP -> Comparator.comparing((VisibleStation entry) -> entry.station().dab())
                    .thenComparing(entry -> entry.station().sortName(), String.CASE_INSENSITIVE_ORDER);
        };
        result.sort(comparator);
        return result;
    }

    private static int frequencyKhz(Dx3Station station) {
        return station.dab() ? DabChannel.byIndex(station.tuning()).frequencyKhz() : station.tuning();
    }

    private boolean isSelected(Dx3Station station) {
        if (menu.isReceiver()) {
            SyncDx3ReceiverStatePayload state = menu.getReceiverState();
            if (state == null) return false;
            return station.dab() ? state.dab() && state.channelIndex() == station.tuning()
                    && state.sid().equalsIgnoreCase(station.sid()) && state.mux().equals(station.mux())
                    : !state.dab() && (station.sid().isBlank() ? state.frequencyKhz() == station.tuning()
                    : station.sid().equalsIgnoreCase(state.pi()));
        }
        if (minecraft == null || minecraft.player == null || !RadioItem.isDx3Selected(minecraft.player)) {
            return false;
        }
        if (station.dab()) {
            return RadioItem.getMode(minecraft.player) == RadioItem.MODE_DAB
                    && RadioItem.getDabChannelIndex(minecraft.player) == station.tuning()
                    && RadioItem.getDabStationSid(minecraft.player).equalsIgnoreCase(station.sid())
                    && RadioItem.getDabStationMux(minecraft.player).equals(station.mux());
        }
        return RadioItem.getMode(minecraft.player) == RadioItem.MODE_FM
                && (station.sid().isBlank() ? RadioItem.getFrequencyKhz(minecraft.player) == station.tuning()
                : station.sid().equalsIgnoreCase(RadioItem.getDx3FmPi(minecraft.player)));
    }

    private void chooseFilter(Filter nextFilter) {
        filter = nextFilter;
        firstRow = 0;
        updateButtons();
    }

    private void changePage(int amount) {
        scrollRows(amount * ROWS);
    }

    private void scrollRows(int amount) {
        int maxFirst = Math.max(0, visibleStations().size() - ROWS);
        firstRow = Math.max(0, Math.min(maxFirst, firstRow + amount));
        updateButtons();
    }

    private void updateButtons() {
        if (fmTab == null) {
            return;
        }
        fmTab.active = filter != Filter.FM;
        dabTab.active = filter != Filter.DAB;
        allTab.active = filter != Filter.ALL;
        playerTab.active = filter != Filter.PLAYER;
        previous.visible = filter != Filter.PLAYER;
        next.visible = filter != Filter.PLAYER;
        previous.active = filter != Filter.PLAYER && firstRow > 0;
        next.active = filter != Filter.PLAYER && firstRow + ROWS < visibleStations().size();
        sortButton.visible = filter != Filter.PLAYER;
        sortButton.setMessage(sortLabel());
        linkButton.setMessage(linkLabel());
        int linkMode = linkMode();
        if (linkMode != shownLinkMode) {
            shownLinkMode = linkMode;
            linkButton.setTooltip(Tooltip.create(Component.literal(switch (linkMode) {
                case RadioItem.DX3_PREFER_DAB -> "Prefer DAB whenever the linked service is receivable";
                case RadioItem.DX3_PREFER_FM -> "Prefer FM whenever the linked service is receivable";
                case RadioItem.DX3_PREFER_QUALITY -> "Prefer DAB above 72 kbps DAB+ or 128 kbps DAB when FM is available";
                default -> "Switch when FM audio deteriorates or DAB reception weakens";
            })));
        }
        mute.setMessage(muteLabel());
        pin.visible = !menu.isReceiver() && filter == Filter.PLAYER && minecraft != null && minecraft.player != null
                && RadioItem.isDx3Selected(minecraft.player);
        pin.setMessage(Component.literal(minecraft != null && minecraft.player != null
                && RadioItem.isDx3Pinned(minecraft.player) ? "UNPIN" : "PIN"));
    }

    private Component sortLabel() {
        return Component.literal("SORT: " + sort.name());
    }

    private Component linkLabel() {
        int mode = linkMode();
        return Component.literal("PREF: " + switch (mode) {
            case RadioItem.DX3_PREFER_DAB -> "DAB";
            case RadioItem.DX3_PREFER_FM -> "FM";
            case RadioItem.DX3_PREFER_QUALITY -> "QUALITY";
            default -> "BALANCED";
        });
    }

    private Component muteLabel() {
        boolean muted = menu.isReceiver() ? menu.getReceiverState() != null && menu.getReceiverState().muted()
                : minecraft != null && minecraft.player != null && RadioItem.isMuted(minecraft.player);
        return Component.literal(muted ? "UNMUTE" : "MUTE");
    }

    private int linkMode() {
        if (menu.isReceiver()) return menu.getReceiverState() == null ? RadioItem.DX3_BALANCED
                : menu.getReceiverState().linkMode();
        return minecraft == null || minecraft.player == null ? RadioItem.DX3_BALANCED
                : RadioItem.getDx3LinkMode(minecraft.player);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String result = value;
        while (!result.isEmpty() && font.width(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private void drawStatus(GuiGraphics graphics) {
        SyncDx3PlayerPayload info = menu.getPlayerInfo();
        if (info.ct() && !info.time().isBlank()) {
            graphics.fill(278, 14, 288, 24, ACCENT);
            graphics.fill(280, 16, 286, 22, PANEL);
            graphics.fill(283, 16, 284, 20, ACCENT);
            graphics.fill(283, 19, 286, 20, ACCENT);
            graphics.drawString(font, info.time(), 292, 15, ACCENT, false);
        }
        for (int index = 0; index < 5; index++) {
            int barHeight = 3 + index * 2;
            graphics.fill(365 + index * 7, 27 - barHeight, 370 + index * 7, 27,
                    index < info.signalBars() ? ACCENT : 0xFF405764);
        }
    }

    private void renderPlayer(GuiGraphics graphics) {
        Dx3Station station = selectedStation();
        SyncDx3PlayerPayload info = menu.getPlayerInfo();
        if (station == null) {
            graphics.drawCenteredString(font, "Select a station from the list", WIDTH / 2, 148, DIM);
            return;
        }
        graphics.fill(25, 79, 97, 151, 0xFF0D1921);
        if (station.dab() && menu.getPlayerLogo() != null) {
            drawLogo(graphics, menu.getPlayerLogo(), 29, 83, 1);
        } else if (station.dab() && station.logoPixels() != null) {
            drawLogo(graphics, station.logoPixels(), 29, 83, 4);
        } else {
            graphics.fill(29, 83, 93, 147, station.dab() ? 0xFF43667B : 0xFF305B67);
            graphics.drawCenteredString(font, station.dab() ? "DAB" : "FM", 61, 111, TEXT);
        }
        String name = info.active() && !info.longPs().isBlank() ? info.longPs()
                : info.active() && !info.ps().isBlank() ? info.ps()
                : station.dab() ? station.label()
                : String.format(java.util.Locale.ROOT, "FM %.1f", station.tuning() / 1000.0);
        graphics.drawString(font, fit(name, 300), 109, 84, TEXT, false);
        graphics.drawString(font, station.dab() ? fit("MUX: " + station.mux(), 300) : "FM station",
                109, 104, ACCENT, false);
        String tuning = station.dab() ? DabChannel.byIndex(station.tuning()).label()
                : String.format(java.util.Locale.ROOT, "%.1f MHz", station.tuning() / 1000.0);
        graphics.drawString(font, fit(tuning + "  ·  " + station.detail(), 300), 109, 123, DIM, false);
        if (station.dab() && info.active() && info.bitrateKbps() > 0) {
            graphics.drawString(font, fit("Codec: " + info.codec() + "  ·  "
                    + info.bitrateKbps() + " kbps", 300), 109, 140, ACCENT, false);
        }
        graphics.drawString(font, "PTY", 26, 166, ACCENT, false);
        graphics.drawString(font, PtyNames.name(info.pty()), 57, 166, TEXT, false);
        graphics.drawString(font, station.dab() ? "DLS" : "RT", 26, 188, ACCENT, false);
        String rt = info.active() ? info.rt() : "";
        if (rt.isBlank()) {
            graphics.drawString(font, "—", 57, 188, DIM, false);
        } else {
            List<FormattedCharSequence> lines = font.split(Component.literal(rt), 350);
            for (int index = 0; index < Math.min(4, lines.size()); index++) {
                graphics.drawString(font, lines.get(index), 57, 188 + index * 13, TEXT, false);
            }
        }
    }

    private Dx3Station selectedStation() {
        for (Dx3Station station : menu.getStations()) {
            if (isSelected(station)) {
                return station;
            }
        }
        return null;
    }

    private static void drawLogo(GuiGraphics graphics, int[] pixels, int x, int y) {
        drawLogo(graphics, pixels, x, y, 1);
    }

    private static void drawLogo(GuiGraphics graphics, int[] pixels, int x, int y, int scale) {
        int size = pixels.length == 64 * 64 ? 64 : pixels.length == 16 * 16 ? 16 : 0;
        if (size == 0) {
            return;
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int color = pixels[row * size + column];
                if ((color >>> 24) != 0) {
                    graphics.fill(x + column * scale, y + row * scale,
                            x + (column + 1) * scale, y + (row + 1) * scale, color);
                }
            }
        }
    }

    private record VisibleStation(int catalogIndex, Dx3Station station) {
    }
}
