package com.radiomod.server;

import com.radiomod.signal.SignalNetwork;
import com.radiomod.signal.DabServerNetwork;
import com.radiomod.signal.WebserverNetwork;
import com.radiomod.signal.AdsbServerNetwork;
import com.radiomod.signal.VorNetwork;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class TransmitterChunkLoading {
    private TransmitterChunkLoading() {
    }

    public static void forceChunkForTransmitter(Level level, BlockPos transmitterPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(transmitterPos);
        serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
    }

    public static void forceChunkForDabServer(Level level, BlockPos serverPos) {
        forceChunkForTransmitter(level, serverPos);
    }

    public static void releaseChunkIfUnused(Level level, BlockPos removedTransmitterPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkPos removedChunkPos = new ChunkPos(removedTransmitterPos);
        boolean hasOtherTransmittersInChunk = SignalNetwork.getTransmitters(level).stream()
                .filter(pos -> !pos.equals(removedTransmitterPos))
                .anyMatch(pos -> pos.getX() >> 4 == removedChunkPos.x && pos.getZ() >> 4 == removedChunkPos.z);
        boolean hasOtherServersInChunk = DabServerNetwork.getServers(level).stream()
                .filter(pos -> !pos.equals(removedTransmitterPos))
                .anyMatch(pos -> pos.getX() >> 4 == removedChunkPos.x && pos.getZ() >> 4 == removedChunkPos.z);
        boolean hasOtherWebserversInChunk = WebserverNetwork.get(level).stream()
                .filter(pos -> !pos.equals(removedTransmitterPos))
                .anyMatch(pos -> pos.getX() >> 4 == removedChunkPos.x && pos.getZ() >> 4 == removedChunkPos.z);
        boolean hasOtherVorsInChunk = VorNetwork.get(level).stream()
                .filter(pos -> !pos.equals(removedTransmitterPos))
                .anyMatch(pos -> pos.getX() >> 4 == removedChunkPos.x && pos.getZ() >> 4 == removedChunkPos.z);
        boolean hasAdsbUse = AdsbServerNetwork.get(level).stream().anyMatch(pos ->
                (!pos.equals(removedTransmitterPos) && pos.getX() >> 4 == removedChunkPos.x
                        && pos.getZ() >> 4 == removedChunkPos.z)
                        || (level.getBlockEntity(pos) instanceof AdsbServerBlockEntity server
                        && server.receiverPos() != null
                        && server.receiverPos().getX() >> 4 == removedChunkPos.x
                        && server.receiverPos().getZ() >> 4 == removedChunkPos.z));
        if (hasOtherTransmittersInChunk || hasOtherServersInChunk || hasOtherWebserversInChunk
                || hasOtherVorsInChunk || hasAdsbUse) {
            return;
        }
        serverLevel.setChunkForced(removedChunkPos.x, removedChunkPos.z, false);
    }
}
