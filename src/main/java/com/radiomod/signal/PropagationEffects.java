package com.radiomod.signal;

import com.radiomod.Config;
import java.util.Locale;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Dimension-wide VHF openings. Real time controls duration; game time controls day, moon and weather. */
public final class PropagationEffects {
    private static final String DATA_NAME = "radio_propagation";
    private static final long SPORADIC_SLOT_MS = 20 * 60_000L;
    private static final long SPORADIC_FADE_MS = 3_500L;
    private static final long AURORA_FADE_MS = 3_000L;
    private PropagationEffects() { }

    public enum Kind { SPORADIC, TROPO, AURORA }

    public static int configured(ServerLevel level, Kind kind) {
        Settings data = settings(level);
        return switch (kind) {
            case SPORADIC -> data.sporadicMuf;
            case TROPO -> data.tropoStrength;
            case AURORA -> data.auroraIntensity;
        };
    }

    public static boolean forced(ServerLevel level, Kind kind) {
        Settings data = settings(level);
        return switch (kind) {
            case SPORADIC -> data.forceSporadic;
            case AURORA -> data.forceAurora;
            case TROPO -> false;
        };
    }

    public static void setForced(ServerLevel level, Kind kind, boolean enabled) {
        Settings data = settings(level);
        switch (kind) {
            case SPORADIC -> data.forceSporadic = enabled;
            case AURORA -> data.forceAurora = enabled;
            case TROPO -> throw new IllegalArgumentException("Tropo does not have an enable mode");
        }
        data.setDirty();
    }

    public static void configure(ServerLevel level, Kind kind, int value) {
        int min = kind == Kind.SPORADIC ? 60 : 1;
        int max = switch (kind) { case SPORADIC -> 108; case TROPO -> 11; case AURORA -> 10; };
        if (value != 0 && (value < min || value > max)) throw new IllegalArgumentException("Outside allowed range");
        Settings data = settings(level);
        switch (kind) {
            case SPORADIC -> data.sporadicMuf = value;
            case TROPO -> data.tropoStrength = value;
            case AURORA -> data.auroraIntensity = value;
        }
        data.setDirty();
    }

    public static String status(ServerLevel level, Kind kind) {
        return status(level, kind, null);
    }

    /** A receiver position lets the status distinguish a cold biome from a warm one. */
    public static String status(ServerLevel level, Kind kind, Vec3 receiver) {
        long now = System.currentTimeMillis();
        int configured = configured(level, kind);
        return switch (kind) {
            case SPORADIC -> {
                Opening opening = sporadic(level, now);
                boolean forced = forced(level, Kind.SPORADIC);
                yield String.format(Locale.ROOT, "Sporadic-E: %s; MUF %d MHz; %s",
                        configured == 0 ? "random" : "set", opening.muf(),
                        forced ? "FORCED OPEN" : sporadicAllowed(level) && opening.active()
                                ? "OPEN (" + opening.remainingSeconds() + " s left)"
                                : "closed");
            }
            case TROPO -> {
                Tropo state = tropo(level, now);
                yield "Tropo: " + (configured == 0 ? "random" : "set") + " strength " + state.strength()
                        + (sunny(level) ? state.duct() ? "; ducting, up to 200 km" : "; fair-weather lift"
                        : "; waiting for sunny weather");
            }
            case AURORA -> {
                Opening opening = aurora(level);
                boolean forced = forced(level, Kind.AURORA);
                boolean night = auroraNight(level);
                boolean cold = receiver == null || coldBiome(level, receiver);
                yield "Aurorascatter: " + (configured == 0 ? "random" : "set") + " intensity "
                        + opening.muf() + (forced ? "; FORCED ACTIVE"
                        : !night ? "; inactive (requires full-moon night)"
                        : !cold ? "; inactive (requires cold or snowy biome)"
                        : "; active (" + opening.remainingSeconds() + " s left)");
            }
        };
    }

    public static double fm(Level level, BlockPos transmitter, Vec3 receiver, int frequencyKhz,
                            double ordinary, boolean underwater) {
        return apply(level, transmitter, receiver, frequencyKhz, ordinary, false, underwater);
    }

