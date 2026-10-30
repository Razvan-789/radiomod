package com.radiomod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue KILOMETERS_PER_BLOCK = BUILDER
            .comment("Distance scaling between world blocks and real distance in kilometers.",
                    "Default 0.001 means 1 block = 1 meter.")
            .defineInRange("kilometersPerBlock", 0.001D, 0.000001D, 10.0D);

    public static final ModConfigSpec.DoubleValue NOISE_FLOOR_MIN_DBUV = BUILDER
            .comment("Minimum receiver noise floor in dBuV.")
            .defineInRange("noiseFloorMinDbuv", -5.0D, -120.0D, 50.0D);

    public static final ModConfigSpec.DoubleValue NOISE_FLOOR_MAX_DBUV = BUILDER
            .comment("Maximum receiver noise floor in dBuV.")
            .defineInRange("noiseFloorMaxDbuv", -1.0D, -120.0D, 50.0D);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static double getKilometersPerBlock() {
        return KILOMETERS_PER_BLOCK.get();
    }

    public static double getNoiseFloorMinDbuv() {
        return Math.min(NOISE_FLOOR_MIN_DBUV.get(), NOISE_FLOOR_MAX_DBUV.get());
    }

    public static double getNoiseFloorMaxDbuv() {
        return Math.max(NOISE_FLOOR_MIN_DBUV.get(), NOISE_FLOOR_MAX_DBUV.get());
    }
}
