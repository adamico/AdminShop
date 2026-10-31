package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import com.ammonium.adminshop.screen.FluidBuyerMenu;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The Fluid Buyer's block entity: set to one fluid Buy Offer, it buys one trade of it every {@link #TICK_COOLDOWN}
 * ticks into its {@link #TANK_CAPACITY} mB tank, charging its Account, while the Account can pay and the trade fits.
 * Pipes drain the tank through the fluid capability and can fill nothing. With {@code lockedRecipe} set, as a
 * datapack or a command can set it, only a creative player can change its Offer.
 */
public class FluidBuyerEntity extends MachineEntity {
    public static final int TICK_COOLDOWN = 20;
    public static final int TANK_CAPACITY = 64_000;

    // The 1.21.1 names, which admins' block entity data uses.
    private static final String OFFER_KEY = "recipe";
    private static final String LOCKED_KEY = "lockedRecipe";
    private static final String TANK_KEY = "tank";

    private final MachineTank tank = new MachineTank(TANK_CAPACITY, resource -> true, this::setChanged);
    private final ResourceHandler<FluidResource> output = tank.extractOnly();
    private @Nullable ResourceKey<Recipe<?>> offer;
    private boolean locked;
    /** Ticks since the last purchase; not saved. */
    private int ticks;

    public FluidBuyerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_BUYER.get(), pos, state);
    }

    /** The Fluid Buyer's tank, which its menu shows. */
    public MachineTank tank() {
        return tank;
    }

    /** The tank as pipes reach it through the fluid capability: they can only drain it. */
    public ResourceHandler<FluidResource> output() {
        return output;
    }

    /** The fluid Buy Offer the Fluid Buyer buys, or null if it has none yet. */
    public @Nullable ResourceKey<Recipe<?>> offer() {
        return offer;
    }

    /** The fluid one purchase gives, or empty if the Fluid Buyer has no Offer the server knows. */
    public FluidStack target() {
        if (offer == null || !(level instanceof ServerLevel server)) {
            return FluidStack.EMPTY;
        }
        return server.getServer().getRecipeManager().byKey(offer)
                .map(holder -> holder.value() instanceof BuyFluidRecipe buy ? buy.result().create() : FluidStack.EMPTY)
                .orElse(FluidStack.EMPTY);
    }

    /**
     * Sets the Fluid Buyer to buy {@code offer}; a locked one's Offer only a creative player can change. Returns
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

    /** Whether only a creative player can change the Fluid Buyer's Offer. */
    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        setChanged();
    }

    /** How far the Fluid Buyer is to its next purchase, in {@link #PROGRESS_STEPS} steps. */
    public int progress() {
        return (ticks + 1) * PROGRESS_STEPS / TICK_COOLDOWN;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidBuyerEntity buyer) {
        if (++buyer.ticks < TICK_COOLDOWN) {
            return;
        }
        buyer.ticks = 0;
        if (buyer.offer != null) {
            ShopTrades.buyFluidInto((ServerLevel) level, buyer, buyer.tank, buyer.offer);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child(TANK_KEY));
        output.storeNullable(OFFER_KEY, Recipe.KEY_CODEC, offer);
        output.putBoolean(LOCKED_KEY, locked);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty(TANK_KEY));
        offer = input.read(OFFER_KEY, Recipe.KEY_CODEC).orElse(null);
        locked = input.getBooleanOr(LOCKED_KEY, false);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.adminshop.fluid_buyer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return FluidBuyerMenu.open(containerId, inventory, this);
    }
}
