package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.recipes.ShopRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * The client shop cache, holding the Offers the server last synced. The server sends the Offer recipe
 * types through NeoForge's recipe sync on login and after each {@code /reload}, and the client stores them here.
 * The shop screen, the machine screens and the recipe viewers read Offers only from this cache, never from a
 * recipe manager.
 */
public final class ShopCache {

    /** The client's cache. {@link ClientShopSync} fills and empties it. */
    public static final ShopCache CLIENT = new ShopCache();

    private List<RecipeHolder<ShopRecipe>> offers = List.of();
    private ShopDisplay display = ShopDisplay.EMPTY;

    /** Replaces the cached Offers with the Offers among {@code synced}, the recipes from one sync. */
    @SuppressWarnings("unchecked")
    public synchronized void replace(RecipeMap synced) {
        List<RecipeHolder<ShopRecipe>> received = new ArrayList<>();
        for (RecipeHolder<?> holder : synced.values()) {
            if (holder.value() instanceof ShopRecipe) {
                received.add((RecipeHolder<ShopRecipe>) holder);
            }
        }
        offers = List.copyOf(received);
        display = ShopDisplay.of(offers);
    }

    /** Removes every Offer. The client calls this when it leaves the server. */
    public synchronized void clear() {
        offers = List.of();
        display = ShopDisplay.EMPTY;
    }

    /** The cached Offer with this key, if the server synced one. */
    public synchronized Optional<RecipeHolder<ShopRecipe>> offer(ResourceKey<Recipe<?>> key) {
        return offers.stream().filter(holder -> holder.id().equals(key)).findFirst();
    }

    public synchronized List<RecipeHolder<ShopRecipe>> offers() {
        return offers;
    }

    /** The cached Offers, arranged as the shop screen lists them. */
    public synchronized ShopDisplay display() {
        return display;
    }
}
