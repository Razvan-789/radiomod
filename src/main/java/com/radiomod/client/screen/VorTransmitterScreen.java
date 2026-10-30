package com.radiomod.client.screen;

import com.radiomod.menu.VorTransmitterMenu;
import com.radiomod.network.SetVorPayload;
import com.radiomod.network.SyncVorPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class VorTransmitterScreen extends AbstractContainerScreen<VorTransmitterMenu> {
    private EditBox frequency, identifier;
    private int seenFrequency = -1;
    private String seenIdentifier = "";
    public VorTransmitterScreen(VorTransmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 322; imageHeight = 205; inventoryLabelY = 1000;
    }
    @Override protected void init() {
        super.init();
        frequency = new EditBox(font, leftPos + 103, topPos + 70, 111, 20, Component.literal("VOR frequency MHz"));
        frequency.setMaxLength(7); frequency.setFilter(value -> value.matches("[0-9]{0,3}(\\.[0-9]{0,3})?"));
        frequency.setHint(Component.literal("108.000–117.950")); addRenderableWidget(frequency);
        identifier = new EditBox(font, leftPos + 103, topPos + 104, 111, 20, Component.literal("Identifier"));
        identifier.setMaxLength(4); identifier.setHint(Component.literal("2–4 letters")); addRenderableWidget(identifier);
        addRenderableWidget(Button.builder(Component.literal("SAVE"), b -> save())
                .bounds(leftPos + 103, topPos + 146, 93, 22).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(leftPos + imageWidth - 29, topPos + 10, 19, 19).build());
    }
    @Override protected void containerTick() {
        super.containerTick();
        SyncVorPayload state = menu.snapshot();
        if (state == null) return;
        if (!frequency.isFocused() && state.frequencyKhz() != seenFrequency) {
            frequency.setValue(String.format(Locale.ROOT, "%.3f", state.frequencyKhz() / 1000.0));
            seenFrequency = state.frequencyKhz();
        }
        if (!identifier.isFocused() && !state.identifier().equals(seenIdentifier)) {
            identifier.setValue(state.identifier()); seenIdentifier = state.identifier();
        }
    }
    private void save() {
        try {
            int khz = (int) Math.round(Double.parseDouble(frequency.getValue()) * 1000);
            if (khz < 108_000 || khz > 117_950) return;
            PacketDistributor.sendToServer(new SetVorPayload(menu.containerId, khz, identifier.getValue()));
            frequency.setFocused(false); identifier.setFocused(false);
        } catch (NumberFormatException ignored) { }
    }
    @Override public void onClose() { save(); super.onClose(); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((frequency.isFocused() || identifier.isFocused()) && minecraft != null
                && minecraft.options.keyInventory.matches(key, scan)) return true;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                && (frequency.isFocused() || identifier.isFocused())) { save(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF18232D);
        g.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + 37, 0xFF28516A);
        g.fill(leftPos + 16, topPos + 49, leftPos + imageWidth - 16, topPos + 184, 0xFF223747);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SyncVorPayload state = menu.snapshot();
        g.drawString(font, "VOR TRANSMITTER", 16, 17, 0xFFE4F5FF, false);
        g.drawString(font, "Frequency", 22, 76, 0xFFE4F5FF, false);
        g.drawString(font, "Identifier", 22, 110, 0xFFE4F5FF, false);
        if (state != null) {
            g.drawString(font, state.antennaConnected() ? "Antenna linked" : "Link a regular antenna", 214, 76,
                    state.antennaConnected() ? 0xFF83E9B1 : 0xFFFFC37A, false);
            g.drawString(font, state.dmeAttached() ? "DME attached" : "No DME module", 214, 110,
                    state.dmeAttached() ? 0xFF83E9B1 : 0xFFADC4CE, false);
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
