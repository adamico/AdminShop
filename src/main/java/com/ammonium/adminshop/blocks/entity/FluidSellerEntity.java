package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.screen.FluidSellerMenu;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The Fluid Seller's block entity. Pipes fill its {@link #TANK_CAPACITY} mB tank through the fluid capability with
 * fluids that a fluid Sell Offer takes. Every {@link #TICK_COOLDOWN} ticks it sells one trade of the tank's contents
 * into its Account. When an admin locks it to one fluid Sell Offer through {@code lockedRecipe}, it only accepts and
 * sells through that Offer.
 */
public class FluidSellerEntity extends MachineEntity {
    public static final int TICK_COOLDOWN = 20;
    public static final int TANK_CAPACITY = 64_000;

    /** Saved data key. Admins' block entity data refers to it by this name. */
    private static final String LOCKED_OFFER_KEY = "lockedRecipe";
    private static final String TANK_KEY = "tank";

    private final MachineTank tank = new MachineTank(TANK_CAPACITY, this::accepts, this::setChanged);
    private @Nullable ResourceKey<Recipe<?>> lockedOffer;
    /** Ticks since the last sale. Not saved. */
    private int ticks;

    public FluidSellerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_SELLER.get(), pos, state);
    }

    /** The tank that pipes reach through the fluid capability. */
    public MachineTank tank() {
        return tank;
    }

    /** The only fluid Sell Offer the Fluid Seller sells through, or null for any. */
    public @Nullable ResourceKey<Recipe<?>> lockedOffer() {
        return lockedOffer;
    }

    public void lockTo(@Nullable ResourceKey<Recipe<?>> offer) {
        lockedOffer = offer;
        setChanged();
    }

    /** Progress towards the next sale, from 0 to {@link #PROGRESS_STEPS}. */
    public int progress() {
        return (ticks + 1) * PROGRESS_STEPS / TICK_COOLDOWN;
    }

    private boolean accepts(FluidResource resource) {
        return level instanceof ServerLevel server
                && ShopTrades.anySellOfferTakes(ShopTrades.sellFluidOffers(server.getServer()), lockedOffer, resource);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidSellerEntity seller) {
        if (++seller.ticks < TICK_COOLDOWN) {
            return;
        }
        seller.ticks = 0;
        ShopTrades.sellFluidFrom((ServerLevel) level, seller, seller.tank, seller.lockedOffer);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child(TANK_KEY));
        output.storeNullable(LOCKED_OFFER_KEY, Recipe.KEY_CODEC, lockedOffer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty(TANK_KEY));
        lockedOffer = input.read(LOCKED_OFFER_KEY, Recipe.KEY_CODEC).orElse(null);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.adminshop.fluid_seller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return FluidSellerMenu.open(containerId, inventory, this);
    }
}
