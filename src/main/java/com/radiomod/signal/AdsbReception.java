package com.radiomod.signal;

import com.radiomod.Config;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import com.radiomod.item.AdsbTransponderItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Loaded-entity ADS-B contacts and height-limited digital reception. */
public final class AdsbReception {
    public static final double MAX_RANGE_KM = 280.0;
    private static final Map<ServerLevel, Cache> CACHES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Track>> TRACKS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<ChunkPos>> LOADED_CHUNKS = new WeakHashMap<>();
    private AdsbReception() { }
    public record Motion(int headingDegrees, double speedKmH) { }
    public record Broadcast(UUID sourceId, String identifier, String operator, String carrier, Vec3 position, Motion motion) { }
    public record Contact(UUID sourceId, String identifier, String operator, String carrier,
                          int x, int y, int z, double rangeKm, double distanceKm, double snrDb,
                          int headingDegrees, double speedKmH) { }
    private record Cache(long tick, List<Broadcast> broadcasts) { }
    private record Track(Vec3 position, long tick) { }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level)
            LOADED_CHUNKS.computeIfAbsent(level, ignored -> new HashSet<>()).add(event.getChunk().getPos());
    }
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Set<ChunkPos> chunks = LOADED_CHUNKS.get(level);
            if (chunks != null) chunks.remove(event.getChunk().getPos());
        }
    }

    /** Each end contributes equally; two high sites reach 280 mod km at Y=640. */
    public static double rangeKm(double transmitterY) {
        double altitude = Math.max(62, Math.min(1024, transmitterY));
        return 4 + (MAX_RANGE_KM - 4) * Math.min(1, (altitude - 62) / (640 - 62));
    }
    public static double rangeKm(double transmitterY, double receiverY) {
        return (rangeKm(transmitterY) + rangeKm(receiverY)) / 2.0;
    }
    public static List<Contact> receive(AdsbReceiverBlockEntity receiver) {
        if (!(receiver.getLevel() instanceof ServerLevel level)) return List.of();
        List<BlockPos> linkedServers = AdsbServerNetwork.get(level).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof AdsbServerBlockEntity server
                        && receiver.getBlockPos().equals(server.receiverPos()))
                .toList();
        if (linkedServers.isEmpty()) return receive(receiver, null);
        Map<String, Contact> strongest = new HashMap<>();
        for (BlockPos serverPos : linkedServers) {
            for (Contact contact : receive(receiver, serverPos)) {
                String key = contact.sourceId() + "/" + contact.identifier();
                Contact previous = strongest.get(key);
                if (previous == null || contact.snrDb() > previous.snrDb()) strongest.put(key, contact);
            }
        }
        List<Contact> contacts = new ArrayList<>(strongest.values());
        contacts.sort(Comparator.comparingDouble(Contact::distanceKm)
                .thenComparing(Contact::identifier, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(contacts.subList(0, Math.min(128, contacts.size())));
    }
    /** Portable reception at the Navigator's position, without a linked external antenna. */
    public static List<Contact> receivePortable(ServerLevel level, Vec3 position) {
        int overhead = SignalMath.countOverheadBlocks(level, position);
        List<Contact> contacts = new ArrayList<>();
        for (Broadcast broadcast : broadcasts(level)) {
            Vec3 source = broadcast.position();
            double distanceKm = Math.hypot(source.x - position.x, source.z - position.z)
                    * Config.getKilometersPerBlock();
            double rangeKm = rangeKm(source.y, position.y);
            if (distanceKm > rangeKm) continue;
            double snr = 24 * (1 - Math.pow(distanceKm / rangeKm, 1.35))
                    - Math.min(12, overhead * 1.5);
            if (snr < 0) continue;
            contacts.add(new Contact(broadcast.sourceId(), broadcast.identifier(), broadcast.operator(),
                    broadcast.carrier(), (int) Math.floor(source.x), (int) Math.floor(source.y),
                    (int) Math.floor(source.z), rangeKm, distanceKm, snr,
                    broadcast.motion().headingDegrees(), broadcast.motion().speedKmH()));
        }
        contacts.sort(Comparator.comparingDouble(Contact::distanceKm)
                .thenComparing(Contact::identifier, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(contacts.subList(0, Math.min(64, contacts.size())));
    }
    /** A server's height and shelter change how well its linked receiver contributes to the network. */
    private static List<Contact> receive(AdsbReceiverBlockEntity receiver, BlockPos serverPos) {
        if (!(receiver.getLevel() instanceof ServerLevel level) || !receiver.hasAntennaConnected()) return List.of();
        BlockPos antenna = receiver.getAntennaPos();
        Vec3 center = Vec3.atCenterOf(antenna);
        double blocksPerKm = 1.0 / Config.getKilometersPerBlock();
        int overhead = SignalMath.countOverheadBlocks(level, center);
        int serverOverhead = serverPos == null ? 0
                : SignalMath.countOverheadBlocks(level, Vec3.atCenterOf(serverPos));
        double heightBonusKm = serverPos == null ? 0
                : 30.0 * Math.max(0, Math.min(1, (serverPos.getY() - 64.0) / 256.0));
        List<Contact> contacts = new ArrayList<>();
        for (Broadcast broadcast : broadcasts(level)) {
            Vec3 source = broadcast.position();
            double distanceKm = Math.hypot(source.x - center.x, source.z - center.z) / blocksPerKm;
            double rangeKm = Math.max(1, Math.min(MAX_RANGE_KM,
                    rangeKm(source.y, center.y) + heightBonusKm - serverOverhead * 1.25));
            if (distanceKm > rangeKm) continue;
            double antennaGain = SignalMath.linkedAntennaGainDb(level, antenna, receiver.getBlockPos(), source);
            double snr = 24 * (1 - Math.pow(distanceKm / rangeKm, 1.35))
                    + antennaGain - Math.min(12, overhead * 1.5) - Math.min(18, serverOverhead * 2.5);
            if (snr < 0) continue;
            contacts.add(new Contact(broadcast.sourceId(), broadcast.identifier(), broadcast.operator(), broadcast.carrier(),
                    (int) Math.floor(source.x), (int) Math.floor(source.y), (int) Math.floor(source.z),
                    rangeKm, distanceKm, snr, broadcast.motion().headingDegrees(), broadcast.motion().speedKmH()));
        }
        contacts.sort(Comparator.comparingDouble(Contact::distanceKm)
                .thenComparing(Contact::identifier, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(contacts.subList(0, Math.min(128, contacts.size())));
    }
    private static List<Broadcast> broadcasts(ServerLevel level) {
        Cache cached = CACHES.get(level);
        if (cached != null && level.getGameTime() - cached.tick < 10) return cached.broadcasts;
        List<Broadcast> found = new ArrayList<>();
        Map<UUID, Track> tracks = TRACKS.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<UUID, Track> nextTracks = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isAlive() || entity.isRemoved()) continue;
            Motion motion = motion(entity, tracks.get(entity.getUUID()), level.getGameTime());
            nextTracks.put(entity.getUUID(), new Track(entity.position(), level.getGameTime()));
            if (entity instanceof ItemEntity dropped)
                add(found, entity.getUUID(), dropped.getItem(), "Dropped item", entity.position(), motion);
            Container container = entity instanceof Player player ? player.getInventory()
                    : entity instanceof AbstractHorse horse ? horse.getInventory()
                    : entity instanceof Container stored ? stored : null;
            if (container != null) {
                for (int slot = 0; slot < Math.min(container.getContainerSize(), 256); slot++)
                    add(found, entity.getUUID(), container.getItem(slot), entity.getName().getString(), entity.position(), motion);
            } else {
                IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
                if (handler != null) for (int slot = 0; slot < Math.min(handler.getSlots(), 256); slot++)
                    add(found, entity.getUUID(), handler.getStackInSlot(slot), entity.getName().getString(), entity.position(), motion);
            }
        }
        for (ChunkPos chunkPos : LOADED_CHUNKS.getOrDefault(level, Set.of())) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) continue;
            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity == null) continue;
                Vec3 position = Vec3.atCenterOf(pos);
                UUID sourceId = UUID.nameUUIDFromBytes((level.dimension().location() + ":" + pos.asLong())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (blockEntity instanceof Container container) {
                    for (int slot = 0; slot < Math.min(container.getContainerSize(), 256); slot++)
                        add(found, sourceId, container.getItem(slot), "Container " + pos.toShortString(), position, new Motion(0, 0));
                } else {
                    IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                    if (handler != null) for (int slot = 0; slot < Math.min(handler.getSlots(), 256); slot++)
                        add(found, sourceId, handler.getStackInSlot(slot), "Container " + pos.toShortString(), position, new Motion(0, 0));
                }
            }
        }
        TRACKS.put(level, nextTracks);
        List<Broadcast> result = List.copyOf(found);
        CACHES.put(level, new Cache(level.getGameTime(), result));
        return result;
    }
    public static Motion currentMotion(Entity entity) { return motion(entity, null, 0); }
    private static Motion motion(Entity entity, Track previous, long tick) {
        Vec3 velocity = entity.getVehicle() != null ? entity.getVehicle().getDeltaMovement() : entity.getDeltaMovement();
        if (previous != null && tick > previous.tick) velocity = entity.position().subtract(previous.position)
                .scale(1.0 / (tick - previous.tick));
        double horizontal = Math.hypot(velocity.x, velocity.z);
        int heading = horizontal > .0001
                ? Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(velocity.x, -velocity.z))), 360)
                : Math.floorMod((int) Math.round(180 - entity.getYRot()), 360);
        // One game block is one real meter for speed, independent of the radio distance scale.
        double speed = horizontal * 20 * 3.6;
        return new Motion(heading, Math.max(0, Math.min(100_000, speed)));
    }
    private static void add(List<Broadcast> found, UUID sourceId, ItemStack stack, String carrier, Vec3 position, Motion motion) {
        if (!stack.is(com.radiomod.Radio.ADSB_TRANSPONDER_ITEM.get())) return;
        String id = AdsbTransponderItem.identifier(stack);
        found.add(new Broadcast(sourceId, id.isBlank() ? "UNASSIGNED" : id,
                AdsbTransponderItem.operator(stack), AdsbTransponderItem.clean(carrier, 64), position, motion));
    }
}
