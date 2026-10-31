package com.ammonium.adminshop.blocks.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Shop's block entity. It stores nothing, because the Shop trades with the Account of whoever uses it. */
public class ShopEntity extends BlockEntity {

    public ShopEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP.get(), pos, state);
    }
}
