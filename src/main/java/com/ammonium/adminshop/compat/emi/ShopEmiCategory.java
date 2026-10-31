package com.ammonium.adminshop.compat.emi;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.compat.ShopCategory;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Shop Buy or Shop Sell category in EMI. Its id is {@code adminshop:shop_buy} or {@code adminshop:shop_sell}.
 * EMI lists JEI plugins' categories under their JEI ids, so these ids differ from the JEI ones to avoid a clash.
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
