package com.radiomod.item;

import com.radiomod.menu.NavigatorMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Its two VOR frequencies travel with the item rather than the player. */
public class NavigatorItem extends Item {
    public NavigatorItem(Properties properties) { super(properties); }
    public static int clampFrequency(int khz) {
        return Math.clamp(Math.round(khz / 50.0f) * 50, 108_000, 117_950);
    }
    public static int frequency(ItemStack stack, int channel) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String key = channel == 1 ? "Vor1Khz" : "Vor2Khz";
        if (data == null || !data.copyTag().contains(key)) return channel == 1 ? 113_000 : 115_000;
        return clampFrequency(data.copyTag().getInt(key));
    }
    public static void setFrequency(ItemStack stack, int channel, int khz) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
                tag.putInt(channel == 1 ? "Vor1Khz" : "Vor2Khz", clampFrequency(khz)));
    }
    public static boolean pinned(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("NavigatorPin");
    }
    public static void setPinned(ItemStack stack, boolean value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("NavigatorPin", value));
    }
    public static ItemStack pinnedInInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(com.radiomod.Radio.NAVIGATOR_ITEM.get()) && pinned(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new NavigatorMenu(id, inventory, hand),
                    Component.translatable("container.radio.navigator")),
                    buffer -> buffer.writeBoolean(hand == InteractionHand.OFF_HAND));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
