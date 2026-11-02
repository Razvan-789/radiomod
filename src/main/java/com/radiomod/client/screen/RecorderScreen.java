package com.radiomod.client.screen;

import com.radiomod.menu.RecorderMenu;
import com.radiomod.network.AudioLibraryActionPayload;
import com.radiomod.network.SyncAudioLibraryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

public class RecorderScreen extends AbstractContainerScreen<RecorderMenu> {
    private long usedBytes;
    private long limitBytes;
    public RecorderScreen(RecorderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        PacketDistributor.sendToServer(new AudioLibraryActionPayload(menu.containerId, "list", "", "", 0));
        addRenderableWidget(Button.builder(Component.literal("OPEN AUDIO LIBRARY"), button ->
                        minecraft.setScreen(new AudioLibraryScreen(this, menu.containerId, true)))
                .bounds(leftPos + 30, topPos + 106, 200, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(leftPos + 96, topPos + 136, 68, 20).build());
    }

    public void setStorage(SyncAudioLibraryPayload payload) {
        if (payload.containerId() != menu.containerId) return;
        usedBytes = payload.usedBytes();
        limitBytes = payload.limitBytes();
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
        graphics.fill(leftPos + 14, topPos + 86, leftPos + 246, topPos + 95, 0xFF25313A);
        if (limitBytes > 0) {
            int filled = (int) Math.round(232.0 * Math.min(1.0, usedBytes / (double) limitBytes));
            graphics.fill(leftPos + 14, topPos + 86, leftPos + 14 + filled, topPos + 95,
                    usedBytes >= limitBytes ? 0xFFE57F73 : 0xFF6CD8B1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "RECORDER", 14, 12, TransmitterUi.TEXT, false);
        graphics.drawString(font, "Upload, rename or delete your audio files.", 14, 35, TransmitterUi.MUTED, false);
        graphics.drawString(font, "Load them from an FM or DAB station.", 14, 48, TransmitterUi.MUTED, false);
        if (limitBytes > 0) graphics.drawString(font, String.format(Locale.ROOT,
                "Free %.1f / %.0f MiB",
                Math.max(0, limitBytes - usedBytes) / 1048576.0, limitBytes / 1048576.0),
                14, 68, TransmitterUi.TEXT, false);
        else graphics.drawString(font, "Loading storage...", 14, 68, TransmitterUi.MUTED, false);
    }
}
