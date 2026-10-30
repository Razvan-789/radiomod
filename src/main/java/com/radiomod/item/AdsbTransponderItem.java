package com.radiomod.item;

import com.radiomod.menu.AdsbTransponderMenu;
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

/** Item data follows the transponder into any entity inventory. */
public class AdsbTransponderItem extends Item {
    public AdsbTransponderItem(Properties properties) { super(properties); }
    public static String identifier(ItemStack stack) { return clean(value(stack, "AdsbIdentifier"), 24); }
    public static String operator(ItemStack stack) { return clean(value(stack, "AdsbOperator"), 32); }
    private static String value(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(key);
    }
    public static void configure(ItemStack stack, String identifier, String operator) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("AdsbIdentifier", clean(identifier, 24));
            tag.putString("AdsbOperator", clean(operator, 32));
        });
    }
    public static String clean(String text, int length) {
        if (text == null) return "";
        String value = text.strip().replaceAll("[\\p{Cntrl}]", "");
        return value.substring(0, Math.min(length, value.length()));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(new SimpleMenuProvider((id, inv, owner) ->
                    new AdsbTransponderMenu(id, inv, hand, identifier(stack), operator(stack)),
                    Component.translatable("container.radio.adsb_transponder")), buffer -> {
                buffer.writeBoolean(hand == InteractionHand.OFF_HAND);
                buffer.writeUtf(identifier(stack), 24);
                buffer.writeUtf(operator(stack), 32);
            });
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
