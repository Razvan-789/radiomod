package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import com.radiomod.network.SyncVorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class VorTransmitterMenu extends AbstractContainerMenu {
    private final Player player;
    private final BlockPos pos;
    private int lastSentTick = -1000;
    private SyncVorPayload snapshot;
    public VorTransmitterMenu(int id, Inventory inventory, BlockPos pos) {
        super(Radio.VOR_TRANSMITTER_MENU.get(), id);
        player = inventory.player; this.pos = pos;
    }
    public SyncVorPayload snapshot() { return snapshot; }
    public void setSnapshot(SyncVorPayload value) {
        if (player.level().isClientSide()) snapshot = value;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount - lastSentTick >= 10) send(serverPlayer);
    }
    public void configure(ServerPlayer sender, int frequencyKhz, String identifier) {
        if (sender != player || sender.containerMenu != this || !stillValid(sender)) return;
        if (sender.level().getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor) {
            vor.setFrequencyKhz(frequencyKhz);
            vor.setIdentifier(identifier);
            send(sender);
        }
    }
    private void send(ServerPlayer viewer) {
        lastSentTick = viewer.tickCount;
        if (!(viewer.level().getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor)) return;
        snapshot = new SyncVorPayload(containerId, vor.frequencyKhz(), vor.identifier(),
                vor.hasAntennaConnected(), vor.hasDmeAttached());
        PacketDistributor.sendToPlayer(viewer, snapshot);
    }
    @Override public boolean stillValid(Player viewer) {
        return viewer == player && viewer.level().getBlockEntity(pos) instanceof VorTransmitterBlockEntity
                && viewer.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
