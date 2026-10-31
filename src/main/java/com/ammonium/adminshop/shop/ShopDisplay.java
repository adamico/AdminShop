package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.recipes.ShopRecipe;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The shop display layer: the synced Offers turned into what the shop screen lists, Buy and Sell Offers
 * apart and each sorted by id. The shop screen and the recipe viewers read the same lists, so they show
 * the same Offers.
 */
public record ShopDisplay(List<OfferDisplay> buyOffers, List<OfferDisplay> sellOffers) {

    public static final ShopDisplay EMPTY = new ShopDisplay(List.of(), List.of());

    public ShopDisplay {
        buyOffers = List.copyOf(buyOffers);
        sellOffers = List.copyOf(sellOffers);
    }

    static ShopDisplay of(List<RecipeHolder<ShopRecipe>> offers) {
        List<OfferDisplay> sorted = offers.stream()
                .map(holder -> new OfferDisplay(holder.id(), holder.value()))
                .sorted(Comparator.comparing(display -> display.offer().identifier().toString(), ShopDisplay::compareIds))
                .toList();
        return new ShopDisplay(
                sorted.stream().filter(OfferDisplay::isBuy).toList(),
                sorted.stream().filter(display -> !display.isBuy()).toList());
    }

    /** The Buy or Sell Offers whose goods' name contains {@code search}, ignoring case and outer spaces. */
    public List<OfferDisplay> search(boolean buy, String search) {
        List<OfferDisplay> offers = buy ? buyOffers : sellOffers;
        String term = search.toLowerCase(Locale.ROOT).strip();
        if (term.isEmpty()) {
            return offers;
        }
        return offers.stream().filter(display -> display.searchTerm().contains(term)).toList();
    }

    /** The first Sell Offer that takes {@code stack}'s item, whatever its count. */
    public Optional<OfferDisplay> sellOfferFor(ItemStack stack) {
        return sellOffers.stream()
                .filter(display -> display.recipe() instanceof SellItemRecipe offer && offer.isMatchingItemNoCount(stack))
                .findFirst();
    }

    /**
     * Orders Offer ids as 1.21.1's shop screen did: by the id without a trailing number, then by that
     * number, so {@code ore2} comes before {@code ore10} and an id without a number before both.
     */
    static int compareIds(String left, String right) {
        String leftBase = left.replaceAll("\\d+$", "");
        String rightBase = right.replaceAll("\\d+$", "");
        int baseCompare = leftBase.compareTo(rightBase);
        if (baseCompare != 0) {
            return baseCompare;
        }
        String leftNumber = left.substring(leftBase.length());
        String rightNumber = right.substring(rightBase.length());
        try {
            int leftValue = leftNumber.isEmpty() ? -1 : Integer.parseInt(leftNumber);
            int rightValue = rightNumber.isEmpty() ? -1 : Integer.parseInt(rightNumber);
            return Integer.compare(leftValue, rightValue);
        } catch (NumberFormatException e) {
            // A number too long for an int.
            return left.compareTo(right);
        }
    }
}
