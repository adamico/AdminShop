package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.blocks.BuyerTier;
import com.ammonium.adminshop.blocks.entity.BuyerEntity;
import com.ammonium.adminshop.blocks.entity.MachineInventory;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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
 * A Buyer's menu: the player's inventory, then the Buyer's output slots, which take nothing by hand, then a hidden
 * slot that carries the goods of the Buyer's Offer to the screen. Its progress to the next purchase, whether it has
 * an owner and whether its Offer is locked ride in data slots.
 */
public class BuyerMenu extends AbstractContainerMenu {
    /** The menu slot of the Buyer's first output slot, after the player's 36. */
    public static final int FIRST_OUTPUT_SLOT = Inventory.INVENTORY_SIZE;

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_OWNED = 1;
    private static final int DATA_LOCKED = 2;
    private static final int DATA_COUNT = 3;

    private final BuyerTier tier;
    private final @Nullable BuyerEntity buyer;
    private final ContainerData data;
    private final Slot target;

    /** Client side: the slots stand over stubs the menu's sync fills. */
    public static BuyerMenu client(BuyerTier tier, int containerId, Inventory playerInventory) {
        return new BuyerMenu(ModMenuTypes.buyer(tier), tier, containerId, playerInventory, null,
                new MachineInventory(tier.slots(), resource -> true, () -> {}), null, new SimpleContainerData(DATA_COUNT));
    }

    /** Server side, over the Buyer's own slots. */
    public static BuyerMenu open(int containerId, Inventory playerInventory, BuyerEntity buyer) {
        return new BuyerMenu(ModMenuTypes.buyer(buyer.tier()), buyer.tier(), containerId, playerInventory, buyer,
                buyer.inventory(), buyer::target, data(buyer));
    }

    protected BuyerMenu(MenuType<?> type, BuyerTier tier, int containerId, Inventory playerInventory, @Nullable BuyerEntity buyer,
                        MachineInventory outputs, @Nullable Supplier<ItemStack> target, ContainerData data) {
        super(type, containerId);
        this.tier = tier;
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
        for (int i = 0; i < tier.slots(); i++) {
            addSlot(new ResourceHandlerSlot(outputs, outputs::set, i, tier.firstSlotX() + i * 18, 42) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        this.target = addSlot(new TargetSlot(target));
        addDataSlots(data);
    }

    protected static ContainerData data(BuyerEntity buyer) {
        return new ContainerData() {
            @Override
            public int get(int index) {
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

    public BuyerTier tier() {
        return tier;
    }

    /** How far the Buyer is to its next purchase, in {@link BuyerEntity#PROGRESS_STEPS} steps, as the server last sent it. */
    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    /** Whether the Buyer belongs to a team. */
    public boolean hasOwner() {
        return data.get(DATA_OWNED) != 0;
    }

    /** Whether only a creative player can change the Buyer's Offer. */
    public boolean isLocked() {
        return data.get(DATA_LOCKED) != 0;
    }

    /** The goods one purchase of the Buyer's Offer gives, or empty if it has none. */
    public ItemStack target() {
        return target.getItem();
    }

    /**
     * Sets the Buyer whose menu {@code player} has open to buy the Buy Offer {@code offer}, as a player does by
     * clicking an item in the Buyer's screen. Returns whether it did: a locked Buyer only a creative player can
     * set, and only a member of its team can set it otherwise.
     */
    public static boolean select(ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        if (!(player.containerMenu instanceof BuyerMenu menu) || menu.buyer == null) {
            return false;
        }
        boolean isBuyOffer = player.level().getServer().getRecipeManager().byKey(offer)
                .map(holder -> holder.value() instanceof BuyItemRecipe)
                .orElse(false);
        if (!isBuyOffer) {
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
        Slot slot = slots.get(index);
        int outputsEnd = FIRST_OUTPUT_SLOT + tier.slots();
        // Only the outputs move, into the player's inventory; nothing moves into them.
        if (index < FIRST_OUTPUT_SLOT || index >= outputsEnd || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, 0, FIRST_OUTPUT_SLOT, true)) {
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
        return buyer == null || Container.stillValidBlockEntity(buyer, player);
    }

    /**
     * Carries the Buyer's goods to the client: on the server it reads them from the Buyer, on the client the menu's
     * sync fills it. Players can neither see nor touch it.
     */
    private static final class TargetSlot extends Slot {
        private final @Nullable Supplier<ItemStack> source;

        TargetSlot(@Nullable Supplier<ItemStack> source) {
            super(new SimpleContainer(1), 0, 0, 0);
            this.source = source;
        }

        @Override
        public ItemStack getItem() {
            return source != null ? source.get() : super.getItem();
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
