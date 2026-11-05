package com.radiomod.server;

import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.network.SyncLinkLinesPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Scans loaded nearby chunks only while a player is holding the linking tool. */
public final class LinkLineSnapshot {
    private static final int RADIUS = 64;

    private LinkLineSnapshot() { }

    public static SyncLinkLinesPayload sample(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        List<SyncLinkLinesPayload.Link> links = new ArrayList<>();
        int minX = (center.getX() - RADIUS) >> 4;
        int maxX = (center.getX() + RADIUS) >> 4;
        int minZ = (center.getZ() - RADIUS) >> 4;
        int maxZ = (center.getZ() + RADIUS) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) continue;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (links.size() >= SyncLinkLinesPayload.MAX_LINKS) break;
                BlockPos from = blockEntity.getBlockPos();
                if (Vec3.atCenterOf(from).distanceToSqr(player.position()) > RADIUS * RADIUS) continue;
                if (blockEntity instanceof AntennaBlockEntity antenna) {
                    BlockPos to = antenna.getLinkedTransmitterPos();
                    BlockEntity endpoint = loadedBlockEntity(level, to);
                    if (to != null && from.equals(fromAntenna(endpoint)))
                        links.add(new SyncLinkLinesPayload.Link(from, to, SyncLinkLinesPayload.ANTENNA));
                } else if (blockEntity instanceof DabTransmitterBlockEntity dab) {
                    BlockPos to = dab.getLinkedServerPos();
                    if (loadedBlockEntity(level, to) instanceof DabServerBlockEntity)
                        links.add(new SyncLinkLinesPayload.Link(from, to, SyncLinkLinesPayload.DAB_SERVER));
                } else if (blockEntity instanceof AdsbServerBlockEntity server) {
                    BlockPos to = server.receiverPos();
                    if (loadedBlockEntity(level, to) instanceof AdsbReceiverBlockEntity)
                        links.add(new SyncLinkLinesPayload.Link(from, to, SyncLinkLinesPayload.ADSB_SERVER));
                }
            }
        }
        return new SyncLinkLinesPayload(level.dimension().location().toString(), List.copyOf(links));
    }

    private static BlockEntity loadedBlockEntity(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    private static BlockPos fromAntenna(BlockEntity endpoint) {
        if (endpoint instanceof TransmitterBlockEntity fm) return fm.getAntennaPos();
        if (endpoint instanceof DabTransmitterBlockEntity dab) return dab.getAntennaPos();
        if (endpoint instanceof DrmTransmitterBlockEntity drm) return drm.getAntennaPos();
        if (endpoint instanceof VorTransmitterBlockEntity vor) return vor.antennaPos();
        if (endpoint instanceof Dx3ReceiverBlockEntity dx3) return dx3.getAntennaPos();
        if (endpoint instanceof DrmRadioBlockEntity drmRadio) return drmRadio.getAntennaPos();
        if (endpoint instanceof WebserverBlockEntity webserver) return webserver.antennaPos();
        if (endpoint instanceof AdsbReceiverBlockEntity adsb) return adsb.getAntennaPos();
        return null;
    }
}
