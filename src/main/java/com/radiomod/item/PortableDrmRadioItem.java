package com.radiomod.item;

import com.radiomod.Radio;
import com.radiomod.menu.PortableDrmMenu;
import com.radiomod.signal.DrmSignal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PortableDrmRadioItem extends Item {
    private static final Map<UUID, Integer> FREQUENCIES = new HashMap<>();
    private static final Map<UUID, Boolean> MUTED = new HashMap<>();
    private static final Map<UUID, Boolean> PINNED = new HashMap<>();
    private static final Map<UUID, Boolean> SELECTED = new HashMap<>();
    public PortableDrmRadioItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        setSelected(player, true);
        RadioItem.setDx3Selected(player, false);
        RadioItem.setTechnisatSelected(player, false);
        if (!level.isClientSide()) player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new PortableDrmMenu(id, inventory),
                Component.translatable("container.radio.portable_drm")));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    public static boolean hasInInventory(Player player) {
        for (ItemStack stack : player.getInventory().items) if (stack.is(Radio.PORTABLE_DRM_RADIO_ITEM.get())) return true;
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(Radio.PORTABLE_DRM_RADIO_ITEM.get())) return true;
        return false;
    }
    public static int frequency(Player player) { return FREQUENCIES.getOrDefault(player.getUUID(), 1000); }
    public static void setFrequency(Player player, int khz) { FREQUENCIES.put(player.getUUID(), DrmSignal.clampFrequency(khz)); }
    public static boolean muted(Player player) { return MUTED.getOrDefault(player.getUUID(), false); }
    public static void setMuted(Player player, boolean value) { MUTED.put(player.getUUID(), value); }
    public static boolean pinned(Player player) { return PINNED.getOrDefault(player.getUUID(), false); }
    public static void setPinned(Player player, boolean value) { PINNED.put(player.getUUID(), value); }
    public static boolean isSelected(Player player) { return SELECTED.getOrDefault(player.getUUID(), false); }
    public static void setSelected(Player player, boolean value) { SELECTED.put(player.getUUID(), value); }
}
