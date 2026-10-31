// 26.1 port: the T1-T3 Buyers (#8) are ported. On 26.1 this builds on src's BuyerBlock / BuyerEntity / BuyerMenu /
// BuyerScreen with BuyerTier.THREE (5 slots, 10 ticks, buyer_3 texture): a BuyerBlock subclass whose newBlockEntity
// passes its own BlockEntityType (the ticker already accepts any BuyerEntity), overriding MachineBlock.claimOnPlacement
// to leave it unclaimed; BuyerEntity's public constructor with setLocked(true) and ShopMachine.bypassesPermits; BuyerMenu's
// protected constructor with BuyerMenu.data(buyer) and its own MenuType, and ClientSetup registering BuyerScreen for it.
package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.screen.CreativeBuyerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CreativeBuyerEntity extends AbstractBuyerEntity {
    private static final int SLOT_SIZE = 5;
    private static final int TICK_COOLDOWN = 10;

    public CreativeBuyerEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.CREATIVE_BUYER.get(), CreativeBuyerMenu::new, blockPos, blockState, SLOT_SIZE, TICK_COOLDOWN);
        super.setLockedRecipe(true);
    }

    @Override
    public boolean bypassesPermits() {
        return true;
    }
}
