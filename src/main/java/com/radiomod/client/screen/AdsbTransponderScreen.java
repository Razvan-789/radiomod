package com.radiomod.client.screen;

import com.radiomod.menu.AdsbTransponderMenu;
import com.radiomod.network.SetAdsbTransponderPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class AdsbTransponderScreen extends AbstractContainerScreen<AdsbTransponderMenu> {
    private EditBox identifier, operator;
    public AdsbTransponderScreen(AdsbTransponderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320; imageHeight = 195; inventoryLabelY = 1000;
    }
    @Override protected void init() {
        super.init();
        identifier = new EditBox(font, leftPos + 24, topPos + 55, 272, 20, Component.literal("Identifier"));
        identifier.setMaxLength(24); identifier.setHint(Component.literal("e.g. RYR123"));
        identifier.setValue(menu.identifier());
        identifier.setResponder(value -> save());
        addRenderableWidget(identifier);
        operator = new EditBox(font, leftPos + 24, topPos + 103, 272, 20, Component.literal("Operator"));
        operator.setMaxLength(32); operator.setHint(Component.literal("e.g. Air Meridian"));
        operator.setValue(menu.operator());
        operator.setResponder(value -> save());
        addRenderableWidget(operator);
        addRenderableWidget(Button.builder(Component.literal("DONE"), b -> { save(); onClose(); })
                .bounds(leftPos + 224, topPos + 160, 72, 20).build());
    }
    private void save() {
        if (identifier != null && operator != null)
            PacketDistributor.sendToServer(new SetAdsbTransponderPayload(menu.containerId,
                    identifier.getValue(), operator.getValue()));
    }
    @Override public void onClose() { save(); super.onClose(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((identifier.isFocused() || operator.isFocused()) && minecraft != null
                && minecraft.options.keyInventory.matches(key, scan)) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF101B24);
        g.fill(leftPos + 8, topPos + 8, leftPos + imageWidth - 8, topPos + 36, 0xFF1C3645);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "ADS-B TRANSPONDER", 19, 18, 0xFF76E8D1, false);
        g.drawString(font, "Identifier", 24, 43, 0xFFEAF5F6, false);
        g.drawString(font, "Operator", 24, 91, 0xFFEAF5F6, false);
        g.drawString(font, String.format(java.util.Locale.ROOT, "HDG %03d°   SPEED %d km/h",
                menu.headingDegrees(), menu.speedKmH()), 24, 140, 0xFF76E8D1, false);
        g.drawString(font, "Active while carried or stored", 24, 169, 0xFF96B2BD, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
