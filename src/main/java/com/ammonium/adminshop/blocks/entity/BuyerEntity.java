package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.blocks.BuyerTier;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.screen.BuyerMenu;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * A Buyer's block entity: set to one Buy Offer, it buys one trade of it every {@link BuyerTier#cooldown()} ticks
 * into its output slots, charging its Account, while the Account can pay and the goods fit. Pipes take the goods
 * out through the item capability and can put nothing in. With {@code lockedRecipe} set, as a datapack or a
 * command can set it, only a creative player can change its Offer.
 */
public class BuyerEntity extends MachineEntity {
    // The 1.21.1 names, which admins' block entity data uses.
    private static final String OFFER_KEY = "recipe";
    private static final String LOCKED_KEY = "lockedRecipe";
    private static final String INVENTORY_KEY = "inventory";

    private final BuyerTier tier;
    private final MachineInventory inventory;
    private final ResourceHandler<ItemResource> output;
    private @Nullable ResourceKey<Recipe<?>> offer;
    private boolean locked;
    /** Ticks since the last purchase; not saved. */
    private int ticks;

    public BuyerEntity(BlockEntityType<? extends BuyerEntity> type, BuyerTier tier, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = tier;
        this.inventory = new MachineInventory(tier.slots(), resource -> true, this::setChanged);
        this.output = inventory.extractOnly();
    }

    public BuyerTier tier() {
        return tier;
    }

    /** The Buyer's output slots, which its menu shows. */
    public MachineInventory inventory() {
        return inventory;
    }

    /** The output slots as pipes reach them through the item capability: they can only take. */
    public ResourceHandler<ItemResource> output() {
        return output;
    }

    /** The Buy Offer the Buyer buys, or null if it has none yet. */
    public @Nullable ResourceKey<Recipe<?>> offer() {
        return offer;
    }

    /** The goods one purchase gives, or empty if the Buyer has no Offer the server knows. */
    public ItemStack target() {
        if (offer == null || !(level instanceof ServerLevel server)) {
            return ItemStack.EMPTY;
        }
        return server.getServer().getRecipeManager().byKey(offer)
                .map(holder -> holder.value() instanceof BuyItemRecipe buy ? buy.getItem() : ItemStack.EMPTY)
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Sets the Buyer to buy {@code offer}; a locked Buyer's Offer only a creative player can change. Returns
     * whether it did.
     */
    public boolean setOffer(ResourceKey<Recipe<?>> offer, boolean creative) {
        if (locked && !creative) {
            return false;
        }
        this.offer = offer;
        setChanged();
        return true;
    }

    /** Whether only a creative player can change the Buyer's Offer. */
    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        setChanged();
    }

    /** How far the Buyer is to its next purchase, in {@link #PROGRESS_STEPS} steps. */
    public int progress() {
        return (ticks + 1) * PROGRESS_STEPS / tier.cooldown();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BuyerEntity buyer) {
        if (++buyer.ticks < buyer.tier.cooldown()) {
            return;
        }
        buyer.ticks = 0;
        if (buyer.offer != null) {
            ShopTrades.buyInto((ServerLevel) level, buyer, buyer.inventory, buyer.offer);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child(INVENTORY_KEY));
        output.storeNullable(OFFER_KEY, Recipe.KEY_CODEC, offer);
        output.putBoolean(LOCKED_KEY, locked);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty(INVENTORY_KEY));
        offer = input.read(OFFER_KEY, Recipe.KEY_CODEC).orElse(null);
        locked = input.getBooleanOr(LOCKED_KEY, false);
    }

    /** Broken, the Buyer drops what it holds; the block drops itself through its loot table. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            inventory.dropContents(level, pos);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.adminshop.buyer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return BuyerMenu.open(containerId, inventory, this);
    }
}
