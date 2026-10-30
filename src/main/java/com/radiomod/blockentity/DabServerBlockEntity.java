package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.signal.DabServerNetwork;
import com.radiomod.signal.DabErrorCorrection;
import com.radiomod.signal.SignalNetwork;
import com.radiomod.server.TransmitterChunkLoading;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DabServerBlockEntity extends BlockEntity {
    private static final String KEY_ENSEMBLES = "Ensembles";
    private static final String KEY_NAME = "Name";
    private static final String KEY_MUX_ID = "MuxId";
    private static final String KEY_STATIONS = "Stations";
    private static final String KEY_SID = "Sid";
    private static final String KEY_LABEL = "Label";
    private static final String KEY_DLS_TEXT = "DlsText";
    private static final String KEY_DLS_URL_MODE = "DlsUrlMode";
    private static final String KEY_DLS_URL = "DlsUrl";
    private static final String KEY_MOT_URL = "MotUrl";
    private static final String KEY_MOT_PNG = "MotPng";
    private static final String KEY_STREAM_URL = "StreamUrl";
    private static final String KEY_BITRATE_KBPS = "BitrateKbps";
    private static final String KEY_PTY = "Pty";

    private static final int MAX_ENSEMBLES = 16;
    private static final int MAX_STATIONS_PER_ENSEMBLE = 32;

    private final List<DabEnsembleDefinition> ensembles = new ArrayList<>();

    public DabServerBlockEntity(BlockPos pos, BlockState blockState) {
        super(Radio.DAB_SERVER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public int getEnsembleCount() {
        return ensembles.size();
    }

    public void addEnsemble() {
        if (ensembles.size() >= MAX_ENSEMBLES) {
            return;
        }
        String candidateBase = "MUX";
        int number = ensembles.size() + 1;
        String candidate = candidateBase + number;
        while (findEnsembleIndexByName(candidate) >= 0) {
            number++;
            candidate = candidateBase + number;
        }
        ensembles.add(new DabEnsembleDefinition(candidate));
        setChanged();
    }

    public void removeEnsemble(int index) {
        if (index < 0 || index >= ensembles.size()) {
            return;
        }
        ensembles.remove(index);
        setChanged();
    }

    public DabEnsembleDefinition getEnsemble(int index) {
        if (ensembles.isEmpty()) {
            return new DabEnsembleDefinition("MUX1");
        }
        int clamped = Math.max(0, Math.min(ensembles.size() - 1, index));
        return ensembles.get(clamped);
    }

    public int findEnsembleIndexById(UUID id) {
        for (int index = 0; index < ensembles.size(); index++) {
            if (ensembles.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    @Nullable
    public DabEnsembleDefinition findEnsembleById(UUID id) {
        int index = findEnsembleIndexById(id);
        return index < 0 ? null : ensembles.get(index);
    }

    public void renameEnsemble(int index, String name) {
        if (index < 0 || index >= ensembles.size()) {
            return;
        }
        String sanitized = sanitizeEnsembleName(name);
        if (sanitized.isBlank()) {
            return;
        }
        int existing = findEnsembleIndexByName(sanitized);
        if (existing >= 0 && existing != index) {
            return;
        }
        ensembles.get(index).setName(sanitized);
        setChanged();
    }

    public void addStation(int ensembleIndex) {
        if (ensembleIndex < 0 || ensembleIndex >= ensembles.size()) {
            return;
        }
        DabEnsembleDefinition ensemble = ensembles.get(ensembleIndex);
        if (ensemble.stations().size() >= MAX_STATIONS_PER_ENSEMBLE) {
            return;
        }
        String sid = generateUniqueSid(ensemble);
        int available = getMuxCapacityKbps(ensembleIndex) - getMuxUsedKbps(ensembleIndex);
        int initialBitrate = Math.min(128, available) / 8 * 8;
        if (initialBitrate < 8) {
            return;
        }
        ensemble.stations().add(new DabStationDefinition(sid, "Station " + (ensemble.stations().size() + 1),
                "", false, "", "", new byte[0], "", initialBitrate, 0));
        setChanged();
    }

    public void removeStation(int ensembleIndex, int stationIndex) {
        if (ensembleIndex < 0 || ensembleIndex >= ensembles.size()) {
            return;
        }
        List<DabStationDefinition> stations = ensembles.get(ensembleIndex).stations();
        if (stationIndex < 0 || stationIndex >= stations.size()) {
            return;
        }
        stations.remove(stationIndex);
        setChanged();
    }

    public DabStationDefinition getStation(int ensembleIndex, int stationIndex) {
        DabEnsembleDefinition ensemble = getEnsemble(ensembleIndex);
        if (ensemble.stations().isEmpty()) {
            return new DabStationDefinition("0001", "Station 1", "", false, "", "", new byte[0], "", 128, 0);
        }
        int clamped = Math.max(0, Math.min(ensemble.stations().size() - 1, stationIndex));
        return ensemble.stations().get(clamped);
    }

    public int getStationCount(int ensembleIndex) {
        if (ensembleIndex < 0 || ensembleIndex >= ensembles.size()) {
            return 0;
        }
        return ensembles.get(ensembleIndex).stations().size();
    }

    public int getMuxUsedKbps(int ensembleIndex) {
        if (ensembleIndex < 0 || ensembleIndex >= ensembles.size()) {
            return 0;
        }
        return ensembles.get(ensembleIndex).stations().stream().mapToInt(DabStationDefinition::bitrateKbps).sum();
    }

    public DabErrorCorrection getMuxErrorCorrection(int ensembleIndex) {
        DabErrorCorrection strictest = DabErrorCorrection.EEP_3A;
        boolean linked = false;
        if (level == null || ensembleIndex < 0 || ensembleIndex >= ensembles.size()) {
            return strictest;
        }
        DabEnsembleDefinition ensemble = ensembles.get(ensembleIndex);
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (level.getBlockEntity(pos) instanceof DabTransmitterBlockEntity transmitter
                    && transmitter.resolveLinkedMux() == ensemble) {
                DabErrorCorrection correction = transmitter.getErrorCorrection();
                if (!linked || correction.getMuxCapacityKbps() < strictest.getMuxCapacityKbps()) {
                    strictest = correction;
                }
                linked = true;
            }
        }
        return strictest;
    }

    public int getMuxCapacityKbps(int ensembleIndex) {
        return getMuxErrorCorrection(ensembleIndex).getMuxCapacityKbps();
    }

    public boolean updateStation(
            int ensembleIndex,
            int stationIndex,
            String sid,
            String label,
            boolean dlsUrlMode,
            String dlsText,
            String dlsUrl,
            String motUrl,
            String streamUrl,
            int bitrateKbps,
            int pty
    ) {
        if (ensembleIndex < 0 || ensembleIndex >= ensembles.size()) {
            return false;
        }
        List<DabStationDefinition> stations = ensembles.get(ensembleIndex).stations();
        if (stationIndex < 0 || stationIndex >= stations.size()) {
            return false;
        }
        DabStationDefinition current = stations.get(stationIndex);
        String sanitizedSid = sanitizeSid(sid);
        String sanitizedLabel = sanitizeLabel(label);
        String sanitizedDlsText = sanitizeDlsText(dlsText);
        String sanitizedDlsUrl = sanitizeUrl(dlsUrl, 256);
        String sanitizedMotUrl = sanitizeUrl(motUrl, 256);
        String sanitizedStreamUrl = sanitizeUrl(streamUrl, 256);
        int sanitizedBitrateKbps = Math.max(8, Math.min(512, bitrateKbps));
        if (sanitizedBitrateKbps % 8 != 0 || pty < 0 || pty > 31
                || getMuxUsedKbps(ensembleIndex) - current.bitrateKbps() + sanitizedBitrateKbps
                > getMuxCapacityKbps(ensembleIndex)) {
            return false;
        }
        byte[] motPng = current.motPng();
        if (!sanitizedMotUrl.equals(current.motUrl())) {
            motPng = fetchAndResizeMotPng(sanitizedMotUrl);
        }
        stations.set(
                stationIndex,
                new DabStationDefinition(
                        sanitizedSid,
                        sanitizedLabel,
                        sanitizedDlsText,
                        dlsUrlMode,
                        sanitizedDlsUrl,
                        sanitizedMotUrl,
                        motPng,
                        sanitizedStreamUrl,
                        sanitizedBitrateKbps,
                        Math.max(0, Math.min(31, pty))
                )
        );
        setChanged();
        return true;
    }

    @Nullable
    public DabEnsembleDefinition findEnsembleByNameExact(String muxName) {
        if (muxName == null || muxName.isBlank()) {
            return null;
        }
        int index = findEnsembleIndexByName(muxName.trim());
        if (index < 0) {
            return null;
        }
        return ensembles.get(index);
    }

    public boolean hasStationSid(String muxName, String sidHex) {
        DabEnsembleDefinition ensemble = findEnsembleByNameExact(muxName);
        if (ensemble == null) {
            return false;
        }
        String sid = sanitizeSid(sidHex);
        for (DabStationDefinition station : ensemble.stations()) {
            if (station.sid().equalsIgnoreCase(sid)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public String getFirstStationSid(String muxName) {
        DabEnsembleDefinition ensemble = findEnsembleByNameExact(muxName);
        if (ensemble == null || ensemble.stations().isEmpty()) {
            return null;
        }
        return ensemble.stations().get(0).sid();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            DabServerNetwork.register(level, getBlockPos());
            TransmitterChunkLoading.forceChunkForDabServer(level, getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            DabServerNetwork.unregister(level, getBlockPos());
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag ensembleTags = new ListTag();
        for (DabEnsembleDefinition ensemble : ensembles) {
            CompoundTag ensembleTag = new CompoundTag();
            ensembleTag.putString(KEY_NAME, sanitizeEnsembleName(ensemble.name()));
            ensembleTag.putString(KEY_MUX_ID, ensemble.id().toString());
            ListTag stationTags = new ListTag();
            for (DabStationDefinition station : ensemble.stations()) {
                CompoundTag stationTag = new CompoundTag();
                stationTag.putString(KEY_SID, sanitizeSid(station.sid()));
                stationTag.putString(KEY_LABEL, sanitizeLabel(station.label()));
                stationTag.putString(KEY_DLS_TEXT, sanitizeDlsText(station.dlsText()));
                stationTag.putBoolean(KEY_DLS_URL_MODE, station.dlsUrlMode());
                stationTag.putString(KEY_DLS_URL, sanitizeUrl(station.dlsUrl(), 256));
                stationTag.putString(KEY_MOT_URL, sanitizeUrl(station.motUrl(), 256));
                stationTag.putByteArray(KEY_MOT_PNG, station.motPng() == null ? new byte[0] : station.motPng());
                stationTag.putString(KEY_STREAM_URL, sanitizeUrl(station.streamUrl(), 256));
                stationTag.putInt(KEY_BITRATE_KBPS, Math.max(8, Math.min(512, station.bitrateKbps())));
                stationTag.putInt(KEY_PTY, station.pty());
                stationTags.add(stationTag);
            }
            ensembleTag.put(KEY_STATIONS, stationTags);
            ensembleTags.add(ensembleTag);
        }
        tag.put(KEY_ENSEMBLES, ensembleTags);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ensembles.clear();
        ListTag ensembleTags = tag.getList(KEY_ENSEMBLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ensembleTags.size() && ensembles.size() < MAX_ENSEMBLES; i++) {
            CompoundTag ensembleTag = ensembleTags.getCompound(i);
            String name = sanitizeEnsembleName(ensembleTag.getString(KEY_NAME));
            UUID id;
            try {
                id = UUID.fromString(ensembleTag.getString(KEY_MUX_ID));
            } catch (IllegalArgumentException exception) {
                id = UUID.nameUUIDFromBytes((getBlockPos().asLong() + ":" + i + ":" + name)
                        .getBytes(StandardCharsets.UTF_8));
            }
            DabEnsembleDefinition ensemble = new DabEnsembleDefinition(name, id);
            ListTag stationTags = ensembleTag.getList(KEY_STATIONS, Tag.TAG_COMPOUND);
            for (int j = 0; j < stationTags.size() && ensemble.stations().size() < MAX_STATIONS_PER_ENSEMBLE; j++) {
                CompoundTag stationTag = stationTags.getCompound(j);
                ensemble.stations().add(
                        new DabStationDefinition(
                                sanitizeSid(stationTag.getString(KEY_SID)),
                                sanitizeLabel(stationTag.getString(KEY_LABEL)),
                                sanitizeDlsText(stationTag.getString(KEY_DLS_TEXT)),
                                stationTag.getBoolean(KEY_DLS_URL_MODE),
                                sanitizeUrl(stationTag.getString(KEY_DLS_URL), 256),
                                sanitizeUrl(stationTag.getString(KEY_MOT_URL), 256),
                                stationTag.getByteArray(KEY_MOT_PNG),
                                sanitizeUrl(stationTag.getString(KEY_STREAM_URL), 256),
                                Math.max(8, Math.min(512, stationTag.getInt(KEY_BITRATE_KBPS))),
                                Math.max(0, Math.min(31, stationTag.getInt(KEY_PTY)))
                        )
                );
            }
            ensembles.add(ensemble);
        }
        if (ensembles.isEmpty()) {
            UUID id = UUID.nameUUIDFromBytes((getBlockPos().asLong() + ":0:MUX1")
                    .getBytes(StandardCharsets.UTF_8));
            ensembles.add(new DabEnsembleDefinition("MUX1", id));
        }
    }

    private int findEnsembleIndexByName(String name) {
        String normalized = sanitizeEnsembleName(name);
        for (int i = 0; i < ensembles.size(); i++) {
            if (ensembles.get(i).name().equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private static String sanitizeEnsembleName(String value) {
        if (value == null) {
            return "MUX1";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "MUX1";
        }
        if (trimmed.length() > 32) {
            return trimmed.substring(0, 32);
        }
        return trimmed;
    }

    private static String sanitizeSid(String value) {
        if (value == null) {
            return "0001";
        }
        String upper = value.toUpperCase();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < upper.length() && builder.length() < 8; i++) {
            char c = upper.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                builder.append(c);
            }
        }
        if (builder.length() < 4) {
            while (builder.length() < 4) {
                builder.insert(0, '0');
            }
        }
        return builder.toString();
    }

    private static String sanitizeLabel(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 32) {
            return trimmed.substring(0, 32);
        }
        return trimmed;
    }

    private static String sanitizeDlsText(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() > 512) {
            return text.substring(0, 512);
        }
        return text;
    }

    private static String sanitizeUrl(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            trimmed = trimmed.substring(0, maxLen);
        }
        return trimmed;
    }

    private static byte[] fetchAndResizeMotPng(String url) {
        if (url.isBlank()) {
            return new byte[0];
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return new byte[0];
            }
            BufferedImage source = ImageIO.read(uri.toURL());
            if (source == null) {
                return new byte[0];
            }
            Image scaled = source.getScaledInstance(256, 256, Image.SCALE_SMOOTH);
            BufferedImage out = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = out.createGraphics();
            graphics.drawImage(scaled, 0, 0, null);
            graphics.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (Exception ex) {
            Radio.LOGGER.warn("Failed to fetch/resize DAB MOT image from {}: {}", url, ex.getMessage());
            return new byte[0];
        }
    }

    private static String generateUniqueSid(DabEnsembleDefinition ensemble) {
        int seed = ensemble.stations().size() + 1;
        while (seed <= 0xFFFF) {
            String candidate = Integer.toHexString(seed).toUpperCase();
            while (candidate.length() < 4) {
                candidate = "0" + candidate;
            }
            boolean used = false;
            for (DabStationDefinition station : ensemble.stations()) {
                if (station.sid().equals(candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
            seed++;
        }
        return "FFFF";
    }

    public static final class DabEnsembleDefinition {
        private final UUID id;
        private String name;
        private final List<DabStationDefinition> stations = new ArrayList<>();

        public DabEnsembleDefinition(String name) {
            this(name, UUID.randomUUID());
        }

        private DabEnsembleDefinition(String name, UUID id) {
            this.id = id;
            this.name = sanitizeEnsembleName(name);
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = sanitizeEnsembleName(name);
        }

        public List<DabStationDefinition> stations() {
            return stations;
        }
    }

    public record DabStationDefinition(
            String sid,
            String label,
            String dlsText,
            boolean dlsUrlMode,
            String dlsUrl,
            String motUrl,
            byte[] motPng,
            String streamUrl,
            int bitrateKbps,
            int pty
    ) {
    }
}
