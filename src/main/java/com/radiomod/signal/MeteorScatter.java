package com.radiomod.signal;

import com.radiomod.blockentity.TransmitterBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Real-time FM meteor openings. Reception remains limited to Minecraft night. */
public final class MeteorScatter {
    public static final int RANDOM_RATE = 0;
    public static final int MIN_RATE = 20;
    public static final int MAX_RATE = 1_200;
    private static final long HOUR_MS = 3_600_000L;
    private static final long NIGHT_START = 13_000L;
    private static final long NIGHT_END = 23_000L;
    private static final String DATA_NAME = "radio_meteor_scatter";
    private static final WeakHashMap<ServerLevel, Hour> HOURS = new WeakHashMap<>();

    private MeteorScatter() {}

    public static double signal(Level level, TransmitterBlockEntity transmitter, Vec3 receiver,
                                int tunedFrequencyKhz, double ordinaryDbuv) {
        return signal(level, transmitter, receiver, tunedFrequencyKhz, ordinaryDbuv, false);
    }

    public static double signal(Level level, TransmitterBlockEntity transmitter, Vec3 receiver,
                                int tunedFrequencyKhz, double ordinaryDbuv, boolean underwater) {
        if (!(level instanceof ServerLevel server) || !isNight(server) || ordinaryDbuv >= 5.0
                || Math.abs(transmitter.getFrequencyKhz() - tunedFrequencyKhz) > 30) return ordinaryDbuv;
        double distance = Vec3.atCenterOf(transmitter.getBlockPos()).distanceTo(receiver);
        if (distance < 512.0 || distance > 6_000.0) return ordinaryDbuv;

        long now = System.currentTimeMillis();
        List<Burst> bursts = hour(server, now).bursts();
        int lastStarted = firstAfter(bursts, now) - 1;
        for (int i = lastStarted; i >= 0 && bursts.get(i).startMs() > now - 10_000L; i--) {
            Burst burst = bursts.get(i);
            if (!burst.transmitter().equals(transmitter.getBlockPos()) || now >= burst.endMs()) continue;
            double fade = Math.min(1.0, Math.min((now - burst.startMs() + 50) / 500.0,
                    (burst.endMs() - now) / 500.0));
            double phase = Math.floorMod(now, 60_000L) * 0.019
                    + receiver.x * 0.011 + receiver.z * 0.017 + Math.floorMod(burst.seed(), 100_000L);
            double wobble = 0.5 + Math.sin(phase) * 0.29 + Math.sin(phase * 2.71) * 0.17;
            double scattered = Math.max(0.0, Math.min(25.0, wobble * 25.0 * fade))
                    - (underwater ? 12.0 : 0.0) + transmitter.antennaGainToward(receiver);
            return Math.max(ordinaryDbuv, scattered);
        }
        return ordinaryDbuv;
    }

    public static String status(ServerLevel level) {
        long now = System.currentTimeMillis();
        Hour hour = hour(level, now);
        String rate = settings(level).rate == RANDOM_RATE
                ? "Random: " + hour.rate() + " meteors/hour "
                        + (isNight(level) ? "tonight" : "next night")
                : hour.rate() + " meteors/hour";
        if (!level.dimensionType().hasSkyLight()) {
            return rate + ". Openings require a dimension with a day-night cycle.";
        }
        if (!isNight(level)) return rate + ". FM openings occur only at night (19:00–05:00 Minecraft time).";
        List<Burst> bursts = hour.bursts();
        int nextIndex = firstAfter(bursts, now);
        Burst active = null;
        for (int i = nextIndex - 1; i >= 0 && bursts.get(i).startMs() > now - 10_000L; i--) {
            if (now < bursts.get(i).endMs()) { active = bursts.get(i); break; }
        }
        Burst next = nextIndex < bursts.size() ? bursts.get(nextIndex) : null;
        if (active != null) return rate + String.format(Locale.ROOT,
                ". Active FM opening on %.2f MHz, %d s remaining.", active.frequencyKhz() / 1000.0,
                (active.endMs() - now + 999) / 1000);
        if (next != null) return rate + String.format(Locale.ROOT,
                ". Next scheduled meteor on %.2f MHz in %d s (night reception only).", next.frequencyKhz() / 1000.0,
                (next.startMs() - now + 999) / 1000);
        return rate + ". No more openings scheduled this real hour.";
    }

