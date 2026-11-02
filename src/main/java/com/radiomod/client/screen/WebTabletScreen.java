package com.radiomod.client.screen;

import com.radiomod.client.WebTabletAudio;
import com.radiomod.menu.WebTabletMenu;
import com.radiomod.network.SyncWebTabletPayload;
import com.radiomod.network.SyncAdsbTabletPayload;
import com.radiomod.network.SyncAdsbRadarPayload;
import com.radiomod.network.WebTabletActionPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** In-game FM-DX style remote receiver, shared by all viewers of one webserver. */
public class WebTabletScreen extends AbstractContainerScreen<WebTabletMenu> {
    private static final int TEXT = 0xFFF2F6EB, DIM = 0xFFB0BAA9;
    private static final int[] ADSB_ZOOMS = {1, 2, 5, 10, 25, 70, 140, 280};
    private EditBox frequency, location, background, color;
    private Button back, fm, drm, af, home, webApp, flightApp, adsbRange, adsbDimension, down, up, tune, play, settings, save;
    private boolean configPage, afPage, adsbMode, homePage, playing;
    private int listScroll, afScroll, adsbListScroll, adsbSelectedIndex = -1;
    private boolean draggingMap, panPending;
    private double dragStartX, dragStartY;
    private int dragOffsetX, dragOffsetY, pendingCenterX, pendingCenterZ;
    private String lastLocation = "", lastBackground = "", lastColor = "";

