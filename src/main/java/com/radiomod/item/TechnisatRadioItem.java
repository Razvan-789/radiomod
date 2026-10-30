package com.radiomod.item;

import com.radiomod.Radio;
import com.radiomod.menu.Dx3Menu;
import com.radiomod.network.Dx3Station;
import com.radiomod.signal.DrmSignal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TechnisatRadioItem extends RadioItem {
    private static final Map<UUID, List<Dx3Station>> SCANS = new HashMap<>();
    private static final Map<UUID, List<Dx3Station>> DRM_SCANS = new HashMap<>();
    private static final Map<UUID, Integer> DRM_FREQUENCIES = new HashMap<>();
    public TechnisatRadioItem(Properties properties) { super(properties); }

    public static boolean hasScan(Player player) { return SCANS.containsKey(player.getUUID()); }

    public static List<Dx3Station> scannedStations(Player player) {
        return SCANS.getOrDefault(player.getUUID(), List.of());
    }

    public static void storeScan(Player player, List<Dx3Station> stations) {
        SCANS.put(player.getUUID(), List.copyOf(stations));
    }

    public static void clearScan(Player player) { SCANS.remove(player.getUUID()); }
    public static boolean hasDrmScan(Player player) { return DRM_SCANS.containsKey(player.getUUID()); }
    public static List<Dx3Station> scannedDrmStations(Player player) {
        return DRM_SCANS.getOrDefault(player.getUUID(), List.of());
    }
    public static void storeDrmScan(Player player, List<Dx3Station> stations) {
        DRM_SCANS.put(player.getUUID(), List.copyOf(stations));
    }

    public static int drmFrequency(Player player) { return DRM_FREQUENCIES.getOrDefault(player.getUUID(), 1000); }
    public static void setDrmFrequency(Player player, int khz) {
        DRM_FREQUENCIES.put(player.getUUID(), DrmSignal.clampFrequency(khz));
    }

    public static boolean hasInInventory(Player player) {
        for (ItemStack stack : player.getInventory().items)
            if (stack.is(Radio.TECHNISAT_RADIO_ITEM.get())) return true;
        for (ItemStack stack : player.getInventory().offhand)
            if (stack.is(Radio.TECHNISAT_RADIO_ITEM.get())) return true;
        return false;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PortableDrmRadioItem.setSelected(player, false);
        setDx3Selected(player, true);
        setTechnisatSelected(player, true);
        setDx3LinkId(player, "");
        if (!level.isClientSide()) player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                new Dx3Menu(id, inventory, owner, true),
                Component.translatable("container.radio.technisat")));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
