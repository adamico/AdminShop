// 26.1 port: the Seller's lookups (#7) are in src's ShopTrades (sellOffers, anySellOfferTakes, sellFrom);
// the server-side replacement for an Offer lookup is server.getRecipeManager().byKey(key).
// The Buyers' lookups (#8) are gone too: ShopTrades.buyInto buys, BuyerEntity.target() finds the goods, and
// BuyerScreen matches a clicked item to a Buy Offer of the client shop cache with ItemStack.isSameItemSameComponents.
// The fluid lookups (#10) are gone as well: ShopTrades.buyFluidInto, sellFluidFrom and anySellOfferTakes(.., FluidResource)
// replace them. What is left lists every Offer for JEI (#15); src's ShopDisplay (shop display layer) lists them now.
package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.blocks.interfaces.*;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.recipes.interfaces.BuyRecipe;
import com.ammonium.adminshop.recipes.interfaces.SellRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class RecipeManager {

    private static MoneyHelper.MoneyAccount getAccount(ServerLevel level, ShopMachine machine) {
        return MoneyHelper.get(level).getAccountById(machine.getTeamId());
    }

    public static List<RecipeHolder<BuyItemRecipe>> getAllBuyItemRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_BUY_ITEM.get());
    }

    public static List<RecipeHolder<BuyFluidRecipe>> getAllBuyFluidRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_BUY_FLUID.get());
    }

    public static Stream<RecipeHolder<BuyRecipe>> getAllBuyRecipes(Level level) {
        return Stream.concat(
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_BUY_ITEM.get()).stream().map(hol -> new RecipeHolder<BuyRecipe>(hol.id(), hol.value())),
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_BUY_FLUID.get()).stream().map(hol -> new RecipeHolder<BuyRecipe>(hol.id(), hol.value()))
        );
    }

    public static List<RecipeHolder<SellItemRecipe>> getAllSellItemRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_SELL_ITEM.get());
    }

    public static List<RecipeHolder<SellFluidRecipe>> getAllSellFluidRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_SELL_FLUID.get());
    }

    public static Stream<RecipeHolder<SellRecipe>> getAllSellRecipes(Level level) {
        return Stream.concat(
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_SELL_ITEM.get()).stream().map(hol -> new RecipeHolder<SellRecipe>(hol.id(), hol.value())),
                level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SHOP_SELL_FLUID.get()).stream().map(hol -> new RecipeHolder<SellRecipe>(hol.id(), hol.value()))
        );
    }
}