    public static double dab(Level level, BlockPos transmitter, Vec3 receiver, int frequencyKhz,
                             double ordinary, boolean underwater) {
        return apply(level, transmitter, receiver, frequencyKhz, ordinary, true, underwater);
    }

    private static double apply(Level level, BlockPos transmitter, Vec3 receiver, int frequencyKhz,
                                double ordinary, boolean digital, boolean underwater) {
        if (!(level instanceof ServerLevel server)) return ordinary;
        if (!server.dimensionType().hasSkyLight() && !forced(server, Kind.SPORADIC)
                && !forced(server, Kind.AURORA)) return ordinary;
        double dx = transmitter.getX() + .5 - receiver.x, dz = transmitter.getZ() + .5 - receiver.z;
        double km = Math.hypot(dx, dz) * Config.getKilometersPerBlock();
        long now = System.currentTimeMillis();
        double result = ordinary;
        if (!digital && km >= 600 && km <= 1200
                && (forced(server, Kind.SPORADIC) || sporadicAllowed(server))) {
            Opening opening = sporadic(server, now);
            if (opening.active() && frequencyKhz <= opening.muf() * 1000) {
                double edge = Math.min((km - 600) / 110.0, (1200 - km) / 130.0);
                double fade = Math.clamp(edge, 0, 1);
                double peak = opening.rare() ? 40.0 : 20.0 + opening.muf() / 108.0 * 5.0;
                double variation = 0.12 + 0.88 * driftingNoise(server, transmitter, receiver, now, SPORADIC_FADE_MS);
                result = Math.max(result, peak * fade * variation - (underwater ? 12 : 0));
            }
        }
        if (sunny(server) && km <= 200) {
            Tropo state = tropo(server, now);
            double reach = Math.clamp((200 - km) / 80.0, 0, 1);
            long dayTick = Math.floorMod(server.dayTime(), 24_000L);
            double dawn = 0.55 + 0.45 * (0.5 + 0.5 * Math.cos(dayTick * Math.PI / 12_000.0));
            double lift = state.strength() * (digital ? 0.75 : 1.0) * reach * dawn;
            result = Math.max(result, ordinary + lift);
            if (state.duct() && km >= 25)
                result = Math.max(result, (8 + state.strength() * 1.8) * reach * dawn - (underwater ? digital ? 20 : 12 : 0));
        }
        if (km >= 100 && km <= 900
                && (forced(server, Kind.AURORA) || auroraAllowed(server, receiver))) {
            Opening opening = aurora(server);
            if (opening.active()) {
                double fade = Math.clamp(Math.min((km - 100) / 100, (900 - km) / 150), 0, 1);
                double peak = (digital ? 5.0 : 8.0) * opening.muf() / 10.0;
                result = Math.max(result, peak * fade * driftingNoise(server, transmitter, receiver, now, AURORA_FADE_MS)
                        - (underwater ? digital ? 20 : 12 : 0));
            }
        }
        return result;
    }

    private static boolean sporadicAllowed(ServerLevel level) {
        long tick = Math.floorMod(level.dayTime(), 24_000L);
        return level.dimensionType().hasSkyLight() && tick < 13_000L;
    }

    private static boolean sunny(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.isRaining() && !level.isThundering();
    }

    private static boolean auroraAllowed(ServerLevel level, Vec3 receiver) {
        return auroraNight(level) && (receiver == null || coldBiome(level, receiver));
    }

    private static boolean auroraNight(ServerLevel level) {
        long tick = Math.floorMod(level.dayTime(), 24_000L);
        return level.dimensionType().hasSkyLight() && tick >= 13_000L && tick < 23_000L
                && level.getMoonPhase() == 0;
    }

    private static boolean coldBiome(ServerLevel level, Vec3 receiver) {
        BlockPos pos = BlockPos.containing(receiver);
        var biome = level.getBiome(pos).value();
        // Temperature also covers modded cold biomes and snowy mountain variants such as Jagged Peaks.
        return biome.getBaseTemperature() <= 0.3F || biome.coldEnoughToSnow(pos);
    }

