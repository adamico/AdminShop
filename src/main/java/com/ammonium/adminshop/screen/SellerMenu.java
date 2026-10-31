package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.entity.MachineInventory;
import com.ammonium.adminshop.blocks.entity.SellerEntity;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.shop.ShopCache;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The Seller's menu. It holds the player's inventory followed by the Seller's slot. Data slots carry the Seller's
 * progress to its next sale and whether it has an owner.
 */
public class SellerMenu extends AbstractContainerMenu {
    /** The menu slot index of the Seller's slot, after the player's 36 slots. */
    public static final int SELLER_SLOT = Inventory.INVENTORY_SIZE;

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_OWNED = 1;
    private static final int DATA_COUNT = 2;

    private final @Nullable SellerEntity seller;
    private final ContainerData data;

    /**
     * Creates the client-side menu. Its slot wraps a stub that menu sync fills, and it accepts any item that a Sell
     * Offer in the client shop cache takes.
     */
    public SellerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(ModMenuTypes.SELLER_MENU.get(), containerId, playerInventory, null,
                new MachineInventory(1, resource -> ShopTrades.anySellOfferTakes(ShopCache.CLIENT.offers(), null, resource.toStack(1)),
                        () -> {}),
                new SimpleContainerData(DATA_COUNT));
    }

    protected SellerMenu(MenuType<?> type, int containerId, Inventory playerInventory, @Nullable SellerEntity seller,
                         MachineInventory slots, ContainerData data) {
        super(type, containerId);
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
        addSlot(new ResourceHandlerSlot(slots, slots::set, 0, 55, 30));
        addDataSlots(data);
    }

    /** Creates the server-side menu over the Seller's own slot. */
    public static SellerMenu open(int containerId, Inventory playerInventory, SellerEntity seller) {
        return new SellerMenu(ModMenuTypes.SELLER_MENU.get(), containerId, playerInventory, seller, seller.inventory(), data(seller));
    }

    protected static ContainerData data(SellerEntity seller) {
        return new ContainerData() {
            @Override
            public int get(int index) {
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
        };
    }

    /**
     * The Seller's progress to its next sale, out of {@link SellerEntity#PROGRESS_STEPS}, as last sent by the
     * server.
     */
    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    /** Whether the Seller belongs to a team. */
    public boolean hasOwner() {
        return data.get(DATA_OWNED) != 0;
    }

    /**
     * Locks the Seller that {@code player} has open to the Sell Offer {@code offer}. A creative player does this by
     * clicking an item in the Seller's screen. Returns false if the player is not in creative mode or {@code offer} is
     * not a Sell Offer.
     */
    public static boolean lock(ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        if (!(player.containerMenu instanceof SellerMenu menu) || menu.seller == null) {
            return false;
        }
        if (!player.isCreative()) {
            AdminShop.LOGGER.warn("{} is not in creative mode, so can't lock a Seller", player.getPlainTextName());
            return false;
        }
        boolean isSellOffer = player.level().getServer().getRecipeManager().byKey(offer)
                .map(holder -> holder.value() instanceof SellItemRecipe)
                .orElse(false);
        if (!isSellOffer) {
            return false;
        }
        menu.seller.lockTo(offer);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == SELLER_SLOT) {
            if (!moveItemStackTo(stack, 0, SELLER_SLOT, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, SELLER_SLOT, SELLER_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return seller == null || Container.stillValidBlockEntity(seller, player);
    }
}
