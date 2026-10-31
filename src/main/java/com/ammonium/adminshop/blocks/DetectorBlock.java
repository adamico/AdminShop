package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.DetectorEntity;
import com.ammonium.adminshop.blocks.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A Detector block of one {@link DetectorKind}. It gives a redstone signal based on its Account's Balance and its
 * threshold, and lights up while the signal is on.
 */
public class DetectorBlock extends MachineBlock {
    public static final MapCodec<DetectorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DetectorKind.CODEC.fieldOf("kind").forGetter(DetectorBlock::kind),
            propertiesCodec()
    ).apply(instance, DetectorBlock::new));

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final DetectorKind kind;

    /** The Basic Detector has no collision, like a torch. */
    public DetectorBlock(DetectorKind kind, Properties properties) {
        super(kind == DetectorKind.BASIC ? properties.noCollision() : properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    public DetectorKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends DetectorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind.shape();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? 0 : signal(level, pos);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind.powersStrongly(direction) ? signal(level, pos) : 0;
    }

    private static int signal(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DetectorEntity detector ? detector.signal() : 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DetectorEntity(ModBlockEntities.detector(kind), kind, pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof DetectorEntity detector) {
                DetectorEntity.serverTick(tickLevel, pos, tickState, detector);
            }
        };
    }
}
