package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.signal.DabSfnReception;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.item.DigitalRadioItem;
import com.radiomod.item.RadioItem;
import com.radiomod.item.SonyRadioItem;
import com.radiomod.network.SyncSonyScanPayload;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.DabErrorCorrection;
import com.radiomod.signal.DabDlsResolver;
import com.radiomod.signal.MeteorScatter;
import com.radiomod.signal.RdsFrameData;
import com.radiomod.signal.RdsGroups;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.SignalNetwork;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RadioMenu extends AbstractContainerMenu {
    public static final int BUTTON_FREQUENCY_DOWN = 0;
    public static final int BUTTON_FREQUENCY_UP = 1;
    public static final int BUTTON_DAB_CHANNEL_DOWN = 2;
    public static final int BUTTON_DAB_CHANNEL_UP = 3;
    public static final int BUTTON_AF_TOGGLE = 4;
    public static final int BUTTON_STEP_MODE_TOGGLE = 5;
    public static final int BUTTON_MODE_FM = 6;
    public static final int BUTTON_MODE_DAB = 7;
    public static final int BUTTON_AUTO_TUNE = 8;
    public static final int BUTTON_SONY_SCAN = 9;
    private static final int BUTTON_SONY_SELECT_BASE = 3_000_000;
    private static final int BUTTON_SET_FREQUENCY_BASE = 1_000_000;
    private static final int BUTTON_SET_DAB_CHANNEL_BASE = 2_000_000;
    private static final int RDS_PI_MAX = 4;
    private static final int RDS_PS_MAX = 8;
    private static final int RDS_RT_MAX = 64;
    private static final int RDS_AF_MAX = 24;
    private static final int RDS_ECC_MAX = 16;
    private static final int RDS_LIC_MAX = 16;
    private static final int RDS_PIN_MAX = 16;
    private static final int RDS_PTYN_MAX = 8;
    private static final int RDS_LONG_PS_MAX = 32;
    private static final int RDS_ODA_GROUP_MAX = 3;
    private static final double MIN_DISPLAY_SIGNAL_DBUV = -5.0;

    private final Player player;
    @Nullable
    private final ItemStack radioStack;
    private final DataSlot frequencyKhzSlot;
    private final DataSlot signalDbuvTimes10Slot;
    private final DataSlot snrDbTimes10Slot;
    private final DataSlot rdsBerPercentSlot;
    private final DataSlot rdsDecodingSlot;
    private final DataSlot rdsTpSlot;
    private final DataSlot rdsPtySlot;
    private final DataSlot rdsGroupLengthSlot;
    private final DataSlot[] rdsGroupSlots;
    private final DataSlot rdsPiLengthSlot;
    private final DataSlot[] rdsPiSlots;
    private final DataSlot rdsPsLengthSlot;
    private final DataSlot[] rdsPsSlots;
    private final DataSlot rdsRtLengthSlot;
    private final DataSlot[] rdsRtSlots;
    private final DataSlot rdsAfLengthSlot;
    private final DataSlot[] rdsAfSlots;
    private final DataSlot rdsEccLengthSlot;
    private final DataSlot[] rdsEccSlots;
    private final DataSlot rdsLicLengthSlot;
    private final DataSlot[] rdsLicSlots;
    private final DataSlot rdsPinLengthSlot;
    private final DataSlot[] rdsPinSlots;
    private final DataSlot rdsPtynLengthSlot;
    private final DataSlot[] rdsPtynSlots;
    private final DataSlot rdsLongPsLengthSlot;
    private final DataSlot[] rdsLongPsSlots;
    private final DataSlot rdsOdaGroupLengthSlot;
    private final DataSlot[] rdsOdaGroupSlots;
    private final DataSlot rdsCtEnabledSlot;
    private final DataSlot rdsOdaRtPlusSlot;
    private final DataSlot dabChannelSlot;
    private final DataSlot dabSignalDbuvTimes10Slot;
    private final DataSlot dabSnrDbTimes10Slot;
    private final DataSlot dabFicStateSlot;
    private final DataSlot dabMscStateSlot;
    private final DataSlot dabFrameSyncStateSlot;
    private final DataSlot dabSupportedSlot;
    private final DataSlot digitalRadioStyleSlot;
    private final DataSlot blackDigitalRadioStyleSlot;
    private final DataSlot blackSonyStyleSlot;
    private final DataSlot dabLabelLengthSlot;
    private final DataSlot[] dabLabelSlots;
    private final DataSlot dabDlsLengthSlot;
    private final DataSlot[] dabDlsSlots;
    private final DataSlot fmStepModeSlot;
    private final DataSlot afEnabledSlot;
    private final DataSlot sonyScanningSlot;
    private final DataSlot sonyScanChannelSlot;
    private final DataSlot sonyScanCountSlot;
    private List<SonyRadioItem.Station> sonyStations = List.of();
    private boolean sonyScanned;
    private boolean sonyScanning;
    private boolean sonyScanSent;
    private int sonyNextChannel;
    private int sonyScanPauseTicks;
    private int sonyScanStepTicks;
    private final Random rdsDecodeRandom;
    private String decodedGroupCode = "";
    private String decodedPi = "----";
    private String decodedPs = "--------";
    private String decodedRt = "";
    private String decodedAf = "";
    private String decodedEcc = "";
    private String decodedLic = "";
    private String decodedPin = "";
    private String decodedPtyn = "";
    private String decodedLongPs = "";
    private String decodedOdaGroup = "";
    private int decodedPty = 0;
    private boolean decodedTp = false;
    private int lastTunedFrequencyKhz = -1;
    private String lastRdsSourceKey = "";

    public RadioMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, inventory.player, null);
    }

    public RadioMenu(int containerId, Inventory inventory, Player player, @Nullable ItemStack radioStack) {
        this(Radio.RADIO_MENU.get(), containerId, inventory, player, radioStack);
    }

    public RadioMenu(MenuType<?> type, int containerId, Inventory inventory, Player player, @Nullable ItemStack radioStack) {
        super(type, containerId);
        this.player = player;
        this.radioStack = radioStack;
        this.rdsDecodeRandom = new Random(player.getUUID().getLeastSignificantBits() ^ ((long) containerId << 17));

        frequencyKhzSlot = addDataSlot(DataSlot.standalone());
        signalDbuvTimes10Slot = addDataSlot(DataSlot.standalone());
        snrDbTimes10Slot = addDataSlot(DataSlot.standalone());
        rdsBerPercentSlot = addDataSlot(DataSlot.standalone());
        rdsDecodingSlot = addDataSlot(DataSlot.standalone());
        rdsTpSlot = addDataSlot(DataSlot.standalone());
        rdsPtySlot = addDataSlot(DataSlot.standalone());
        rdsGroupLengthSlot = addDataSlot(DataSlot.standalone());
        rdsGroupSlots = addCharSlots(3);
        rdsPiLengthSlot = addDataSlot(DataSlot.standalone());
        rdsPiSlots = addCharSlots(RDS_PI_MAX);
        rdsPsLengthSlot = addDataSlot(DataSlot.standalone());
        rdsPsSlots = addCharSlots(RDS_PS_MAX);
        rdsRtLengthSlot = addDataSlot(DataSlot.standalone());
        rdsRtSlots = addCharSlots(RDS_RT_MAX);
        rdsAfLengthSlot = addDataSlot(DataSlot.standalone());
        rdsAfSlots = addCharSlots(RDS_AF_MAX);
        rdsEccLengthSlot = addDataSlot(DataSlot.standalone());
        rdsEccSlots = addCharSlots(RDS_ECC_MAX);
        rdsLicLengthSlot = addDataSlot(DataSlot.standalone());
        rdsLicSlots = addCharSlots(RDS_LIC_MAX);
        rdsPinLengthSlot = addDataSlot(DataSlot.standalone());
        rdsPinSlots = addCharSlots(RDS_PIN_MAX);
        rdsPtynLengthSlot = addDataSlot(DataSlot.standalone());
        rdsPtynSlots = addCharSlots(RDS_PTYN_MAX);
        rdsLongPsLengthSlot = addDataSlot(DataSlot.standalone());
        rdsLongPsSlots = addCharSlots(RDS_LONG_PS_MAX);
        rdsOdaGroupLengthSlot = addDataSlot(DataSlot.standalone());
        rdsOdaGroupSlots = addCharSlots(RDS_ODA_GROUP_MAX);
        rdsCtEnabledSlot = addDataSlot(DataSlot.standalone());
        rdsOdaRtPlusSlot = addDataSlot(DataSlot.standalone());
        dabChannelSlot = addDataSlot(DataSlot.standalone());
        dabSignalDbuvTimes10Slot = addDataSlot(DataSlot.standalone());
        dabSnrDbTimes10Slot = addDataSlot(DataSlot.standalone());
        dabFicStateSlot = addDataSlot(DataSlot.standalone());
        dabMscStateSlot = addDataSlot(DataSlot.standalone());
        dabFrameSyncStateSlot = addDataSlot(DataSlot.standalone());
        dabSupportedSlot = addDataSlot(DataSlot.standalone());
        digitalRadioStyleSlot = addDataSlot(DataSlot.standalone());
        blackDigitalRadioStyleSlot = addDataSlot(DataSlot.standalone());
        blackSonyStyleSlot = addDataSlot(DataSlot.standalone());
        dabLabelLengthSlot = addDataSlot(DataSlot.standalone());
        dabLabelSlots = addCharSlots(32);
        dabDlsLengthSlot = addDataSlot(DataSlot.standalone());
        dabDlsSlots = addCharSlots(64);
        fmStepModeSlot = addDataSlot(DataSlot.standalone());
        afEnabledSlot = addDataSlot(DataSlot.standalone());
        sonyScanningSlot = addDataSlot(DataSlot.standalone());
        sonyScanChannelSlot = addDataSlot(DataSlot.standalone());
        sonyScanCountSlot = addDataSlot(DataSlot.standalone());

        int initialFrequencyKhz = RadioItem.getFrequencyKhz(player);
        lastTunedFrequencyKhz = initialFrequencyKhz;
        frequencyKhzSlot.set(initialFrequencyKhz);
        signalDbuvTimes10Slot.set(0);
        snrDbTimes10Slot.set(0);
        rdsBerPercentSlot.set(100);
        rdsDecodingSlot.set(0);
        rdsTpSlot.set(0);
        rdsPtySlot.set(0);
        setStringSlots("", rdsGroupLengthSlot, rdsGroupSlots, 3);
        setStringSlots("", rdsPiLengthSlot, rdsPiSlots, RDS_PI_MAX);
        setStringSlots("", rdsPsLengthSlot, rdsPsSlots, RDS_PS_MAX);
        setStringSlots("", rdsRtLengthSlot, rdsRtSlots, RDS_RT_MAX);
        setStringSlots("", rdsAfLengthSlot, rdsAfSlots, RDS_AF_MAX);
        setStringSlots("", rdsEccLengthSlot, rdsEccSlots, RDS_ECC_MAX);
        setStringSlots("", rdsLicLengthSlot, rdsLicSlots, RDS_LIC_MAX);
        setStringSlots("", rdsPinLengthSlot, rdsPinSlots, RDS_PIN_MAX);
        setStringSlots("", rdsPtynLengthSlot, rdsPtynSlots, RDS_PTYN_MAX);
        setStringSlots("", rdsLongPsLengthSlot, rdsLongPsSlots, RDS_LONG_PS_MAX);
        setStringSlots("", rdsOdaGroupLengthSlot, rdsOdaGroupSlots, RDS_ODA_GROUP_MAX);
        rdsCtEnabledSlot.set(0);
        rdsOdaRtPlusSlot.set(0);
        dabChannelSlot.set(RadioItem.getDabChannelIndex(player));
        dabSignalDbuvTimes10Slot.set(0);
        dabSnrDbTimes10Slot.set(0);
        dabFicStateSlot.set(0);
        dabMscStateSlot.set(0);
        dabFrameSyncStateSlot.set(0);
        dabSupportedSlot.set(isDabSupportedForCurrentRadio() ? 1 : 0);
        digitalRadioStyleSlot.set(isDigitalRadioItem() ? 1 : 0);
        blackDigitalRadioStyleSlot.set(radioStack != null && radioStack.is(Radio.BLACK_DIGITAL_RADIO_ITEM.get()) ? 1 : 0);
        blackSonyStyleSlot.set(radioStack != null && radioStack.is(Radio.BLACK_SONY_RADIO_ITEM.get()) ? 1 : 0);
        setStringSlots("", dabLabelLengthSlot, dabLabelSlots, 32);
        setStringSlots("", dabDlsLengthSlot, dabDlsSlots, 64);
        fmStepModeSlot.set(RadioItem.getFmStepMode(player));
        if (!isDabSupported()) {
            RadioItem.setMode(player, RadioItem.MODE_FM);
        }
        afEnabledSlot.set(RadioItem.isAfEnabled(player) ? 1 : 0);
        if (isSonyRadio() && !player.level().isClientSide()) {
            sonyScanned = SonyRadioItem.hasScan(player);
            sonyStations = SonyRadioItem.scannedStations(player);
        }
        sonyScanCountSlot.set(sonyStations.size());
        restoreDecodedRdsFromCache(initialFrequencyKhz);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_SONY_SELECT_BASE) {
            int index = id - BUTTON_SONY_SELECT_BASE;
            if (!isSonyRadio() || !sonyScanned || index < 0 || index >= sonyStations.size()) return false;
            SonyRadioItem.Station station = sonyStations.get(index);
            setDabChannelIndex(station.channelIndex());
            RadioItem.setDabStation(player, station.mux(), station.sid());
            RadioItem.setMode(player, RadioItem.MODE_DAB);
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_SET_DAB_CHANNEL_BASE) {
            if (!isDabSupported()) {
                return false;
            }
            setDabChannelIndex(id - BUTTON_SET_DAB_CHANNEL_BASE);
            return true;
        }

        if (id >= BUTTON_SET_FREQUENCY_BASE) {
            setFrequencyKhz(id - BUTTON_SET_FREQUENCY_BASE);
            return true;
        }

        return switch (id) {
            case BUTTON_FREQUENCY_DOWN -> {
                setFrequencyKhz(nextFrequencyKhzByStepMode(false));
                yield true;
            }
            case BUTTON_FREQUENCY_UP -> {
                setFrequencyKhz(nextFrequencyKhzByStepMode(true));
                yield true;
            }
            case BUTTON_DAB_CHANNEL_DOWN -> {
                if (!isDabSupported()) {
                    yield false;
                }
                setDabChannelIndex(RadioItem.getDabChannelIndex(player) - 1);
                yield true;
            }
            case BUTTON_DAB_CHANNEL_UP -> {
                if (!isDabSupported()) {
                    yield false;
                }
                setDabChannelIndex(RadioItem.getDabChannelIndex(player) + 1);
                yield true;
            }
            case BUTTON_AF_TOGGLE -> {
                if (isSonyRadio()) yield false;
                boolean nextAfEnabled = !RadioItem.isAfEnabled(player);
                RadioItem.setAfEnabled(player, nextAfEnabled);
                afEnabledSlot.set(nextAfEnabled ? 1 : 0);
                broadcastChanges();
                yield true;
            }
            case BUTTON_STEP_MODE_TOGGLE -> {
                int nextMode = RadioItem.nextFmStepMode(getFmStepMode());
                RadioItem.setFmStepMode(player, nextMode);
                fmStepModeSlot.set(nextMode);
                broadcastChanges();
                yield true;
            }
            case BUTTON_MODE_FM, BUTTON_MODE_DAB -> {
                if (id == BUTTON_MODE_DAB && !isDabSupported()) yield false;
                RadioItem.setMode(player, id == BUTTON_MODE_DAB ? RadioItem.MODE_DAB : RadioItem.MODE_FM);
                broadcastChanges();
                yield true;
            }
            case BUTTON_AUTO_TUNE -> {
                if (isSonyRadio() && RadioItem.getMode(player) == RadioItem.MODE_DAB)
                    startSonyScan();
                else setFrequencyKhz(findSeekFrequencyKhz(RadioItem.getFrequencyKhz(player), true));
                yield true;
            }
            case BUTTON_SONY_SCAN -> {
                if (!isSonyRadio()) yield false;
                startSonyScan();
                yield true;
            }
            default -> false;
        };
    }

    public static int encodeSetFrequencyButtonId(int frequencyKhz) {
        return BUTTON_SET_FREQUENCY_BASE + frequencyKhz;
    }

    public static int encodeSetDabChannelButtonId(int channelIndex) {
        return BUTTON_SET_DAB_CHANNEL_BASE + channelIndex;
    }

    public static int encodeSonySelectButtonId(int index) { return BUTTON_SONY_SELECT_BASE + index; }

    private boolean isSonyRadio() {
        return radioStack != null && radioStack.getItem() instanceof SonyRadioItem;
    }

    private void sendSonyScan() {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu == this) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new SyncSonyScanPayload(containerId, sonyScanned, sonyStations));
            sonyScanSent = true;
        }
    }

    private void startSonyScan() {
        sonyStations = new ArrayList<>();
        sonyScanned = false;
        sonyScanning = true;
        sonyNextChannel = 0;
        sonyScanPauseTicks = 0;
        sonyScanStepTicks = 0;
        sonyScanningSlot.set(1);
        sonyScanChannelSlot.set(0);
        sonyScanCountSlot.set(0);
        sendSonyScan();
    }

    private void tickSonyScan() {
        if (!isSonyRadio()) return;
        if (!sonyScanSent) sendSonyScan();
        if (!sonyScanning || sonyScanPauseTicks-- > 0 || ++sonyScanStepTicks < 2) return;
        sonyScanStepTicks = 0;
        if (sonyNextChannel >= DabChannel.size()) {
            sonyScanning = false;
            sonyScanned = true;
            sonyScanningSlot.set(0);
            SonyRadioItem.storeScan(player, sonyStations);
            sendSonyScan();
            return;
        }
        int index = sonyNextChannel++;
        sonyScanChannelSlot.set(index);
        int overhead = SignalMath.countOverheadBlocks(player.level(), player.position());
        boolean foundMux = false;
        List<SonyRadioItem.Station> updated = new ArrayList<>(sonyStations);
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overhead,
                DabChannel.byIndex(index).frequencyKhz())) {
            if (group.frequencyKhz() != DabChannel.byIndex(index).frequencyKhz()) continue;
            if (group.transmitter().getErrorCorrection().decodeState(
                    group.signalDbuv(), group.snrDb(), 0.8) <= 0) continue;
            if (group.ensemble().stations().isEmpty()) continue;
            foundMux = true;
            for (var service : group.ensemble().stations()) {
                if (updated.size() >= 128) break;
                SonyRadioItem.Station station = new SonyRadioItem.Station(index,
                        group.ensemble().name(), service.sid(), service.label());
                if (updated.stream().noneMatch(existing -> existing.channelIndex() == index
                        && existing.mux().equals(station.mux()) && existing.sid().equals(station.sid())))
                    updated.add(station);
            }
        }
        if (updated.size() != sonyStations.size()) {
            sonyStations = List.copyOf(updated);
            sonyScanCountSlot.set(sonyStations.size());
            sendSonyScan();
        }
        if (foundMux) sonyScanPauseTicks = 20;
    }

    public boolean isSonyScanning() { return sonyScanningSlot.get() > 0; }
    public int getSonyScanChannel() { return sonyScanChannelSlot.get(); }
    public int getSonyScanCount() { return sonyScanCountSlot.get(); }
    public boolean hasSonyScan() { return sonyScanned; }
    public List<SonyRadioItem.Station> getSonyStations() { return sonyStations; }
    public void setSonyScan(boolean scanned, List<SonyRadioItem.Station> stations) {
        if (player.level().isClientSide()) {
            sonyScanned = scanned;
            sonyStations = List.copyOf(stations);
        }
    }

    private void setFrequencyKhz(int frequencyKhz) {
        if (radioStack == null) {
            return;
        }
        int clampedFrequencyKhz = SignalMath.clampFmFrequencyKhz(frequencyKhz);
        if (clampedFrequencyKhz != frequencyKhzSlot.get()) {
            clearRdsSlots();
            lastTunedFrequencyKhz = clampedFrequencyKhz;
            RadioItem.clearDecodedAfState(player);
        }
        RadioItem.setFrequencyKhz(player, clampedFrequencyKhz);
        frequencyKhzSlot.set(clampedFrequencyKhz);
        broadcastChanges();
    }

    private void setDabChannelIndex(int dabChannelIndex) {
        if (radioStack == null) {
            return;
        }
        int clampedIndex = DabChannel.clampIndex(dabChannelIndex);
        if (isSonyRadio() && clampedIndex != RadioItem.getDabChannelIndex(player))
            RadioItem.setDabStation(player, "", "");
        RadioItem.setDabChannelIndex(player, clampedIndex);
        dabChannelSlot.set(clampedIndex);
        broadcastChanges();
    }

    private int nextFrequencyKhzByStepMode(boolean up) {
        int currentFrequencyKhz = RadioItem.getFrequencyKhz(player);
        return switch (getFmStepMode()) {
            case RadioItem.FM_STEP_MODE_0_5_MHZ -> currentFrequencyKhz + (up ? 500 : -500);
            case RadioItem.FM_STEP_MODE_1_0_MHZ -> currentFrequencyKhz + (up ? 1000 : -1000);
            case RadioItem.FM_STEP_MODE_SEEK -> findSeekFrequencyKhz(currentFrequencyKhz, up);
            default -> currentFrequencyKhz + (up ? 100 : -100);
        };
    }

    private int findSeekFrequencyKhz(int currentFrequencyKhz, boolean up) {
        Level level = player.level();
        Vec3 radioPosition = player.position();
        int overheadBlockCount = SignalMath.countOverheadBlocks(level, radioPosition);
        java.util.TreeSet<Integer> occupiedFrequenciesKhz = new java.util.TreeSet<>();
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter) {
                int candidateFrequencyKhz = SignalMath.clampFmFrequencyKhz(transmitter.getFrequencyKhz());
                double candidateSignalDbuv = SignalMath.calculateSignalDbuv(
                        transmitter.getPowerW(),
                        candidateFrequencyKhz,
                        candidateFrequencyKhz,
                        transmitter.hasAntennaConnected(),
                        transmitter.isDirectionalEnabled(),
                        transmitter.getDirectionDegrees(),
                        Vec3.atCenterOf(pos),
                        radioPosition,
                        overheadBlockCount,
                        player.isUnderWater()
                ) + transmitter.antennaGainToward(radioPosition);
                if (candidateSignalDbuv > -5.0) {
                    occupiedFrequenciesKhz.add(candidateFrequencyKhz);
                }
            } else if (!(level.getBlockEntity(pos) instanceof DabTransmitterBlockEntity)) {
                SignalNetwork.unregisterTransmitter(level, pos);
            }
        }
        if (occupiedFrequenciesKhz.isEmpty()) {
            return currentFrequencyKhz;
        }
        Integer targetFrequencyKhz = up
                ? occupiedFrequenciesKhz.higher(currentFrequencyKhz)
                : occupiedFrequenciesKhz.lower(currentFrequencyKhz);
        if (targetFrequencyKhz == null) {
            targetFrequencyKhz = up ? occupiedFrequenciesKhz.first() : occupiedFrequenciesKhz.last();
        }
        return targetFrequencyKhz == null ? currentFrequencyKhz : targetFrequencyKhz;
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide()) {
            tickSonyScan();
            updateSignalReadings();
        }
        super.broadcastChanges();
    }

    private void updateSignalReadings() {
        Level level = player.level();
        int frequencyKhz = frequencyKhzSlot.get();
        frequencyKhz = SignalMath.clampFmFrequencyKhz(RadioItem.getFrequencyKhz(player));
        frequencyKhzSlot.set(frequencyKhz);
        if (lastTunedFrequencyKhz != frequencyKhz) {
            clearRdsSlots();
            lastTunedFrequencyKhz = frequencyKhz;
        }

        Vec3 radioPosition = player.position();
        int overheadBlockCount = SignalMath.countOverheadBlocks(level, radioPosition);
        double bestFmSignalDbuv = -120.0;
        TransmitterBlockEntity bestFmTransmitter = null;
        double bestDabSignalDbuv = -120.0;
        DabErrorCorrection bestDabEc = DabErrorCorrection.EEP_4A;
        int dabChannelIndex = DabChannel.clampIndex(RadioItem.getDabChannelIndex(player));
        DabChannel dabChannel = DabChannel.byIndex(dabChannelIndex);
        dabChannelSlot.set(dabChannelIndex);

        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter) {
                double signalDbuv = SignalMath.calculateSignalDbuv(
                        transmitter.getPowerW(),
                        transmitter.getFrequencyKhz(),
                        frequencyKhz,
                        transmitter.hasAntennaConnected(),
                        transmitter.isDirectionalEnabled(),
                        transmitter.getDirectionDegrees(),
                        Vec3.atCenterOf(pos),
                        radioPosition,
                        overheadBlockCount,
                        player.isUnderWater()
                ) + transmitter.antennaGainToward(radioPosition);
                signalDbuv = MeteorScatter.signal(level, transmitter, radioPosition,
                        frequencyKhz, signalDbuv, player.isUnderWater());
                if (signalDbuv > bestFmSignalDbuv) {
                    bestFmSignalDbuv = signalDbuv;
                    bestFmTransmitter = transmitter;
                }
            } else if (level.getBlockEntity(pos) instanceof DabTransmitterBlockEntity) {
                // DAB transmitters are evaluated together below so one SFN is one signal.
            } else {
                SignalNetwork.unregisterTransmitter(level, pos);
            }
        }

        for (DabSfnReception.Group group : DabSfnReception.scan(
                player, overheadBlockCount, dabChannel.frequencyKhz())) {
            if (isSonyRadio() && !RadioItem.getDabStationSid(player).isBlank()
                    && (!group.ensemble().name().equals(RadioItem.getDabStationMux(player))
                    || group.ensemble().stations().stream().noneMatch(station ->
                    station.sid().equals(RadioItem.getDabStationSid(player))))) continue;
            if (group.signalDbuv() > bestDabSignalDbuv) {
                bestDabSignalDbuv = group.signalDbuv();
                bestDabEc = group.transmitter().getErrorCorrection();
                var mux = group.transmitter().resolveBroadcastMux();
                if (mux != null && !mux.stations().isEmpty()) {
                    var station = mux.stations().stream()
                            .filter(candidate -> isSonyRadio()
                                    && !RadioItem.getDabStationSid(player).isBlank()
                                    ? mux.name().equals(RadioItem.getDabStationMux(player))
                                    && candidate.sid().equals(RadioItem.getDabStationSid(player))
                                    : !candidate.streamUrl().isBlank())
                            .findFirst().orElse(mux.stations().getFirst());
                    setStringSlots(station.label(), dabLabelLengthSlot, dabLabelSlots, 32);
                    setStringSlots(DabDlsResolver.text(station), dabDlsLengthSlot, dabDlsSlots, 64);
                } else {
                    setStringSlots("", dabLabelLengthSlot, dabLabelSlots, 32);
                    setStringSlots("", dabDlsLengthSlot, dabDlsSlots, 64);
                }
            }
        }
        if (bestDabSignalDbuv <= -120.0) {
            setStringSlots("", dabLabelLengthSlot, dabLabelSlots, 32);
            setStringSlots("", dabDlsLengthSlot, dabDlsSlots, 64);
        }

        double noiseFloorDbuv = SignalMath.calculateNoiseFloorDbuv(radioPosition);
        double fmSnrDb = SignalMath.calculateSnrDb(bestFmSignalDbuv, noiseFloorDbuv);
        double dabSnrDb = SignalMath.calculateDabSnrDb(bestDabSignalDbuv, noiseFloorDbuv);
        double displayFmSignalDbuv = Math.max(bestFmSignalDbuv, MIN_DISPLAY_SIGNAL_DBUV);
        double displayFmSnrDb = SignalMath.calculateSnrDb(displayFmSignalDbuv, noiseFloorDbuv);

        signalDbuvTimes10Slot.set((int) Math.round(displayFmSignalDbuv * 10.0));
        snrDbTimes10Slot.set((int) Math.round(displayFmSnrDb * 10.0));
        afEnabledSlot.set(RadioItem.isAfEnabled(player) ? 1 : 0);
        int berPercent = calculateRdsBerPercent(bestFmSignalDbuv, fmSnrDb, bestFmTransmitter != null && bestFmTransmitter.isRdsEnabled());
        rdsBerPercentSlot.set(berPercent);
        boolean canDecodeRds = bestFmSignalDbuv > -10.0 && fmSnrDb > -5.0 && berPercent <= 98;
        if (bestFmTransmitter != null && bestFmTransmitter.isRdsEnabled() && canDecodeRds) {
            String sourceKey = bestFmTransmitter.getBlockPos().asLong() + ":" + bestFmTransmitter.getFrequencyKhz();
            if (!sourceKey.equals(lastRdsSourceKey)) {
                clearRdsSlots();
                lastRdsSourceKey = sourceKey;
            }
            String ctString = level.dayTime() % 24000L + "";
            RdsFrameData frame = bestFmTransmitter.getCurrentRdsFrame(berPercent, level.getGameTime(), ctString);
            if (frame != null) {
                updateRdsDecodedState(frame, bestFmTransmitter, berPercent);
                persistDecodedRdsCache(frequencyKhz);
            }
            if (!bestFmTransmitter.isRdsGroupEnabled(RdsGroups.indexByCode("4A"))
                    || !bestFmTransmitter.isRdsCtEnabled()) {
                rdsCtEnabledSlot.set(0);
            }
        } else if (bestFmSignalDbuv < -20.0 || fmSnrDb < -10.0) {
            lastRdsSourceKey = "";
            clearRdsSlots();
        }
        rdsDecodingSlot.set(bestFmTransmitter != null && bestFmTransmitter.isRdsEnabled() && canDecodeRds ? 1 : 0);
        publishDecodedRdsToSlots();

        dabSignalDbuvTimes10Slot.set((int) Math.round(bestDabSignalDbuv * 10.0));
        dabSnrDbTimes10Slot.set((int) Math.round(dabSnrDb * 10.0));
        dabFicStateSlot.set(bestDabEc.decodeState(bestDabSignalDbuv, dabSnrDb, 0.0));
        dabMscStateSlot.set(bestDabEc.decodeState(bestDabSignalDbuv, dabSnrDb, 0.8));
        dabFrameSyncStateSlot.set(bestDabEc.decodeState(bestDabSignalDbuv, dabSnrDb, -0.5));
    }

    private static int calculateRdsBerPercent(double signalDbuv, double snrDb, boolean rdsEnabled) {
        if (!rdsEnabled) {
            return 100;
        }

        double quality = (signalDbuv * 0.45) + (snrDb * 0.55);
        double referenceQuality = 9.11;
        double ber = 99.0 * Math.exp(-0.191 * (quality - referenceQuality));
        return (int) Math.round(Math.max(0.0, Math.min(99.0, ber)));
    }

    private void clearRdsSlots() {
        rdsDecodingSlot.set(0);
        decodedGroupCode = "";
        decodedPi = "----";
        decodedPs = "--------";
        decodedRt = "";
        decodedAf = "";
        decodedEcc = "";
        decodedLic = "";
        decodedPin = "";
        decodedPtyn = "";
        decodedLongPs = "";
        decodedOdaGroup = "";
        decodedPty = 0;
        decodedTp = false;
        lastRdsSourceKey = "";
        rdsCtEnabledSlot.set(0);
        rdsOdaRtPlusSlot.set(0);
        publishDecodedRdsToSlots();
    }

    private void restoreDecodedRdsFromCache(int frequencyKhz) {
        RadioItem.CachedRdsSnapshot snapshot = RadioItem.getCachedRdsSnapshot(player, frequencyKhz);
        if (snapshot == null) {
            return;
        }
        decodedGroupCode = snapshot.groupCode();
        decodedPi = snapshot.pi();
        decodedPs = snapshot.ps();
        decodedRt = snapshot.rt();
        decodedAf = snapshot.af();
        decodedEcc = snapshot.ecc();
        decodedLic = snapshot.lic();
        decodedPin = snapshot.pin();
        decodedPtyn = snapshot.ptyn();
        decodedLongPs = snapshot.longPs();
        decodedOdaGroup = snapshot.odaGroup();
        decodedPty = snapshot.pty();
        decodedTp = snapshot.tp();
        // A cached CT flag cannot prove that the currently tuned station is sending 4A.
        rdsCtEnabledSlot.set(0);
        rdsOdaRtPlusSlot.set(snapshot.rtPlusEnabled() ? 1 : 0);
        publishDecodedRdsToSlots();
    }

    private void publishDecodedRdsToSlots() {
        rdsTpSlot.set(decodedTp ? 1 : 0);
        rdsPtySlot.set(decodedPty);
        setStringSlots(decodedGroupCode, rdsGroupLengthSlot, rdsGroupSlots, 3);
        setStringSlots(decodedPi, rdsPiLengthSlot, rdsPiSlots, RDS_PI_MAX);
        setStringSlots(decodedPs, rdsPsLengthSlot, rdsPsSlots, RDS_PS_MAX);
        setStringSlots(decodedRt, rdsRtLengthSlot, rdsRtSlots, RDS_RT_MAX);
        setStringSlots(decodedAf, rdsAfLengthSlot, rdsAfSlots, RDS_AF_MAX);
        setStringSlots(decodedEcc, rdsEccLengthSlot, rdsEccSlots, RDS_ECC_MAX);
        setStringSlots(decodedLic, rdsLicLengthSlot, rdsLicSlots, RDS_LIC_MAX);
        setStringSlots(decodedPin, rdsPinLengthSlot, rdsPinSlots, RDS_PIN_MAX);
        setStringSlots(decodedPtyn, rdsPtynLengthSlot, rdsPtynSlots, RDS_PTYN_MAX);
        setStringSlots(decodedLongPs, rdsLongPsLengthSlot, rdsLongPsSlots, RDS_LONG_PS_MAX);
        setStringSlots(decodedOdaGroup, rdsOdaGroupLengthSlot, rdsOdaGroupSlots, RDS_ODA_GROUP_MAX);
    }

    private void updateRdsDecodedState(RdsFrameData frame, TransmitterBlockEntity transmitter, int berPercent) {
        double quality = Mth.clamp((100.0 - berPercent) / 100.0, 0.0, 1.0);
        decodedGroupCode = decodeStringGradually(decodedGroupCode, frame.groupCode(), 3, quality, true);
        decodedPi = decodeStringGradually(decodedPi, frame.pi(), RDS_PI_MAX, quality, false);
        decodedPs = decodeStringGradually(decodedPs, frame.ps(), RDS_PS_MAX, quality, true);
        if ("2A".equals(frame.groupCode())) {
            decodedRt = decodeStringGraduallyRt(decodedRt, frame.rt(), quality);
        }
        switch (frame.groupCode()) {
            case "0A" -> decodedAf = decodeStringGradually(decodedAf, transmitter.getRdsAf(), RDS_AF_MAX, quality, true);
            case "1A" -> {
                decodedEcc = decodeStringGradually(decodedEcc, transmitter.getRdsEcc(), RDS_ECC_MAX, quality, true);
                decodedLic = decodeStringGradually(decodedLic, transmitter.getRdsLic(), RDS_LIC_MAX, quality, true);
                decodedPin = decodeStringGradually(decodedPin, transmitter.getRdsPin(), RDS_PIN_MAX, quality, true);
            }
            case "3A" -> decodedOdaGroup = decodeStringGradually(decodedOdaGroup, transmitter.getRdsOdaGroup(), RDS_ODA_GROUP_MAX, quality, true);
            case "10A" -> decodedPtyn = decodeStringGradually(decodedPtyn, transmitter.getRdsPtyn(), RDS_PTYN_MAX, quality, true);
            case "15A" -> decodedLongPs = decodeStringGradually(decodedLongPs, transmitter.getRdsLongPs(), RDS_LONG_PS_MAX, quality, true);
            default -> {
            }
        }
        if ("3A".equals(frame.groupCode()) && rdsDecodeRandom.nextDouble() < (0.2 + quality * 0.8)) {
            rdsOdaRtPlusSlot.set(transmitter.isRdsOdaRtPlus() ? 1 : 0);
        }
        if ("4A".equals(frame.groupCode()) && rdsDecodeRandom.nextDouble() < (0.2 + quality * 0.8)) {
            rdsCtEnabledSlot.set(transmitter.isRdsCtEnabled() ? 1 : 0);
        }

        if (rdsDecodeRandom.nextDouble() < (0.2 + quality * 0.8)) {
            decodedPty = frame.pty();
        }
        if (rdsDecodeRandom.nextDouble() < (0.15 + quality * 0.85)) {
            decodedTp = frame.tp();
        }
    }

    private void persistDecodedRdsCache(int frequencyKhz) {
        RadioItem.setCachedRdsSnapshot(
                player,
                new RadioItem.CachedRdsSnapshot(
                        frequencyKhz,
                        decodedGroupCode,
                        decodedPi,
                        decodedPs,
                        decodedRt,
                        decodedAf,
                        decodedEcc,
                        decodedLic,
                        decodedPin,
                        decodedPtyn,
                        decodedLongPs,
                        decodedOdaGroup,
                        decodedPty,
                        decodedTp,
                        rdsCtEnabledSlot.get() > 0,
                        rdsOdaRtPlusSlot.get() > 0
                )
        );
    }

    private String decodeStringGradually(String current, String target, int maxLen, double quality, boolean useSpacePadding) {
        String normalizedCurrent = normalizeString(current, maxLen, useSpacePadding);
        String normalizedTarget = normalizeString(target, maxLen, useSpacePadding);
        StringBuilder out = new StringBuilder(maxLen);
        double decodeChance = 0.08 + (quality * 0.88);
        double errorChance = (1.0 - quality) * 0.22;
        for (int i = 0; i < maxLen; i++) {
            char cur = normalizedCurrent.charAt(i);
            char tgt = normalizedTarget.charAt(i);
            if (cur == tgt) {
                out.append(cur);
                continue;
            }
            double roll = rdsDecodeRandom.nextDouble();
            if (roll < decodeChance) {
                out.append(tgt);
            } else if (roll < decodeChance + errorChance) {
                out.append(randomRdsGlyph(useSpacePadding));
            } else {
                out.append(cur);
            }
        }
        return out.toString().stripTrailing();
    }

    private String decodeStringGraduallyRt(String current, String target, double quality) {
        String normalizedCurrent = normalizeString(current, RDS_RT_MAX, true);
        String normalizedTarget = normalizeString(target, RDS_RT_MAX, true);
        StringBuilder out = new StringBuilder(RDS_RT_MAX);

        double decodeChance = 0.03 + (quality * 0.42);
        double errorChance = (1.0 - quality) * 0.28;

        for (int i = 0; i < RDS_RT_MAX; i++) {
            char cur = normalizedCurrent.charAt(i);
            char tgt = normalizedTarget.charAt(i);
            if (cur == tgt) {
                out.append(cur);
                continue;
            }
            double roll = rdsDecodeRandom.nextDouble();
            if (roll < decodeChance) {
                out.append(tgt);
            } else if (roll < decodeChance + errorChance) {
                out.append(randomRdsGlyph(true));
            } else {
                out.append(cur);
            }
        }
        return out.toString().stripTrailing();
    }

    private String normalizeString(String input, int maxLen, boolean useSpacePadding) {
        String value = input == null ? "" : input;
        if (value.length() > maxLen) {
            value = value.substring(0, maxLen);
        }
        StringBuilder builder = new StringBuilder(value);
        char pad = useSpacePadding ? ' ' : '-';
        while (builder.length() < maxLen) {
            builder.append(pad);
        }
        return builder.toString();
    }

    private char randomRdsGlyph(boolean allowSpace) {
        String alphabet = allowSpace ? " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" : "ABCDEF0123456789";
        return alphabet.charAt(rdsDecodeRandom.nextInt(alphabet.length()));
    }

    private DataSlot[] addCharSlots(int size) {
        DataSlot[] slots = new DataSlot[size];
        for (int i = 0; i < size; i++) {
            slots[i] = addDataSlot(DataSlot.standalone());
        }
        return slots;
    }

    private void setStringSlots(String value, DataSlot lengthSlot, DataSlot[] slots, int maxLength) {
        String safe = value == null ? "" : value;
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        lengthSlot.set(safe.length());
        for (int i = 0; i < slots.length; i++) {
            slots[i].set(i < safe.length() ? safe.charAt(i) : 0);
        }
    }

    private String readString(DataSlot lengthSlot, DataSlot[] slots) {
        int length = Math.max(0, Math.min(slots.length, lengthSlot.get()));
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) slots[i].get());
        }
        return builder.toString();
    }

    public int getFrequencyKhz() {
        return frequencyKhzSlot.get();
    }

    public double getSignalDbuv() {
        return signalDbuvTimes10Slot.get() / 10.0;
    }

    public double getSnrDb() {
        return snrDbTimes10Slot.get() / 10.0;
    }

    public int getRdsBerPercent() {
        return rdsBerPercentSlot.get();
    }

    public boolean isRdsDecoding() {
        return rdsDecodingSlot.get() > 0;
    }

    public String getRdsGroupCode() {
        return readString(rdsGroupLengthSlot, rdsGroupSlots);
    }

    public String getRdsPi() {
        return readString(rdsPiLengthSlot, rdsPiSlots);
    }

    public String getRdsPs() {
        return readString(rdsPsLengthSlot, rdsPsSlots);
    }

    public String getRdsRt() {
        return readString(rdsRtLengthSlot, rdsRtSlots);
    }

    public int getRdsPty() {
        return rdsPtySlot.get();
    }

    public boolean isRdsTp() {
        return rdsTpSlot.get() > 0;
    }

    public String getRdsAf() {
        return readString(rdsAfLengthSlot, rdsAfSlots);
    }

    public String getRdsEcc() {
        return readString(rdsEccLengthSlot, rdsEccSlots);
    }

    public String getRdsLic() {
        return readString(rdsLicLengthSlot, rdsLicSlots);
    }

    public String getRdsPin() {
        return readString(rdsPinLengthSlot, rdsPinSlots);
    }

    public String getRdsPtyn() {
        return readString(rdsPtynLengthSlot, rdsPtynSlots);
    }

    public String getRdsLongPs() {
        return readString(rdsLongPsLengthSlot, rdsLongPsSlots);
    }

    public String getRdsOdaGroup() {
        return readString(rdsOdaGroupLengthSlot, rdsOdaGroupSlots);
    }

    public boolean isRdsCtEnabled() {
        return rdsCtEnabledSlot.get() > 0;
    }

    public boolean isRdsOdaRtPlusEnabled() {
        return rdsOdaRtPlusSlot.get() > 0;
    }

    public int getDabChannelIndex() {
        return dabChannelSlot.get();
    }

    public String getDabChannelLabel() {
        return DabChannel.byIndex(getDabChannelIndex()).label();
    }

    public double getDabSignalDbuv() {
        return dabSignalDbuvTimes10Slot.get() / 10.0;
    }

    public double getDabSnrDb() {
        return dabSnrDbTimes10Slot.get() / 10.0;
    }

    public int getDabFicState() {
        return dabFicStateSlot.get();
    }

    public int getDabMscState() {
        return dabMscStateSlot.get();
    }

    public int getDabFrameSyncState() {
        return dabFrameSyncStateSlot.get();
    }

    public boolean isDabSupported() {
        return dabSupportedSlot.get() > 0;
    }

    public boolean isDigitalRadioStyle() {
        return digitalRadioStyleSlot.get() > 0;
    }

    public boolean isBlackDigitalRadioStyle() {
        return blackDigitalRadioStyleSlot.get() > 0;
    }

    public boolean isBlackSonyStyle() { return blackSonyStyleSlot.get() > 0; }
    public String getDabLabel() { return readString(dabLabelLengthSlot, dabLabelSlots); }
    public String getDabDls() { return readString(dabDlsLengthSlot, dabDlsSlots); }

    public int getFmStepMode() {
        return fmStepModeSlot.get();
    }

    public boolean isAfEnabled() {
        return afEnabledSlot.get() > 0;
    }

    private boolean isDabSupportedForCurrentRadio() {
        if (radioStack == null) {
            return true;
        }
        if (!(radioStack.getItem() instanceof RadioItem radioItem)) {
            return true;
        }
        return radioItem.supportsDab();
    }

    private boolean isDigitalRadioItem() {
        if (radioStack == null) {
            return false;
        }
        return radioStack.getItem() instanceof DigitalRadioItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
