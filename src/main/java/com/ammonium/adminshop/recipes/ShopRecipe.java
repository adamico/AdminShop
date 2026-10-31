package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.shop.ShopGoods;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

/**
 * An Offer: one priced entry in the Shop. Offers are recipe types so datapacks can define them, but no
 * crafting station uses them: the Shop and the machines trade them, and the recipe book never shows them.
 */
public interface ShopRecipe extends Recipe<RecipeInput> {

    /** What one trade of this Offer costs or earns. */
    long price();

    /** The Permit this Offer needs, or the empty string for none. */
    String permit();

    /** Whether this is a Buy Offer (the team pays and receives the goods) rather than a Sell Offer. */
    boolean isBuy();

    /** What one trade of this Offer hands over, as the shop screen shows it. */
    ShopGoods goods();

    /** The goods' name, as the shop screen's tooltip shows it. */
    Component name();

    /** What the shop screen's search matches. */
    default String searchTerm() {
        return name().getString().toLowerCase(Locale.ROOT).strip();
    }

    @Override
    default boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    default ItemStack assemble(RecipeInput input) {
        return ItemStack.EMPTY;
    }

    /** Special, so loading never warns that it has no ingredients to place. */
    @Override
    default boolean isSpecial() {
        return true;
    }

    @Override
    default boolean showNotification() {
        return false;
    }

    @Override
    default String group() {
        return "";
    }

    @Override
    default PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    // display() stays empty, so no Offer ever reaches the recipe book: the category below is never read.
    @Override
    default RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
