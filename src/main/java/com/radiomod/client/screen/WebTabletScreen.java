package com.radiomod.client.screen;

import com.radiomod.client.WebTabletAudio;
import com.radiomod.menu.WebTabletMenu;
import com.radiomod.network.SyncWebTabletPayload;
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
    private EditBox frequency, location, background, color;
    private Button back, fm, drm, af, down, up, tune, play, settings, save;
    private boolean configPage, afPage, playing;
    private int listScroll, afScroll;
    private String lastLocation = "", lastBackground = "", lastColor = "";

    public WebTabletScreen(WebTabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 620; imageHeight = 374; inventoryLabelY = 1000;
        configPage = menu.localConfig();
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
        fm = addRenderableWidget(Button.builder(Component.literal("FM"), b -> send("mode", "", 0))
                .bounds(x + 104, y + 10, 42, 20).build());
        drm = addRenderableWidget(Button.builder(Component.literal("DRM"), b -> send("mode", "", 1))
                .bounds(x + 150, y + 10, 48, 20).build());
        af = addRenderableWidget(Button.builder(Component.literal("AF"), b -> afPage = !afPage)
                .bounds(x + 202, y + 10, 48, 20).build());
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
        location = field(x + 145, y + 91, w - 175, 64, "e.g. Alba Iulia");
        background = field(x + 145, y + 135, w - 175, 512, "https://.../background.png");
        color = field(x + 145, y + 179, 110, 6, "Hex RGB");
        color.setFilter(value -> value.matches("(?i)[0-9a-f]{0,6}"));
        save = addRenderableWidget(Button.builder(Component.literal("SAVE SETTINGS"), b -> saveConfig())
                .bounds(x + 145, y + 222, 130, 22).build());
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
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
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
        SyncWebTabletPayload state = menu.snapshot();
        if (state == null) { g.drawString(font, "Connecting...", 20, 52, DIM, false); return; }
        int accent = 0xFF000000 | state.themeColor();
        g.drawString(font, state.selected() ? fit(state.location(), imageWidth - 390) : "WEB RECEIVERS",
                state.selected() ? 256 : 210, 16, accent, false);
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
        renderTooltip(g, mouseX, mouseY);
    }
}
