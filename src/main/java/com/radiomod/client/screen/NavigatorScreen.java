package com.radiomod.client.screen;

import com.radiomod.menu.NavigatorMenu;
import com.radiomod.network.NavigatorActionPayload;
import com.radiomod.network.SyncNavigatorPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Portable navigation display: ADS-B contacts, dual VOR, and VOR registry. */
public class NavigatorScreen extends AbstractContainerScreen<NavigatorMenu> {
    private static final int WHITE = 0xFFEAF5FF, DIM = 0xFFA7BFCC, CYAN = 0xFF74DCF6;
    private static final double RADAR_RANGE_KM = 280.0;
    private int page, scroll;
    private EditBox vor1, vor2;
    private Button save1, save2, pin;
    private int seen1 = -1, seen2 = -1;
    public NavigatorScreen(NavigatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 560; imageHeight = 354; inventoryLabelY = 1000;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("ADS-B"), b -> switchPage(0))
                .bounds(leftPos + 14, topPos + 10, 76, 21).build());
        addRenderableWidget(Button.builder(Component.literal("VOR 1 / VOR 2"), b -> switchPage(1))
                .bounds(leftPos + 96, topPos + 10, 116, 21).build());
        addRenderableWidget(Button.builder(Component.literal("VOR REGISTRY"), b -> switchPage(2))
                .bounds(leftPos + 218, topPos + 10, 119, 21).build());
        pin = addRenderableWidget(Button.builder(Component.literal("PIN VOR"), b -> {
            SyncNavigatorPayload state = menu.snapshot();
            PacketDistributor.sendToServer(new NavigatorActionPayload(menu.containerId, 0,
                    state != null && state.pinned() ? 0 : 1));
        }).bounds(leftPos + 345, topPos + 10, 89, 21).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(leftPos + imageWidth - 30, topPos + 10, 19, 21).build());
        vor1 = field(leftPos + 83, topPos + 82, "VOR 1 MHz");
        vor2 = field(leftPos + 83, topPos + 194, "VOR 2 MHz");
        save1 = addRenderableWidget(Button.builder(Component.literal("TUNE"), b -> tune(1))
                .bounds(leftPos + 215, topPos + 81, 59, 22).build());
        save2 = addRenderableWidget(Button.builder(Component.literal("TUNE"), b -> tune(2))
                .bounds(leftPos + 215, topPos + 193, 59, 22).build());
        updateWidgets();
    }
    private EditBox field(int x, int y, String hint) {
        EditBox box = new EditBox(font, x, y, 124, 20, Component.literal(hint));
        box.setMaxLength(7); box.setFilter(value -> value.matches("[0-9]{0,3}(\\.[0-9]{0,3})?"));
        box.setHint(Component.literal("108.000–117.950")); addRenderableWidget(box);
        return box;
    }
    private void switchPage(int next) {
        if (page == 1) { tune(1); tune(2); }
        page = next; scroll = 0; updateWidgets();
    }
    private void updateWidgets() {
        boolean visible = page == 1;
        vor1.visible = vor2.visible = save1.visible = save2.visible = visible;
    }
    private void tune(int channel) {
        EditBox field = channel == 1 ? vor1 : vor2;
        try {
            int khz = (int) Math.round(Double.parseDouble(field.getValue()) * 1000);
            if (khz < 108_000 || khz > 117_950) return;
            PacketDistributor.sendToServer(new NavigatorActionPayload(menu.containerId, channel, khz));
            field.setFocused(false);
        } catch (NumberFormatException ignored) { }
    }
    @Override protected void containerTick() {
        super.containerTick();
        SyncNavigatorPayload state = menu.snapshot();
        if (state == null) return;
        pin.setMessage(Component.literal(state.pinned() ? "UNPIN VOR" : "PIN VOR"));
        if (!vor1.isFocused() && state.frequency1Khz() != seen1) {
            vor1.setValue(String.format(Locale.ROOT, "%.3f", state.frequency1Khz() / 1000.0)); seen1 = state.frequency1Khz();
        }
        if (!vor2.isFocused() && state.frequency2Khz() != seen2) {
            vor2.setValue(String.format(Locale.ROOT, "%.3f", state.frequency2Khz() / 1000.0)); seen2 = state.frequency2Khz();
        }
    }
    @Override public void onClose() {
        if (page == 1) { tune(1); tune(2); }
        super.onClose();
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((vor1.isFocused() || vor2.isFocused()) && minecraft != null
                && minecraft.options.keyInventory.matches(key, scan)) return true;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                && (vor1.isFocused() || vor2.isFocused())) {
            tune(vor1.isFocused() ? 1 : 2); return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        SyncNavigatorPayload state = menu.snapshot();
        if (state != null && page != 1) {
            int count = page == 0 ? state.transponders().size() : state.registry().size();
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, count - 9));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF101C28);
        g.fill(x + 4, y + 4, x + imageWidth - 4, y + 38, 0xFF21415B);
        if (page == 0) {
            g.fill(x + 16, y + 52, x + 283, y + 319, 0xFF153044);
            int cx = x + 149, cy = y + 185;
            for (int r : new int[]{40, 80, 120}) circle(g, cx, cy, r, 0xFF355F77);
            g.fill(cx - 120, cy, cx + 121, cy + 1, 0xFF355F77);
            g.fill(cx, cy - 120, cx + 1, cy + 121, 0xFF355F77);
            g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFFFFFFFF);
            SyncNavigatorPayload state = menu.snapshot();
            if (state != null && minecraft != null && minecraft.player != null) {
                BlockPos center = minecraft.player.blockPosition();
                for (SyncNavigatorPayload.Transponder contact : state.transponders()) {
                    double east = (contact.x() - center.getX()) * state.kilometersPerBlock() / RADAR_RANGE_KM;
                    double south = (contact.z() - center.getZ()) * state.kilometersPerBlock() / RADAR_RANGE_KM;
                    if (east * east + south * south > 1) continue;
                    int px = cx + (int) Math.round(east * 120), py = cy + (int) Math.round(south * 120);
                    g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF75EACA);
                    double heading = Math.toRadians(contact.headingDegrees());
                    int hx = px + (int) Math.round(Math.sin(heading) * 7);
                    int hy = py - (int) Math.round(Math.cos(heading) * 7);
                    g.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFF75EACA);
                }
                drawDmeMarker(g, state.vor1(), center, cx, cy, state.kilometersPerBlock(), 0xFFFFD16F);
                drawDmeMarker(g, state.vor2(), center, cx, cy, state.kilometersPerBlock(), 0xFFED92FC);
            }
            g.fill(x + 296, y + 52, x + 544, y + 319, 0xFF1C3548);
        } else if (page == 1) {
            g.fill(x + 16, y + 55, x + 544, y + 162, 0xFF1C3548);
            g.fill(x + 16, y + 169, x + 544, y + 276, 0xFF1C3548);
        } else g.fill(x + 16, y + 55, x + 544, y + 319, 0xFF1C3548);
    }
    private static void drawDmeMarker(GuiGraphics g, SyncNavigatorPayload.Fix fix, BlockPos center,
                                      int cx, int cy, double kilometersPerBlock, int color) {
        if (fix == null || !fix.dme()) return;
        double east = (fix.x() - center.getX()) * kilometersPerBlock / RADAR_RANGE_KM;
        double south = (fix.z() - center.getZ()) * kilometersPerBlock / RADAR_RANGE_KM;
        if (east * east + south * south > 1) return;
        int px = cx + (int) Math.round(east * 120), py = cy + (int) Math.round(south * 120);
        g.fill(px - 4, py - 1, px + 5, py + 2, color);
        g.fill(px - 1, py - 4, px + 2, py + 5, color);
    }
    private static void circle(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int degrees = 0; degrees < 360; degrees += 3) {
            double a = Math.toRadians(degrees);
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int py = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SyncNavigatorPayload state = menu.snapshot();
        if (state == null) { g.drawString(font, "Scanning...", 22, 56, WHITE, false); return; }
        if (page == 0) {
            g.drawString(font, "NEARBY ADS-B TRANSPONDERS", 304, 61, CYAN, false);
            g.drawString(font, "N", 146, 57, WHITE, false);
            g.drawString(font, "280 km", 222, 301, DIM, false);
            if (state.transponders().isEmpty()) g.drawString(font, "No ADS-B contacts nearby", 304, 90, DIM, false);
            for (int i = 0; i < 9 && i + scroll < state.transponders().size(); i++) {
                SyncNavigatorPayload.Transponder contact = state.transponders().get(i + scroll);
                int y = 85 + i * 25;
                g.drawString(font, font.plainSubstrByWidth(contact.identifier() + "  " + contact.operator(), 230),
                        304, y, WHITE, false);
                g.drawString(font, String.format(Locale.ROOT, "%.1f km · %03d° · %.0f km/h",
                        contact.distanceKm(), contact.headingDegrees(), contact.speedKmH()), 304, y + 11, DIM, false);
            }
            g.drawString(font, "VOR 1 DME", 29, 330, 0xFFFFD16F, false);
            g.drawString(font, "VOR 2 DME", 102, 330, 0xFFED92FC, false);
        } else if (page == 1) {
            drawFix(g, "VOR 1", state.vor1(), 70);
            drawFix(g, "VOR 2", state.vor2(), 182);
            g.drawString(font, "Tune 108.000–117.950 MHz in 0.050 MHz steps", 23, 298, DIM, false);
        } else {
            g.drawString(font, "VOR REGISTRY  ·  " + state.registry().size() + " entries", 24, 62, CYAN, false);
            if (state.registry().isEmpty()) g.drawString(font, "No VOR transmitters online", 25, 91, DIM, false);
            for (int i = 0; i < 9 && i + scroll < state.registry().size(); i++) {
                SyncNavigatorPayload.VorEntry entry = state.registry().get(i + scroll);
                int y = 88 + i * 24;
                BlockPos pos = BlockPos.of(entry.pos());
                g.drawString(font, entry.identifier() + "  " + String.format(Locale.ROOT, "%.3f MHz", entry.frequencyKhz() / 1000.0)
                        + (entry.dme() ? "  DME" : ""), 25, y, WHITE, false);
                g.drawString(font, entry.dimension() + "  ·  " + pos.toShortString()
                        + (entry.connected() ? "" : "  ·  no antenna"), 25, y + 10, DIM, false);
            }
        }
    }
    private void drawFix(GuiGraphics g, String label, SyncNavigatorPayload.Fix fix, int top) {
        g.drawString(font, label, 25, top, CYAN, false);
        if (fix == null) {
            g.drawString(font, "NO VOR SIGNAL", 295, top + 13, 0xFFFFAE76, false);
            return;
        }
        g.drawString(font, fix.identifier() + "  RADIAL " + String.format(Locale.ROOT, "%03d°", fix.radialDegrees()),
                294, top + 7, WHITE, false);
        g.drawString(font, String.format(Locale.ROOT, "SNR %.1f dB  ·  %.1f km", fix.snrDb(), fix.distanceKm()),
                294, top + 25, DIM, false);
        g.drawString(font, fix.dme() ? String.format(Locale.ROOT, "DME %.0f blocks", fix.dmeBlocks()) : "DME unavailable",
                294, top + 43, fix.dme() ? CYAN : DIM, false);
        g.drawString(font, "TX " + fix.x() + ", " + fix.y() + ", " + fix.z(), 294, top + 61, DIM, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
