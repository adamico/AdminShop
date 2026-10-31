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
 * A Detector's menu. It holds only the player's inventory. Data slots carry the threshold and whether the Detector
 * has an owner. A data slot travels as a signed 16-bit number, so the threshold is split across
 * {@link #THRESHOLD_SLOTS} slots of 16 bits each.
 */
public class DetectorMenu extends AbstractContainerMenu {
    private static final int THRESHOLD_SLOTS = 4;
    private static final int DATA_OWNED = THRESHOLD_SLOTS;
    private static final int DATA_COUNT = THRESHOLD_SLOTS + 1;

    private final DetectorKind kind;
    private final @Nullable DetectorEntity detector;
    private final ContainerData data;

    /** Creates the client-side menu. The threshold and owner arrive in the data slots. */
    public static DetectorMenu client(DetectorKind kind, int containerId, Inventory playerInventory) {
        return new DetectorMenu(kind, containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    /** Creates the server-side menu over the Detector. */
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

    /** Builds the data slots the server sends for a Detector with {@code threshold} and owner state {@code owned}. */
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

    /** Reads the threshold from {@code data}, as written by {@link #serverData} or received by the client. */
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

    /** The Detector's threshold, as last sent by the server. */
    public long threshold() {
        return threshold(data);
    }

    /** Whether the Detector belongs to a team. */
    public boolean hasOwner() {
        return data.get(DATA_OWNED) != 0;
    }

    /**
     * Sets the threshold of the Detector that {@code player} has open, as the confirm button on its screen does.
     * Returns false if the player has no Detector menu open or the threshold is negative. Only members of the owning
     * team can open the menu.
     */
    public static boolean setThreshold(ServerPlayer player, long threshold) {
        if (!(player.containerMenu instanceof DetectorMenu menu) || menu.detector == null || threshold < 0) {
            return false;
        }
        menu.detector.setThreshold(threshold);
        return true;
    }

    /** Moves nothing, since the menu has no slots of its own. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return detector == null || Container.stillValidBlockEntity(detector, player);
    }
}
