package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.recipes.ShopRecipe;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/**
 * One Offer as shown by the shop screen, JEI and EMI. It holds the Offer's recipe key, which a trade request names,
 * and exposes whether it is a Buy or Sell Offer, its goods, price and Permit.
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

    /**
     * The Permit's name as shown on the shop screen. It is translated from {@code adminshop.permit.<permit>}, and
     * falls back to the Permit itself. It is empty when the Offer needs no Permit. Permit {@code "0"} is not shown,
     * though trading still requires it.
     */
    public Optional<Component> permitName() {
        String permit = permit();
        if (permit.isEmpty() || permit.equals("0")) {
            return Optional.empty();
        }
        return Optional.of(Component.translatableWithFallback("adminshop.permit." + permit, permit));
    }

    /** The goods' name. It reads item components, so call it only after a world has loaded. */
    public Component name() {
        return recipe.name();
    }

    /** The text the search matches. It reads item components, so call it only after a world has loaded. */
    public String searchTerm() {
        return recipe.searchTerm();
    }
}
