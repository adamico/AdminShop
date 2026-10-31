package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.blocks.BuyerTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The Creative Buyer's block entity: a T3 Buyer, locked from the start, that buys without Permits. */
public class CreativeBuyerEntity extends BuyerEntity {

    public CreativeBuyerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_BUYER.get(), BuyerTier.THREE, pos, state);
        setLocked(true);
    }

    @Override
    public boolean bypassesPermits() {
        return true;
    }
}
