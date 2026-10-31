package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.AdminShop;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Offer recipe types. */
public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, AdminShop.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<BuyItemRecipe>> SHOP_BUY_ITEM = register("item_buying");
    public static final DeferredHolder<RecipeType<?>, RecipeType<SellItemRecipe>> SHOP_SELL_ITEM = register("item_selling");
    public static final DeferredHolder<RecipeType<?>, RecipeType<BuyFluidRecipe>> SHOP_BUY_FLUID = register("fluid_buying");
    public static final DeferredHolder<RecipeType<?>, RecipeType<SellFluidRecipe>> SHOP_SELL_FLUID = register("fluid_selling");

    /** Every Offer type. The server syncs these to clients. */
    public static List<RecipeType<?>> offerTypes() {
        return List.of(SHOP_BUY_ITEM.get(), SHOP_SELL_ITEM.get(), SHOP_BUY_FLUID.get(), SHOP_SELL_FLUID.get());
    }

    private static <T extends ShopRecipe> DeferredHolder<RecipeType<?>, RecipeType<T>> register(String name) {
        return RECIPE_TYPES.register(name, id -> RecipeType.simple(id));
    }

    public static void register(IEventBus eventBus) {
        RECIPE_TYPES.register(eventBus);
    }
}
