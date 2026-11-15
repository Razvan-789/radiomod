package com.radiomod.client.screen;

import com.radiomod.network.SiteNameActionPayload;
import com.radiomod.network.SyncSiteNamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Edits the shared name of a connected 50-block transmitter site. */
public class SiteNameScreen extends Screen {
    private final Screen parent;
    private final int containerId;
    private EditBox input;
    private int left, top, panelWidth;

    public SiteNameScreen(Screen parent, int containerId) {
        super(Component.literal("TRANSMITTER SITE NAME"));
        this.parent = parent;
        this.containerId = containerId;
    }

    @Override protected void init() {
        panelWidth = Math.min(370, width - 16);
        left = (width - panelWidth) / 2;
        top = (height - 152) / 2;
        input = new EditBox(font, left + 14, top + 55, panelWidth - 28, 20, Component.literal("Site name"));
        input.setMaxLength(64);
        input.setHint(Component.literal("e.g. North Hill Transmitter Site"));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("SAVE"), button -> save(input.getValue()))
                .bounds(left + 14, top + 91, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLEAR NAME"), button -> save(""))
                .bounds(left + 114, top + 91, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> onClose())
                .bounds(left + panelWidth - 70, top + 91, 56, 20).build());
        PacketDistributor.sendToServer(new SiteNameActionPayload(containerId, false, ""));
    }

    private void save(String value) {
        input.setFocused(false);
        PacketDistributor.sendToServer(new SiteNameActionPayload(containerId, true, value));
    }

    public static void receive(SyncSiteNamePayload payload) {
        Minecraft game = Minecraft.getInstance();
        if (game.screen instanceof SiteNameScreen screen && screen.containerId == payload.containerId()
                && (!screen.input.isFocused() || screen.input.getValue().isEmpty()))
            screen.input.setValue(payload.name());
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save(input.getValue());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        TransmitterUi.frame(graphics, left, top, panelWidth, 152);
        graphics.drawString(font, title, left + 14, top + 14, TransmitterUi.TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(
                        "Shared by connected FM, DAB and DRM transmitters within 50 blocks.", panelWidth - 28),
                left + 14, top + 36, TransmitterUi.MUTED, false);
        graphics.drawString(font, "Clearing the name makes this site unnamed.",
                left + 14, top + 125, TransmitterUi.MUTED, false);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
