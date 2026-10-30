package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.signal.RdsFrameData;
import com.radiomod.signal.RdsGroups;
import com.radiomod.signal.SignalNetwork;
import com.radiomod.signal.SignalMath;
import com.radiomod.server.TransmitterChunkLoading;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TransmitterBlockEntity extends BlockEntity {
    private static final int PS_MAX_CHARS = 512;
    private static final int STREAM_URL_MAX_CHARS = 1024;
    private static final long DYNAMIC_PS_FRAME_INTERVAL_FRAMES = 24L; // 4s at 6 RDS frames/s
    private static final String KEY_FREQUENCY_KHZ = "FrequencyKhz";
    private static final String KEY_POWER_W = "PowerW";
    private static final String KEY_ANTENNA_POS = "AntennaPos";
    private static final String KEY_RDS_GROUPS = "RdsGroups";
    private static final String KEY_RDS_PI = "RdsPi";
    private static final String KEY_RDS_PS = "RdsPs";
    private static final String KEY_RDS_TP = "RdsTp";
    private static final String KEY_RDS_PTY = "RdsPty";
    private static final String KEY_RDS_AF = "RdsAf";
    private static final String KEY_RDS_ECC = "RdsEcc";
    private static final String KEY_RDS_LIC = "RdsLic";
    private static final String KEY_RDS_PIN = "RdsPin";
    private static final String KEY_RDS_RT = "RdsRt";
    private static final String KEY_RDS_RT_URL_MODE = "RdsRtUrlMode";
    private static final String KEY_RDS_RT_URL = "RdsRtUrl";
    private static final String KEY_RDS_ODA_RTPLUS = "RdsOdaRtPlus";
    private static final String KEY_RDS_ODA_GROUP = "RdsOdaGroup";
    private static final String KEY_RDS_CT_ENABLED = "RdsCtEnabled";
    private static final String KEY_RDS_PTYN = "RdsPtyn";
    private static final String KEY_RDS_LONG_PS = "RdsLongPs";
    private static final String KEY_STREAM_URL = "StreamUrl";
    private static final String KEY_DIRECTIONAL_ENABLED = "DirectionalEnabled";
    private static final String KEY_DIRECTION_DEGREES = "DirectionDegrees";

    private int frequencyKhz = 101_700;
    private int powerW = 50;
    private final boolean[] enabledRdsGroups = new boolean[RdsGroups.size()];
    private String rdsPi = "9150";
    private String rdsPs = "RADIO+  ";
    private boolean rdsTp = false;
    private int rdsPty = 0;
    private String rdsAf = "";
    private String rdsEcc = "";
    private String rdsLic = "";
    private String rdsPin = "";
    private String rdsRt = "";
    private boolean rdsRtUrlMode = false;
    private String rdsRtUrl = "";
    private boolean rdsOdaRtPlus = false;
    private String rdsOdaGroup = "5A";
    private boolean rdsCtEnabled = false;
    private String rdsPtyn = "";
    private String rdsLongPs = "";
    private String streamUrl = "";
    private long rtUrlLastFetchGameTime = -1;
    private String rtUrlCachedText = "";
    private boolean directionalEnabled;
    private int directionDegrees;
    @Nullable
    private BlockPos antennaPos;

    public TransmitterBlockEntity(BlockPos pos, BlockState blockState) {
        super(Radio.TRANSMITTER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public int getFrequencyKhz() {
        return frequencyKhz;
    }

    public void setFrequencyKhz(int frequencyKhz) {
        this.frequencyKhz = SignalMath.clampFmFrequencyKhz(frequencyKhz);
        setChangedAndSync();
    }

    public int getPowerW() {
        return powerW;
    }

    public void setPowerW(int powerW) {
        this.powerW = SignalMath.clampPowerW(powerW);
        setChangedAndSync();
    }

    public boolean isRdsEnabled() {
        return hasAnyRdsGroupEnabled();
    }

    public boolean isDirectionalEnabled() {
        return directionalEnabled;
    }

    public void setDirectionalEnabled(boolean directionalEnabled) {
        this.directionalEnabled = directionalEnabled;
        setChangedAndSync();
    }

    public int getDirectionDegrees() {
        return directionDegrees;
    }

    public void setDirectionDegrees(int directionDegrees) {
        this.directionDegrees = Math.floorMod(directionDegrees, 360);
        setChangedAndSync();
    }

    public void setDirectionFromYaw(float yawDegrees) {
        setDirectionDegrees(Math.round(yawDegrees + 90.0f));
    }

    public boolean isRdsGroupEnabled(int index) {
        int clamped = Math.max(0, Math.min(enabledRdsGroups.length - 1, index));
        return enabledRdsGroups[clamped];
    }

    public void setRdsGroupEnabled(int index, boolean enabled) {
        int clamped = Math.max(0, Math.min(enabledRdsGroups.length - 1, index));
        enabledRdsGroups[clamped] = enabled;
        setChangedAndSync();
    }

    public void applyRdsConfigAction(String action, String value) {
        switch (action) {
            case "set_pi" -> rdsPi = sanitizeHex(value, 4, "9150");
            case "set_ps" -> rdsPs = sanitizeAscii(value, PS_MAX_CHARS);
            case "set_tp" -> rdsTp = Boolean.parseBoolean(value);
            case "set_pty" -> rdsPty = Math.max(0, Math.min(31, parseIntSafe(value, 0)));
            case "set_af" -> rdsAf = sanitizeText(value, 64);
            case "set_ecc" -> rdsEcc = sanitizeText(value, 24);
            case "set_lic" -> rdsLic = sanitizeText(value, 24);
            case "set_pin" -> rdsPin = sanitizeText(value, 16);
            case "set_rt" -> rdsRt = sanitizeAscii(value, 64);
            case "set_rt_url_mode" -> rdsRtUrlMode = Boolean.parseBoolean(value);
            case "set_rt_url" -> rdsRtUrl = sanitizeText(value, 200);
            case "set_oda_rtplus" -> rdsOdaRtPlus = Boolean.parseBoolean(value);
            case "set_oda_group" -> rdsOdaGroup = sanitizeText(value, 3);
            case "set_ct_enabled" -> rdsCtEnabled = Boolean.parseBoolean(value);
            case "set_ptyn" -> rdsPtyn = sanitizeAscii(value, 8);
            case "set_long_ps" -> rdsLongPs = sanitizeText(value, 32);
            case "set_stream_url" -> streamUrl = sanitizeText(value, STREAM_URL_MAX_CHARS);
            case "set_group_enabled" -> {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    int groupIndex = Math.max(0, Math.min(RdsGroups.size() - 1, parseIntSafe(parts[0], 0)));
                    enabledRdsGroups[groupIndex] = Boolean.parseBoolean(parts[1]);
                }
            }
            default -> {
            }
        }
        setChangedAndSync();
    }

    public int getRdsMaskLow() {
        int lowMask = 0;
        for (int i = 0; i < 16; i++) {
            if (enabledRdsGroups[i]) {
                lowMask |= (1 << i);
            }
        }
        return lowMask;
    }

    public int getRdsMaskHigh() {
        int highMask = 0;
        for (int i = 16; i < 32; i++) {
            if (enabledRdsGroups[i]) {
                highMask |= (1 << (i - 16));
            }
        }
        return highMask;
    }

    public boolean isRdsTp() {
        return rdsTp;
    }

    public boolean isRdsRtUrlMode() {
        return rdsRtUrlMode;
    }

    public boolean isRdsOdaRtPlus() {
        return rdsOdaRtPlus;
    }

    public boolean isRdsCtEnabled() {
        return rdsCtEnabled;
    }

    public String getRdsPi() {
        return rdsPi;
    }

    public String getRdsAf() {
        return rdsAf;
    }

    public String getRdsPs() {
        return rdsPs;
    }

    public String getRdsEcc() {
        return rdsEcc;
    }

    public String getRdsLic() {
        return rdsLic;
    }

    public String getRdsPin() {
        return rdsPin;
    }

    public String getRdsPtyn() {
        return rdsPtyn;
    }

    public String getRdsLongPs() {
        return rdsLongPs;
    }

    public int getRdsPty() {
        return rdsPty;
    }

    public String getRdsRt() {
        return rdsRt;
    }

    public String getRdsRtUrl() {
        return rdsRtUrl;
    }

    public String getCurrentRtText(long gameTime) {
        return rdsRtUrlMode ? fetchRtFromUrlCached(gameTime) : rdsRt;
    }

    public String getCurrentPsFrame(long gameTime) {
        return currentPsFrame(Math.max(0L, (gameTime * 6L) / 20L));
    }

    public String getRdsOdaGroup() {
        return rdsOdaGroup;
    }

    public String getStreamUrl() {
        return streamUrl;
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
        for (String key : preset.getAllKeys()) {
            if (!key.equals(KEY_FREQUENCY_KHZ) && !key.equals(KEY_POWER_W)
                    && !key.equals(KEY_ANTENNA_POS) && !key.equals("id")
                    && !key.equals("x") && !key.equals("y") && !key.equals("z")) {
                settings.put(key, preset.get(key).copy());
            }
        }
        loadAdditional(settings, registries);
        streamUrl = com.radiomod.server.ServerAudioProxy.restartUploadedAudioClock(streamUrl);
        rtUrlLastFetchGameTime = -1;
        rtUrlCachedText = "";
        setChangedAndSync();
    }

    @Nullable
    public RdsFrameData getCurrentRdsFrame(int berPercent, long gameTime, String timeString) {
        List<Integer> enabled = getEnabledGroupIndices();
        if (enabled.isEmpty()) {
            return null;
        }

        long frame = Math.max(0L, (gameTime * 6L) / 20L);
        if (berPercent > 0) {
            int interval = Math.max(1, (int) Math.round((100.0 / berPercent) - 1.0));
            if ((frame % (interval + 1L)) == interval) {
                return null;
            }
        }

        int groupIndex = enabled.get((int) (frame % enabled.size()));
        String groupCode = RdsGroups.codeByIndex(groupIndex);

        String rt = "";
        if ("2A".equals(groupCode)) {
            rt = rdsRtUrlMode ? fetchRtFromUrlCached(gameTime) : rdsRt;
        } else if ("4A".equals(groupCode) && rdsCtEnabled) {
            rt = "CT " + timeString;
        } else if ("15A".equals(groupCode)) {
            rt = rdsLongPs;
        }

        return new RdsFrameData(groupCode, rdsPi, currentPsFrame(frame), rdsTp, rdsPty, sanitizeText(rt, 64));
    }

    private String currentPsFrame(long frame) {
        String source = rdsPs.isEmpty() ? "--------" : rdsPs;
        if (source.length() <= 8) {
            return padRight(source, 8);
        }

        int frameCount = Math.max(1, (source.length() + 7) / 8);
        long psFrame = frame / DYNAMIC_PS_FRAME_INTERVAL_FRAMES;
        int frameIndex = (int) (psFrame % frameCount);
        int start = frameIndex * 8;
        int end = Math.min(source.length(), start + 8);
        return padRight(source.substring(start, end), 8);
    }

    private String fetchRtFromUrlCached(long gameTime) {
        if (rdsRtUrl.isBlank()) {
            return "";
        }
        if (rtUrlLastFetchGameTime >= 0 && gameTime - rtUrlLastFetchGameTime < 100L) {
            return rtUrlCachedText;
        }
        rtUrlLastFetchGameTime = gameTime;
        try {
            URI uri = URI.create(rdsRtUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return rtUrlCachedText;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(uri.toURL().openStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && builder.length() < 256) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(line);
                }
                rtUrlCachedText = sanitizeText(builder.toString(), 64);
                return rtUrlCachedText;
            }
        } catch (Exception ignored) {
            return rtUrlCachedText;
        }
    }

    private List<Integer> getEnabledGroupIndices() {
        List<Integer> groups = new ArrayList<>();
        for (int i = 0; i < enabledRdsGroups.length; i++) {
            if (enabledRdsGroups[i]) {
                groups.add(i);
            }
        }
        return groups;
    }

    private boolean hasAnyRdsGroupEnabled() {
        for (boolean enabled : enabledRdsGroups) {
            if (enabled) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public BlockPos getAntennaPos() {
        return antennaPos;
    }

    public void setAntennaPos(@Nullable BlockPos antennaPos) {
        this.antennaPos = antennaPos;
        setChangedAndSync();
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
        tag.putBoolean(KEY_RDS_GROUPS, false);
        int lowMask = 0;
        int highMask = 0;
        for (int i = 0; i < 16; i++) {
            if (enabledRdsGroups[i]) {
                lowMask |= (1 << i);
            }
        }
        for (int i = 16; i < 32; i++) {
            if (enabledRdsGroups[i]) {
                highMask |= (1 << (i - 16));
            }
        }
        tag.putInt(KEY_RDS_GROUPS + "Low", lowMask);
        tag.putInt(KEY_RDS_GROUPS + "High", highMask);
        tag.putString(KEY_RDS_PI, rdsPi);
        tag.putString(KEY_RDS_PS, rdsPs);
        tag.putBoolean(KEY_RDS_TP, rdsTp);
        tag.putInt(KEY_RDS_PTY, rdsPty);
        tag.putString(KEY_RDS_AF, rdsAf);
        tag.putString(KEY_RDS_ECC, rdsEcc);
        tag.putString(KEY_RDS_LIC, rdsLic);
        tag.putString(KEY_RDS_PIN, rdsPin);
        tag.putString(KEY_RDS_RT, rdsRt);
        tag.putBoolean(KEY_RDS_RT_URL_MODE, rdsRtUrlMode);
        tag.putString(KEY_RDS_RT_URL, rdsRtUrl);
        tag.putBoolean(KEY_RDS_ODA_RTPLUS, rdsOdaRtPlus);
        tag.putString(KEY_RDS_ODA_GROUP, rdsOdaGroup);
        tag.putBoolean(KEY_RDS_CT_ENABLED, rdsCtEnabled);
        tag.putString(KEY_RDS_PTYN, rdsPtyn);
        tag.putString(KEY_RDS_LONG_PS, rdsLongPs);
        tag.putString(KEY_STREAM_URL, streamUrl);
        tag.putBoolean(KEY_DIRECTIONAL_ENABLED, directionalEnabled);
        tag.putInt(KEY_DIRECTION_DEGREES, directionDegrees);
        if (antennaPos != null) {
            tag.putLong(KEY_ANTENNA_POS, antennaPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequencyKhz = SignalMath.clampFmFrequencyKhz(tag.getInt(KEY_FREQUENCY_KHZ));
        powerW = SignalMath.clampPowerW(tag.getInt(KEY_POWER_W));
        int lowMask = tag.getInt(KEY_RDS_GROUPS + "Low");
        int highMask = tag.getInt(KEY_RDS_GROUPS + "High");
        for (int i = 0; i < 16; i++) {
            enabledRdsGroups[i] = (lowMask & (1 << i)) != 0;
        }
        for (int i = 16; i < 32; i++) {
            enabledRdsGroups[i] = (highMask & (1 << (i - 16))) != 0;
        }
        rdsPi = sanitizeHex(tag.getString(KEY_RDS_PI), 4, "9150");
        rdsPs = sanitizeAscii(tag.getString(KEY_RDS_PS), PS_MAX_CHARS);
        rdsTp = tag.getBoolean(KEY_RDS_TP);
        rdsPty = Math.max(0, Math.min(31, tag.getInt(KEY_RDS_PTY)));
        rdsAf = sanitizeText(tag.getString(KEY_RDS_AF), 64);
        rdsEcc = sanitizeText(tag.getString(KEY_RDS_ECC), 24);
        rdsLic = sanitizeText(tag.getString(KEY_RDS_LIC), 24);
        rdsPin = sanitizeText(tag.getString(KEY_RDS_PIN), 16);
        rdsRt = sanitizeAscii(tag.getString(KEY_RDS_RT), 64);
        rdsRtUrlMode = tag.getBoolean(KEY_RDS_RT_URL_MODE);
        rdsRtUrl = sanitizeText(tag.getString(KEY_RDS_RT_URL), 200);
        rdsOdaRtPlus = tag.getBoolean(KEY_RDS_ODA_RTPLUS);
        rdsOdaGroup = sanitizeText(tag.getString(KEY_RDS_ODA_GROUP), 3);
        rdsCtEnabled = tag.getBoolean(KEY_RDS_CT_ENABLED);
        rdsPtyn = sanitizeAscii(tag.getString(KEY_RDS_PTYN), 8);
        rdsLongPs = sanitizeText(tag.getString(KEY_RDS_LONG_PS), 32);
        streamUrl = sanitizeText(tag.getString(KEY_STREAM_URL), STREAM_URL_MAX_CHARS);
        directionalEnabled = tag.getBoolean(KEY_DIRECTIONAL_ENABLED);
        directionDegrees = Math.floorMod(tag.getInt(KEY_DIRECTION_DEGREES), 360);
        antennaPos = tag.contains(KEY_ANTENNA_POS) ? BlockPos.of(tag.getLong(KEY_ANTENNA_POS)) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    private static String sanitizeAscii(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value;
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c <= 126) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static String sanitizeHex(String value, int maxLength, String fallback) {
        if (value == null) {
            return fallback;
        }
        String upper = value.toUpperCase();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < upper.length() && builder.length() < maxLength; i++) {
            char c = upper.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                builder.append(c);
            }
        }
        if (builder.isEmpty()) {
            return fallback;
        }
        while (builder.length() < maxLength) {
            builder.insert(0, '0');
        }
        return builder.toString();
    }
}
