package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.screen.SellerMenu;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The Seller's block entity. It has one slot that accepts only items a Sell Offer takes. Every
 * {@link #TICK_COOLDOWN} ticks it sells one trade of the slot's contents into its Account. A creative player can
 * lock it to one Sell Offer, and then it only accepts and sells through that Offer.
 */
public class SellerEntity extends MachineEntity {
    public static final int TICK_COOLDOWN = 20;

    private static final String INVENTORY_KEY = "inventory";
    private static final String LOCKED_OFFER_KEY = "locked_offer";

    private final MachineInventory inventory = new MachineInventory(1, this::accepts, this::setChanged);
    private @Nullable ResourceKey<Recipe<?>> lockedOffer;
    /** Ticks since the last sale. Not saved. */
    private int ticks;

    public SellerEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.SELLER.get(), pos, state);
    }

    protected SellerEntity(BlockEntityType<? extends SellerEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The slot that pipes reach through the item capability. */
    public MachineInventory inventory() {
        return inventory;
    }

    /** The only Sell Offer the Seller sells through, or null for any. */
    public @Nullable ResourceKey<Recipe<?>> lockedOffer() {
        return lockedOffer;
    }

    public void lockTo(ResourceKey<Recipe<?>> offer) {
        lockedOffer = offer;
        setChanged();
    }

    /** Progress towards the next sale, from 0 to {@link #PROGRESS_STEPS}. */
    public int progress() {
        return (ticks + 1) * PROGRESS_STEPS / TICK_COOLDOWN;
    }

    private boolean accepts(ItemResource resource) {
        return level instanceof ServerLevel server
                && ShopTrades.anySellOfferTakes(ShopTrades.sellOffers(server.getServer()), lockedOffer, resource.toStack(1));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SellerEntity seller) {
        if (++seller.ticks < TICK_COOLDOWN) {
            return;
        }
        seller.ticks = 0;
        ShopTrades.sellFrom((ServerLevel) level, seller, seller.inventory, seller.lockedOffer);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child(INVENTORY_KEY));
        output.storeNullable(LOCKED_OFFER_KEY, Recipe.KEY_CODEC, lockedOffer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty(INVENTORY_KEY));
        lockedOffer = input.read(LOCKED_OFFER_KEY, Recipe.KEY_CODEC).orElse(null);
    }

    /** Drops the slot contents when the Seller is broken. The block itself drops through its loot table. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            inventory.dropContents(level, pos);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.adminshop.seller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return SellerMenu.open(containerId, inventory, this);
    }
}
