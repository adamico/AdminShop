package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.SellerEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The Seller: sells what is put into it through Sell Offers, crediting its Account. */
public class SellerBlock extends MachineBlock {
    public static final MapCodec<SellerBlock> CODEC = simpleCodec(SellerBlock::new);

    public SellerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SellerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SellerEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Not tied to one block entity type, so the Creative Seller's sells too.
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof SellerEntity seller) {
                SellerEntity.serverTick(tickLevel, pos, tickState, seller);
            }
        };
    }
}
