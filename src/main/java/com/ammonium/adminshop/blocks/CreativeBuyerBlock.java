package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.CreativeBuyerEntity;
import com.ammonium.adminshop.blocks.entity.MachineEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Creative Buyer: a T3 Buyer nobody can break, whose Offer only a creative player sets and which buys it
 * without the Offer's Permit. Placed, it stays unclaimed and opens for the placer to set its Offer; the first
 * player to use it after that claims it for their team.
 */
public class CreativeBuyerBlock extends BuyerBlock {
    public static final MapCodec<CreativeBuyerBlock> CODEC = simpleCodec(CreativeBuyerBlock::new);

    public CreativeBuyerBlock(Properties properties) {
        super(BuyerTier.THREE, properties, -1.0f, 3_600_000.0f);
    }

    @Override
    protected MapCodec<CreativeBuyerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeBuyerEntity(pos, state);
    }

    @Override
    protected void claimOnPlacement(ServerPlayer placer, MachineEntity machine) {
        show(placer, machine);
    }
}
