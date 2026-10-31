package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.blocks.DetectorBlock;
import com.ammonium.adminshop.blocks.DetectorKind;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.DetectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Detector's block entity. It stores the threshold and, every {@link #TICK_COOLDOWN} ticks, recomputes the signal
 * its {@link DetectorKind} gives for the Account's Balance. With no owner or no Account, the signal is 0.
 */
public class DetectorEntity extends MachineEntity {
    public static final int TICK_COOLDOWN = 20;

    private static final String THRESHOLD_KEY = "threshold";
    private static final String SIGNAL_KEY = "signal";

    private final DetectorKind kind;
    private long threshold;
    private int signal;
    /** Ticks since the last Balance check. Not saved. It starts one short of the cooldown, so the first tick checks. */
    private int ticks = TICK_COOLDOWN - 1;

    public DetectorEntity(BlockEntityType<? extends DetectorEntity> type, DetectorKind kind, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.kind = kind;
    }

    public DetectorKind kind() {
        return kind;
    }

    public long getThreshold() {
        return threshold;
    }

    /** Sets the threshold, which must not be negative. The signal updates at the next Balance check. */
    public void setThreshold(long threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("A Detector's threshold can't be negative: " + threshold);
        }
        this.threshold = threshold;
        setChanged();
    }

    /** The current redstone signal, 0 to 15. */
    public int signal() {
        return signal;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DetectorEntity detector) {
        if (++detector.ticks < TICK_COOLDOWN) {
            return;
        }
        detector.ticks = 0;
        var owner = detector.getTeamId();
        var account = owner == null ? null : MoneyHelper.get((ServerLevel) level).getAccountById(owner);
        detector.setSignal(level, pos, state, account == null ? 0 : detector.kind.signal(account.balance(), detector.threshold));
    }

    /**
     * Stores the new signal, updates the lit state and notifies neighbours. Like a redstone torch, it updates the
     * blocks around it and the blocks around each neighbour, because it can power them strongly.
     */
    private void setSignal(Level level, BlockPos pos, BlockState state, int newSignal) {
        if (signal == newSignal) {
            return;
        }
        signal = newSignal;
        setChanged();
        boolean lit = newSignal > 0;
        if (state.getValue(DetectorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(DetectorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), block);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong(THRESHOLD_KEY, threshold);
        output.putInt(SIGNAL_KEY, signal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        threshold = Math.max(0, input.getLongOr(THRESHOLD_KEY, 0));
        signal = Math.clamp(input.getIntOr(SIGNAL_KEY, 0), 0, 15);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(kind == DetectorKind.BASIC ? "screen.adminshop.detector" : "screen.adminshop.adv_detector");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return DetectorMenu.open(containerId, inventory, this);
    }
}
