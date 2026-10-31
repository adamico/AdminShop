package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.CreativeSellerEntity;
import com.ammonium.adminshop.blocks.entity.MachineEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Creative Seller. It is an unbreakable Seller that sells without the Offers' Permits. Placing it leaves it
 * unclaimed and opens its screen, so a creative player can lock it to a Sell Offer. The first player to use it
 * after that claims it for their team.
 */
public class CreativeSellerBlock extends SellerBlock {
    public static final MapCodec<CreativeSellerBlock> CODEC = simpleCodec(CreativeSellerBlock::new);

    public CreativeSellerBlock(Properties properties) {
        super(properties, -1.0f, 3_600_000.0f);
    }

    @Override
    protected MapCodec<CreativeSellerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeSellerEntity(pos, state);
    }

    @Override
    protected void claimOnPlacement(ServerPlayer placer, MachineEntity machine) {
        show(placer, machine);
    }
}
