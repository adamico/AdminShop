package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.blocks.entity.FluidBuyerEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * The Fluid Buyer's menu: the player's inventory only. Its progress to the next purchase, whether it has an owner,
 * whether its Offer is locked, what its tank holds and the fluid it buys ride in data slots.
 */
public class FluidBuyerMenu extends AbstractContainerMenu {
    private static final int DATA_PROGRESS = 0;
    private static final int DATA_OWNED = 1;
    private static final int DATA_LOCKED = 2;
    private static final int DATA_TANK = 3;
    private static final int DATA_TARGET = DATA_TANK + TankData.SLOTS;
    private static final int DATA_COUNT = DATA_TARGET + TankData.SLOTS;

    private final @Nullable FluidBuyerEntity buyer;
    private final ContainerData data;

    /** Client side: the data slots the menu's sync fills. */
    public FluidBuyerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, new SimpleContainerData(DATA_COUNT));
    }

    private FluidBuyerMenu(int containerId, Inventory playerInventory, @Nullable FluidBuyerEntity buyer, ContainerData data) {
        super(ModMenuTypes.FLUID_BUYER_MENU.get(), containerId);
        this.buyer = buyer;
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

    /** Server side, over the Fluid Buyer itself. */
    public static FluidBuyerMenu open(int containerId, Inventory playerInventory, FluidBuyerEntity buyer) {
        return new FluidBuyerMenu(containerId, playerInventory, buyer, data(buyer));
    }

    private static ContainerData data(FluidBuyerEntity buyer) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= DATA_TARGET) {
                    return TankData.encode(buyer.target(), index - DATA_TARGET);
                }
                if (index >= DATA_TANK) {
                    return TankData.encode(buyer.tank().fluid(), index - DATA_TANK);
                }
                return switch (index) {
                    case DATA_PROGRESS -> buyer.progress();
                    case DATA_OWNED -> buyer.getTeamId() != null ? 1 : 0;
                    case DATA_LOCKED -> buyer.isLocked() ? 1 : 0;
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
        };
    }

    /** How far the Fluid Buyer is to its next purchase, in {@link FluidBuyerEntity#PROGRESS_STEPS} steps, as the server last sent it. */
    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    /** Whether the Fluid Buyer belongs to a team. */
    public boolean hasOwner() {
        return data.get(DATA_OWNED) != 0;
    }

    /** Whether only a creative player can change the Fluid Buyer's Offer. */
    public boolean isLocked() {
        return data.get(DATA_LOCKED) != 0;
    }

    /** What the tank holds, without components. */
    public FluidStack tank() {
        return TankData.decode(data, DATA_TANK);
    }

    /** The fluid one purchase of the Fluid Buyer's Offer gives, without components, or empty if it has none. */
    public FluidStack target() {
        return TankData.decode(data, DATA_TARGET);
    }

    /**
     * Sets the Fluid Buyer whose menu {@code player} has open to buy the fluid Buy Offer {@code offer}, as a player
     * does by clicking a fluid container in its screen. Returns whether it did: a locked Fluid Buyer only a creative
     * player can set, and only a member of its team can set it otherwise.
     */
    public static boolean select(ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        if (!(player.containerMenu instanceof FluidBuyerMenu menu) || menu.buyer == null) {
            return false;
        }
        boolean isFluidBuyOffer = player.level().getServer().getRecipeManager().byKey(offer)
                .map(holder -> holder.value() instanceof BuyFluidRecipe)
                .orElse(false);
        if (!isFluidBuyOffer) {
            return false;
        }
        boolean creative = player.isCreative();
        var owner = menu.buyer.getTeamId();
        if (!creative && (owner == null || !MoneyHelper.get(player.level()).isMemberOfTeam(owner, player))) {
            return false;
        }
        return menu.buyer.setOffer(offer, creative);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The menu has no slots of its own to move to.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return buyer == null || Container.stillValidBlockEntity(buyer, player);
    }
}