    private static Opening sporadic(ServerLevel level, long now) {
        long slot = Math.floorDiv(now, SPORADIC_SLOT_MS);
        SplittableRandom random = random(level, slot, 0x51f3322L);
        boolean scheduled = random.nextInt(8) == 0;
        long start = slot * SPORADIC_SLOT_MS + random.nextInt(3) * 60_000L;
        long end = start + (5 + random.nextInt(11)) * 60_000L;
        int configured = configured(level, Kind.SPORADIC);
        int muf;
        if (configured != 0) muf = configured;
        else if (forced(level, Kind.SPORADIC)) muf = 108;
        else {
            int tier = random.nextInt(100);
            muf = tier < 83 ? 30 + random.nextInt(30)
                    : tier < 97 ? 60 + random.nextInt(30) : 90 + random.nextInt(19);
        }
        return new Opening(forced(level, Kind.SPORADIC) || scheduled && now >= start && now < end, muf,
                Math.max(0, (end - now) / 1000), random.nextInt(25) == 0);
    }

    private static Opening aurora(ServerLevel level) {
        long gameDay = Math.floorDiv(level.dayTime(), 24_000L);
        SplittableRandom random = random(level, gameDay, 0xA43012L);
        int configured = configured(level, Kind.AURORA);
        long remainingTicks = 23_000L - Math.floorMod(level.dayTime(), 24_000L);
        return new Opening(forced(level, Kind.AURORA) || auroraNight(level),
                configured == 0 ? 1 + random.nextInt(10) : configured,
                Math.max(0, remainingTicks / 20), false);
    }

    private static Tropo tropo(ServerLevel level, long now) {
        long gameDay = Math.floorDiv(level.dayTime(), 24_000L);
        long period = Math.floorDiv(gameDay, 4);
        SplittableRandom random = random(level, period, 0x7f0329L);
        boolean duct = random.nextInt(12) == 0;
        int ductDays = 3 + random.nextInt(2);
        duct &= Math.floorMod(gameDay, 4) < ductDays;
        int configured = configured(level, Kind.TROPO);
        int strength = configured == 0 ? (duct ? 8 + random.nextInt(4) : 1 + random.nextInt(5)) : configured;
        // A small lift is common. Exceptional ducts persist through this three-to-four-day period.
        return new Tropo(strength, duct || strength >= 9);
    }

    private static double driftingNoise(ServerLevel level, BlockPos tx, Vec3 rx, long now, long interval) {
        long key = tx.asLong() ^ (BlockPos.containing(rx).asLong() * 0x9E3779B97F4A7C15L);
        long bucket = Math.floorDiv(now, interval);
        double a = unit(key ^ bucket ^ level.getSeed());
        double b = unit(key ^ (bucket + 1) ^ level.getSeed());
        double t = (now % interval) / (double) interval;
        return a + (b - a) * t;
    }

    private static double unit(long value) {
        long mixed = value * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30; mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27; mixed *= 0x94D049BB133111EBL;
        return ((mixed ^ (mixed >>> 31)) >>> 11) * 0x1.0p-53;
    }

    private static SplittableRandom random(ServerLevel level, long slot, long salt) {
        return new SplittableRandom(level.getSeed() ^ slot * 0x9E3779B97F4A7C15L
                ^ level.dimension().location().hashCode() ^ salt);
    }

    private static Settings settings(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Settings::new, Settings::load), DATA_NAME);
    }

    private record Opening(boolean active, int muf, long remainingSeconds, boolean rare) { }
    private record Tropo(int strength, boolean duct) { }
    private static final class Settings extends SavedData {
        private int sporadicMuf, tropoStrength, auroraIntensity;
        private boolean forceSporadic, forceAurora;
        private static Settings load(CompoundTag tag, HolderLookup.Provider registries) {
            Settings data = new Settings();
            data.sporadicMuf = tag.getInt("SporadicMuf");
            data.tropoStrength = tag.getInt("TropoStrength");
            data.auroraIntensity = tag.getInt("AuroraIntensity");
            data.forceSporadic = tag.getBoolean("ForceSporadic");
            data.forceAurora = tag.getBoolean("ForceAurora");
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("SporadicMuf", sporadicMuf);
            tag.putInt("TropoStrength", tropoStrength);
            tag.putInt("AuroraIntensity", auroraIntensity);
            tag.putBoolean("ForceSporadic", forceSporadic);
            tag.putBoolean("ForceAurora", forceAurora);
            return tag;
        }
    }
}
