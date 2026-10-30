package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.network.RegistryActionPayload;
import com.radiomod.network.RegistryFilterPayload;
import com.radiomod.network.SyncRegistryPayload;
import com.radiomod.signal.RegistryCountries;
import com.radiomod.signal.SignalNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class RegistryMenu extends AbstractContainerMenu {
    private static final int PAGE_SIZE = 8;
    private final Player owner;
    private int page;
    private int sort;
    private boolean descending;
    private String query = "";
    private int minFrequencyKhz = -1, maxFrequencyKhz = -1, minPowerW = -1, maxPowerW = -1;
    private int filterMode;
    private SyncRegistryPayload snapshot;

    public RegistryMenu(int id, Inventory inventory) {
        super(Radio.REGISTRY_MENU.get(), id);
        owner = inventory.player;
    }

    public SyncRegistryPayload snapshot() { return snapshot; }
    public void setSnapshot(SyncRegistryPayload snapshot) { this.snapshot = snapshot; }

    public void action(ServerPlayer player, RegistryActionPayload request) {
        if (player != owner || player.containerMenu != this || request.containerId() != containerId
                || !stillValid(player)) return;
        switch (request.action()) {
            case "sort" -> {
                if (request.value() >= 0 && request.value() <= 4) {
                    if (sort == request.value()) descending = !descending;
                    else { sort = request.value(); descending = false; }
                    page = 0;
                }
            }
            case "page" -> page = Math.max(0, request.value());
            case "country" -> RegistryCountries.get(player.getServer()).set(request.value(), request.text());
            case "refresh" -> { }
            default -> { return; }
        }
        send(player);
    }

    public void filters(ServerPlayer player, RegistryFilterPayload request) {
        if (player != owner || player.containerMenu != this || request.containerId() != containerId
                || !stillValid(player)) return;
        query = request.query().strip().toLowerCase(Locale.ROOT);
        minFrequencyKhz = validNonnegative(request.minFrequencyKhz(), 2_000_000);
        maxFrequencyKhz = validNonnegative(request.maxFrequencyKhz(), 2_000_000);
        minPowerW = validNonnegative(request.minPowerW(), 1_000_000);
        maxPowerW = validNonnegative(request.maxPowerW(), 1_000_000);
        filterMode = Math.max(0, Math.min(8, request.mode()));
        page = 0;
        send(player);
    }

    private static int validNonnegative(int value, int max) {
        return value < 0 ? -1 : Math.min(value, max);
    }

    public void send(ServerPlayer player) {
        RegistryCountries countries = RegistryCountries.get(player.getServer());
        List<SyncRegistryPayload.Entry> all = new ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
                if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm) {
                    String code = fm.getRdsPi();
                    String ps = !fm.isRdsEnabled() ? "" : fm.getRdsLongPs().isBlank() ? fm.getRdsPs() : fm.getRdsLongPs();
                    if (ps.length() > 8 && fm.getRdsLongPs().isBlank()) ps = ps.substring(0, 8);
                    add(all, "FM", "", fm.getFrequencyKhz(), dimension, pos, fm.getPowerW(),
                            ps.isBlank() ? "Unnamed FM" : ps.strip(), source(fm.getStreamUrl()), code, countries);
                } else if (level.getBlockEntity(pos) instanceof DabTransmitterBlockEntity dab) {
                    DabServerBlockEntity.DabEnsembleDefinition mux = dab.resolveBroadcastMux();
                    if (mux == null || mux.stations().isEmpty()) {
                        add(all, dab.getDabVersion().getLabel(), "", dab.getFrequencyKhz(), dimension, pos, dab.getPowerW(),
                                dab.getEnsembleName().isBlank() ? "No multiplex" : dab.getEnsembleName(),
                                "No active services", "", countries);
                    } else for (DabServerBlockEntity.DabStationDefinition station : mux.stations()) {
                        add(all, dab.getDabVersion().getLabel(), "", dab.getFrequencyKhz(), dimension, pos, dab.getPowerW(),
                                station.label(), mux.name() + " · " + source(station.streamUrl()), station.sid(), countries);
                    }
                } else if (level.getBlockEntity(pos) instanceof DrmTransmitterBlockEntity drm) {
                    add(all, "DRM", drm.getModeLabel(), drm.getFrequencyKhz(), dimension, pos, drm.getPowerW(),
                            drm.getServiceLabel(), drm.getModeLabel() + " · " + source(drm.getStreamUrl()),
                            "", countries);
                }
            }
        }
        all.removeIf(entry -> !matches(entry));
        Comparator<SyncRegistryPayload.Entry> comparator = switch (sort) {
            case 1 -> Comparator.comparingInt(SyncRegistryPayload.Entry::frequencyKhz);
            case 2 -> Comparator.comparing(SyncRegistryPayload.Entry::programme, String.CASE_INSENSITIVE_ORDER);
            case 3 -> Comparator.comparing(SyncRegistryPayload.Entry::country, String.CASE_INSENSITIVE_ORDER);
            case 4 -> Comparator.comparingInt(SyncRegistryPayload.Entry::powerW);
            default -> Comparator.comparing(SyncRegistryPayload.Entry::type);
        };
        if (descending) comparator = comparator.reversed();
        all.sort(comparator.thenComparing(SyncRegistryPayload.Entry::dimension)
                .thenComparingInt(SyncRegistryPayload.Entry::x)
                .thenComparingInt(SyncRegistryPayload.Entry::y)
                .thenComparingInt(SyncRegistryPayload.Entry::z)
                .thenComparing(SyncRegistryPayload.Entry::programme));
        int pageCount = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pageCount - 1);
        List<String> names = List.of(countries.names());
        PacketDistributor.sendToPlayer(player, new SyncRegistryPayload(containerId, page, pageCount, all.size(), sort, descending,
                List.copyOf(all.subList(page * PAGE_SIZE, Math.min(all.size(), (page + 1) * PAGE_SIZE))), names));
    }

    private boolean matches(SyncRegistryPayload.Entry entry) {
        if (minFrequencyKhz >= 0 && entry.frequencyKhz() < minFrequencyKhz) return false;
        if (maxFrequencyKhz >= 0 && entry.frequencyKhz() > maxFrequencyKhz) return false;
        if (minPowerW >= 0 && entry.powerW() < minPowerW) return false;
        if (maxPowerW >= 0 && entry.powerW() > maxPowerW) return false;
        if (!query.isBlank() && !entry.programme().toLowerCase(Locale.ROOT).contains(query)
                && !entry.code().toLowerCase(Locale.ROOT).contains(query)) return false;
        return switch (filterMode) {
            case 1 -> entry.type().equals("FM");
            case 2 -> entry.type().equals("DAB");
            case 3 -> entry.type().equals("DAB+");
            case 4 -> entry.type().equals("DRM");
            case 5, 6, 7, 8 -> entry.type().equals("DRM")
                    && entry.mode().equals("ABCD".substring(filterMode - 5, filterMode - 4));
            default -> true;
        };
    }

    private static void add(List<SyncRegistryPayload.Entry> list, String type, String mode, int frequency, String dimension,
                            BlockPos pos, int power, String programme, String detail, String code,
                            RegistryCountries countries) {
        list.add(new SyncRegistryPayload.Entry(type, mode, frequency, dimension, pos.getX(), pos.getY(), pos.getZ(),
                power, limit(programme, 48), limit(detail, 80), limit(code, 16), countries.countryFor(code)));
    }

    private static String source(String url) {
        if (url == null || url.isBlank()) return "No audio";
        return url.startsWith("radio-file://") ? "Uploaded audio" : "Live stream";
    }

    private static String limit(String text, int max) {
        return text == null ? "" : text.substring(0, Math.min(max, text.length()));
    }

    @Override public boolean stillValid(Player player) {
        return player.getInventory().contains(new ItemStack(Radio.REGISTRY_ITEM.get()));
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
