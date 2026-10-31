package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.FluidBuyerEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The Fluid Buyer: buys its fluid Buy Offer on its own into its tank, charging its Account. */
public class FluidBuyerBlock extends MachineBlock {
    public static final MapCodec<FluidBuyerBlock> CODEC = simpleCodec(FluidBuyerBlock::new);

    public FluidBuyerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FluidBuyerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidBuyerEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof FluidBuyerEntity machine) {
                FluidBuyerEntity.serverTick(tickLevel, pos, tickState, machine);
            }
        };
    }
}
