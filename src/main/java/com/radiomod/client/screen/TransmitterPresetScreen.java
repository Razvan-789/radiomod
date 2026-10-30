package com.radiomod.client.screen;

import com.radiomod.server.TransmitterPresets;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared FM and DAB preset picker. The transmitter menu remains open behind this screen. */
public class TransmitterPresetScreen extends Screen {
    private final Screen parent;
    private final IntPredicate hasPreset;
    private final IntConsumer sendButton;
    private final Runnable afterLoad;
    private final int saveBase;
    private final int loadBase;
    private final Button[] loadButtons = new Button[TransmitterPresets.SLOT_COUNT];
    private int left;
    private int top;

    public TransmitterPresetScreen(Screen parent, String type, IntPredicate hasPreset,
                                   IntConsumer sendButton, Runnable afterLoad, int saveBase, int loadBase) {
        super(Component.literal(type + " TRANSMITTER PRESETS"));
        this.parent = parent;
        this.hasPreset = hasPreset;
        this.sendButton = sendButton;
        this.afterLoad = afterLoad;
        this.saveBase = saveBase;
        this.loadBase = loadBase;
    }

    @Override
    protected void init() {
        left = (width - 320) / 2;
        top = (height - 202) / 2;
        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> onClose())
                .bounds(left + 258, top + 7, 50, 20).build());
        for (int slot = 0; slot < TransmitterPresets.SLOT_COUNT; slot++) {
            int index = slot;
            int y = top + 44 + slot * 38;
            Button save = addRenderableWidget(Button.builder(Component.literal("SAVE"), button ->
                            sendButton.accept(saveBase + index))
                    .bounds(left + 170, y, 60, 20).build());
            save.setTooltip(Tooltip.create(Component.literal("Save current settings to this personal slot; replaces its previous contents")));
            loadButtons[slot] = addRenderableWidget(Button.builder(Component.literal("LOAD"), button -> {
                        sendButton.accept(loadBase + index);
                        afterLoad.run();
                        onClose();
                    })
                    .bounds(left + 238, y, 60, 20).build());
        }
    }

    @Override
    public void tick() {
        for (int slot = 0; slot < loadButtons.length; slot++) {
            loadButtons[slot].active = hasPreset.test(slot);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        TransmitterUi.frame(graphics, left, top, 320, 202);
        graphics.drawString(font, title, left + 12, top + 13, TransmitterUi.TEXT, false);
        for (int slot = 0; slot < TransmitterPresets.SLOT_COUNT; slot++) {
            int y = top + 42 + slot * 38;
            TransmitterUi.card(graphics, left + 12, y - 2, 296, 28);
            graphics.drawString(font, "SLOT " + (slot + 1), left + 20, y + 3, TransmitterUi.ACCENT, false);
            graphics.drawString(font, hasPreset.test(slot) ? "Saved" : "Empty", left + 78, y + 3,
                    hasPreset.test(slot) ? TransmitterUi.GOOD : TransmitterUi.MUTED, false);
        }
        graphics.drawString(font, "Frequency, power and antenna link stay unchanged.",
                left + 15, top + 169, TransmitterUi.MUTED, false);
        graphics.drawString(font, "Presets are yours in this world, across transmitters.",
                left + 15, top + 182, TransmitterUi.MUTED, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
