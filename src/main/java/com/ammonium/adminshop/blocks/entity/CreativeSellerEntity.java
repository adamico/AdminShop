package com.ammonium.adminshop.blocks.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The Creative Seller's block entity. It is a Seller that sells without Permits. */
public class CreativeSellerEntity extends SellerEntity {

    public CreativeSellerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_SELLER.get(), pos, state);
    }

    @Override
    public boolean bypassesPermits() {
        return true;
    }
}
