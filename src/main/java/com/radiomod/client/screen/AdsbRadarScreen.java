package com.radiomod.client.screen;

import com.radiomod.menu.AdsbRadarMenu;
import com.radiomod.network.SyncAdsbRadarPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Radar sweep, map blips, and decoded ADS-B contact list. */
public class AdsbRadarScreen extends AbstractContainerScreen<AdsbRadarMenu> {
    private static final int GREEN = 0xFF70E8A8, PALE = 0xFFD8F7E5, DIM = 0xFF8BB8A0;
    private static final int[] RANGES = {5, 10, 25, 70, 140, 280};
    private int rangeIndex = 5, listScroll, selectedIndex = -1;
    public AdsbRadarScreen(AdsbRadarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 610; imageHeight = 350; inventoryLabelY = 1000;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("RANGE 280 km"), button -> {
            rangeIndex = (rangeIndex + 1) % RANGES.length;
            button.setMessage(Component.literal("RANGE " + RANGES[rangeIndex] + " km"));
        }).bounds(leftPos + 21, topPos + 315, 125, 22).build());
        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(leftPos + imageWidth - 29, topPos + 11, 18, 20).build());
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        SyncAdsbRadarPayload state = menu.snapshot();
        if (state != null && mx >= leftPos + 345) {
            listScroll = Math.max(0, Math.min(Math.max(0, state.contacts().size() - 6),
                    listScroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        SyncAdsbRadarPayload state = menu.snapshot();
        if (button == 0 && state != null && mx >= leftPos + 347 && mx < leftPos + 589
                && my >= topPos + 70 && my < topPos + 274) {
            int index = listScroll + ((int) my - topPos - 70) / 34;
            if (index < state.contacts().size()) { selectedIndex = index; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF07130E);
        g.fill(x + 8, y + 8, x + imageWidth - 8, y + 39, 0xFF173628);
        g.fill(x + 347, y + 48, x + 591, y + 338, 0xFF10291E);
        int cx = x + 174, cy = y + 182, radius = 127;
        g.fill(cx - radius, cy, cx + radius + 1, cy + 1, 0xFF285C40);
        g.fill(cx, cy - radius, cx + 1, cy + radius + 1, 0xFF285C40);
        for (int ring = 1; ring <= 4; ring++) circle(g, cx, cy, radius * ring / 4, 0xFF285C40);
        double angle = (System.currentTimeMillis() % 8000L) * Math.PI * 2 / 8000.0;
        for (int r = 0; r <= radius; r++) {
            int px = cx + (int) Math.round(Math.sin(angle) * r);
            int py = cy - (int) Math.round(Math.cos(angle) * r);
            g.fill(px, py, px + 1, py + 1, 0xFF3E9C65);
        }
        SyncAdsbRadarPayload state = menu.snapshot();
        if (state == null || !state.connected()) return;
        for (SyncAdsbRadarPayload.Contact contact : state.contacts()) {
            double east = contact.eastKm() / RANGES[rangeIndex], south = contact.southKm() / RANGES[rangeIndex];
            if (east * east + south * south > 1) continue;
            int px = cx + (int) Math.round(east * radius), py = cy + (int) Math.round(south * radius);
            g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF86FFD0);
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
        }
        for (int row = 0; row < 6 && row + listScroll < state.contacts().size(); row++) {
            int top = y + 70 + row * 34;
            g.fill(x + 355, top, x + 583, top + 30,
                    row + listScroll == selectedIndex ? 0xFF235D43 : 0xFF173B2A);
        }
    }
    private static void circle(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int degrees = 0; degrees < 360; degrees += 2) {
            double a = Math.toRadians(degrees);
            int x = cx + (int) Math.round(radius * Math.cos(a));
            int y = cy + (int) Math.round(radius * Math.sin(a));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SyncAdsbRadarPayload state = menu.snapshot();
        g.drawString(font, "ADS-B RADAR", 19, 18, GREEN, false);
        if (state == null) { g.drawString(font, "Connecting...", 365, 54, DIM, false); return; }
        g.drawString(font, "N", 170, 44, GREEN, false);
        g.drawString(font, "E", 307, 178, GREEN, false);
        g.drawString(font, "S", 170, 314, GREEN, false);
        g.drawString(font, "W", 40, 178, GREEN, false);
        g.drawString(font, state.connected() ? "CONTACTS " + state.contacts().size() : "NO ANTENNA", 359, 53, GREEN, false);
        if (!state.connected()) {
            g.drawString(font, "Link any antenna with the Linking Tool", 357, 85, PALE, false);
            return;
        }
        List<SyncAdsbRadarPayload.Contact> contacts = state.contacts();
        if (contacts.isEmpty()) g.drawString(font, "No transponders in range", 359, 87, DIM, false);
        for (int row = 0; row < 6 && row + listScroll < contacts.size(); row++) {
            SyncAdsbRadarPayload.Contact contact = contacts.get(row + listScroll);
            int top = 76 + row * 34;
            g.drawString(font, fit(contact.identifier(), 130), 363, top, PALE, false);
            g.drawString(font, String.format(Locale.ROOT, "%.1f km  %.1f dB", contact.distanceKm(), contact.snrDb()),
                    363, top + 12, DIM, false);
        }
        g.drawString(font, "RX " + state.receiverX() + ", " + state.receiverY() + ", " + state.receiverZ(),
                162, 323, DIM, false);
        if (selectedIndex >= 0 && selectedIndex < contacts.size()) {
            SyncAdsbRadarPayload.Contact contact = contacts.get(selectedIndex);
            g.drawString(font, fit(contact.operator().isBlank() ? contact.carrier() : contact.operator(), 220),
                    359, 277, GREEN, false);
            g.drawString(font, "TX " + contact.x() + ", " + contact.y() + ", " + contact.z(), 359, 291, PALE, false);
            g.drawString(font, String.format(Locale.ROOT, "HDG %03d°  %.0f km/h", contact.headingDegrees(), contact.speedKmH()),
                    359, 305, PALE, false);
            g.drawString(font, String.format(Locale.ROOT, "ALT Y%d  RANGE %.1f km", contact.y(), contact.rangeKm()),
                    359, 319, DIM, false);
        } else g.drawString(font, "Select a contact for details", 359, 298, DIM, false);
    }
    private String fit(String text, int width) { return font.plainSubstrByWidth(text, width); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
