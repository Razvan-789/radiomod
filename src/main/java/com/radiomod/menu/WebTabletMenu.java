package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import com.radiomod.network.SyncAdsbTabletPayload;
import com.radiomod.network.SyncAdsbRadarPayload;
import com.radiomod.signal.AdsbReception;
import com.radiomod.signal.AdsbServerNetwork;
import com.radiomod.signal.VorNetwork;
import com.radiomod.network.SyncWebTabletPayload;
import com.radiomod.network.WebTabletActionPayload;
import com.radiomod.signal.WebserverNetwork;
import com.radiomod.signal.WebserverReception;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private SyncAdsbTabletPayload adsbSnapshot;
    private boolean adsbMode;
    private String adsbDimension = "";
    private int adsbCenterX, adsbCenterZ;
    private int adsbRangeKm = 70;

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
    public SyncAdsbTabletPayload adsbSnapshot() { return adsbSnapshot; }
    public void setAdsbSnapshot(SyncAdsbTabletPayload value) {
        if (player.level().isClientSide()) adsbSnapshot = value;
    }
    public void clearAdsbSnapshot() { if (player.level().isClientSide()) adsbSnapshot = null; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount - lastSentTick >= (adsbMode ? 10 : 4)) {
            if (adsbMode) sendAdsb(serverPlayer); else send(serverPlayer);
        }
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
    private void sendAdsb(ServerPlayer serverPlayer) {
        lastSentTick = player.tickCount;
        List<SyncAdsbTabletPayload.Entry> servers = new ArrayList<>();
        ServerLevel mapLevel = serverPlayer.serverLevel();
        for (ServerLevel level : serverPlayer.getServer().getAllLevels()) {
            if (level.dimension().location().toString().equals(adsbDimension)) mapLevel = level;
            for (BlockPos pos : AdsbServerNetwork.get(level))
                if (level.getBlockEntity(pos) instanceof AdsbServerBlockEntity)
                    servers.add(new SyncAdsbTabletPayload.Entry(level.dimension().location().toString(),
                            pos.asLong(), "ADS-B " + pos.toShortString()));
        }
        servers.sort(Comparator.comparing(SyncAdsbTabletPayload.Entry::name)
                .thenComparing(SyncAdsbTabletPayload.Entry::dimension));
        if (servers.size() > 512) servers = servers.subList(0, 512);
        if (adsbDimension.isBlank() || !mapLevel.dimension().location().toString().equals(adsbDimension)) {
            adsbDimension = mapLevel.dimension().location().toString();
            centerOnNetwork(mapLevel, serverPlayer);
        }
        BlockPos center = new BlockPos(adsbCenterX, 64, adsbCenterZ);
        boolean connected = false;
        Map<String, AdsbReception.Contact> strongest = new HashMap<>();
        for (BlockPos pos : AdsbServerNetwork.get(mapLevel)) {
            if (!(mapLevel.getBlockEntity(pos) instanceof AdsbServerBlockEntity server)) continue;
            AdsbReceiverBlockEntity receiver = server.receiver();
            if (receiver == null || !receiver.hasAntennaConnected()) continue;
            connected = true;
            for (AdsbReception.Contact contact : AdsbReception.receive(receiver)) {
                String key = contact.sourceId() + "/" + contact.identifier();
                AdsbReception.Contact previous = strongest.get(key);
                if (previous == null || contact.snrDb() > previous.snrDb()) strongest.put(key, contact);
            }
        }
        List<SyncAdsbRadarPayload.Contact> contacts = new ArrayList<>();
        List<AdsbReception.Contact> sorted = strongest.values().stream()
                .sorted(Comparator.comparing(AdsbReception.Contact::identifier, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AdsbReception.Contact::sourceId)).limit(128).toList();
        for (AdsbReception.Contact contact : sorted)
            contacts.add(new SyncAdsbRadarPayload.Contact(contact.identifier(), contact.operator(), contact.carrier(),
                    contact.x(), contact.y(), contact.z(),
                    (float) ((contact.x() - center.getX()) * com.radiomod.Config.getKilometersPerBlock()),
                    (float) ((contact.z() - center.getZ()) * com.radiomod.Config.getKilometersPerBlock()),
                    (float) contact.rangeKm(), (float) contact.distanceKm(), (float) contact.snrDb(),
                    contact.headingDegrees(), (float) contact.speedKmH()));
        List<SyncAdsbTabletPayload.VorMarker> vors = new ArrayList<>();
        for (BlockPos pos : VorNetwork.get(mapLevel)) {
            if (!(mapLevel.getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor)) continue;
            vors.add(new SyncAdsbTabletPayload.VorMarker(pos.asLong(), vor.identifier(),
                    vor.frequencyKhz(), vor.hasDmeAttached(), vor.hasAntennaConnected()));
            if (vors.size() == 512) break;
        }
        vors.sort(Comparator.comparing(SyncAdsbTabletPayload.VorMarker::identifier)
                .thenComparingInt(SyncAdsbTabletPayload.VorMarker::frequencyKhz));
        adsbSnapshot = new SyncAdsbTabletPayload(containerId, List.copyOf(servers),
                adsbDimension, 0, true, connected, center.getX(), center.getY(), center.getZ(), adsbRangeKm,
                com.radiomod.Config.getKilometersPerBlock(), List.copyOf(vors),
                List.copyOf(contacts));
        PacketDistributor.sendToPlayer(serverPlayer, adsbSnapshot);
    }
    private void centerOnNetwork(ServerLevel level, ServerPlayer viewer) {
        var positions = AdsbServerNetwork.get(level);
        if (positions.isEmpty()) {
            adsbCenterX = viewer.getBlockX(); adsbCenterZ = viewer.getBlockZ();
        } else {
            adsbCenterX = (int) (positions.stream().mapToLong(BlockPos::getX).sum() / positions.size());
            adsbCenterZ = (int) (positions.stream().mapToLong(BlockPos::getZ).sum() / positions.size());
        }
    }
    public void action(ServerPlayer sender, WebTabletActionPayload request) {
        if (sender != player || request.containerId() != containerId || !stillValid(sender)) return;
        if ("adsb_mode".equals(request.action()) && localPos == null) {
            adsbMode = request.number() != 0;
            if (adsbMode && adsbDimension.isBlank()) {
                adsbDimension = sender.serverLevel().dimension().location().toString();
                centerOnNetwork(sender.serverLevel(), sender);
            }
            if (adsbMode) sendAdsb(sender); else send(sender);
            return;
        }
        if (adsbMode) {
            switch (request.action()) {
                case "adsb_dim" -> {
                    for (ServerLevel level : sender.getServer().getAllLevels()) {
                        if (!level.dimension().location().toString().equals(request.dimension())
                                || AdsbServerNetwork.get(level).isEmpty()) continue;
                        adsbDimension = request.dimension();
                        centerOnNetwork(level, sender);
                        break;
                    }
                }
                case "adsb_pan" -> {
                    adsbCenterX = Math.clamp(request.number(), -29_999_984, 29_999_984);
                    adsbCenterZ = (int) Math.clamp(request.position(), -29_999_984L, 29_999_984L);
                }
                case "adsb_range" -> {
                    if (request.number() == 1 || request.number() == 2 || request.number() == 5
                            || request.number() == 10 || request.number() == 25 || request.number() == 70
                            || request.number() == 140 || request.number() == 280) adsbRangeKm = request.number();
                }
                default -> { return; }
            }
            sendAdsb(sender);
            return;
        }
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
