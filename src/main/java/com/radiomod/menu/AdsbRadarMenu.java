package com.radiomod.menu;

import com.radiomod.Radio;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import com.radiomod.network.SyncAdsbRadarPayload;
import com.radiomod.signal.AdsbReception;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AdsbRadarMenu extends AbstractContainerMenu {
    private final Player player;
    private final BlockPos pos;
    private int lastSentTick = -1000;
    private SyncAdsbRadarPayload snapshot;
    public AdsbRadarMenu(int id, Inventory inventory, BlockPos pos) {
        super(Radio.ADSB_RADAR_MENU.get(), id);
        player = inventory.player; this.pos = pos;
    }
    public SyncAdsbRadarPayload snapshot() { return snapshot; }
    public void setSnapshot(SyncAdsbRadarPayload value) {
        if (player.level().isClientSide()) snapshot = value;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && player.tickCount - lastSentTick >= 10) send(serverPlayer);
    }
    private void send(ServerPlayer player) {
        lastSentTick = player.tickCount;
        if (!(player.level().getBlockEntity(pos) instanceof AdsbReceiverBlockEntity receiver)) return;
        boolean connected = receiver.hasAntennaConnected();
        BlockPos center = connected ? receiver.getAntennaPos() : pos;
        List<SyncAdsbRadarPayload.Contact> contacts = new ArrayList<>();
        for (AdsbReception.Contact contact : AdsbReception.receive(receiver))
            contacts.add(new SyncAdsbRadarPayload.Contact(contact.identifier(), contact.operator(), contact.carrier(),
                    contact.x(), contact.y(), contact.z(),
                    (float) ((contact.x() - center.getX()) * com.radiomod.Config.getKilometersPerBlock()),
                    (float) ((contact.z() - center.getZ()) * com.radiomod.Config.getKilometersPerBlock()),
                    (float) contact.rangeKm(), (float) contact.distanceKm(), (float) contact.snrDb(),
                    contact.headingDegrees(), (float) contact.speedKmH()));
        PacketDistributor.sendToPlayer(player, new SyncAdsbRadarPayload(containerId, connected,
                center.getX(), center.getY(), center.getZ(), List.copyOf(contacts)));
    }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof AdsbReceiverBlockEntity
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
