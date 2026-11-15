package com.radiomod.client.screen;

import com.google.gson.JsonParser;
import com.radiomod.network.RequestStreamHistoryPayload;
import com.radiomod.network.SyncStreamHistoryPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Selects a previously used stream while keeping the transmitter/server menu open. */
public class StreamHistoryScreen extends Screen {
    private record Entry(String mode, String url, String code, String name) { }

    private final Screen parent;
    private final int containerId;
    private final Consumer<String> apply;
    private final List<Entry> entries = new ArrayList<>();
    private final Button[] rows = new Button[8];
    private Button previous, next, use;
    private int selected = -1, page, pageCount = 1, left, top;

    public StreamHistoryScreen(Screen parent, int containerId, Consumer<String> apply) {
        super(Component.literal("PREVIOUSLY USED STREAMS"));
        this.parent = parent;
        this.containerId = containerId;
        this.apply = apply;
    }

    @Override protected void init() {
        left = (width - 430) / 2;
        top = (height - 310) / 2;
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> onClose())
                .bounds(left + 362, top + 8, 56, 20).build());
        for (int i = 0; i < rows.length; i++) {
            int index = i;
            rows[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                selected = index;
                refreshRows();
            }).bounds(left + 14, top + 44 + i * 26, 402, 24).build());
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> request(page - 1))
                .bounds(left + 14, top + 258, 32, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> request(page + 1))
                .bounds(left + 384, top + 258, 32, 20).build());
        use = addRenderableWidget(Button.builder(Component.literal("USE SELECTED STREAM"), b -> {
            if (selected < 0 || selected >= entries.size()) return;
            apply.accept(entries.get(selected).url());
            onClose();
        }).bounds(left + 128, top + 281, 174, 20).build());
        refreshRows();
        request(0);
    }

    private void request(int requestedPage) {
        if (requestedPage < 0 || requestedPage >= pageCount) return;
        PacketDistributor.sendToServer(new RequestStreamHistoryPayload(containerId, requestedPage));
    }

    public static void receive(SyncStreamHistoryPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof StreamHistoryScreen screen && screen.containerId == payload.containerId())
            screen.update(payload);
    }

    private void update(SyncStreamHistoryPayload payload) {
        page = payload.page();
        pageCount = payload.pageCount();
        selected = -1;
        entries.clear();
        try {
            for (var value : JsonParser.parseString(payload.json()).getAsJsonArray()) {
                var entry = value.getAsJsonObject();
                entries.add(new Entry(entry.get("mode").getAsString(), entry.get("url").getAsString(),
                        entry.get("code").getAsString(), entry.get("name").getAsString()));
            }
        } catch (RuntimeException ignored) { entries.clear(); }
        refreshRows();
    }

    private void refreshRows() {
        for (int i = 0; i < rows.length; i++) {
            Button button = rows[i];
            if (button == null) continue;
            button.visible = i < entries.size();
            if (button.visible) {
                Entry entry = entries.get(i);
                String label = (selected == i ? "▶ " : "  ") + entry.mode() + " · " + entry.name()
                        + (entry.code().isBlank() ? "" : " (" + entry.code() + ")");
                button.setMessage(Component.literal(font.plainSubstrByWidth(label, 382)));
                button.setTooltip(Tooltip.create(Component.literal(entry.url())));
            }
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = page + 1 < pageCount;
        if (use != null) use.active = selected >= 0 && selected < entries.size();
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        TransmitterUi.frame(graphics, left, top, 430, 310);
        graphics.drawString(font, title, left + 14, top + 14, TransmitterUi.TEXT, false);
        graphics.drawString(font, entries.isEmpty() ? "No streams used yet" : "Choose a stream by station name and PI/SID",
                left + 15, top + 32, TransmitterUi.MUTED, false);
        graphics.drawString(font, "Page " + (page + 1) + " / " + pageCount,
                left + 180, top + 264, TransmitterUi.TEXT, false);
        if (selected >= 0 && selected < entries.size()) {
            String url = font.plainSubstrByWidth(entries.get(selected).url(), 390);
            graphics.drawString(font, url, left + 15, top + 243, TransmitterUi.MUTED, false);
        }
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
