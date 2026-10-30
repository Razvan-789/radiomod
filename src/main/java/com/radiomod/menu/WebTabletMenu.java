package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.network.SyncWebTabletPayload;
import com.radiomod.network.WebTabletActionPayload;
import com.radiomod.signal.WebserverNetwork;
import com.radiomod.signal.WebserverReception;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Remote list and shared tuner; block opening also permits local configuration. */
public class WebTabletMenu extends AbstractContainerMenu {
    private final Player player;
    @Nullable private final BlockPos localPos;
    private String selectedDimension = "";
    private long selectedPos;
    private int lastSentTick = -1000;
    private SyncWebTabletPayload snapshot;

    public WebTabletMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public WebTabletMenu(int id, Inventory inventory, @Nullable BlockPos localPos) {
        super(localPos == null ? Radio.WEB_TABLET_MENU.get() : Radio.WEBSERVER_MENU.get(), id);
        player = inventory.player;
        this.localPos = localPos;
        if (localPos != null) {
            selectedDimension = player.level().dimension().location().toString();
            selectedPos = localPos.asLong();
        }
    }
    public boolean localConfig() { return localPos != null; }
    public SyncWebTabletPayload snapshot() { return snapshot; }
    public void setSnapshot(SyncWebTabletPayload value) { if (player.level().isClientSide()) snapshot = value; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount - lastSentTick >= 4) send(serverPlayer);
    }
    private void send(ServerPlayer serverPlayer) {
        lastSentTick = player.tickCount;
        WebserverBlockEntity webserver = selected();
        List<SyncWebTabletPayload.Entry> entries = new ArrayList<>();
        if (webserver == null) {
            for (ServerLevel level : serverPlayer.getServer().getAllLevels()) {
                for (BlockPos pos : WebserverNetwork.get(level)) {
                    if (level.getBlockEntity(pos) instanceof WebserverBlockEntity listed)
                        entries.add(new SyncWebTabletPayload.Entry(level.dimension().location().toString(),
                                pos.asLong(), listed.location()));
                }
            }
            entries.sort(Comparator.comparing(SyncWebTabletPayload.Entry::location, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(SyncWebTabletPayload.Entry::dimension)
                    .thenComparingLong(SyncWebTabletPayload.Entry::pos));
            if (entries.size() > 1024) entries = entries.subList(0, 1024);
        }
        WebserverReception.Sample sample = webserver == null ? WebserverReception.Sample.EMPTY : webserver.sample();
        int[] history = webserver == null ? new int[60] : webserver.history();
        if (webserver == null) java.util.Arrays.fill(history, -1000);
        snapshot = new SyncWebTabletPayload(containerId, localConfig(), List.copyOf(entries),
                webserver == null ? "" : selectedDimension, webserver == null ? 0 : selectedPos,
                webserver != null, webserver == null ? "" : webserver.location(),
                webserver == null ? "" : webserver.backgroundUrl(),
                webserver == null ? 0x8BD15B : webserver.themeColor(),
                webserver != null && webserver.hasAntennaConnected(),
                webserver != null && webserver.drm(), webserver == null ? 101700 : webserver.frequencyKhz(),
                sample.signal(), (int) Math.round(sample.signalDbuv() * 10),
                (int) Math.round(sample.snrDb() * 10), sample.berPercent(),
                sample.ps(), sample.pi(), sample.country(), sample.rt(), sample.identified(),
                sample.stationName(), sample.txX(), sample.txY(), sample.txZ(), sample.powerW(),
                sample.azimuthDegrees(), sample.distanceMeters(), sample.logo(), history,
                sample.streamUrl(), sample.sourceUrl(), sample.afFrequenciesKhz());
        PacketDistributor.sendToPlayer(serverPlayer, snapshot);
    }
    @Nullable private WebserverBlockEntity selected() {
        if (selectedDimension.isBlank() || !(player instanceof ServerPlayer serverPlayer)) return null;
        try {
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(selectedDimension));
            ServerLevel level = serverPlayer.getServer().getLevel(key);
            BlockPos pos = BlockPos.of(selectedPos);
            return level != null && WebserverNetwork.get(level).contains(pos)
                    && level.getBlockEntity(pos) instanceof WebserverBlockEntity webserver ? webserver : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    public void action(ServerPlayer sender, WebTabletActionPayload request) {
        if (sender != player || request.containerId() != containerId || !stillValid(sender)) return;
        if ("select".equals(request.action())) {
            if (localPos != null) return;
            selectedDimension = request.dimension(); selectedPos = request.position();
            if (selected() == null) { selectedDimension = ""; selectedPos = 0; }
        } else if ("back".equals(request.action())) {
            if (localPos == null) { selectedDimension = ""; selectedPos = 0; }
        } else {
            WebserverBlockEntity webserver = selected();
            if (webserver == null) return;
            switch (request.action()) {
                case "mode" -> webserver.setMode(request.number() != 0);
                case "tune" -> webserver.setFrequencyKhz(request.number());
                case "step" -> webserver.setFrequencyKhz(webserver.frequencyKhz()
                        + (request.number() < 0 ? -1 : 1) * (webserver.drm() ? 1 : 100));
                case "location" -> { if (canConfigure(webserver)) webserver.setLocation(request.value()); }
                case "background" -> { if (canConfigure(webserver)) webserver.setBackgroundUrl(request.value()); }
                case "color" -> { if (canConfigure(webserver)) webserver.setThemeColor(request.number()); }
                default -> { return; }
            }
        }
        send(sender);
    }
    private boolean canConfigure(WebserverBlockEntity webserver) {
        return localPos != null && webserver.getLevel() == player.level()
                && localPos.equals(webserver.getBlockPos()) && stillValid(player);
    }
    @Override public boolean stillValid(Player player) {
        return localPos == null || player.level().getBlockEntity(localPos) instanceof WebserverBlockEntity
                && player.distanceToSqr(localPos.getX() + .5, localPos.getY() + .5, localPos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
