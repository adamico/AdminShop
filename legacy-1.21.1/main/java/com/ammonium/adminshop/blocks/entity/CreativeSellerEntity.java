// 26.1 port: the Seller (#7) is ported. On 26.1 this builds on src's SellerBlock / SellerEntity / SellerMenu /
// SellerScreen: override MachineBlock.claimOnPlacement (was assignInitialTeamId) to leave it unclaimed, SellerEntity's
// protected constructor and ShopMachine.bypassesPermits, SellerMenu's protected constructor with SellerMenu.data(seller).
package com.ammonium.adminshop.blocks.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import com.ammonium.adminshop.screen.CreativeSellerMenu;
import org.jetbrains.annotations.Nullable;

public class CreativeSellerEntity extends SellerEntity {

    public CreativeSellerEntity(BlockPos pWorldPosition, BlockState pBlockState) {
        super(pWorldPosition, pBlockState, ModBlockEntities.CREATIVE_SELLER.get());
    }

    @Override
    public boolean bypassesPermits() {
        return true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
        return new CreativeSellerMenu(pContainerId, pInventory, this);
    }
}
