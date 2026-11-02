package com.radiomod.client.screen;

import com.radiomod.client.LocalAudioUpload;
import com.radiomod.network.AudioLibraryActionPayload;
import com.radiomod.network.SyncAudioLibraryPayload;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Shared server-owned audio catalog. The station or recorder menu stays open behind it. */
public class AudioLibraryScreen extends Screen {
    private record Entry(String id, String name, long bytes) {}

    private final Screen parent;
    private final int containerId;
    private final boolean manage;
    private final List<Entry> entries = new ArrayList<>();
    private final Button[] rows = new Button[8];
    private Button previous;
    private Button next;
    private Button load;
    private Button rename;
    private Button delete;
    private EditBox nameInput;
    private String selectedId = "";
    private boolean confirmDelete;
    private int page;
    private int pageCount = 1;
    private long usedBytes, limitBytes;
    private int left;
    private int top;

    public AudioLibraryScreen(Screen parent, int containerId, boolean manage) {
        super(Component.literal(manage ? "RECORDER · AUDIO LIBRARY" : "SELECT UPLOADED AUDIO"));
        this.parent = parent;
        this.containerId = containerId;
        this.manage = manage;
    }

    @Override
    protected void init() {
        left = (width - 360) / 2;
        top = (height - 300) / 2;
        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> onClose())
                .bounds(left + 292, top + 8, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH"), button -> request(page))
                .bounds(left + 226, top + 8, 62, 20).build());
        for (int index = 0; index < rows.length; index++) {
            int row = index;
            rows[index] = addRenderableWidget(Button.builder(Component.empty(), button -> selectRow(row))
                    .bounds(left + 14, top + 45 + index * 23, 332, 21).build());
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> request(page - 1))
                .bounds(left + 14, top + 234, 28, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> request(page + 1))
                .bounds(left + 318, top + 234, 28, 20).build());
        if (manage) {
            nameInput = new EditBox(font, left + 14, top + 267, 150, 18, Component.literal("Audio name"));
            nameInput.setMaxLength(80);
            nameInput.setHint(Component.literal("Name for selected audio"));
            addRenderableWidget(nameInput);
            rename = addRenderableWidget(Button.builder(Component.literal("RENAME"), button -> {
                        if (!selectedId.isEmpty() && !nameInput.getValue().isBlank()) {
                            send("rename", selectedId, nameInput.getValue());
                        }
                    }).bounds(left + 169, top + 265, 66, 20).build());
            delete = addRenderableWidget(Button.builder(Component.literal("DELETE"), button -> {
                        if (selectedId.isEmpty()) return;
                        if (!confirmDelete) {
                            confirmDelete = true;
                            delete.setMessage(Component.literal("CONFIRM"));
                        } else {
                            send("delete", selectedId, "");
                            selectedId = "";
                            confirmDelete = false;
                            delete.setMessage(Component.literal("DELETE"));
                        }
                    }).bounds(left + 239, top + 265, 67, 20).build());
            addRenderableWidget(Button.builder(Component.literal("UPLOAD"), button ->
                            LocalAudioUpload.chooseAndUpload(containerId, "library"))
                    .bounds(left + 310, top + 265, 44, 20).build());
        } else {
            load = addRenderableWidget(Button.builder(Component.literal("LOAD SELECTED AUDIO"), button -> {
                        if (!selectedId.isEmpty()) {
                            send("select", selectedId, "");
                            onClose();
                        }
                    }).bounds(left + 82, top + 264, 196, 22).build());
        }
        refreshRows();
        request(page);
    }

    private void selectRow(int index) {
        if (index >= entries.size()) return;
        Entry entry = entries.get(index);
        selectedId = entry.id();
        confirmDelete = false;
        if (delete != null) delete.setMessage(Component.literal("DELETE"));
        if (nameInput != null) nameInput.setValue(entry.name());
        refreshRows();
    }

    private void request(int requestedPage) {
        if (requestedPage < 0 || requestedPage >= pageCount) return;
        send("list", "", "", requestedPage);
    }

    private void send(String action, String id, String name) {
        send(action, id, name, page);
    }

    private void send(String action, String id, String name, int requestedPage) {
        PacketDistributor.sendToServer(new AudioLibraryActionPayload(containerId, action, id, name, requestedPage));
    }

    public static void receive(SyncAudioLibraryPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AudioLibraryScreen screen && screen.containerId == payload.containerId()) {
            screen.update(payload);
        } else if (minecraft.screen instanceof RecorderScreen recorder) {
            recorder.setStorage(payload);
        }
    }

    private void update(SyncAudioLibraryPayload payload) {
        page = payload.page();
        pageCount = payload.pageCount();
        usedBytes = payload.usedBytes();
        limitBytes = payload.limitBytes();
        if (parent instanceof RecorderScreen recorder) recorder.setStorage(payload);
        entries.clear();
        for (String line : payload.entries().split("\\n")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) continue;
            try {
                entries.add(new Entry(parts[0], new String(Base64.getUrlDecoder().decode(parts[2]),
                        StandardCharsets.UTF_8), Long.parseLong(parts[1])));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (entries.stream().noneMatch(entry -> entry.id().equals(selectedId))) {
            selectedId = "";
        }
        refreshRows();
    }

    private void refreshRows() {
        for (int index = 0; index < rows.length; index++) {
            Button button = rows[index];
            if (button == null) continue;
            boolean present = index < entries.size();
            button.visible = present;
            if (present) {
                Entry entry = entries.get(index);
                String name = font.plainSubstrByWidth(entry.name(), 230);
                String size = entry.bytes() < 1048576
                        ? String.format(java.util.Locale.ROOT, "%.0f KiB", Math.max(1.0, entry.bytes() / 1024.0))
                        : String.format(java.util.Locale.ROOT, "%.1f MiB", entry.bytes() / 1048576.0);
                button.setMessage(Component.literal((entry.id().equals(selectedId) ? "▶ " : "  ")
                        + name + "   ·   " + size));
            }
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = page + 1 < pageCount;
        if (load != null) load.active = !selectedId.isEmpty();
        if (rename != null) rename.active = !selectedId.isEmpty();
        if (delete != null) delete.active = !selectedId.isEmpty();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        TransmitterUi.frame(graphics, left, top, 360, 300);
        graphics.drawString(font, title, left + 14, top + 14, TransmitterUi.TEXT, false);
        graphics.drawString(font, entries.isEmpty() ? "No uploaded audio on this page" : "Your audio in this world",
                left + 15, top + 33, TransmitterUi.MUTED, false);
        graphics.drawString(font, "Page " + (page + 1) + " / " + pageCount,
                left + 151, top + 240, TransmitterUi.TEXT, false);
        if (limitBytes > 0) graphics.drawString(font, String.format(java.util.Locale.ROOT,
                "%.1f MiB free of %.0f MiB", Math.max(0, limitBytes - usedBytes) / 1048576.0,
                limitBytes / 1048576.0), left + 14, top + 252, TransmitterUi.MUTED, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameInput != null && nameInput.isFocused() && minecraft != null
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && nameInput != null && nameInput.isFocused() && !selectedId.isEmpty()) {
            send("rename", selectedId, nameInput.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
