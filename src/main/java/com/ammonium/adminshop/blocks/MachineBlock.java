package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.blocks.entity.MachineEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A machine block, facing the player who placed it. A machine placed by a player belongs to that player's team.
 * If nobody placed it, the first player to use it claims it for their team. Only members of the owning team can
 * open it.
 */
public abstract class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9);

    protected MachineBlock(Properties properties) {
        this(properties, 1.0f, 1.0f);
    }

    /**
     * Takes a custom strength. The Creative machines pass a destroy time of -1 and a very high explosion
     * resistance, so they cannot be broken or blown up.
     */
    protected MachineBlock(Properties properties, float destroyTime, float explosionResistance) {
        super(properties
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(destroyTime, explosionResistance)
                .dynamicShape()
                .forceSolidOn()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (by instanceof ServerPlayer placer && level.getBlockEntity(pos) instanceof MachineEntity machine) {
            claimOnPlacement(placer, machine);
        }
    }

    /** Assigns the placed machine to {@code placer}'s team. The Creative machines leave it unclaimed instead. */
    protected void claimOnPlacement(ServerPlayer placer, MachineEntity machine) {
        machine.setTeamId(team(placer));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MachineEntity machine) {
            open(serverPlayer, machine);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Opens {@code machine}'s screen for {@code player} if they are on its team. If nobody owns it, it is claimed
     * for their team first, and only the claiming player hears the level-up sound. A player of another team gets a
     * no-access message.
     */
    public static void open(ServerPlayer player, MachineEntity machine) {
        MoneyHelper accounts = MoneyHelper.get(player.level());
        @Nullable UUID owner = machine.getTeamId();
        if (owner == null) {
            machine.setTeamId(team(player));
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, player.getRandom().nextLong()));
            player.sendSystemMessage(Component.translatable("message.adminshop.claimed_machine"));
        } else if (!accounts.isMemberOfTeam(owner, player)) {
            player.sendSystemMessage(Component.translatable("message.adminshop.no_access"));
            return;
        }
        show(player, machine);
    }

    /**
     * Opens {@code machine}'s screen for {@code player} without checking ownership. It syncs the Accounts to the
     * player first, because the screen shows the Account's name.
     */
    protected static void show(ServerPlayer player, MachineEntity machine) {
        MoneyHelper.get(player.level()).syncToPlayer(player);
        player.openMenu(machine, machine.getBlockPos());
    }

    /** The team of {@code player}'s Account. The Account is created if it does not exist yet. */
    private static UUID team(ServerPlayer player) {
        return MoneyHelper.get(player.level()).getPlayerAccount(player).teamId();
    }
}