    public WebTabletScreen(WebTabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 620; imageHeight = 374; inventoryLabelY = 1000;
        configPage = menu.localConfig();
        homePage = !menu.localConfig();
    }
    @Override protected void init() {
        imageWidth = Math.min(620, width - 12);
        imageHeight = Math.min(374, height - 12);
        super.init();
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        back = addRenderableWidget(Button.builder(Component.literal("SERVERS"), b -> {
            if (menu.localConfig()) configPage = false;
            else { send("back", "", 0); stopAudio(); }
        }).bounds(x + 12, y + 10, 82, 20).build());
        home = addRenderableWidget(Button.builder(Component.literal("HOME"), b -> showHome())
                .bounds(x + 104, y + 10, 55, 20).build());
        int cardWidth = Math.min(190, (w - 68) / 2);
        int firstCard = x + w / 2 - cardWidth - 16;
        int secondCard = x + w / 2 + 16;
        webApp = addRenderableWidget(Button.builder(Component.literal("WEBSERVERS"), b -> openWebApp())
                .bounds(firstCard + 12, y + 239, Math.max(70, cardWidth - 24), 24).build());
        flightApp = addRenderableWidget(Button.builder(Component.literal("FLIGHTTRACKING"), b -> openFlightApp())
                .bounds(secondCard + 12, y + 239, Math.max(70, cardWidth - 24), 24).build());
        adsbDimension = addRenderableWidget(Button.builder(Component.literal("DIMENSION"), b -> cycleAdsbDimension())
                .bounds(x + 317, y + 10, Math.max(70, w - 448), 20).build());
        fm = addRenderableWidget(Button.builder(Component.literal("FM"), b -> send("mode", "", 0))
                .bounds(x + 163, y + 10, 42, 20).build());
        drm = addRenderableWidget(Button.builder(Component.literal("DRM"), b -> send("mode", "", 1))
                .bounds(x + 209, y + 10, 48, 20).build());
        af = addRenderableWidget(Button.builder(Component.literal("AF"), b -> afPage = !afPage)
                .bounds(x + 261, y + 10, 48, 20).build());
        settings = addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> {
            configPage = !configPage;
        }).bounds(x + w - 119, y + 10, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(x + w - 31, y + 10, 20, 20).build());
        down = addRenderableWidget(Button.builder(Component.literal("◀"), b -> send("step", "", -1))
                .bounds(x + 22, y + h - 39, 48, 22).build());
        frequency = new EditBox(font, x + 74, y + h - 38, 115, 20, Component.literal("Frequency in MHz"));
        frequency.setMaxLength(7); frequency.setFilter(value -> value.matches("[0-9]{0,3}(\\.[0-9]{0,3})?"));
        frequency.setHint(Component.literal("MHz")); addRenderableWidget(frequency);
        tune = addRenderableWidget(Button.builder(Component.literal("TUNE"), b -> tune())
                .bounds(x + 193, y + h - 39, 54, 22).build());
        up = addRenderableWidget(Button.builder(Component.literal("▶"), b -> send("step", "", 1))
                .bounds(x + 251, y + h - 39, 48, 22).build());
        play = addRenderableWidget(Button.builder(Component.literal("▶ PLAY"), b -> {
            playing = !playing;
            if (!playing) stopAudio();
        }).bounds(x + w - 112, y + h - 39, 98, 22).build());
        adsbRange = addRenderableWidget(Button.builder(Component.literal("ZOOM 70 km"), b -> {
            SyncAdsbTabletPayload view = menu.adsbSnapshot();
            int current = view == null ? 70 : view.rangeKm();
            int index = zoomIndex(current);
            send("adsb_range", "", ADSB_ZOOMS[(index + 1) % ADSB_ZOOMS.length]);
        }).bounds(x + 22, y + h - 39, 130, 22).build());
        location = field(x + 145, y + 91, w - 175, 64, "e.g. Alba Iulia");
        background = field(x + 145, y + 135, w - 175, 512, "https://.../background.png");
        color = field(x + 145, y + 179, 110, 6, "Hex RGB");
        color.setFilter(value -> value.matches("(?i)[0-9a-f]{0,6}"));
        save = addRenderableWidget(Button.builder(Component.literal("SAVE SETTINGS"), b -> saveConfig())
                .bounds(x + 145, y + 222, 130, 22).build());
        updateWidgets();
    }
    private void showHome() {
        if (menu.localConfig()) return;
        if (adsbMode) send("adsb_mode", "", 0);
        else if (menu.snapshot() != null && menu.snapshot().selected()) send("back", "", 0);
        homePage = true;
        adsbMode = false;
        afPage = false;
        stopAudio();
        draggingMap = panPending = false;
        dragOffsetX = dragOffsetY = 0;
        updateWidgets();
    }
    private void openWebApp() {
        homePage = false;
        adsbMode = false;
        send("adsb_mode", "", 0);
        updateWidgets();
    }
    private void openFlightApp() {
        homePage = false;
        adsbMode = true;
        stopAudio();
        menu.clearAdsbSnapshot();
        draggingMap = panPending = false;
        dragOffsetX = dragOffsetY = 0;
        send("adsb_mode", "", 1);
        updateWidgets();
    }
    private EditBox field(int x, int y, int width, int max, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(max); box.setHint(Component.literal(hint)); addRenderableWidget(box);
        return box;
    }
    private void send(String action, String value, int number) {
        SyncWebTabletPayload state = menu.snapshot();
        PacketDistributor.sendToServer(new WebTabletActionPayload(menu.containerId, action,
                state == null ? "" : state.selectedDimension(), state == null ? 0 : state.selectedPos(), value, number));
    }
    private static int zoomIndex(int range) {
        for (int i = 0; i < ADSB_ZOOMS.length; i++) if (ADSB_ZOOMS[i] == range) return i;
        return 5;
    }
    private void cycleAdsbDimension() {
        SyncAdsbTabletPayload view = menu.adsbSnapshot();
        if (view == null) return;
        java.util.List<String> dimensions = view.servers().stream().map(SyncAdsbTabletPayload.Entry::dimension)
                .distinct().sorted().toList();
        if (dimensions.size() < 2) return;
        int index = dimensions.indexOf(view.selectedDimension());
        String next = dimensions.get((index + 1) % dimensions.size());
        draggingMap = panPending = false;
        dragOffsetX = dragOffsetY = 0;
        adsbSelectedIndex = -1;
        adsbListScroll = 0;
        PacketDistributor.sendToServer(new WebTabletActionPayload(menu.containerId, "adsb_dim", next, 0, "", 0));
    }
    private void tune() {
        try {
            double mhz = Double.parseDouble(frequency.getValue());
            if (Double.isFinite(mhz)) send("tune", "", (int) Math.round(mhz * 1000));
        } catch (NumberFormatException ignored) { }
        frequency.setFocused(false);
    }
    private void saveConfig() {
        if (!menu.localConfig()) return;
        if (!location.getValue().equals(lastLocation)) send("location", location.getValue(), 0);
        if (!background.getValue().equals(lastBackground)) send("background", background.getValue(), 0);
        if (color.getValue().matches("(?i)[0-9a-f]{6}") && !color.getValue().equalsIgnoreCase(lastColor))
            send("color", "", Integer.parseInt(color.getValue(), 16));
    }
    @Override public void onClose() { saveConfig(); stopAudio(); super.onClose(); }
    private void stopAudio() { playing = false; WebTabletAudio.get().stop(); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (textFocused() && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && textFocused()) {
            if (frequency.isFocused()) tune(); else saveConfig();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    private boolean textFocused() {
        return frequency.isFocused() || location.isFocused() || background.isFocused() || color.isFocused();
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (homePage) { WebTabletAudio.get().stop(); updateWidgets(); return; }
        if (adsbMode) {
            WebTabletAudio.get().stop();
            SyncAdsbTabletPayload view = menu.adsbSnapshot();
            if (panPending && view != null && view.centerX() == pendingCenterX
                    && view.centerZ() == pendingCenterZ) {
                panPending = false;
                dragOffsetX = dragOffsetY = 0;
            }
            updateWidgets();
            return;
        }
        SyncWebTabletPayload state = menu.snapshot();
        if (state == null) return;
        String nextFrequency = String.format(Locale.ROOT, "%.3f", state.frequencyKhz() / 1000.0);
        if (!frequency.isFocused() && !frequency.getValue().equals(nextFrequency)) frequency.setValue(nextFrequency);
        lastLocation = sync(location, lastLocation, state.location());
        lastBackground = sync(background, lastBackground, state.backgroundUrl());
        lastColor = sync(color, lastColor, String.format(Locale.ROOT, "%06X", state.themeColor()));
        if (playing && state.selected())
            WebTabletAudio.get().play(minecraft, state.drm(), state.signal(),
                    state.signalTenths() / 10.0, state.snrTenths() / 10.0,
                    state.streamUrl(), state.sourceUrl());
        else WebTabletAudio.get().stop();
        play.setMessage(Component.literal(playing ? "■ STOP" : "▶ PLAY"));
        updateWidgets();
    }
    private String sync(EditBox box, String previous, String current) {
        if (!box.isFocused() && box.getValue().equals(previous) && !current.equals(previous)) box.setValue(current);
        return current;
    }
    private void updateWidgets() {
        SyncWebTabletPayload state = menu.snapshot();
        home.visible = !menu.localConfig() && !homePage;
        webApp.visible = flightApp.visible = homePage;
        if (homePage) {
            back.visible = fm.visible = drm.visible = af.visible = down.visible = up.visible = tune.visible = play.visible = false;
            frequency.visible = settings.visible = location.visible = background.visible = color.visible = save.visible = false;
            adsbRange.visible = adsbDimension.visible = false;
            return;
        }
        if (adsbMode) {
            SyncAdsbTabletPayload view = menu.adsbSnapshot();
            back.visible = false;
            fm.visible = drm.visible = af.visible = down.visible = up.visible = tune.visible = play.visible = false;
            frequency.visible = settings.visible = location.visible = background.visible = color.visible = save.visible = false;
            adsbRange.visible = true;
            adsbDimension.visible = true;
            if (view != null) {
                adsbRange.setMessage(Component.literal("ZOOM " + view.rangeKm() + " km"));
                adsbDimension.setMessage(Component.literal("DIM " + fit(view.selectedDimension(), adsbDimension.getWidth() - 20)));
                adsbDimension.active = view.servers().stream().map(SyncAdsbTabletPayload.Entry::dimension).distinct().count() > 1;
            }
            return;
        }
        adsbDimension.visible = false;
        adsbRange.visible = false;
        boolean selected = state != null && state.selected();
        boolean radio = selected && !configPage;
        back.visible = selected && !menu.localConfig();
        fm.visible = drm.visible = down.visible = up.visible = tune.visible = play.visible = radio;
        af.visible = radio && state != null && !state.drm();
        if (state != null && state.drm()) afPage = false;
        if (state != null) af.setMessage(Component.literal("AF " + state.afFrequenciesKhz().size()));
        frequency.visible = radio;
        settings.visible = menu.localConfig() && selected;
        settings.setMessage(Component.literal(configPage ? "RADIO" : "SETTINGS"));
        location.visible = background.visible = color.visible = save.visible = selected && configPage && menu.localConfig();
        if (state != null) {
            fm.active = state.drm(); drm.active = !state.drm();
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (homePage) return super.mouseClicked(mx, my, button);
        if (adsbMode) return adsbMouseClicked(mx, my, button);
        SyncWebTabletPayload state = menu.snapshot();
        if (button == 0 && state != null && !state.selected()) {
            int rx = (int) mx - leftPos, ry = (int) my - topPos;
            int index = listScroll + (ry - 72) / 34;
            if (rx >= 20 && rx < imageWidth - 20 && ry >= 72 && ry < imageHeight - 24
                    && index >= 0 && index < state.servers().size()) {
                SyncWebTabletPayload.Entry entry = state.servers().get(index);
                PacketDistributor.sendToServer(new WebTabletActionPayload(menu.containerId, "select",
                        entry.dimension(), entry.pos(), "", 0));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (homePage) return super.mouseScrolled(mx, my, sx, sy);
        if (adsbMode) {
            SyncAdsbTabletPayload view = menu.adsbSnapshot();
            if (view != null) {
                if (mx >= leftPos + 24 && mx < leftPos + 300
                        && my >= topPos + 52 && my < topPos + 328) {
                    int index = zoomIndex(view.rangeKm()) - (int) Math.signum(sy);
                    index = Math.clamp(index, 0, ADSB_ZOOMS.length - 1);
                    if (ADSB_ZOOMS[index] != view.rangeKm()) send("adsb_range", "", ADSB_ZOOMS[index]);
                } else adsbListScroll = Math.max(0, Math.min(Math.max(0, view.contacts().size() - 6),
                        adsbListScroll - (int) Math.signum(sy)));
            }
            return true;
        }
        SyncWebTabletPayload state = menu.snapshot();
        if (state != null && !state.selected()) {
            int visible = Math.max(1, (imageHeight - 100) / 34);
            listScroll = Math.max(0, Math.min(Math.max(0, state.servers().size() - visible),
                    listScroll - (int) Math.signum(sy)));
            return true;
        }
        if (state != null && state.selected() && afPage) {
            int visible = Math.max(1, (imageHeight - 145) / 20);
            afScroll = Math.max(0, Math.min(Math.max(0, state.afFrequenciesKhz().size() - visible),
                    afScroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    private boolean adsbMouseClicked(double mx, double my, int button) {
        SyncAdsbTabletPayload view = menu.adsbSnapshot();
        if (button == 0 && view != null) {
            int rx = (int) mx - leftPos, ry = (int) my - topPos;
            if (rx >= 320 && rx < imageWidth - 20 && ry >= 70 && ry < 274) {
                int index = adsbListScroll + (ry - 70) / 34;
                if (index < view.contacts().size()) { adsbSelectedIndex = index; return true; }
            }
            if (rx >= 24 && rx < 300 && ry >= 52 && ry < 328) {
                int centerX = 162, centerY = 190;
                for (int i = 0; i < view.contacts().size(); i++) {
                    SyncAdsbRadarPayload.Contact contact = view.contacts().get(i);
                    int px = centerX + dragOffsetX + (int) Math.round(contact.eastKm() / view.rangeKm() * 138);
                    int py = centerY + dragOffsetY + (int) Math.round(contact.southKm() / view.rangeKm() * 138);
                    if (Math.abs(rx - px) <= 6 && Math.abs(ry - py) <= 6) {
                        adsbSelectedIndex = i; return true;
                    }
                }
                draggingMap = true;
                dragStartX = mx - dragOffsetX;
                dragStartY = my - dragOffsetY;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (adsbMode && draggingMap && button == 0) {
            dragOffsetX = (int) Math.round(mx - dragStartX);
            dragOffsetY = (int) Math.round(my - dragStartY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (adsbMode && draggingMap && button == 0) {
            draggingMap = false;
            SyncAdsbTabletPayload view = menu.adsbSnapshot();
            if (view != null && (dragOffsetX != 0 || dragOffsetY != 0)) {
                double blocksPerPixel = view.rangeKm() / (138.0 * view.kilometersPerBlock());
                pendingCenterX = (int) Math.clamp(Math.round(view.centerX() - dragOffsetX * blocksPerPixel),
                        -29_999_984L, 29_999_984L);
                pendingCenterZ = (int) Math.clamp(Math.round(view.centerZ() - dragOffsetY * blocksPerPixel),
                        -29_999_984L, 29_999_984L);
                panPending = true;
                PacketDistributor.sendToServer(new WebTabletActionPayload(menu.containerId, "adsb_pan", "",
                        pendingCenterZ, "", pendingCenterX));
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }
    private void renderAdsbBg(GuiGraphics g) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x, y, x + w, y + h, 0xFF0B1514);
        g.fill(x + 4, y + 4, x + w - 4, y + 37, 0xFF173E36);
        SyncAdsbTabletPayload view = menu.adsbSnapshot();
        if (view == null) return;
        int mapX = x + 24, mapY = y + 52, size = 276;
        g.fill(mapX - 2, mapY - 2, mapX + size + 2, mapY + size + 2, 0xFF2C6550);
        g.fill(mapX, mapY, mapX + size, mapY + size, 0xFF0D1D25);
        g.enableScissor(mapX, mapY, mapX + size, mapY + size);
        drawCoordinateGrid(g, view, mapX, mapY, size);
        for (SyncAdsbTabletPayload.VorMarker vor : view.vors()) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(vor.pos());
            double east = (pos.getX() - view.centerX()) * view.kilometersPerBlock() / view.rangeKm();
            double south = (pos.getZ() - view.centerZ()) * view.kilometersPerBlock() / view.rangeKm();
            if (Math.abs(east) > 1 || Math.abs(south) > 1) continue;
            int px = x + 162 + dragOffsetX + (int) Math.round(east * 138);
            int py = y + 190 + dragOffsetY + (int) Math.round(south * 138);
            int color = vor.connected() ? 0xFFFFCF73 : 0xFF958A74;
            g.fill(px - 4, py - 3, px + 5, py + 4, 0xFF32291C);
            g.fill(px - 2, py - 1, px + 3, py + 2, color);
            g.fill(px, py - 5, px + 1, py + 5, color);
            g.drawString(font, vor.identifier(), px + 7, py - 4, color, false);
        }
        for (int i = 0; i < view.contacts().size(); i++) {
            SyncAdsbRadarPayload.Contact contact = view.contacts().get(i);
            double east = contact.eastKm() / view.rangeKm(), south = contact.southKm() / view.rangeKm();
            if (Math.abs(east) > 1 || Math.abs(south) > 1) continue;
            int px = x + 162 + dragOffsetX + (int) Math.round(east * 138);
            int py = y + 190 + dragOffsetY + (int) Math.round(south * 138);
            int color = i == adsbSelectedIndex ? 0xFFFFFF83 : 0xFFEAFFF2;
            g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF123A30);
            g.fill(px - 2, py - 2, px + 3, py + 3, color);
            double bearing = Math.toRadians(contact.headingDegrees());
            for (int step = 4; step <= 8; step++) {
                int hx = px + (int) Math.round(Math.sin(bearing) * step);
                int hy = py - (int) Math.round(Math.cos(bearing) * step);
                g.fill(hx, hy, hx + 1, hy + 1, color);
            }
        }
        g.disableScissor();
        g.fill(x + 162, mapY, x + 163, mapY + size, 0x5534C790);
        g.fill(mapX, y + 190, mapX + size, y + 191, 0x5534C790);
        g.fill(x + 314, y + 48, x + w - 18, y + h - 48, 0xE013322C);
        for (int row = 0; row < 6 && row + adsbListScroll < view.contacts().size(); row++) {
            int top = y + 70 + row * 34;
            g.fill(x + 322, top, x + w - 26, top + 31,
                    row + adsbListScroll == adsbSelectedIndex ? 0xFF276349 : 0xFF1A473A);
        }
    }
    private void drawCoordinateGrid(GuiGraphics g, SyncAdsbTabletPayload view, int mapX, int mapY, int size) {
        double blocksPerHalf = view.rangeKm() / view.kilometersPerBlock();
        double pixelsPerBlock = size / (2.0 * blocksPerHalf);
        double targetStep = 2.0 * blocksPerHalf / 5.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(targetStep)));
        double normalized = targetStep / magnitude;
        double step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude;
        double baseX = Math.floor(view.centerX() / step) * step;
        double baseZ = Math.floor(view.centerZ() / step) * step;
        for (int i = -12; i <= 12; i++) {
            double worldX = baseX + i * step;
            int px = mapX + size / 2 + dragOffsetX
                    + (int) Math.round((worldX - view.centerX()) * pixelsPerBlock);
            if (px >= mapX && px < mapX + size) {
                g.fill(px, mapY, px + 1, mapY + size, 0xFF284759);
                g.drawString(font, "X " + (long) worldX, px + 3, mapY + 4, 0xFF7196A6, false);
            }
            double worldZ = baseZ + i * step;
            int py = mapY + size / 2 + dragOffsetY
                    + (int) Math.round((worldZ - view.centerZ()) * pixelsPerBlock);
            if (py >= mapY && py < mapY + size) {
                g.fill(mapX, py, mapX + size, py + 1, 0xFF284759);
                g.drawString(font, "Z " + (long) worldZ, mapX + 4, py + 3, 0xFF7196A6, false);
            }
        }
    }
    private void renderAdsbLabels(GuiGraphics g) {
        SyncAdsbTabletPayload view = menu.adsbSnapshot();
        g.drawString(font, "FLIGHTTRACKING · ADS-B", 167, 16, 0xFF8EF0BE, false);
        if (view == null) { g.drawString(font, "Connecting...", 25, 55, DIM, false); return; }
        g.drawString(font, "N", 159, 55, 0xFFFFFFFF, false);
        g.drawString(font, "W", 28, 184, 0xFFFFFFFF, false);
        g.drawString(font, "E", 288, 184, 0xFFFFFFFF, false);
        g.drawString(font, "S", 159, 315, 0xFFFFFFFF, false);
        long serverCount = view.servers().stream().filter(entry -> entry.dimension().equals(view.selectedDimension())).count();
        g.drawString(font, serverCount + " SERVERS  ·  " + view.vors().size() + " VORS  ·  "
                        + view.contacts().size() + " CONTACTS",
                323, 54, 0xFF8EF0BE, false);
        g.drawString(font, "ADS-B", 27, imageHeight - 42, 0xFFEAFFF2, false);
        g.drawString(font, "VOR", 88, imageHeight - 42, 0xFFFFCF73, false);
        g.drawString(font, "WORLD COORDINATES", 139, imageHeight - 42, 0xFF7196A6, false);
        g.drawString(font, "X " + view.centerX() + "  Z " + view.centerZ() + "  ·  GRID IN BLOCKS",
                27, imageHeight - 29, DIM, false);
        if (!view.connected()) {
            g.drawString(font, serverCount == 0 ? "No ADS-B servers in this dimension"
                    : "Link a receiver and antenna", 324, 82, TEXT, false);
            return;
        }
        for (int row = 0; row < 6 && row + adsbListScroll < view.contacts().size(); row++) {
            SyncAdsbRadarPayload.Contact contact = view.contacts().get(row + adsbListScroll);
            int top = 77 + row * 34;
            g.drawString(font, fit(contact.identifier(), imageWidth - 350), 330, top, TEXT, false);
            g.drawString(font, String.format(Locale.ROOT, "%.1f km   HDG %03d°   %.0f km/h",
                    contact.distanceKm(), contact.headingDegrees(), contact.speedKmH()), 330, top + 13, DIM, false);
        }
        if (adsbSelectedIndex >= 0 && adsbSelectedIndex < view.contacts().size()) {
            SyncAdsbRadarPayload.Contact contact = view.contacts().get(adsbSelectedIndex);
            g.drawString(font, fit(contact.operator().isBlank() ? contact.carrier() : contact.operator(), imageWidth - 345),
                    323, 279, 0xFF8EF0BE, false);
            g.drawString(font, "X " + contact.x() + "  Y " + contact.y() + "  Z " + contact.z(), 323, 294, TEXT, false);
            g.drawString(font, String.format(Locale.ROOT, "SNR %.1f dB   %.1f km", contact.snrDb(), contact.distanceKm()),
                    323, 309, DIM, false);
        } else g.drawString(font, "Select a contact for details", 323, 294, DIM, false);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        if (homePage) { renderHomeBg(g); return; }
        if (adsbMode) { renderAdsbBg(g); return; }
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        SyncWebTabletPayload state = menu.snapshot();
        int panel = panelColor(state == null ? 0x8BD15B : state.themeColor());
        g.fill(x, y, x + w, y + h, 0xFF0B100D);
        if (state != null && state.selected() && !state.backgroundUrl().isBlank()) {
            WebBackgroundCache.draw(g, state.backgroundUrl(), x + 3, y + 3, w - 6, h - 6);
            g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0x8008100A);
        }
        g.fill(x + 4, y + 4, x + w - 4, y + 37, panel);
        if (state == null || !state.selected()) return;
        if (configPage && menu.localConfig()) {
            g.fill(x + 18, y + 56, x + w - 18, y + 269, panel);
            return;
        }
        if (afPage) {
            g.fill(x + 18, y + 48, x + w - 18, y + h - 48, panel);
            return;
        }
        g.fill(x + 18, y + 48, x + w - 18, y + 155, panel);
        g.fill(x + 18, y + 164, x + w / 3 - 4, y + 246, panel);
        g.fill(x + w / 3 + 4, y + 164, x + w * 2 / 3 - 4, y + 246, panel);
        g.fill(x + w * 2 / 3 + 4, y + 164, x + w - 18, y + 246, panel);
        g.fill(x + 18, y + 256, x + w / 2 - 4, y + h - 48, panel);
        g.fill(x + w / 2 + 4, y + 256, x + w - 18, y + h - 48, panel);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (homePage) { renderHomeLabels(g); return; }
        if (adsbMode) { renderAdsbLabels(g); return; }
        SyncWebTabletPayload state = menu.snapshot();
        if (state == null) { g.drawString(font, "Connecting...", 20, 52, DIM, false); return; }
        int accent = 0xFF000000 | state.themeColor();
        g.drawString(font, state.selected() ? fit(state.location(), imageWidth - 450) : "WEB RECEIVERS",
                state.selected() ? 316 : 210, 16, accent, false);
        if (!state.selected()) { renderList(g, state, accent); return; }
        if (configPage && menu.localConfig()) {
            g.drawString(font, "WEBSERVER SETTINGS", 27, 61, accent, false);
            g.drawString(font, "Location", 28, 96, TEXT, false);
            g.drawString(font, "Background", 28, 140, TEXT, false);
            g.drawString(font, "Theme RGB", 28, 184, TEXT, false);
            g.drawString(font, state.connected() ? "Antenna connected" : "Link an antenna for stronger reception",
                    28, 263, state.connected() ? accent : DIM, false);
            return;
        }
        if (afPage) { renderAf(g, state, accent); return; }
        renderGraph(g, state, accent);
        int w = imageWidth, one = w / 3;
        g.drawString(font, state.drm() ? "DRM" : "FM", 27, 171, accent, false);
        g.drawString(font, String.format(Locale.ROOT, "%.3f MHz", state.frequencyKhz() / 1000.0),
                28, 199, TEXT, false);
        g.drawString(font, state.drm() ? "SERVICE" : "PS / LOGO", one + 13, 171, accent, false);
        String ps = state.ps().isBlank() ? "—" : state.ps();
        g.drawString(font, fit(ps, one - 32), one + 13, 197, TEXT, false);
        if (state.logo() != null) drawLogo(g, state.logo(), one * 2 - 64, 180, 52);
        g.drawString(font, "SIGNAL", one * 2 + 13, 171, accent, false);
        g.drawString(font, String.format(Locale.ROOT, "%.1f dBµV", state.signalTenths() / 10.0),
                one * 2 + 13, 194, TEXT, false);
        g.drawString(font, state.signal() ? String.format(Locale.ROOT, "SNR %.1f dB", state.snrTenths() / 10.0)
                : "NOISE FLOOR", one * 2 + 13, 216, DIM, false);
        g.drawString(font, state.drm() ? "DRM SERVICE" : "PI CODE", 27, 263, accent, false);
        g.drawString(font, state.drm() ? fit(state.ps(), w / 2 - 48)
                : state.pi().isBlank() ? "----" : state.pi() + "  " + state.country(), 28, 286, TEXT, false);
        g.drawString(font, state.drm() ? "TEXT" : "RADIO TEXT / ID", w / 2 + 13, 263, accent, false);
        if (state.identified()) {
            g.drawString(font, fit(state.stationName() + "  " + state.txX() + ", " + state.txY() + ", " + state.txZ(),
                    w / 2 - 48), w / 2 + 13, 281, TEXT, false);
            g.drawString(font, state.powerW() + " W  ·  " + state.distance() + " m  ·  "
                    + state.bearing() + "°", w / 2 + 13, 296, DIM, false);
        } else g.drawString(font, state.drm() ? "DRM service" : "Decoding PI and PS...",
                w / 2 + 13, 282, DIM, false);
        if (!state.rt().isBlank()) g.drawString(font, fit(state.rt(), w - 48), 26, imageHeight - 61, TEXT, false);
        g.drawString(font, state.drm() ? "0.150–30.000 MHz" : "65.000–108.000 MHz",
                307, imageHeight - 31, DIM, false);
    }
    private void renderList(GuiGraphics g, SyncWebTabletPayload state, int accent) {
        g.drawString(font, "Choose a webserver to receive from its location", 21, 51, DIM, false);
        int visible = Math.max(1, (imageHeight - 100) / 34);
        if (state.servers().isEmpty()) g.drawString(font, "No webservers are online", 22, 84, TEXT, false);
        for (int row = 0; row < visible && row + listScroll < state.servers().size(); row++) {
            SyncWebTabletPayload.Entry entry = state.servers().get(row + listScroll);
            int y = 72 + row * 34;
            g.fill(20, y, imageWidth - 20, y + 31, panelColor(0x8BD15B));
            g.drawString(font, fit(entry.location(), imageWidth - 170), 29, y + 5, accent, false);
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(entry.pos());
            g.drawString(font, fit(entry.dimension() + "  ·  " + pos.toShortString(), imageWidth - 65),
                    29, y + 18, DIM, false);
        }
    }
    private void renderAf(GuiGraphics g, SyncWebTabletPayload state, int accent) {
        g.drawString(font, "DECODED ALTERNATIVE FREQUENCIES", 28, 58, accent, false);
        g.drawString(font, "RDS group 0A · " + state.afFrequenciesKhz().size() + " decoded", 28, 75, DIM, false);
        if (state.afFrequenciesKhz().isEmpty())
            g.drawString(font, state.signal() ? "Waiting for AF data..." : "No RDS signal", 28, 103, TEXT, false);
        int visible = Math.max(1, (imageHeight - 145) / 20);
        for (int row = 0; row < visible && row + afScroll < state.afFrequenciesKhz().size(); row++) {
            int khz = state.afFrequenciesKhz().get(row + afScroll);
            int y = 100 + row * 20;
            g.drawString(font, String.format(Locale.ROOT, "%02d   %.3f MHz", row + afScroll + 1, khz / 1000.0),
                    30, y, TEXT, false);
        }
    }
    private static int panelColor(int rgb) {
        int r = (18 * 4 + ((rgb >>> 16) & 255) * 3) / 7;
        int g = (18 * 4 + ((rgb >>> 8) & 255) * 3) / 7;
        int b = (18 * 4 + (rgb & 255) * 3) / 7;
        return 0xD8000000 | (r << 16) | (g << 8) | b;
    }
    private void renderGraph(GuiGraphics g, SyncWebTabletPayload state, int accent) {
        int left = 29, top = 56, width = imageWidth - 58, height = 89;
        g.drawString(font, "SIGNAL HISTORY · 60 s", left + 5, top + 3, accent, false);
        for (int i = 0; i < 60; i++) {
            int sample = state.history()[i];
            if (sample <= -1000) continue;
            int x = left + i * width / 60;
            int y = top + height - 8 - (int) Math.max(0, Math.min(height - 25, (sample / 10.0 + 20) / 120 * (height - 25)));
            g.fill(x, y, x + Math.max(2, width / 60), top + height - 7, (accent & 0x00FFFFFF) | 0x55000000);
            g.fill(x, y, x + Math.max(2, width / 60), y + 2, accent);
        }
        g.drawString(font, !state.signal() ? "NO RECEPTION" : state.drm() ? "DRM" : "BER " + state.ber() + "%",
                left + width - 80, top + 3, DIM, false);
    }
    private String fit(String value, int pixels) { return font.plainSubstrByWidth(value, Math.max(16, pixels)); }
    private void renderHomeBg(GuiGraphics g) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x, y, x + w, y + h, 0xFF101820);
        g.fill(x + 8, y + 8, x + w - 8, y + h - 8, 0xFF173047);
        g.fill(x + 8, y + 8, x + w - 8, y + 45, 0xFF234969);
        g.fill(x + 8, y + h - 43, x + w - 8, y + h - 8, 0xFF142838);
        int cardWidth = Math.min(190, (w - 68) / 2);
        int first = x + w / 2 - cardWidth - 16, second = x + w / 2 + 16;
        g.fill(first, y + 86, first + cardWidth, y + 273, 0xFF22425B);
        g.fill(first + 4, y + 90, first + cardWidth - 4, y + 226, 0xFF2D5B75);
        g.fill(second, y + 86, second + cardWidth, y + 273, 0xFF1B4955);
        g.fill(second + 4, y + 90, second + cardWidth - 4, y + 226, 0xFF236573);
        int wx = first + cardWidth / 2, fx = second + cardWidth / 2;
        // Web receiver screen, tuning scale and antenna.
        g.fill(wx - 42, y + 124, wx + 43, y + 185, 0xFF0E2532);
        g.fill(wx - 37, y + 129, wx + 38, y + 169, 0xFF8ACCD0);
        g.fill(wx - 30, y + 146, wx + 31, y + 148, 0xFF416F82);
        g.fill(wx - 4, y + 132, wx + 2, y + 165, 0xFF1B556D);
        g.fill(wx - 20, y + 179, wx + 21, y + 183, 0xFF91B6C5);
        // Flight tracking radar with a single contact and a heading trail.
        g.fill(fx - 42, y + 121, fx + 43, y + 205, 0xFF10363F);
        g.fill(fx - 1, y + 124, fx + 1, y + 202, 0xFF3A7481);
        g.fill(fx - 38, y + 163, fx + 39, y + 165, 0xFF3A7481);
        g.fill(fx + 15, y + 145, fx + 22, y + 152, 0xFF9BE5D5);
        g.fill(fx + 18, y + 139, fx + 20, y + 145, 0xFF9BE5D5);
        g.fill(fx - 29, y + 186, fx - 24, y + 191, 0xFFFFD286);
    }
    private void renderHomeLabels(GuiGraphics g) {
        g.drawString(font, "TABLET", 19, 17, 0xFFE7F4FF, false);
        g.drawString(font, "Choose an app", 20, 58, 0xFFBBD2E2, false);
        g.drawString(font, "Webservers", imageWidth / 2 - 184, 291, TEXT, false);
        g.drawString(font, "FlightTracking", imageWidth / 2 + 28, 291, TEXT, false);
        g.drawString(font, "FM / DRM receivers", imageWidth / 2 - 184, 307, DIM, false);
        g.drawString(font, "ADS-B / VOR map", imageWidth / 2 + 28, 307, DIM, false);
    }
    private void renderTabletBezel(GuiGraphics g) {
        if (menu.localConfig()) return;
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x, y, x + w, y + 3, 0xFF050A0E);
        g.fill(x, y + h - 4, x + w, y + h, 0xFF050A0E);
        g.fill(x, y, x + 4, y + h, 0xFF050A0E);
        g.fill(x + w - 4, y, x + w, y + h, 0xFF050A0E);
        g.fill(x + w / 2 - 2, y + 1, x + w / 2 + 2, y + 3, 0xFF51728B);
    }
    private static void drawLogo(GuiGraphics g, int[] pixels, int x, int y, int size) {
        if (pixels.length != 4096) return;
        for (int py = 0; py < 64; py++) for (int px = 0; px < 64; px++) {
            int color = pixels[py * 64 + px];
            if ((color >>> 24) == 0) continue;
            int x0 = x + px * size / 64, x1 = x + (px + 1) * size / 64;
            int y0 = y + py * size / 64, y1 = y + (py + 1) * size / 64;
            if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        renderTabletBezel(g);
        renderTooltip(g, mouseX, mouseY);
        if (adsbMode) renderAdsbMarkerTooltip(g, mouseX, mouseY);
    }
    private void renderAdsbMarkerTooltip(GuiGraphics g, int mouseX, int mouseY) {
        SyncAdsbTabletPayload view = menu.adsbSnapshot();
        if (view == null || mouseX < leftPos + 24 || mouseX >= leftPos + 300
                || mouseY < topPos + 52 || mouseY >= topPos + 328) return;
        int centerX = leftPos + 162 + dragOffsetX, centerY = topPos + 190 + dragOffsetY;
        for (SyncAdsbRadarPayload.Contact contact : view.contacts()) {
            int px = centerX + (int) Math.round(contact.eastKm() / view.rangeKm() * 138);
            int py = centerY + (int) Math.round(contact.southKm() / view.rangeKm() * 138);
            if (Math.abs(mouseX - px) <= 5 && Math.abs(mouseY - py) <= 5) {
                g.renderTooltip(font, Component.literal("ADS-B " + contact.identifier() + " · X "
                        + contact.x() + " Y " + contact.y() + " Z " + contact.z()), mouseX, mouseY);
                return;
            }
        }
        for (SyncAdsbTabletPayload.VorMarker vor : view.vors()) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(vor.pos());
            int px = centerX + (int) Math.round((pos.getX() - view.centerX())
                    * view.kilometersPerBlock() / view.rangeKm() * 138);
            int py = centerY + (int) Math.round((pos.getZ() - view.centerZ())
                    * view.kilometersPerBlock() / view.rangeKm() * 138);
            if (Math.abs(mouseX - px) <= 6 && Math.abs(mouseY - py) <= 6) {
                g.renderTooltip(font, Component.literal("VOR " + vor.identifier() + " · "
                        + String.format(Locale.ROOT, "%.3f MHz", vor.frequencyKhz() / 1000.0)
                        + (vor.dme() ? " · DME" : "") + " · " + pos.toShortString()), mouseX, mouseY);
                return;
            }
        }
    }
}
