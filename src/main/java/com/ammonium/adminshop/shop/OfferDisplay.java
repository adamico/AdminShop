package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.recipes.ShopRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/**
 * One Offer as the shop screen, and later JEI and EMI, show it: its recipe key (what a trade request
 * names), whether it is a Buy or a Sell Offer, its goods, price and Permit.
 */
public record OfferDisplay(ResourceKey<Recipe<?>> offer, ShopRecipe recipe) {

    public boolean isBuy() {
        return recipe.isBuy();
    }

    public ShopGoods goods() {
        return recipe.goods();
    }

    /** How much one trade hands over. */
    public int count() {
        return goods().count();
    }

    public long price() {
        return recipe.price();
    }

    /** The Permit the Offer needs, or the empty string for none. */
    public String permit() {
        return recipe.permit();
    }

    /** The goods' name. It reads item components, so only once a world has loaded. */
    public Component name() {
        return recipe.name();
    }

    /** What the search matches. It reads item components, so only once a world has loaded. */
    public String searchTerm() {
        return recipe.searchTerm();
    }
}
