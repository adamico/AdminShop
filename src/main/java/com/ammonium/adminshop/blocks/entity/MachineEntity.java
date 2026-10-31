package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.blocks.interfaces.ShopMachine;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A machine's block entity. It saves the owning team, whose Account it works for, and opens a screen for that
 * team's members. {@link com.ammonium.adminshop.blocks.MachineBlock} assigns the owner.
 */
public abstract class MachineEntity extends BlockEntity implements ShopMachine, MenuProvider {

    /** The number of steps in a machine screen's progress bar. */
    public static final int PROGRESS_STEPS = 8;

    private static final String TEAM_KEY = "team";

    private @Nullable UUID teamId;

    protected MachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public @Nullable UUID getTeamId() {
        return teamId;
    }

    @Override
    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable(TEAM_KEY, UUIDUtil.CODEC, teamId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        teamId = input.read(TEAM_KEY, UUIDUtil.CODEC).orElse(null);
    }
}
