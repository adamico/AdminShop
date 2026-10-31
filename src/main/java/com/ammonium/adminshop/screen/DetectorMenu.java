package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.blocks.DetectorKind;
import com.ammonium.adminshop.blocks.entity.DetectorEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A Detector's menu: only the player's inventory. The threshold and whether the Detector has an owner ride in data
 * slots; a data slot travels as a signed 16-bit number, so the threshold goes in {@link #THRESHOLD_SLOTS} of 16 bits.
 */
public class DetectorMenu extends AbstractContainerMenu {
    private static final int THRESHOLD_SLOTS = 4;
    private static final int DATA_OWNED = THRESHOLD_SLOTS;
    private static final int DATA_COUNT = THRESHOLD_SLOTS + 1;

    private final DetectorKind kind;
    private final @Nullable DetectorEntity detector;
    private final ContainerData data;

    /** Client side: the threshold and owner arrive in the data slots. */
    public static DetectorMenu client(DetectorKind kind, int containerId, Inventory playerInventory) {
        return new DetectorMenu(kind, containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    /** Server side, over the Detector itself. */
    public static DetectorMenu open(int containerId, Inventory playerInventory, DetectorEntity detector) {
        return new DetectorMenu(detector.kind(), containerId, playerInventory, detector, new ContainerData() {
            @Override
            public int get(int index) {
                return slot(detector.getThreshold(), detector.getTeamId() != null, index);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private DetectorMenu(DetectorKind kind, int containerId, Inventory playerInventory, @Nullable DetectorEntity detector,
                         ContainerData data) {
        super(ModMenuTypes.detector(kind), containerId);
        this.kind = kind;
        this.detector = detector;
        this.data = data;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 10 + column * 18, 70 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 10 + column * 18, 128));
        }
        addDataSlots(data);
    }

    /** The data slots the server sends for a Detector with {@code threshold} that has an owner or not. */
    public static ContainerData serverData(long threshold, boolean owned) {
        var data = new SimpleContainerData(DATA_COUNT);
        for (int i = 0; i < DATA_COUNT; i++) {
            data.set(i, slot(threshold, owned, i));
        }
        return data;
    }

    private static int slot(long threshold, boolean owned, int index) {
        if (index < THRESHOLD_SLOTS) {
            return (int) (threshold >>> (16 * index)) & 0xFFFF;
        }
        return index == DATA_OWNED && owned ? 1 : 0;
    }

    /** The threshold in {@code data}, as {@link #serverData} wrote it, or the client received it. */
    public static long threshold(ContainerData data) {
        long threshold = 0;
        for (int i = 0; i < THRESHOLD_SLOTS; i++) {
            threshold |= (long) (data.get(i) & 0xFFFF) << (16 * i);
        }
        return threshold;
    }

    public DetectorKind kind() {
        return kind;
    }

    /** The Detector's threshold, as the server last sent it. */
    public long threshold() {
        return threshold(data);
    }

    /** Whether the Detector belongs to a team. */
    public boolean hasOwner() {
        return data.get(DATA_OWNED) != 0;
    }

    /**
     * Sets the threshold of the Detector whose menu {@code player} has open, as its screen's confirm button does.
     * Returns whether it did: not without a Detector's menu open, nor to a negative threshold. Only the owning team's
     * members can open the menu.
     */
    public static boolean setThreshold(ServerPlayer player, long threshold) {
        if (!(player.containerMenu instanceof DetectorMenu menu) || menu.detector == null || threshold < 0) {
            return false;
        }
        menu.detector.setThreshold(threshold);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Nowhere to move to.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return detector == null || Container.stillValidBlockEntity(detector, player);
    }
}
