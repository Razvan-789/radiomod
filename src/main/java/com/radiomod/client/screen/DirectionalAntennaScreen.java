package com.radiomod.client.screen;

import com.radiomod.menu.DirectionalAntennaMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DirectionalAntennaScreen extends AbstractContainerScreen<DirectionalAntennaMenu> {
    private static final String[] DIRECTIONS = {
            "0° East / 360°", "45° Southeast", "90° South", "135° Southwest",
            "180° West", "225° Northwest", "270° North", "315° Northeast"
    };

    public DirectionalAntennaScreen(DirectionalAntennaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 258;
        imageHeight = 256;
    }

    @Override protected void init() {
        super.init();
        for (int i = 0; i < DIRECTIONS.length; i++) {
            final int sector = i;
            int y = topPos + 44 + i * 23;
            addRenderableWidget(Button.builder(Component.literal("−"), button -> changeGain(sector * 2))
                    .bounds(leftPos + 170, y, 27, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> changeGain(sector * 2 + 1))
                    .bounds(leftPos + 222, y, 27, 18).build());
        }
    }

    private void changeGain(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF10171D);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4,
                topPos + imageHeight - 4, 0xFF223542);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int y = topPos + 41 + i * 23;
            graphics.fill(leftPos + 10, y, leftPos + imageWidth - 10, y + 22,
                    i % 2 == 0 ? 0xFF18232C : 0xFF1C2B35);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 12, 0xFFF0F5F8, false);
        graphics.drawString(font, "Gain by compass direction (dB)", 12, 27, 0xFF70C2DC, false);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int y = 49 + i * 23;
            graphics.drawString(font, DIRECTIONS[i], 14, y, 0xFFF0F5F8, false);
            int gain = menu.gainAt(i);
            graphics.drawCenteredString(font, (gain > 0 ? "+" : "") + gain,
                    210, y, gain >= 0 ? 0xFF70C2DC : 0xFFFFB982);
        }
        graphics.drawString(font, "Each press moves up to 3 dB (−24 to +8).", 12, 231, 0xFFA3BBC7, false);
        graphics.drawString(font, "Opposite sectors share the gain budget.", 12, 243, 0xFFA3BBC7, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
