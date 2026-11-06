package com.radiomod.item;

import com.radiomod.Radio;
import com.radiomod.menu.RadioMenu;
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

/** Portable FM/DAB radio sharing the standard receiver's tuning and audio state. */
public class SonyRadioItem extends RadioItem {
    private static final Map<UUID, List<Station>> SCANS = new HashMap<>();
    public record Station(int channelIndex, String mux, String sid, String label) { }
    public static boolean hasScan(Player player) { return SCANS.containsKey(player.getUUID()); }
    public static List<Station> scannedStations(Player player) { return SCANS.getOrDefault(player.getUUID(), List.of()); }
    public static void storeScan(Player player, List<Station> stations) {
        SCANS.put(player.getUUID(), List.copyOf(stations));
    }
    public static boolean hasInInventory(Player player) {
        for (ItemStack stack : player.getInventory().items)
            if (stack.is(Radio.SONY_RADIO_ITEM.get()) || stack.is(Radio.BLACK_SONY_RADIO_ITEM.get())) return true;
        for (ItemStack stack : player.getInventory().offhand)
            if (stack.is(Radio.SONY_RADIO_ITEM.get()) || stack.is(Radio.BLACK_SONY_RADIO_ITEM.get())) return true;
        return false;
    }
    public SonyRadioItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        setDx3Selected(player, false);
        setTechnisatSelected(player, false);
        setSonySelected(player, true);
        PortableDrmRadioItem.setSelected(player, false);
        if (getMode(player) == MODE_DRM) setMode(player, MODE_FM);
        if (!level.isClientSide()) player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new RadioMenu(Radio.SONY_RADIO_MENU.get(), id, inventory, owner, stack),
                Component.translatable("container.radio.sony")));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
