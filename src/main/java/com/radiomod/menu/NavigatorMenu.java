package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import com.radiomod.item.NavigatorItem;
import com.radiomod.network.SyncNavigatorPayload;
import com.radiomod.signal.AdsbReception;
import com.radiomod.signal.VorNetwork;
import com.radiomod.signal.VorReception;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class NavigatorMenu extends AbstractContainerMenu {
    private final Player player;
    private final InteractionHand hand;
    private int lastSentTick = -1000;
    private SyncNavigatorPayload snapshot;
    public NavigatorMenu(int id, Inventory inventory, InteractionHand hand) {
        super(Radio.NAVIGATOR_MENU.get(), id);
        player = inventory.player; this.hand = hand;
    }
    public SyncNavigatorPayload snapshot() { return snapshot; }
    public void setSnapshot(SyncNavigatorPayload value) {
        if (player.level().isClientSide()) snapshot = value;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount - lastSentTick >= 10) send(serverPlayer);
    }
    public void setFrequency(ServerPlayer sender, int channel, int frequencyKhz) {
        if (sender != player || sender.containerMenu != this || !stillValid(sender)) return;
        if (channel == 0) {
            NavigatorItem.setPinned(sender.getItemInHand(hand), frequencyKhz != 0);
            send(sender);
            return;
        }
        if (channel != 1 && channel != 2) return;
        NavigatorItem.setFrequency(sender.getItemInHand(hand), channel, frequencyKhz);
        send(sender);
    }
    private void send(ServerPlayer viewer) {
        lastSentTick = viewer.tickCount;
        ServerLevel level = viewer.serverLevel();
        ItemStack stack = viewer.getItemInHand(hand);
        int frequency1 = NavigatorItem.frequency(stack, 1);
        int frequency2 = NavigatorItem.frequency(stack, 2);
        List<SyncNavigatorPayload.Transponder> contacts = new ArrayList<>();
        for (AdsbReception.Contact contact : AdsbReception.receivePortable(level, viewer.position()))
            contacts.add(new SyncNavigatorPayload.Transponder(contact.identifier(), contact.operator(),
                    contact.x(), contact.y(), contact.z(), (float) contact.distanceKm(),
                    (float) contact.snrDb(), contact.headingDegrees(), (float) contact.speedKmH()));
        List<SyncNavigatorPayload.VorEntry> registry = new ArrayList<>();
        for (ServerLevel world : viewer.getServer().getAllLevels())
            for (BlockPos pos : VorNetwork.get(world))
                if (world.getBlockEntity(pos) instanceof VorTransmitterBlockEntity vor)
                    registry.add(new SyncNavigatorPayload.VorEntry(world.dimension().location().toString(),
                            pos.asLong(), vor.identifier(), vor.frequencyKhz(), vor.hasDmeAttached(),
                            vor.hasAntennaConnected()));
        registry.sort(Comparator.comparing(SyncNavigatorPayload.VorEntry::identifier)
                .thenComparingInt(SyncNavigatorPayload.VorEntry::frequencyKhz));
        if (registry.size() > 512) registry = registry.subList(0, 512);
        snapshot = new SyncNavigatorPayload(containerId, frequency1, frequency2,
                com.radiomod.Config.getKilometersPerBlock(), NavigatorItem.pinned(stack),
                List.copyOf(contacts), List.copyOf(registry),
                toFix(VorReception.receive(level, viewer.position(), frequency1)),
                toFix(VorReception.receive(level, viewer.position(), frequency2)));
        PacketDistributor.sendToPlayer(viewer, snapshot);
    }
    private static SyncNavigatorPayload.Fix toFix(VorReception.Fix fix) {
        if (fix == null) return null;
        BlockPos pos = fix.transmitterPos();
        return new SyncNavigatorPayload.Fix(fix.identifier(), fix.radialDegrees(),
                (float) fix.horizontalKm(), (float) fix.dmeBlocks(), (float) fix.snrDb(), fix.dme(),
                pos.getX(), pos.getY(), pos.getZ());
    }
    @Override public boolean stillValid(Player player) {
        return player == this.player && player.getItemInHand(hand).is(Radio.NAVIGATOR_ITEM.get());
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
