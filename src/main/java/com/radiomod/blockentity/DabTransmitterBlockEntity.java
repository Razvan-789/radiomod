package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.signal.DabServerNetwork;
import com.radiomod.signal.DabErrorCorrection;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.SignalNetwork;
import com.radiomod.signal.DabVersion;
import com.radiomod.server.TransmitterChunkLoading;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DabTransmitterBlockEntity extends BlockEntity {
    @Override public void setChanged() {
        super.setChanged();
        SignalNetwork.markChanged(level);
    }
    private static final String KEY_FREQUENCY_KHZ = "FrequencyKhz";
    private static final String KEY_POWER_W = "PowerW";
    private static final String KEY_ANTENNA_POS = "AntennaPos";
    private static final String KEY_ERROR_CORRECTION = "ErrorCorrection";
    private static final String KEY_ENSEMBLE_ID = "EnsembleId";
    private static final String KEY_ENSEMBLE_NAME = "EnsembleName";
    private static final String KEY_LINKED_SERVER_POS = "LinkedServerPos";
    private static final String KEY_LINKED_ENSEMBLE_INDEX = "LinkedEnsembleIndex";
    private static final String KEY_LINKED_MUX_ID = "LinkedMuxId";
    private static final String KEY_SERVER_UNLINKED = "ServerUnlinked";
    private static final String KEY_DAB_VERSION = "DabVersion";
    private static final String KEY_DIRECTIONAL_ENABLED = "DirectionalEnabled";
    private static final String KEY_DIRECTION_DEGREES = "DirectionDegrees";
    private static final String KEY_TII_CODE = "TiiCode";

    private int frequencyKhz = 220_352;
    private int powerW = 50;
    @Nullable
    private BlockPos antennaPos;
    private DabErrorCorrection errorCorrection = DabErrorCorrection.EEP_3A;
    private DabVersion dabVersion = DabVersion.DAB_PLUS;
    private int ensembleId = 1001;
    private String ensembleName = "BLANK MUX A";
    @Nullable
    private BlockPos linkedServerPos;

    @Nullable
    public BlockPos getLinkedServerPos() {
        return linkedServerPos;
    }
    private int linkedEnsembleIndex;
    @Nullable
    private UUID linkedMuxId;
    private boolean serverUnlinked;
    private boolean directionalEnabled;
    private int directionDegrees;
    private String tiiCode;

    public DabTransmitterBlockEntity(BlockPos pos, BlockState blockState) {
        super(Radio.DAB_TRANSMITTER_BLOCK_ENTITY.get(), pos, blockState);
        tiiCode = String.format(java.util.Locale.ROOT, "%04X", Long.hashCode(pos.asLong()) & 0xFFFF);
    }

    public String getTiiCode() {
        return tiiCode;
    }

    public void setTiiCode(String code) {
        if (code != null && code.matches("(?i)[0-9a-f]{4}")) {
            tiiCode = code.toUpperCase(java.util.Locale.ROOT);
            setChanged();
        }
    }

    public int getFrequencyKhz() {
        return frequencyKhz;
    }

    public void setFrequencyKhz(int frequencyKhz) {
        this.frequencyKhz = SignalMath.clampFrequencyKhz(frequencyKhz);
        setChanged();
    }

    public int getPowerW() {
        return powerW;
    }

    public CompoundTag createPreset(HolderLookup.Provider registries) {
        CompoundTag preset = saveWithoutMetadata(registries);
        preset.remove(KEY_FREQUENCY_KHZ);
        preset.remove(KEY_POWER_W);
        preset.remove(KEY_ANTENNA_POS);
        return preset;
    }

    public void applyPreset(CompoundTag preset, HolderLookup.Provider registries) {
        CompoundTag settings = saveWithoutMetadata(registries);
        settings.remove(KEY_LINKED_SERVER_POS);
        settings.remove(KEY_LINKED_ENSEMBLE_INDEX);
        settings.remove(KEY_LINKED_MUX_ID);
        for (String key : preset.getAllKeys()) {
            if (!key.equals(KEY_FREQUENCY_KHZ) && !key.equals(KEY_POWER_W)
                    && !key.equals(KEY_ANTENNA_POS) && !key.equals("id")
                    && !key.equals("x") && !key.equals("y") && !key.equals("z")) {
                settings.put(key, preset.get(key).copy());
            }
        }
        DabErrorCorrection oldCorrection = errorCorrection;
        loadAdditional(settings, registries);
        DabErrorCorrection requested = errorCorrection;
        errorCorrection = oldCorrection;
        setErrorCorrection(requested);
        setChanged();
    }

    public void setPowerW(int powerW) {
        this.powerW = SignalMath.clampPowerW(powerW);
        setChanged();
    }

    public boolean isDirectionalEnabled() {
        return directionalEnabled;
    }

    public void setDirectionalEnabled(boolean directionalEnabled) {
        this.directionalEnabled = directionalEnabled;
        setChanged();
    }

    public int getDirectionDegrees() {
        return directionDegrees;
    }

    public void setDirectionDegrees(int directionDegrees) {
        this.directionDegrees = Math.floorMod(directionDegrees, 360);
        setChanged();
    }

    public void setDirectionFromYaw(float yawDegrees) {
        setDirectionDegrees(Math.round(yawDegrees + 90.0f));
    }

    @Nullable
    public BlockPos getAntennaPos() {
        return antennaPos;
    }

    public void setAntennaPos(@Nullable BlockPos antennaPos) {
        this.antennaPos = antennaPos;
        setChanged();
    }

    public boolean hasAntennaConnected() {
        return antennaPos != null && level != null && level.getBlockEntity(antennaPos) instanceof AntennaBlockEntity antennaBlockEntity
                && !(antennaBlockEntity instanceof LoopAntennaBlockEntity)
                && !(antennaBlockEntity instanceof YagiAntennaBlockEntity)
                && getBlockPos().equals(antennaBlockEntity.getLinkedTransmitterPos());
    }

    public double antennaGainToward(net.minecraft.world.phys.Vec3 receiver) {
        return com.radiomod.signal.SignalMath.linkedAntennaGainDb(level, antennaPos, getBlockPos(), receiver);
    }

    public DabErrorCorrection getErrorCorrection() {
        return errorCorrection;
    }

    public void setErrorCorrection(DabErrorCorrection errorCorrection) {
        DabServerBlockEntity.DabEnsembleDefinition mux = resolveLinkedMux();
        if (mux != null && mux.stations().stream().mapToInt(DabServerBlockEntity.DabStationDefinition::bitrateKbps).sum()
                > errorCorrection.getMuxCapacityKbps()) {
            return;
        }
        this.errorCorrection = errorCorrection;
        setChanged();
    }

    public int getEnsembleId() {
        return ensembleId;
    }

    public void setEnsembleId(int ensembleId) {
        this.ensembleId = Math.max(0, Math.min(65_535, ensembleId));
        setChanged();
    }

    public String getEnsembleName() {
        DabServerBlockEntity.DabEnsembleDefinition linked = resolveLinkedMux();
        return linked == null ? ensembleName : linked.name();
    }

    public void setEnsembleName(String ensembleName) {
        if (ensembleName == null) {
            return;
        }
        if (linkedServerPos != null && level != null
                && level.getBlockEntity(linkedServerPos) instanceof DabServerBlockEntity server) {
            for (int index = 0; index < server.getEnsembleCount(); index++) {
                if (server.getEnsemble(index).name().equals(ensembleName.trim())) {
                    linkedEnsembleIndex = index;
                    linkedMuxId = server.getEnsemble(index).id();
                    setChanged();
                    return;
                }
            }
            return;
        }
        this.ensembleName = sanitizeEnsembleName(ensembleName);
        setChanged();
    }

    public void linkToServer(BlockPos serverPos) {
        serverUnlinked = false;
        linkedServerPos = serverPos.immutable();
        linkedEnsembleIndex = 0;
        linkedMuxId = null;
        if (level != null && level.getBlockEntity(linkedServerPos) instanceof DabServerBlockEntity server
                && server.getEnsembleCount() > 0) {
            DabServerBlockEntity.DabEnsembleDefinition first = server.getEnsemble(0);
            linkedMuxId = first.id();
            ensembleName = first.name();
        }
        setChanged();
    }

    public void unlinkServer() {
        serverUnlinked = true;
        linkedServerPos = null;
        linkedEnsembleIndex = 0;
        linkedMuxId = null;
        ensembleName = "";
        setChanged();
    }

    public int getAvailableServerCount() {
        return availableServers().size();
    }

    public String getLinkedServerLabel() {
        return linkedServerPos == null ? "No server selected" : linkedServerPos.toShortString();
    }

    public void cycleServerLink(int direction) {
        List<BlockPos> servers = availableServers();
        if (servers.isEmpty()) {
            return;
        }
        int current = linkedServerPos == null ? -1 : servers.indexOf(linkedServerPos);
        int next = current < 0 ? direction < 0 ? servers.size() - 1 : 0
                : Math.floorMod(current + direction, servers.size());
        if (!servers.get(next).equals(linkedServerPos)) {
            linkToServer(servers.get(next));
        }
    }

    private List<BlockPos> availableServers() {
        if (level == null) {
            return List.of();
        }
        return DabServerNetwork.getServers(level).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof DabServerBlockEntity server
                        && server.getEnsembleCount() > 0)
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
    }

    public boolean hasServerLink() {
        return linkedServerPos != null;
    }

    public boolean isServerLinked() {
        return linkedServerPos != null && linkedMuxId != null && level != null
                && level.getBlockEntity(linkedServerPos) instanceof DabServerBlockEntity server
                && server.findEnsembleById(linkedMuxId) != null;
    }

    public void cycleLinkedMux(int direction) {
        if (!isServerLinked() || level == null) {
            return;
        }
        DabServerBlockEntity server = (DabServerBlockEntity) level.getBlockEntity(linkedServerPos);
        int currentIndex = server.findEnsembleIndexById(linkedMuxId);
        linkedEnsembleIndex = Math.floorMod(currentIndex + direction, server.getEnsembleCount());
        DabServerBlockEntity.DabEnsembleDefinition linked = server.getEnsemble(linkedEnsembleIndex);
        linkedMuxId = linked.id();
        ensembleName = linked.name();
        setChanged();
    }

    public String getSfnIdentity() {
        return linkedServerPos == null ? "legacy:" + getBlockPos().asLong()
                : "server:" + linkedServerPos.asLong() + ":" + linkedMuxId;
    }

    public DabVersion getDabVersion() {
        return dabVersion;
    }

    public void setDabVersion(DabVersion dabVersion) {
        this.dabVersion = dabVersion == null ? DabVersion.DAB_PLUS : dabVersion;
        setChanged();
    }

    public String getLinkedMuxName() {
        return getEnsembleName();
    }

    @Nullable
    public DabServerBlockEntity.DabEnsembleDefinition resolveLinkedMux() {
        if (level == null) {
            return null;
        }
        if (linkedServerPos != null) {
            if (level.getBlockEntity(linkedServerPos) instanceof DabServerBlockEntity server
                    && linkedMuxId != null) {
                return server.findEnsembleById(linkedMuxId);
            }
            return null;
        }
        if (serverUnlinked) {
            return null;
        }
        if (ensembleName.isBlank()) {
            return null;
        }
        String exactName = ensembleName.trim();
        DabServerBlockEntity.DabEnsembleDefinition match = null;
        for (BlockPos pos : DabServerNetwork.getServers(level)) {
            if (level.getBlockEntity(pos) instanceof DabServerBlockEntity server) {
                DabServerBlockEntity.DabEnsembleDefinition ensemble = server.findEnsembleByNameExact(exactName);
                if (ensemble != null) {
                    if (match != null) {
                        return null;
                    }
                    match = ensemble;
                }
            }
        }
        return match;
    }

    @Nullable
    public DabServerBlockEntity.DabEnsembleDefinition resolveBroadcastMux() {
        DabServerBlockEntity.DabEnsembleDefinition mux = resolveLinkedMux();
        if (mux == null || mux.stations().stream().mapToInt(DabServerBlockEntity.DabStationDefinition::bitrateKbps).sum()
                > errorCorrection.getMuxCapacityKbps()) {
            return null;
        }
        return mux;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            SignalNetwork.registerTransmitter(level, getBlockPos());
            TransmitterChunkLoading.forceChunkForTransmitter(level, getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            SignalNetwork.unregisterTransmitter(level, getBlockPos());
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_FREQUENCY_KHZ, frequencyKhz);
        tag.putInt(KEY_POWER_W, powerW);
        tag.putInt(KEY_ERROR_CORRECTION, errorCorrection.ordinal());
        tag.putInt(KEY_DAB_VERSION, dabVersion.ordinal());
        tag.putInt(KEY_ENSEMBLE_ID, ensembleId);
        tag.putString(KEY_ENSEMBLE_NAME, ensembleName);
        tag.putBoolean(KEY_SERVER_UNLINKED, serverUnlinked);
        if (linkedServerPos != null) {
            tag.putLong(KEY_LINKED_SERVER_POS, linkedServerPos.asLong());
            tag.putInt(KEY_LINKED_ENSEMBLE_INDEX, linkedEnsembleIndex);
            if (linkedMuxId != null) {
                tag.putString(KEY_LINKED_MUX_ID, linkedMuxId.toString());
            }
        }
        tag.putBoolean(KEY_DIRECTIONAL_ENABLED, directionalEnabled);
        tag.putInt(KEY_DIRECTION_DEGREES, directionDegrees);
        tag.putString(KEY_TII_CODE, tiiCode);
        if (antennaPos != null) {
            tag.putLong(KEY_ANTENNA_POS, antennaPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequencyKhz = SignalMath.clampFrequencyKhz(tag.getInt(KEY_FREQUENCY_KHZ));
        powerW = SignalMath.clampPowerW(tag.getInt(KEY_POWER_W));
        int index = Math.max(0, Math.min(DabErrorCorrection.values().length - 1, tag.getInt(KEY_ERROR_CORRECTION)));
        errorCorrection = DabErrorCorrection.values()[index];
        int dabIndex = Math.max(0, Math.min(DabVersion.values().length - 1, tag.getInt(KEY_DAB_VERSION)));
        dabVersion = DabVersion.values()[dabIndex];
        ensembleId = Math.max(0, Math.min(65_535, tag.getInt(KEY_ENSEMBLE_ID)));
        ensembleName = sanitizeEnsembleName(tag.getString(KEY_ENSEMBLE_NAME));
        linkedServerPos = tag.contains(KEY_LINKED_SERVER_POS) ? BlockPos.of(tag.getLong(KEY_LINKED_SERVER_POS)) : null;
        serverUnlinked = tag.getBoolean(KEY_SERVER_UNLINKED);
        linkedEnsembleIndex = Math.max(0, tag.getInt(KEY_LINKED_ENSEMBLE_INDEX));
        try {
            linkedMuxId = tag.contains(KEY_LINKED_MUX_ID) ? UUID.fromString(tag.getString(KEY_LINKED_MUX_ID)) : null;
        } catch (IllegalArgumentException exception) {
            linkedMuxId = null;
        }
        directionalEnabled = tag.getBoolean(KEY_DIRECTIONAL_ENABLED);
        directionDegrees = Math.floorMod(tag.getInt(KEY_DIRECTION_DEGREES), 360);
        if (tag.contains(KEY_TII_CODE)) {
            setTiiCode(tag.getString(KEY_TII_CODE));
        }
        antennaPos = tag.contains(KEY_ANTENNA_POS) ? BlockPos.of(tag.getLong(KEY_ANTENNA_POS)) : null;
    }

    private static String sanitizeEnsembleName(String value) {
        if (value == null) {
            return "BLANK MUX A";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "BLANK MUX A";
        }
        if (trimmed.length() > 32) {
            return trimmed.substring(0, 32);
        }
        return trimmed;
    }
}
