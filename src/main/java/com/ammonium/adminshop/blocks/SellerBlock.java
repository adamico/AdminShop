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

/** The Seller block. It sells what is put into it through Sell Offers and credits its Account. */
public class SellerBlock extends MachineBlock {
    public static final MapCodec<SellerBlock> CODEC = simpleCodec(SellerBlock::new);

    public SellerBlock(Properties properties) {
        super(properties);
    }

    protected SellerBlock(Properties properties, float destroyTime, float explosionResistance) {
        super(properties, destroyTime, explosionResistance);
    }

    @Override
    protected MapCodec<? extends SellerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SellerEntity(pos, state);
    }

    /** The ticker accepts any {@link SellerEntity}, so the Creative Seller ticks too. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
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
