package com.ammonium.adminshop.compat.emi;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.compat.ShopCategory;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shop Buy or Shop Sell Offers in EMI. Its id, {@code adminshop:shop_buy} or {@code adminshop:shop_sell}, differs
 * from the JEI categories' ids, which EMI shows JEI plugins' categories under.
 */
final class ShopEmiCategory extends EmiRecipeCategory {

    final ShopCategory category;

    ShopEmiCategory(ShopCategory category) {
        super(Identifier.fromNamespaceAndPath(AdminShop.MODID, "shop_" + category.name().toLowerCase(Locale.ROOT)),
                EmiStack.of(category.icon()));
        this.category = category;
    }

    @Override
    public Component getName() {
        return category.title();
    }
}