    private static int firstAfter(List<Burst> bursts, long now) {
        int low = 0, high = bursts.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (bursts.get(mid).startMs() <= now) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    public static int configuredRate(ServerLevel level) {
        return settings(level).rate;
    }

    public static void setRate(ServerLevel level, int rate) {
        if (rate != RANDOM_RATE && (rate < MIN_RATE || rate > MAX_RATE)) {
            throw new IllegalArgumentException("Meteor rate must be 0 or 20–1200 per hour");
        }
        Settings settings = settings(level);
        if (settings.rate != rate) {
            settings.rate = rate;
            settings.setDirty();
            synchronized (MeteorScatter.class) { HOURS.remove(level); }
        }
    }

    private static boolean isNight(ServerLevel level) {
        long tick = Math.floorMod(level.dayTime(), 24_000L);
        return level.dimensionType().hasSkyLight() && tick >= NIGHT_START && tick < NIGHT_END;
    }

    /** Before 23:00 ticks forecast tonight; after it forecast the following night. */
    private static long forecastNightIndex(ServerLevel level) {
        long time = level.dayTime();
        long tick = Math.floorMod(time, 24_000L);
        return Math.floorDiv(time, 24_000L) + (tick >= NIGHT_END ? 1 : 0);
    }

    private static int randomNightRate(ServerLevel level, long nightIndex) {
        long seed = level.getSeed() ^ (nightIndex * 0xD1B54A32D192ED03L)
                ^ ((long) level.dimension().location().hashCode() << 32);
        SplittableRandom random = new SplittableRandom(seed);
        int band = random.nextInt(100);
        if (band < 5) return 20 + random.nextInt(10);       // 5%: 20–29
        if (band < 75) return 30 + random.nextInt(31);      // 70%: 30–60
        if (band < 91) return 61 + random.nextInt(90);      // 16%: 61–150
        if (band < 98) return 151 + random.nextInt(350);    // 7%: 151–500
        return 501 + random.nextInt(700);                   // 2%: 501–1200
    }

    private static Settings settings(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Settings::new, Settings::load), DATA_NAME);
    }

    private static synchronized Hour hour(ServerLevel level, long now) {
        long index = Math.floorDiv(now, HOUR_MS);
        long nightIndex = forecastNightIndex(level);
        long revision = SignalNetwork.revision(level);
        int configured = configuredRate(level);
        Hour cached = HOURS.get(level);
        if (cached != null && cached.index() == index && cached.revision() == revision
                && cached.configuredRate() == configured && cached.nightIndex() == nightIndex) return cached;

        List<TransmitterBlockEntity> transmitters = new ArrayList<>();
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (level.getBlockEntity(pos) instanceof TransmitterBlockEntity fm) transmitters.add(fm);
        }
        transmitters.sort(Comparator.comparingLong(fm -> fm.getBlockPos().asLong()));
        long seed = level.getSeed() ^ (index * 0x9E3779B97F4A7C15L)
                ^ level.dimension().location().hashCode();
        SplittableRandom random = new SplittableRandom(seed);
        int rate = configured == RANDOM_RATE ? randomNightRate(level, nightIndex) : configured;
        List<Burst> bursts = new ArrayList<>(transmitters.isEmpty() ? 0 : rate);
        if (!transmitters.isEmpty()) {
            long hourStart = index * HOUR_MS;
            for (int i = 0; i < rate; i++) {
                TransmitterBlockEntity fm = transmitters.get(random.nextInt(transmitters.size()));
                long slotStart = hourStart + (i * HOUR_MS) / rate;
                long slotEnd = hourStart + ((i + 1L) * HOUR_MS) / rate;
                long start = Math.min(slotStart + random.nextLong(Math.max(1L, slotEnd - slotStart)),
                        hourStart + HOUR_MS - 10_000L);
                long duration = 1_000L + random.nextLong(9_001L);
                bursts.add(new Burst(fm.getBlockPos().immutable(), fm.getFrequencyKhz(), start,
                        start + duration, random.nextLong()));
            }
        }
        bursts.sort(Comparator.comparingLong(Burst::startMs));
        Hour result = new Hour(index, nightIndex, revision, configured, rate, List.copyOf(bursts));
        HOURS.put(level, result);
        return result;
    }

    private record Hour(long index, long nightIndex, long revision, int configuredRate, int rate,
                        List<Burst> bursts) {}
    private record Burst(BlockPos transmitter, int frequencyKhz, long startMs, long endMs, long seed) {}

    private static final class Settings extends SavedData {
        private int rate = RANDOM_RATE;

        private static Settings load(CompoundTag tag, HolderLookup.Provider registries) {
            Settings settings = new Settings();
            if (tag.contains("Rate")) {
                int saved = tag.getInt("Rate");
                settings.rate = saved == RANDOM_RATE || saved >= MIN_RATE && saved <= MAX_RATE ? saved : RANDOM_RATE;
            }
            return settings;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Rate", rate);
            return tag;
        }
    }
}
