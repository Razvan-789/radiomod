package com.radiomod.blockentity;

import com.radiomod.Radio;
import com.radiomod.item.RadioItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Fixed DAB-only speaker with a saved scan and no service following. */
public class PureBugBlockEntity extends TechnisatReceiverBlockEntity {
    public PureBugBlockEntity(BlockPos pos, BlockState state) {
        super(Radio.PURE_BUG_BLOCK_ENTITY.get(), pos, state);
    }

    @Override public int getRadioMode() { return RadioItem.MODE_DAB; }
    @Override public void setRadioMode(int ignored) { }
    @Override public boolean isDab() { return true; }
    @Override public String getLinkId() { return ""; }
    @Override public void select(boolean dab, int tuning, String sid, String mux) {
        if (dab) super.select(true, tuning, sid, mux);
    }
}
