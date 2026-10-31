package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.FluidSellerEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The Fluid Seller block. It sells fluid piped into it through fluid Sell Offers and credits its Account. */
public class FluidSellerBlock extends MachineBlock {
    public static final MapCodec<FluidSellerBlock> CODEC = simpleCodec(FluidSellerBlock::new);

    public FluidSellerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FluidSellerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidSellerEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof FluidSellerEntity machine) {
                FluidSellerEntity.serverTick(tickLevel, pos, tickState, machine);
            }
        };
    }
}
