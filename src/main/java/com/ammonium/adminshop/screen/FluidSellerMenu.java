package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.blocks.entity.FluidSellerEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * The Fluid Seller's menu: the player's inventory only. Its progress to the next sale, whether it has an owner and
 * what its tank holds ride in data slots.
 */
public class FluidSellerMenu extends AbstractContainerMenu {
    private static final int DATA_PROGRESS = 0;
    private static final int DATA_OWNED = 1;
    private static final int DATA_TANK = 2;
    private static final int DATA_COUNT = DATA_TANK + TankData.SLOTS;

    private final @Nullable FluidSellerEntity seller;
    private final ContainerData data;

    /** Client side: the data slots the menu's sync fills. */
    public FluidSellerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    private FluidSellerMenu(int containerId, Inventory playerInventory, @Nullable FluidSellerEntity seller, ContainerData data) {
        super(ModMenuTypes.FLUID_SELLER_MENU.get(), containerId);
        this.seller = seller;
        this.data = data;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    /** Server side, over the Fluid Seller itself. */
    public static FluidSellerMenu open(int containerId, Inventory playerInventory, FluidSellerEntity seller) {
        return new FluidSellerMenu(containerId, playerInventory, seller, new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= DATA_TANK) {
                    return TankData.encode(seller.tank().fluid(), index - DATA_TANK);
                }
                return switch (index) {
                    case DATA_PROGRESS -> seller.progress();
                    case DATA_OWNED -> seller.getTeamId() != null ? 1 : 0;
                    default -> 0;
                };
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

    /** How far the Fluid Seller is to its next sale, in {@link FluidSellerEntity#PROGRESS_STEPS} steps, as the server last sent it. */
    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    /** Whether the Fluid Seller belongs to a team. */
    public boolean hasOwner() {
        return data.get(DATA_OWNED) != 0;
    }

    /** What the tank holds, without components. */
    public FluidStack tank() {
        return TankData.decode(data, DATA_TANK);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The menu has no slots of its own to move to.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return seller == null || Container.stillValidBlockEntity(seller, player);
    }
}
