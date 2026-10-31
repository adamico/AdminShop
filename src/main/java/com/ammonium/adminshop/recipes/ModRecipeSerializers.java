package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The serializers of the Offer recipe types, under the same ids as the types: a datapack's {@code type}. */
public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, AdminShop.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BuyItemRecipe>> SHOP_BUY_ITEM =
            RECIPE_SERIALIZERS.register("item_buying", () -> BuyItemRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SellItemRecipe>> SHOP_SELL_ITEM =
            RECIPE_SERIALIZERS.register("item_selling", () -> SellItemRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BuyFluidRecipe>> SHOP_BUY_FLUID =
            RECIPE_SERIALIZERS.register("fluid_buying", () -> BuyFluidRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SellFluidRecipe>> SHOP_SELL_FLUID =
            RECIPE_SERIALIZERS.register("fluid_selling", () -> SellFluidRecipe.SERIALIZER);

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
