package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.BuyerEntity;
import com.ammonium.adminshop.blocks.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A Buyer block of one {@link BuyerTier}. It buys its Buy Offer on its own and charges its Account. */
public class BuyerBlock extends MachineBlock {
    public static final MapCodec<BuyerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuyerTier.CODEC.fieldOf("tier").forGetter(BuyerBlock::tier),
            propertiesCodec()
    ).apply(instance, BuyerBlock::new));

    private final BuyerTier tier;

    public BuyerBlock(BuyerTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    protected BuyerBlock(BuyerTier tier, Properties properties, float destroyTime, float explosionResistance) {
        super(properties, destroyTime, explosionResistance);
        this.tier = tier;
    }

    public BuyerTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BuyerBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuyerEntity(ModBlockEntities.buyer(tier), tier, pos, state);
    }

    /** The ticker accepts any {@link BuyerEntity}, so the Creative Buyer ticks too. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof BuyerEntity buyer) {
                BuyerEntity.serverTick(tickLevel, pos, tickState, buyer);
            }
        };
    }
}
