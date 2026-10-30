package com.radiomod.blockentity;

import com.radiomod.Radio;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Eight coupled 45-degree gain sectors; 360 degrees aliases 0 degrees. */
public class DirectionalAntennaBlockEntity extends AntennaBlockEntity {
    public static final int SECTORS = 8;
    public static final int MIN_GAIN_DB = -24;
    public static final int MAX_GAIN_DB = 8;
    private final int[] gains = new int[SECTORS];

    public DirectionalAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.DIRECTIONAL_ANTENNA_BLOCK_ENTITY.get(), pos, state);
    }

    public int gainAt(int sector) {
        return gains[Math.floorMod(sector, SECTORS)];
    }

    /** Moves up to 3 dB into or out of one sector, balancing the opposite three. */
    public void adjustGain(int sector, int direction) {
        if (sector < 0 || sector >= SECTORS || direction != -1 && direction != 1) return;
        int[] opposite = {(sector + 4) % SECTORS, (sector + 3) % SECTORS,
                (sector + 5) % SECTORS};
        boolean changed = false;
        for (int step = 0; step < 3; step++) {
            int next = gains[sector] + direction;
            if (next < MIN_GAIN_DB || next > MAX_GAIN_DB) break;
            int donor = -1;
            for (int offset = 0; offset < opposite.length; offset++) {
                int candidate = opposite[(step + offset) % opposite.length];
                int balanced = gains[candidate] - direction;
                if (balanced >= MIN_GAIN_DB && balanced <= MAX_GAIN_DB) {
                    donor = candidate;
                    break;
                }
            }
            if (donor < 0) break;
            gains[sector] = next;
            gains[donor] -= direction;
            changed = true;
        }
        if (changed) setChanged();
    }

    public double gainToward(Vec3 target) {
        Vec3 center = Vec3.atCenterOf(getBlockPos());
        double dx = target.x - center.x;
        double dz = target.z - center.z;
        if (dx * dx + dz * dz < 0.0001) return 0.0;
        double degrees = (Math.toDegrees(Math.atan2(dz, dx)) + 360.0) % 360.0;
        double sectorPosition = degrees / 45.0;
        int first = (int) sectorPosition;
        double fraction = sectorPosition - first;
        double blend = fraction * fraction * (3.0 - 2.0 * fraction);
        return gains[first] + (gains[(first + 1) % SECTORS] - gains[first]) * blend;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("SectorGains", gains);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] saved = tag.getIntArray("SectorGains");
        for (int i = 0; i < SECTORS; i++) {
            gains[i] = i < saved.length ? Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, saved[i])) : 0;
        }
        // Existing worlds stored independent gains. Redistribute their shared budget once loaded.
        int sum = java.util.Arrays.stream(gains).sum();
        while (sum != 0) {
            int selected = 0;
            for (int i = 1; i < SECTORS; i++) {
                if (sum > 0 ? gains[i] > gains[selected] : gains[i] < gains[selected]) selected = i;
            }
            gains[selected] += sum > 0 ? -1 : 1;
            sum += sum > 0 ? -1 : 1;
        }
    }
}
