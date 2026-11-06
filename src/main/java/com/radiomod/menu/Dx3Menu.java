package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.item.RadioItem;
import com.radiomod.item.TechnisatRadioItem;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.TechnisatReceiverBlockEntity;
import com.radiomod.network.SyncDx3ReceiverStatePayload;
import com.radiomod.signal.Dx3ReceiverCatalog;
import com.radiomod.network.Dx3Station;
import com.radiomod.network.SelectDx3StationPayload;
import com.radiomod.network.SyncDx3CatalogPayload;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.network.SyncDx3LogoPayload;
import com.radiomod.network.SyncTechnisatStatusPayload;
import com.radiomod.signal.Dx3Catalog;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.DrmReception;
import com.radiomod.signal.RdsTextDecoding;
import com.radiomod.signal.TechnisatReception;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class Dx3Menu extends AbstractContainerMenu {
    private final Player player;
    private final BlockPos receiverPos;
    private final boolean technisat;
    private SyncDx3ReceiverStatePayload receiverState;
    private List<Dx3Station> stations = List.of();
    private int lastCatalogHash;
    private int lastScanTick = -1000;
    private int lastSentTick = -1000;
    private boolean sentCatalog;
    private SyncDx3PlayerPayload playerInfo = new SyncDx3PlayerPayload(0, false, "", "", "", 0, 0, false, "", 0, "");
    private SyncTechnisatStatusPayload technisatStatus;
    private String ctSource = "";
    private boolean ctDecoded;
    private int lastPlayerInfoTick = -1000;
    private int[] playerLogo;
    private String logoSource = "";
    private boolean scanningDab;
    private int scanChannelIndex;
    private int scanStationCount;
    private int scanNextTick;
    private int scanPauseChannel = -1;
    private List<Dx3Station> scanCandidates = List.of();
    private final List<Dx3Station> scanFound = new ArrayList<>();
    private final List<List<Dx3Station>> scanPendingMuxes = new ArrayList<>();

    public Dx3Menu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player);
    }

    public Dx3Menu(int containerId, Inventory inventory, Player player) {
        this(containerId, inventory, player, false);
    }

    public Dx3Menu(int containerId, Inventory inventory, Player player, boolean technisat) {
        super(technisat ? Radio.TECHNISAT_MENU.get() : Radio.DX3_MENU.get(), containerId);
        this.player = player;
        receiverPos = null;
        this.technisat = technisat;
        if (technisat && !player.level().isClientSide()) {
            stations = technisatVisible(null);
        }
    }

    public Dx3Menu(int containerId, Inventory inventory, Player player, Dx3ReceiverBlockEntity receiver) {
        this(containerId, inventory, player, receiver, false);
    }

    public Dx3Menu(int containerId, Inventory inventory, Player player, Dx3ReceiverBlockEntity receiver,
                   boolean technisat) {
        this(containerId, inventory, player, receiver, technisat,
                technisat ? Radio.TECHNISAT_RECEIVER_MENU.get() : Radio.DX3_RECEIVER_MENU.get());
    }

    public Dx3Menu(int containerId, Inventory inventory, Player player, Dx3ReceiverBlockEntity receiver,
                   boolean technisat, MenuType<?> menuType) {
        super(menuType, containerId);
        this.player = player;
        receiverPos = receiver.getBlockPos();
        this.technisat = technisat;
        if (technisat && receiver instanceof TechnisatReceiverBlockEntity radio)
            stations = technisatVisible(radio);
    }

    public Dx3Menu(int containerId, Inventory inventory, BlockPos receiverPos) {
        this(containerId, inventory, receiverPos, false);
    }

    public Dx3Menu(int containerId, Inventory inventory, BlockPos receiverPos, boolean technisat) {
        this(containerId, inventory, receiverPos, technisat,
                technisat ? Radio.TECHNISAT_RECEIVER_MENU.get() : Radio.DX3_RECEIVER_MENU.get());
    }

    public Dx3Menu(int containerId, Inventory inventory, BlockPos receiverPos,
                   boolean technisat, MenuType<?> menuType) {
        super(menuType, containerId);
        player = inventory.player;
        this.receiverPos = receiverPos;
        this.technisat = technisat;
    }

    public boolean isReceiver() { return receiverPos != null; }
    public BlockPos receiverPos() { return receiverPos; }
    public boolean isTechnisat() { return technisat; }
    public SyncTechnisatStatusPayload getTechnisatStatus() { return technisatStatus; }
    public void setTechnisatStatus(SyncTechnisatStatusPayload status) {
        if (technisat && player.level().isClientSide()) technisatStatus = status;
    }

    private Dx3ReceiverBlockEntity receiver() {
        return receiverPos != null && player.level().getBlockEntity(receiverPos) instanceof Dx3ReceiverBlockEntity be ? be : null;
    }

    private int beMode() {
        Dx3ReceiverBlockEntity be = receiver();
        return be instanceof TechnisatReceiverBlockEntity radio ? radio.getRadioMode()
                : RadioItem.getMode(player);
    }

    private List<Dx3Station> technisatVisible(Dx3ReceiverBlockEntity be) {
        if (be instanceof TechnisatReceiverBlockEntity radio) {
            return radio.getRadioMode() == RadioItem.MODE_DAB
                    ? TechnisatReception.visible(radio, radio.scannedStations())
                    : radio.getRadioMode() == RadioItem.MODE_DRM
                            ? TechnisatReception.visibleDrm(radio, radio.scannedDrmStations()) : List.of();
        }
        return RadioItem.getMode(player) == RadioItem.MODE_DAB
                ? TechnisatReception.visible(player, TechnisatRadioItem.scannedStations(player))
                : RadioItem.getMode(player) == RadioItem.MODE_DRM
                        ? TechnisatReception.visibleDrm(player, TechnisatRadioItem.scannedDrmStations(player))
                        : List.of();
    }

    public SyncDx3ReceiverStatePayload getReceiverState() { return receiverState; }

    public void setReceiverState(SyncDx3ReceiverStatePayload state) {
        if (isReceiver() && player.level().isClientSide()) receiverState = state;
    }

    @Override
    public void broadcastChanges() {
        if (scanningDab) advanceDabScan();
        if (isReceiver()) {
            broadcastReceiverChanges();
            super.broadcastChanges();
            return;
        }
        if (technisat && player instanceof ServerPlayer serverPlayer
                && (!sentCatalog || player.tickCount - lastScanTick >= 20)) {
            lastScanTick = player.tickCount;
            List<Dx3Station> visible = technisatVisible(null);
            if (!sentCatalog || !visible.equals(stations)) {
                stations = visible;
                sentCatalog = true;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3CatalogPayload(containerId, stations));
            }
        }
        if (!technisat && player instanceof ServerPlayer serverPlayer && player.tickCount - lastScanTick >= 20) {
            lastScanTick = player.tickCount;
            List<Dx3Station> current = Dx3Catalog.scan(player);
            int hash = catalogHash(current);
            stations = current;
            if (!sentCatalog || hash != lastCatalogHash || player.tickCount - lastSentTick >= 200) {
                lastCatalogHash = hash;
                lastSentTick = player.tickCount;
                sentCatalog = true;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3CatalogPayload(containerId, stations));
            }
        }
        if (player instanceof ServerPlayer serverPlayer && player.tickCount - lastPlayerInfoTick >= 5) {
            lastPlayerInfoTick = player.tickCount;
            sendPlayerInfo(serverPlayer);
        }
        if (technisat && player instanceof ServerPlayer serverPlayer) sendTechnisatStatus(serverPlayer);
        super.broadcastChanges();
    }

    private void broadcastReceiverChanges() {
        Dx3ReceiverBlockEntity be = receiver();
        if (!(player instanceof ServerPlayer serverPlayer) || be == null) return;
        if (technisat && be instanceof TechnisatReceiverBlockEntity radio
                && (!sentCatalog || player.tickCount - lastScanTick >= 20)) {
            lastScanTick = player.tickCount;
            List<Dx3Station> visible = technisatVisible(radio);
            if (!sentCatalog || !visible.equals(stations)) {
                stations = visible;
                sentCatalog = true;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3CatalogPayload(containerId, stations));
            }
        }
        if (!technisat && player.tickCount - lastScanTick >= 20) {
            lastScanTick = player.tickCount;
            List<Dx3Station> current = Dx3ReceiverCatalog.scan(be);
            int hash = catalogHash(current);
            stations = current;
            if (!sentCatalog || hash != lastCatalogHash || player.tickCount - lastSentTick >= 200) {
                lastCatalogHash = hash; lastSentTick = player.tickCount; sentCatalog = true;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3CatalogPayload(containerId, stations));
            }
        }
        if (player.tickCount - lastPlayerInfoTick >= 5) {
            lastPlayerInfoTick = player.tickCount;
            boolean drm = technisat && be instanceof TechnisatReceiverBlockEntity radio
                    && radio.getRadioMode() == RadioItem.MODE_DRM;
            if (drm) RdsTextDecoding.clearReceiver(be);
            Dx3ReceiverCatalog.Sample sample = drm ? Dx3ReceiverCatalog.Sample.EMPTY : Dx3ReceiverCatalog.sample(be);
            SyncDx3PlayerPayload info = sample.info();
            SyncDx3PlayerPayload next;
            if (drm && be instanceof TechnisatReceiverBlockEntity radio) {
                next = drmPlayerInfo(DrmReception.sampleReceiver(radio));
            } else next = new SyncDx3PlayerPayload(containerId, info.active(), info.ps(),
                    info.longPs(), info.rt(), info.pty(), info.signalBars(), info.ct(), info.time(),
                    info.bitrateKbps(), info.codec(), info.rds());
            if (!next.equals(playerInfo)) {
                playerInfo = next;
                PacketDistributor.sendToPlayer(serverPlayer, next);
            }
            int[] nextLogo = technisat && be instanceof TechnisatReceiverBlockEntity radio
                    && radio.getRadioMode() == RadioItem.MODE_DRM ? null : sample.logo();
            if (!Arrays.equals(nextLogo, playerLogo)) {
                playerLogo = nextLogo;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3LogoPayload(containerId, playerLogo));
            }
            SyncDx3ReceiverStatePayload state = new SyncDx3ReceiverStatePayload(containerId, be.isDab(),
                    be.getFrequencyKhz(), be.getChannelIndex(), be.getSid(), be.getMux(), be.getPi(),
                    be.getLinkMode(), be.isMuted(), be.hasAntennaConnected());
            if (!state.equals(receiverState)) {
                receiverState = state;
                PacketDistributor.sendToPlayer(serverPlayer, state);
            }
        }
        if (technisat) sendTechnisatStatus(serverPlayer);
    }

    public void requestCatalog() {
        if (technisat && beMode() == RadioItem.MODE_FM) return;
        if (technisat && beMode() == RadioItem.MODE_DAB) {
            startDabScan();
            return;
        }
        if (isReceiver()) {
            Dx3ReceiverBlockEntity be = receiver();
            if (be != null && player instanceof ServerPlayer serverPlayer) {
                if (technisat && be instanceof TechnisatReceiverBlockEntity radio
                        && radio.getRadioMode() == RadioItem.MODE_DRM) {
                    radio.storeDrmScan(TechnisatReception.scanDrm(radio));
                    stations = technisatVisible(radio);
                } else {
                    stations = Dx3ReceiverCatalog.scan(be);
                    if (technisat && be instanceof TechnisatReceiverBlockEntity radio) {
                        radio.storeScan(stations.stream().filter(Dx3Station::dab).toList());
                        stations = technisatVisible(radio);
                    }
                }
                lastCatalogHash = catalogHash(stations);
                lastScanTick = player.tickCount;
                lastSentTick = player.tickCount;
                sentCatalog = true;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3CatalogPayload(containerId, stations));
                lastPlayerInfoTick = -1000;
                broadcastReceiverChanges();
            }
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (technisat && RadioItem.getMode(player) == RadioItem.MODE_DRM) {
                TechnisatRadioItem.storeDrmScan(player, TechnisatReception.scanDrm(player));
                stations = technisatVisible(null);
            } else {
                stations = Dx3Catalog.scan(player);
                if (technisat) {
                    TechnisatRadioItem.storeScan(player,
                            stations.stream().filter(Dx3Station::dab).toList());
                    stations = technisatVisible(null);
                }
            }
            lastCatalogHash = catalogHash(stations);
            lastScanTick = player.tickCount;
            lastSentTick = player.tickCount;
            sentCatalog = true;
            PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3CatalogPayload(containerId, stations));
            sendPlayerInfo(serverPlayer);
            if (technisat) sendTechnisatStatus(serverPlayer);
        }
    }

    private void startDabScan() {
        Dx3ReceiverBlockEntity be = receiver();
        if (isReceiver() && be == null || !(player instanceof ServerPlayer serverPlayer)) return;
        scanCandidates = (isReceiver() ? Dx3ReceiverCatalog.scan(be) : Dx3Catalog.scan(player))
                .stream().filter(Dx3Station::dab)
                .sorted(Comparator.comparingInt(Dx3Station::tuning)).limit(128).toList();
        scanFound.clear();
        scanPendingMuxes.clear();
        scanChannelIndex = 0;
        scanPauseChannel = -1;
        scanStationCount = 0;
        scanNextTick = player.tickCount + 4;
        scanningDab = true;
        sendTechnisatStatus(serverPlayer);
    }

    private void advanceDabScan() {
        if (player.tickCount < scanNextTick) return;
        if (beMode() != RadioItem.MODE_DAB || !technisat) {
            scanningDab = false;
            scanCandidates = List.of();
            return;
        }
        if (scanPauseChannel >= 0) {
            scanPauseChannel = -1;
            if (!scanPendingMuxes.isEmpty()) acceptNextMux();
            else {
                scanChannelIndex++;
                if (scanChannelIndex >= DabChannel.size()) finishDabScan();
                else scanNextTick = player.tickCount + 4;
            }
            return;
        }
        int channel = scanChannelIndex;
        List<Dx3Station> found = scanCandidates.stream().filter(station -> station.tuning() == channel).toList();
        if (!found.isEmpty()) {
            Map<String, List<Dx3Station>> byMux = new LinkedHashMap<>();
            for (Dx3Station station : found)
                byMux.computeIfAbsent(station.mux(), ignored -> new ArrayList<>()).add(station);
            scanPendingMuxes.addAll(byMux.values());
            acceptNextMux();
        } else {
            scanChannelIndex++;
            if (scanChannelIndex >= DabChannel.size()) finishDabScan();
            else scanNextTick = player.tickCount + 4;
        }
    }

    private void acceptNextMux() {
        scanFound.addAll(scanPendingMuxes.removeFirst());
        scanStationCount = scanFound.size();
        scanPauseChannel = scanChannelIndex;
        scanNextTick = player.tickCount + 20;
    }

    private void finishDabScan() {
        scanningDab = false;
        List<Dx3Station> result = List.copyOf(scanFound);
        if (receiver() instanceof TechnisatReceiverBlockEntity radio) radio.storeScan(result);
        else TechnisatRadioItem.storeScan(player, result);
        scanCandidates = List.of();
        scanPendingMuxes.clear();
        scanFound.clear();
        sentCatalog = false;
        lastScanTick = -1000;
    }

    private void sendTechnisatStatus(ServerPlayer serverPlayer) {
        Dx3ReceiverBlockEntity be = receiver();
        SyncTechnisatStatusPayload next;
        String artworkUrl = "";
        if (be instanceof TechnisatReceiverBlockEntity radio) {
            int mode = radio.getRadioMode();
            if (mode == RadioItem.MODE_DRM) {
                DrmReception.Sample sample = DrmReception.sampleReceiver(radio);
                if (sample.decoded()) artworkUrl = sample.transmitter().getImageUrl();
            }
            next = new SyncTechnisatStatusPayload(containerId, mode,
                    mode == RadioItem.MODE_DRM ? radio.getDrmFrequencyKhz() : radio.getFrequencyKhz(),
                    radio.getChannelIndex(), mode == RadioItem.MODE_DRM ? radio.hasDrmScan()
                            : mode == RadioItem.MODE_DAB && radio.hasScan(), radio.isMuted(),
                    scanningDab, scanPauseChannel >= 0 ? scanPauseChannel : scanChannelIndex,
                    scanStationCount, artworkUrl);
        } else {
            int mode = RadioItem.getMode(player);
            if (mode == RadioItem.MODE_DRM) {
                DrmReception.Sample sample = DrmReception.samplePortable(player, TechnisatRadioItem.drmFrequency(player));
                if (sample.decoded()) artworkUrl = sample.transmitter().getImageUrl();
            }
            next = new SyncTechnisatStatusPayload(containerId, mode,
                    mode == RadioItem.MODE_DRM ? TechnisatRadioItem.drmFrequency(player)
                            : RadioItem.getFrequencyKhz(player),
                    RadioItem.getDabChannelIndex(player), mode == RadioItem.MODE_DRM
                            ? TechnisatRadioItem.hasDrmScan(player)
                            : mode == RadioItem.MODE_DAB && TechnisatRadioItem.hasScan(player),
                    RadioItem.isMuted(player), scanningDab,
                    scanPauseChannel >= 0 ? scanPauseChannel : scanChannelIndex, scanStationCount, artworkUrl);
        }
        if (!next.equals(technisatStatus)) {
            technisatStatus = next;
            PacketDistributor.sendToPlayer(serverPlayer, next);
        }
    }

    public void technisatAction(int action) {
        if (!technisat || player.level().isClientSide()) return;
        Dx3ReceiverBlockEntity be = receiver();
        TechnisatReceiverBlockEntity radio = be instanceof TechnisatReceiverBlockEntity r ? r : null;
        switch (action) {
            case 0 -> {
                scanningDab = false;
                scanPauseChannel = -1;
                scanCandidates = List.of();
                scanPendingMuxes.clear();
                if (radio != null) {
                    int next = (radio.getRadioMode() + 1) % 3;
                    radio.setRadioMode(next);
                    if (next == RadioItem.MODE_FM && radio.isDab())
                        radio.select(false, radio.getFrequencyKhz(), "", "");
                    if (next == RadioItem.MODE_DAB && !radio.isDab())
                        radio.select(true, radio.getChannelIndex(), "", "");
                } else RadioItem.setMode(player, (RadioItem.getMode(player) + 1) % 3);
                sentCatalog = false;
                lastScanTick = -1000;
            }
            case 1, 2 -> {
                int direction = action == 1 ? -1 : 1;
                int mode = radio != null ? radio.getRadioMode() : RadioItem.getMode(player);
                if (mode == RadioItem.MODE_DRM) {
                    if (radio != null) radio.setDrmFrequencyKhz(radio.getDrmFrequencyKhz() + direction);
                    else TechnisatRadioItem.setDrmFrequency(player,
                            TechnisatRadioItem.drmFrequency(player) + direction);
                } else if (mode == RadioItem.MODE_FM) {
                    if (be != null) be.select(false, be.getFrequencyKhz() + direction * 100, "", "");
                    else RadioItem.setFrequencyKhz(player, RadioItem.getFrequencyKhz(player) + direction * 100);
                }
            }
            case 3 -> {
                if (be != null) be.setMuted(!be.isMuted());
                else RadioItem.setMuted(player, !RadioItem.isMuted(player));
            }
            case 4, 5 -> {
                int mode = radio != null ? radio.getRadioMode() : RadioItem.getMode(player);
                if (mode != RadioItem.MODE_FM) return;
                int current = be != null ? be.getFrequencyKhz() : RadioItem.getFrequencyKhz(player);
                List<Integer> frequencies = (be != null ? Dx3ReceiverCatalog.scan(be) : Dx3Catalog.scan(player))
                        .stream().filter(station -> !station.dab()).map(Dx3Station::tuning)
                        .distinct().sorted().toList();
                if (frequencies.isEmpty()) return;
                int chosen = action == 5
                        ? frequencies.stream().filter(frequency -> frequency > current).findFirst().orElse(frequencies.getFirst())
                        : frequencies.reversed().stream().filter(frequency -> frequency < current)
                                .findFirst().orElse(frequencies.getLast());
                if (be != null) be.select(false, chosen, "", "");
                else RadioItem.setFrequencyKhz(player, chosen);
            }
            default -> { return; }
        }
        if (player instanceof ServerPlayer serverPlayer) {
            lastPlayerInfoTick = -1000;
            if (isReceiver()) broadcastReceiverChanges();
            else { sendPlayerInfo(serverPlayer); sendTechnisatStatus(serverPlayer); }
        }
    }

    private SyncDx3PlayerPayload drmPlayerInfo(DrmReception.Sample sample) {
        if (!sample.decoded()) return new SyncDx3PlayerPayload(containerId, false, "", "", "", 0,
                0, false, "", 0, "");
        var tx = sample.transmitter();
        int bars = sample.signalDbuv() < 5 ? 0 : sample.signalDbuv() < 18 ? 1
                : sample.signalDbuv() < 30 ? 2 : sample.signalDbuv() < 42 ? 3
                : sample.signalDbuv() < 55 ? 4 : 5;
        return new SyncDx3PlayerPayload(containerId, true,
                tx.getShortLabel().isBlank() ? tx.getServiceLabel() : tx.getShortLabel(),
                tx.getServiceLabel(), tx.getTextMessage(), 0, bars, false, "",
                tx.getOpusBitrateKbps(), "DRM " + "ABCD".charAt(Math.max(0, Math.min(3, tx.getMode()))));
    }

    public List<Dx3Station> getStations() {
        return stations;
    }

    public void setCatalog(List<Dx3Station> entries) {
        if (player.level().isClientSide()) {
            stations = List.copyOf(entries);
        }
    }

    public SyncDx3PlayerPayload getPlayerInfo() {
        return playerInfo;
    }

    public void setPlayerInfo(SyncDx3PlayerPayload info) {
        if (player.level().isClientSide()) {
            playerInfo = info;
        }
    }

    public int[] getPlayerLogo() {
        return playerLogo;
    }

    public void setPlayerLogo(int[] pixels) {
        if (player.level().isClientSide()) {
            playerLogo = pixels;
        }
    }

    private void sendPlayerInfo(ServerPlayer serverPlayer) {
        if (technisat && RadioItem.getMode(player) == RadioItem.MODE_DRM) {
            RdsTextDecoding.clearPlayer(player);
            ctSource = "";
            ctDecoded = false;
            SyncDx3PlayerPayload next = drmPlayerInfo(DrmReception.samplePortable(player,
                    TechnisatRadioItem.drmFrequency(player)));
            if (!next.equals(playerInfo)) {
                playerInfo = next;
                PacketDistributor.sendToPlayer(serverPlayer, next);
            }
            if (playerLogo != null) {
                playerLogo = null;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3LogoPayload(containerId, null));
            }
            return;
        }
        Dx3Catalog.PlayerInfo info = Dx3Catalog.playerInfo(player);
        if (!info.source().equals(ctSource) || !info.ctBroadcast()) {
            ctSource = info.source();
            ctDecoded = false;
        }
        if (info.ctFrame()) {
            ctDecoded = true;
        }
        long dayTicks = Math.floorMod(player.level().dayTime() + 6000L, 24000L);
        String time = String.format(java.util.Locale.ROOT, "%02d:%02d", dayTicks / 1000L,
                (dayTicks % 1000L) * 60L / 1000L);
        SyncDx3PlayerPayload next = new SyncDx3PlayerPayload(containerId, info.active(), info.ps(),
                info.longPs(), info.rt(), info.pty(), info.bars(), ctDecoded, ctDecoded ? time : "",
                info.bitrateKbps(), info.codec(), info.rds());
        if (!next.equals(playerInfo)) {
            playerInfo = next;
            PacketDistributor.sendToPlayer(serverPlayer, next);
        }
        if (!info.source().equals(logoSource) || player.tickCount % 40 == 0) {
            int[] nextLogo = Dx3Catalog.playerLogo(player);
            if (!info.source().equals(logoSource) || !Arrays.equals(nextLogo, playerLogo)) {
                logoSource = info.source();
                playerLogo = nextLogo;
                PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3LogoPayload(containerId, nextLogo));
            }
        }
    }

    public void selectStation(SelectDx3StationPayload selection) {
        int index = selection.stationIndex();
        if (player.level().isClientSide()) {
            return;
        }
        if (index < 0 || index >= stations.size()) {
            if (!technisat) requestCatalog();
            return;
        }
        Dx3Station selected = stations.get(index);
        int technisatMode = beMode();
        if (technisat && (technisatMode == RadioItem.MODE_FM
                || selected.dab() != (technisatMode == RadioItem.MODE_DAB))) return;
        if (selected.dab() != selection.dab() || selected.tuning() != selection.tuning()
                || !selected.sid().equals(selection.sid()) || !selected.mux().equals(selection.mux())) {
            if (!technisat) requestCatalog();
            return;
        }
        Dx3ReceiverBlockEntity be = receiver();
        if (isReceiver() && be == null) return;
        Dx3Station current = (technisat
                ? isReceiver() && be instanceof TechnisatReceiverBlockEntity radio
                        ? selected.dab() ? TechnisatReception.visible(radio, List.of(selected))
                                : TechnisatReception.visibleDrm(radio, List.of(selected))
                        : selected.dab() ? TechnisatReception.visible(player, List.of(selected))
                                : TechnisatReception.visibleDrm(player, List.of(selected))
                : isReceiver() ? be == null ? List.<Dx3Station>of() : Dx3ReceiverCatalog.scan(be)
                        : Dx3Catalog.scan(player)).stream().filter(selected::sameStation).findFirst().orElse(null);
        if (current == null) {
            if (!technisat) requestCatalog();
            return;
        }
        if (isReceiver()) {
            if (technisat && technisatMode == RadioItem.MODE_DRM
                    && be instanceof TechnisatReceiverBlockEntity radio) {
                radio.setDrmFrequencyKhz(current.tuning());
                lastPlayerInfoTick = -1000;
                broadcastReceiverChanges();
                return;
            }
            be.select(selected.dab(), selected.tuning(), selected.sid(), selected.mux());
            lastPlayerInfoTick = -1000;
            broadcastReceiverChanges();
            return;
        }
        if (selected.dab()) {
            RadioItem.setDx3Selected(player, true);
            RadioItem.setMode(player, RadioItem.MODE_DAB);
            RadioItem.setDabChannelIndex(player, selected.tuning());
            RadioItem.setDabStation(player, selected.mux(), selected.sid());
            RadioItem.setDx3FmPi(player, "");
            RadioItem.setDx3LinkId(player, technisat ? "" : selected.sid());
        } else if (technisat && technisatMode == RadioItem.MODE_DRM) {
            RadioItem.setDx3Selected(player, true);
            RadioItem.setMode(player, RadioItem.MODE_DRM);
            TechnisatRadioItem.setDrmFrequency(player, current.tuning());
        } else {
            RadioItem.setDx3Selected(player, true);
            RadioItem.setMode(player, RadioItem.MODE_FM);
            RadioItem.setFrequencyKhz(player, current.tuning());
            RadioItem.setDx3FmPi(player, selected.sid());
            RadioItem.setDx3LinkId(player, selected.sid());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            sendPlayerInfo(serverPlayer);
        }
    }

    public void setLinkMode(int mode) {
        if (isReceiver()) {
            Dx3ReceiverBlockEntity be = receiver();
            if (be != null && !player.level().isClientSide()) be.setLinkMode(mode);
            return;
        }
        if (!player.level().isClientSide() && mode >= RadioItem.DX3_PREFER_DAB
                && mode <= RadioItem.DX3_PREFER_QUALITY) {
            RadioItem.setDx3LinkMode(player, mode);
        }
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        Dx3ReceiverBlockEntity be = receiver();
        if (!isReceiver() || be == null || id != 1) return false;
        if (!player.level().isClientSide()) be.setMuted(!be.isMuted());
        return true;
    }

    private static int catalogHash(List<Dx3Station> entries) {
        int result = 1;
        for (Dx3Station station : entries) {
            result = 31 * result + Boolean.hashCode(station.dab());
            result = 31 * result + station.tuning();
            result = 31 * result + station.sid().hashCode();
            result = 31 * result + station.mux().hashCode();
            result = 31 * result + station.label().hashCode();
            result = 31 * result + station.sortName().hashCode();
            result = 31 * result + station.detail().hashCode();
            result = 31 * result + Arrays.hashCode(station.logoPixels());
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return !isReceiver() || receiver() != null && player.distanceToSqr(
                receiverPos.getX() + 0.5, receiverPos.getY() + 0.5, receiverPos.getZ() + 0.5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
