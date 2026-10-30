package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.item.AdsbTransponderItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class AdsbTransponderMenu extends AbstractContainerMenu {
    private final Player player;
    private final InteractionHand hand;
    private final String identifier, operator;
    private final DataSlot heading = DataSlot.standalone(), speed = DataSlot.standalone();
    public AdsbTransponderMenu(int id, Inventory inventory, InteractionHand hand, String identifier, String operator) {
        super(Radio.ADSB_TRANSPONDER_MENU.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        this.identifier = identifier;
        this.operator = operator;
        addDataSlot(heading); addDataSlot(speed);
    }
    public String identifier() { return identifier; }
    public String operator() { return operator; }
    public int headingDegrees() { return heading.get(); }
    public int speedKmH() { return speed.get(); }
    @Override public void broadcastChanges() {
        if (player instanceof ServerPlayer && player.tickCount % 10 == 0) {
            var motion = com.radiomod.signal.AdsbReception.currentMotion(player);
            heading.set(motion.headingDegrees());
            speed.set((int) Math.min(30_000, Math.round(motion.speedKmH())));
        }
        super.broadcastChanges();
    }
    public void configure(ServerPlayer sender, String identifier, String operator) {
        if (sender != player || sender.containerMenu != this || !stillValid(sender)) return;
        AdsbTransponderItem.configure(sender.getItemInHand(hand), identifier, operator);
    }
    @Override public boolean stillValid(Player player) {
        return player == this.player && player.getItemInHand(hand).is(Radio.ADSB_TRANSPONDER_ITEM.get());
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
