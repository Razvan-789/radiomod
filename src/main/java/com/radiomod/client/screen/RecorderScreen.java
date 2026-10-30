package com.radiomod.client.screen;

import com.radiomod.menu.RecorderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RecorderScreen extends AbstractContainerScreen<RecorderMenu> {
    public RecorderScreen(RecorderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 135;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("OPEN AUDIO LIBRARY"), button ->
                        minecraft.setScreen(new AudioLibraryScreen(this, menu.containerId, true)))
                .bounds(leftPos + 30, topPos + 67, 200, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(leftPos + 96, topPos + 101, 68, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        TransmitterUi.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "RECORDER", 14, 12, TransmitterUi.TEXT, false);
        graphics.drawString(font, "Upload, rename or delete your audio files.", 14, 35, TransmitterUi.MUTED, false);
        graphics.drawString(font, "Load them from an FM or DAB station.", 14, 48, TransmitterUi.MUTED, false);
    }
}
