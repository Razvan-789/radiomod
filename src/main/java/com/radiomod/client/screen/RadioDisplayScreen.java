package com.radiomod.client.screen;

import com.radiomod.blockentity.RadioDisplayBlockEntity;
import com.radiomod.menu.RadioDisplayMenu;
import com.radiomod.network.RadioDisplayActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadioDisplayScreen extends AbstractContainerScreen<RadioDisplayMenu> {
    private static final String[] FIELDS = {"Logo", "PS / Programme", "RT / DLS", "RT+ / DLS+", "Signal", "PTY"};
    private final Button[] buttons = new Button[7];

    public RadioDisplayScreen(RadioDisplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 250;
        imageHeight = 190;
    }

    @Override protected void init() {
        super.init();
        buttons[0] = addRenderableWidget(Button.builder(Component.empty(), b -> action(0))
                .bounds(leftPos + 12, topPos + 42, 226, 20).build());
        for (int i = 0; i < 6; i++) {
            final int action = i + 1;
            buttons[action] = addRenderableWidget(Button.builder(Component.empty(), b -> action(action))
                    .bounds(leftPos + 12 + (i % 2) * 114, topPos + 69 + (i / 2) * 26, 110, 20).build());
        }
    }

    private void action(int action) {
        PacketDistributor.sendToServer(new RadioDisplayActionPayload(menu.containerId, action));
        // Reflect the choice immediately; the block update from the server remains authoritative.
        RadioDisplayBlockEntity display = menu.display();
        if (display != null) display.action(action);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RadioDisplayBlockEntity display = menu.display();
        if (display != null) {
            String[] choices = {"Logo", "PS + PTY", "Signal bars + level"};
            buttons[0].setMessage(Component.literal("1x1 view: " + choices[display.singleMode()]));
            for (int i = 0; i < 6; i++) buttons[i + 1].setMessage(Component.literal(
                    ((display.options() & (1 << i)) != 0 ? "[x] " : "[ ] ") + FIELDS[i]));
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF171D29);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3,
                topPos + imageHeight - 3, 0xFF253346);
        RadioDisplayBlockEntity display = menu.display();
        String link = display == null || display.radioPos() == null ? "No radio linked"
                : "Radio: " + display.radioPos().toShortString();
        graphics.drawString(font, link, leftPos + 12, topPos + 27, 0xFF9BB8D4, false);
        graphics.drawString(font, "Place 2 or 3 displays side by side for wider layouts.",
                leftPos + 12, topPos + 155, 0xFFDCE9F2, false);
        graphics.drawString(font, "Link a radio to any panel with the Linking Tool.",
                leftPos + 12, topPos + 168, 0xFF9BB8D4, false);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0xFFFFFFFF, false);
    }
}
