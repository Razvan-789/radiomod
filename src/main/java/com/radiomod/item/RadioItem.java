package com.radiomod.item;

import com.radiomod.menu.RadioMenu;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.SignalMath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RadioItem extends Item {
    public static final int MODE_FM = 0;
    public static final int MODE_DAB = 1;
    public static final int MODE_DRM = 2;
    public static final int DX3_PREFER_DAB = 0;
    public static final int DX3_PREFER_FM = 1;
    public static final int DX3_BALANCED = 2;
    public static final int DX3_PREFER_QUALITY = 3;
    public static final int FM_STEP_MODE_0_1_MHZ = 0;
    public static final int FM_STEP_MODE_0_5_MHZ = 1;
    public static final int FM_STEP_MODE_1_0_MHZ = 2;
    public static final int FM_STEP_MODE_SEEK = 3;
    private static final int DEFAULT_FREQUENCY_KHZ = 101_700;
    private static final int DEFAULT_DAB_CHANNEL_INDEX = 26; // 11C
    private static final int DEFAULT_FM_STEP_MODE = FM_STEP_MODE_0_1_MHZ;
    private static final Component TITLE = Component.translatable("container.radio.radio");
    private static final Map<UUID, Integer> PLAYER_FREQUENCIES_KHZ = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_DAB_CHANNEL_INDEX = new HashMap<>();
    private static final Map<UUID, String> PLAYER_DAB_STATION_SID = new HashMap<>();
    private static final Map<UUID, String> PLAYER_DAB_STATION_MUX = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_DX3_SELECTED = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_TECHNISAT_SELECTED = new HashMap<>();
    private static final Map<UUID, String> PLAYER_DX3_FM_PI = new HashMap<>();
    private static final Map<UUID, String> PLAYER_DX3_LINK_ID = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_DX3_LINK_MODE = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_MUTED = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_AF_ENABLED = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_HUD_PINNED = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_DX3_PINNED = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_MODE = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_FM_STEP_MODE = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_AF_DECODED_FREQUENCY_KHZ = new HashMap<>();
    private static final Map<UUID, String> PLAYER_AF_DECODED_PI = new HashMap<>();
    private static final Map<UUID, String> PLAYER_AF_DECODED_LIST = new HashMap<>();
    private static final Map<UUID, CachedRdsSnapshot> PLAYER_CACHED_RDS = new HashMap<>();

    public RadioItem(Properties properties) {
        super(properties);
    }

    public boolean supportsDab() {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        setDx3Selected(player, false);
        setTechnisatSelected(player, false);
        if (getMode(player) == MODE_DRM) setMode(player, MODE_FM);
        PortableDrmRadioItem.setSelected(player, false);
        if (!level.isClientSide()) {
            MenuProvider provider = new SimpleMenuProvider((containerId, inventory, menuPlayer) ->
                    new RadioMenu(containerId, inventory, player, stack), TITLE);
            player.openMenu(provider);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static int getFrequencyKhz(ItemStack stack) {
        return DEFAULT_FREQUENCY_KHZ;
    }

    public static int getFrequencyKhz(Player player) {
        if (PLAYER_FREQUENCIES_KHZ.containsKey(player.getUUID())) {
            return PLAYER_FREQUENCIES_KHZ.get(player.getUUID());
        }
        return DEFAULT_FREQUENCY_KHZ;
    }

    public static void setFrequencyKhz(Player player, int frequencyKhz) {
        PLAYER_FREQUENCIES_KHZ.put(player.getUUID(), SignalMath.clampFmFrequencyKhz(frequencyKhz));
    }

    public static int getDabChannelIndex(Player player) {
        if (PLAYER_DAB_CHANNEL_INDEX.containsKey(player.getUUID())) {
            return PLAYER_DAB_CHANNEL_INDEX.get(player.getUUID());
        }
        return DEFAULT_DAB_CHANNEL_INDEX;
    }

    public static void setDabChannelIndex(Player player, int dabChannelIndex) {
        PLAYER_DAB_CHANNEL_INDEX.put(player.getUUID(), DabChannel.clampIndex(dabChannelIndex));
    }

    public static String getDabStationSid(Player player) {
        return PLAYER_DAB_STATION_SID.getOrDefault(player.getUUID(), "");
    }

    public static String getDabStationMux(Player player) {
        return PLAYER_DAB_STATION_MUX.getOrDefault(player.getUUID(), "");
    }

    public static void setDabStation(Player player, String mux, String sid) {
        PLAYER_DAB_STATION_MUX.put(player.getUUID(), mux);
        PLAYER_DAB_STATION_SID.put(player.getUUID(), sid);
    }

    public static boolean isDx3Selected(Player player) {
        return PLAYER_DX3_SELECTED.getOrDefault(player.getUUID(), false);
    }

    public static void setDx3Selected(Player player, boolean selected) {
        PLAYER_DX3_SELECTED.put(player.getUUID(), selected);
    }

    public static boolean isTechnisatSelected(Player player) {
        return PLAYER_TECHNISAT_SELECTED.getOrDefault(player.getUUID(), false);
    }

    public static void setTechnisatSelected(Player player, boolean selected) {
        PLAYER_TECHNISAT_SELECTED.put(player.getUUID(), selected);
    }

    public static String getDx3FmPi(Player player) {
        return PLAYER_DX3_FM_PI.getOrDefault(player.getUUID(), "");
    }

    public static void setDx3FmPi(Player player, String pi) {
        PLAYER_DX3_FM_PI.put(player.getUUID(), pi == null ? "" : pi);
    }

    public static String getDx3LinkId(Player player) {
        return PLAYER_DX3_LINK_ID.getOrDefault(player.getUUID(), "");
    }

    public static void setDx3LinkId(Player player, String id) {
        PLAYER_DX3_LINK_ID.put(player.getUUID(), id == null ? "" : id.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public static int getDx3LinkMode(Player player) {
        return PLAYER_DX3_LINK_MODE.getOrDefault(player.getUUID(), DX3_BALANCED);
    }

    public static void setDx3LinkMode(Player player, int mode) {
        PLAYER_DX3_LINK_MODE.put(player.getUUID(), mode >= DX3_PREFER_DAB && mode <= DX3_PREFER_QUALITY
                ? mode : DX3_BALANCED);
    }

    public static boolean isMuted(Player player) {
        return PLAYER_MUTED.getOrDefault(player.getUUID(), Boolean.FALSE);
    }

    public static void setMuted(Player player, boolean muted) {
        PLAYER_MUTED.put(player.getUUID(), muted);
    }

    public static boolean isAfEnabled(Player player) {
        return PLAYER_AF_ENABLED.getOrDefault(player.getUUID(), Boolean.TRUE);
    }

    public static void setAfEnabled(Player player, boolean enabled) {
        PLAYER_AF_ENABLED.put(player.getUUID(), enabled);
    }

    public static void setDecodedAfState(Player player, int frequencyKhz, String piCode, String afList) {
        UUID playerId = player.getUUID();
        PLAYER_AF_DECODED_FREQUENCY_KHZ.put(playerId, frequencyKhz);
        PLAYER_AF_DECODED_PI.put(playerId, piCode == null ? "" : piCode);
        PLAYER_AF_DECODED_LIST.put(playerId, afList == null ? "" : afList);
    }

    public static void clearDecodedAfState(Player player) {
        UUID playerId = player.getUUID();
        PLAYER_AF_DECODED_FREQUENCY_KHZ.remove(playerId);
        PLAYER_AF_DECODED_PI.remove(playerId);
        PLAYER_AF_DECODED_LIST.remove(playerId);
    }

    public static boolean hasDecodedAfStateForFrequency(Player player, int frequencyKhz) {
        return PLAYER_AF_DECODED_FREQUENCY_KHZ.getOrDefault(player.getUUID(), Integer.MIN_VALUE) == frequencyKhz;
    }

    public static String getDecodedAfPi(Player player) {
        return PLAYER_AF_DECODED_PI.getOrDefault(player.getUUID(), "");
    }

    public static String getDecodedAfList(Player player) {
        return PLAYER_AF_DECODED_LIST.getOrDefault(player.getUUID(), "");
    }

    public static boolean isHudPinned(Player player) {
        return PLAYER_HUD_PINNED.getOrDefault(player.getUUID(), Boolean.FALSE);
    }

    public static void setHudPinned(Player player, boolean pinned) {
        PLAYER_HUD_PINNED.put(player.getUUID(), pinned);
    }

    public static boolean isDx3Pinned(Player player) {
        return PLAYER_DX3_PINNED.getOrDefault(player.getUUID(), false);
    }

    public static void setDx3Pinned(Player player, boolean pinned) {
        PLAYER_DX3_PINNED.put(player.getUUID(), pinned);
    }

    public static int getMode(Player player) {
        return PLAYER_MODE.getOrDefault(player.getUUID(), MODE_FM);
    }

    public static void setMode(Player player, int mode) {
        PLAYER_MODE.put(player.getUUID(), mode == MODE_DAB ? MODE_DAB : mode == MODE_DRM ? MODE_DRM : MODE_FM);
    }

    public static int getFmStepMode(Player player) {
        return clampFmStepMode(PLAYER_FM_STEP_MODE.getOrDefault(player.getUUID(), DEFAULT_FM_STEP_MODE));
    }

    public static void setFmStepMode(Player player, int stepMode) {
        PLAYER_FM_STEP_MODE.put(player.getUUID(), clampFmStepMode(stepMode));
    }

    public static int nextFmStepMode(int stepMode) {
        return (clampFmStepMode(stepMode) + 1) % 4;
    }

    public static void setCachedRdsSnapshot(Player player, CachedRdsSnapshot snapshot) {
        PLAYER_CACHED_RDS.put(player.getUUID(), snapshot);
    }

    public static CachedRdsSnapshot getCachedRdsSnapshot(Player player, int frequencyKhz) {
        CachedRdsSnapshot snapshot = PLAYER_CACHED_RDS.get(player.getUUID());
        if (snapshot == null || snapshot.frequencyKhz() != frequencyKhz) {
            return null;
        }
        return snapshot;
    }

    private static int clampFmStepMode(int stepMode) {
        return switch (stepMode) {
            case FM_STEP_MODE_0_1_MHZ, FM_STEP_MODE_0_5_MHZ, FM_STEP_MODE_1_0_MHZ, FM_STEP_MODE_SEEK -> stepMode;
            default -> DEFAULT_FM_STEP_MODE;
        };
    }

    public record CachedRdsSnapshot(
            int frequencyKhz,
            String groupCode,
            String pi,
            String ps,
            String rt,
            String af,
            String ecc,
            String lic,
            String pin,
            String ptyn,
            String longPs,
            String odaGroup,
            int pty,
            boolean tp,
            boolean ctEnabled,
            boolean rtPlusEnabled
    ) {
    }
}
