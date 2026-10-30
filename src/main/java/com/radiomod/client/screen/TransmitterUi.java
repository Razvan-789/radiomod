package com.radiomod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/** Shared panel styling for the FM and DAB transmitter controls. */
final class TransmitterUi {
    static final int TEXT = 0xFFEAF4F8;
    static final int MUTED = 0xFFA3BBC7;
    static final int ACCENT = 0xFF70C2DC;
    static final int GOOD = 0xFF83D8A4;
    static final int BAD = 0xFFFF9B92;

    private TransmitterUi() {
    }

    static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF0D141B);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF182630);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 31, 0xFF223844);
        graphics.fill(x + 12, y + 30, x + width - 12, y + 31, 0xFF456A79);
    }

    static void card(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF0D1C25);
        graphics.fill(x, y, x + 2, y + height, 0xFF365767);
    }

    static void status(GuiGraphics graphics, Font font, String label, int x, int y, boolean good) {
        int color = good ? GOOD : BAD;
        graphics.fill(x, y + 2, x + 6, y + 8, color);
        graphics.drawString(font, label, x + 11, y, color, false);
    }
}
