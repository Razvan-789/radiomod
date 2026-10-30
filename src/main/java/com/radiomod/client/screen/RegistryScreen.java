package com.radiomod.client.screen;

import com.radiomod.menu.RegistryMenu;
import com.radiomod.network.RegistryActionPayload;
import com.radiomod.network.RegistryFilterPayload;
import com.radiomod.network.SyncRegistryPayload;
import com.radiomod.signal.RegistryCountries;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class RegistryScreen extends AbstractContainerScreen<RegistryMenu> {
    private boolean countriesTab;
    private int selectedPrefix;
    private int rowScroll;
    private int modeIndex;
    private String filterError = "";
    private EditBox countryInput;
    private EditBox queryInput, minFrequencyInput, maxFrequencyInput, minPowerInput, maxPowerInput;
    private Button previous, next;
    private Button saveCountry, clearCountry;
    private Button modeButton, applyButton, clearFiltersButton;
    private final Button[] sortButtons = new Button[5];
    private final Button[] prefixButtons = new Button[16];
    private static final String[] MODES = {"ALL", "FM", "DAB", "DAB+", "DRM", "DRM A", "DRM B", "DRM C", "DRM D"};

    public RegistryScreen(RegistryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 512;
        imageHeight = 392;
        inventoryLabelY = 1000;
    }

    @Override protected void init() {
        String oldQuery = value(queryInput), oldMinFrequency = value(minFrequencyInput);
        String oldMaxFrequency = value(maxFrequencyInput), oldMinPower = value(minPowerInput);
        String oldMaxPower = value(maxPowerInput);
        imageWidth = Math.min(512, Math.max(350, width - 12));
        imageHeight = Math.min(392, Math.max(300, height - 12));
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("TRANSMITTERS"), b -> {
            countriesTab = false; rowScroll = 0; updateWidgets(); request("refresh", 0, "");
        }).bounds(x + 12, y + 30, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("COUNTRIES"), b -> {
            countriesTab = true; updateWidgets();
        }).bounds(x + 128, y + 30, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH"), b -> request("refresh", 0, ""))
                .bounds(x + imageWidth - 110, y + 30, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(x + imageWidth - 34, y + 8, 22, 19).build());
        int queryWidth = imageWidth - 223;
        queryInput = input(x + 12, y + 57, queryWidth, "Name, PI or SID", oldQuery, 80, false);
        modeButton = addRenderableWidget(Button.builder(Component.literal("MODE: ALL"), b -> {
            modeIndex = (modeIndex + 1) % MODES.length;
            modeButton.setMessage(Component.literal("MODE: " + MODES[modeIndex]));
        }).bounds(x + 17 + queryWidth, y + 56, 84, 20).build());
        applyButton = addRenderableWidget(Button.builder(Component.literal("APPLY"), b -> applyFilters())
                .bounds(x + 106 + queryWidth, y + 56, 50, 20).build());
        clearFiltersButton = addRenderableWidget(Button.builder(Component.literal("CLEAR"), b -> clearFilters())
                .bounds(x + 161 + queryWidth, y + 56, 50, 20).build());
        int fieldWidth = (imageWidth - 39) / 4;
        minFrequencyInput = input(x + 12, y + 83, fieldWidth, "Min MHz", oldMinFrequency, 12, true);
        maxFrequencyInput = input(x + 17 + fieldWidth, y + 83, fieldWidth, "Max MHz", oldMaxFrequency, 12, true);
        minPowerInput = input(x + 22 + fieldWidth * 2, y + 83, fieldWidth, "Min W", oldMinPower, 9, true);
        maxPowerInput = input(x + 27 + fieldWidth * 3, y + 83, fieldWidth, "Max W", oldMaxPower, 9, true);
        String[] sorts = {"TYPE", "FREQ", "NAME", "COUNTRY", "POWER"};
        int start = x + 12;
        int sortWidth = (imageWidth - 44) / 5;
        for (int i = 0; i < sorts.length; i++) {
            int index = i;
            sortButtons[i] = addRenderableWidget(Button.builder(Component.literal(sorts[i]), b -> {
                rowScroll = 0; request("sort", index, "");
            }).bounds(start, y + 111, sortWidth, 19).build());
            start += sortWidth + 5;
        }
        previous = addRenderableWidget(Button.builder(Component.literal("< PREV"), b -> {
            SyncRegistryPayload p = menu.snapshot(); if (p != null) { rowScroll = 0; request("page", p.page() - 1, ""); }
        }).bounds(x + 12, y + imageHeight - 28, 60, 19).build());
        next = addRenderableWidget(Button.builder(Component.literal("NEXT >"), b -> {
            SyncRegistryPayload p = menu.snapshot(); if (p != null) { rowScroll = 0; request("page", p.page() + 1, ""); }
        }).bounds(x + imageWidth - 72, y + imageHeight - 28, 60, 19).build());
        int cellWidth = (imageWidth - 60) / 4;
        for (int i = 0; i < 16; i++) {
            int index = i;
            int column = i / 4, row = i % 4;
            prefixButtons[i] = addRenderableWidget(Button.builder(Component.literal(""), b -> selectPrefix(index))
                    .bounds(x + 12 + column * (cellWidth + 12), y + 90 + row * 26,
                            cellWidth, 20).build());
        }
        countryInput = new EditBox(font, x + 12, y + imageHeight - 58, imageWidth - 163, 18,
                Component.literal("Country name"));
        countryInput.setMaxLength(40);
        countryInput.setHint(Component.literal("Country name for selected prefix"));
        addRenderableWidget(countryInput);
        saveCountry = addRenderableWidget(Button.builder(Component.literal("SAVE"), b ->
                request("country", selectedPrefix, countryInput.getValue()))
                .bounds(x + imageWidth - 144, y + imageHeight - 59, 58, 20).build());
        clearCountry = addRenderableWidget(Button.builder(Component.literal("CLEAR"), b -> {
            countryInput.setValue(""); request("country", selectedPrefix, "");
        }).bounds(x + imageWidth - 81, y + imageHeight - 59, 69, 20).build());
        selectPrefix(selectedPrefix);
        modeButton.setMessage(Component.literal("MODE: " + MODES[modeIndex]));
        updateWidgets();
        request("refresh", 0, "");
    }

    private static String value(EditBox box) { return box == null ? "" : box.getValue(); }

    private EditBox input(int x, int y, int width, String hint, String current, int max, boolean numeric) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setMaxLength(max);
        box.setHint(Component.literal(hint));
        if (numeric) box.setFilter(text -> text.matches(hint.contains("MHz") ? "[0-9.]*" : "[0-9]*"));
        box.setValue(current);
        addRenderableWidget(box);
        return box;
    }

    private void applyFilters() {
        try {
            int minFreq = frequency(minFrequencyInput), maxFreq = frequency(maxFrequencyInput);
            int minPower = power(minPowerInput), maxPower = power(maxPowerInput);
            if (minFreq >= 0 && maxFreq >= 0 && minFreq > maxFreq) {
                filterError = "Minimum frequency exceeds maximum"; return;
            }
            if (minPower >= 0 && maxPower >= 0 && minPower > maxPower) {
                filterError = "Minimum power exceeds maximum"; return;
            }
            filterError = "";
            rowScroll = 0;
            PacketDistributor.sendToServer(new RegistryFilterPayload(menu.containerId, queryInput.getValue(),
                    minFreq, maxFreq, minPower, maxPower, modeIndex));
        } catch (NumberFormatException ex) {
            filterError = "Enter valid MHz and whole-watt values";
        }
    }

    private static int frequency(EditBox box) {
        if (box.getValue().isBlank()) return -1;
        double mhz = Double.parseDouble(box.getValue());
        if (!Double.isFinite(mhz) || mhz < 0 || mhz > 2000) throw new NumberFormatException();
        return (int) Math.round(mhz * 1000);
    }

    private static int power(EditBox box) {
        if (box.getValue().isBlank()) return -1;
        return Integer.parseInt(box.getValue());
    }

    private void clearFilters() {
        queryInput.setValue(""); minFrequencyInput.setValue(""); maxFrequencyInput.setValue("");
        minPowerInput.setValue(""); maxPowerInput.setValue(""); modeIndex = 0;
        modeButton.setMessage(Component.literal("MODE: ALL"));
        applyFilters();
    }

    private void request(String action, int value, String text) {
        PacketDistributor.sendToServer(new RegistryActionPayload(menu.containerId, action, value, text));
    }

    private void selectPrefix(int index) {
        selectedPrefix = index;
        SyncRegistryPayload p = menu.snapshot();
        countryInput.setValue(p == null ? "" : p.countries().get(index));
    }

    private void updateWidgets() {
        for (Button button : sortButtons) if (button != null) button.visible = !countriesTab;
        for (Button button : prefixButtons) if (button != null) button.visible = countriesTab;
        if (previous != null) previous.visible = !countriesTab;
        if (next != null) next.visible = !countriesTab;
        if (countryInput != null) countryInput.visible = countriesTab;
        if (saveCountry != null) saveCountry.visible = countriesTab;
        if (clearCountry != null) clearCountry.visible = countriesTab;
        for (EditBox box : new EditBox[]{queryInput, minFrequencyInput, maxFrequencyInput, minPowerInput, maxPowerInput})
            if (box != null) box.visible = !countriesTab;
        if (modeButton != null) modeButton.visible = !countriesTab;
        if (applyButton != null) applyButton.visible = !countriesTab;
        if (clearFiltersButton != null) clearFiltersButton.visible = !countriesTab;
    }

    public static void receive(SyncRegistryPayload payload) {
        if (Minecraft.getInstance().screen instanceof RegistryScreen screen
                && screen.menu.containerId == payload.containerId()
                && screen.countryInput != null && !screen.countryInput.isFocused()) {
            screen.countryInput.setValue(payload.countries().get(screen.selectedPrefix));
        }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean editing = countryInput.isFocused() || queryInput.isFocused() || minFrequencyInput.isFocused()
                || maxFrequencyInput.isFocused() || minPowerInput.isFocused() || maxPowerInput.isFocused();
        if (editing && minecraft != null
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        if (countriesTab && countryInput.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            request("country", selectedPrefix, countryInput.getValue()); return true;
        }
        if (!countriesTab && editing
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            applyFilters(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        SyncRegistryPayload p = menu.snapshot();
        if (!countriesTab && p != null && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos + 136 && mouseY < topPos + imageHeight - 30) {
            int visible = Math.min(8, Math.max(1, (imageHeight - 177) / 22));
            rowScroll = Math.max(0, Math.min(Math.max(0, p.entries().size() - visible),
                    rowScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF14252D);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF203943);
        g.fill(x + 9, y + (countriesTab ? 79 : 136), x + imageWidth - 9,
                y + imageHeight - 34, 0xFF10222A);
        g.fill(x + 9, y + (countriesTab ? 79 : 136), x + imageWidth - 9,
                y + (countriesTab ? 81 : 138), 0xFF58D2CC);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "TRANSMITTER REGISTRY", 12, 13, 0xFFE6F6F5, false);
        SyncRegistryPayload p = menu.snapshot();
        if (p == null) {
            g.drawString(font, "Loading registry...", 18, 91, 0xFFA5BEC5, false);
            return;
        }
        if (countriesTab) {
            g.drawString(font, "PI / service SID first hex digit  →  country", 14, 61, 0xFF9BCACB, false);
            int cellWidth = (imageWidth - 60) / 4;
            for (int i = 0; i < 16; i++) {
                String name = p.countries().get(i);
                String label = RegistryCountries.PREFIXES.charAt(i) + "  " + (name.isBlank() ? "Unassigned" : name);
                if (i == selectedPrefix) label = "> " + label;
                prefixButtons[i].setMessage(Component.literal(font.plainSubstrByWidth(label, cellWidth - 12)));
            }
            return;
        }
        String[] sorts = {"TYPE", "FREQ", "NAME", "COUNTRY", "POWER"};
        for (int i = 0; i < 5; i++) sortButtons[i].setMessage(Component.literal(
                (p.sort() == i ? p.descending() ? "▼ " : "▲ " : "") + sorts[i]));
        List<SyncRegistryPayload.Entry> entries = p.entries();
        int available = imageHeight - 177;
        int visible = Math.min(8, Math.max(1, available / 22));
        int rowHeight = Math.max(22, available / visible);
        int nameStart = imageWidth > 430 ? 126 : 118;
        int countryStart = imageWidth > 430 ? imageWidth - 133 : imageWidth - 105;
        for (int i = 0; i < visible && i + rowScroll < entries.size(); i++) {
            SyncRegistryPayload.Entry e = entries.get(i + rowScroll);
            int top = 141 + i * rowHeight;
            if ((i & 1) == 0) g.fill(12, top - 2, imageWidth - 12, top + rowHeight - 2, 0xFF1B3038);
            String frequency = e.frequencyKhz() >= 50_000
                    ? String.format(Locale.ROOT, "%.3f MHz", e.frequencyKhz() / 1000.0)
                    : e.frequencyKhz() + " kHz";
            String technical = e.type() + (e.mode().isBlank() ? "" : " " + e.mode()) + " " + frequency;
            g.drawString(font, font.plainSubstrByWidth(technical, nameStart - 20),
                    16, top, 0xFF6DE1D6, false);
            g.drawString(font, font.plainSubstrByWidth(e.programme(), countryStart - nameStart - 6),
                    nameStart, top, 0xFFF3F9F8, false);
            g.drawString(font, font.plainSubstrByWidth(e.country(), imageWidth - countryStart - 16),
                    countryStart, top, 0xFFE4D393, false);
            if (rowHeight >= 20) {
                String sub = e.powerW() + " W  · " + e.x() + ", " + e.y() + ", " + e.z()
                        + "  · " + e.dimension() + "  · " + e.detail()
                        + (e.code().isBlank() ? "" : "  · " + e.code());
                g.drawString(font, font.plainSubstrByWidth(sub, imageWidth - 34), 16, top + 10,
                        0xFF9EB5BC, false);
            }
        }
        if (entries.isEmpty()) g.drawString(font, "No transmitters match these filters.", 18, 146, 0xFFA5BEC5, false);
        if (entries.size() > visible) g.drawString(font, "Mouse wheel: more rows", 80,
                imageHeight - 24, 0xFF9EB5BC, false);
        if (!filterError.isBlank()) g.drawString(font, filterError, 14, 131, 0xFFFF9B8B, false);
        g.drawCenteredString(font, p.total() + " matches · Page " + (p.page() + 1) + " / " + p.pageCount(),
                imageWidth / 2, imageHeight - 24, 0xFFD3E4E6);
        previous.active = p.page() > 0;
        next.active = p.page() + 1 < p.pageCount();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g, mouseX, mouseY, tick);
        super.render(g, mouseX, mouseY, tick);
        renderTooltip(g, mouseX, mouseY);
        SyncRegistryPayload p = menu.snapshot();
        if (countriesTab || p == null || mouseX < leftPos + 10 || mouseX >= leftPos + imageWidth - 10
                || mouseY < topPos + 138 || mouseY >= topPos + imageHeight - 34) return;
        int available = imageHeight - 177;
        int visible = Math.min(8, Math.max(1, available / 22));
        int rowHeight = Math.max(22, available / visible);
        int row = (mouseY - topPos - 141) / rowHeight;
        int index = row + rowScroll;
        if (row < 0 || row >= visible || index < 0 || index >= p.entries().size()) return;
        SyncRegistryPayload.Entry e = p.entries().get(index);
        String frequency = e.frequencyKhz() >= 50_000
                ? String.format(Locale.ROOT, "%.3f MHz", e.frequencyKhz() / 1000.0)
                : e.frequencyKhz() + " kHz";
        g.renderComponentTooltip(font, List.of(
                Component.literal(e.type() + (e.mode().isBlank() ? "" : " " + e.mode()) + " · " + e.programme()),
                Component.literal(frequency + " · " + e.powerW() + " W · " + e.country()),
                Component.literal(e.dimension() + " · " + e.x() + ", " + e.y() + ", " + e.z()),
                Component.literal(e.code().isBlank() ? e.detail() : e.code() + " · " + e.detail())),
                mouseX, mouseY);
    }
}
