package com.ammonium.adminshop.compat.emi;

import com.ammonium.adminshop.compat.ShopCategory;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopGoods;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** One Offer in EMI. It draws a {@link ShopCategory}'s background, goods slot, price and Permit lines. */
final class ShopEmiRecipe extends BasicEmiRecipe {

    private final ShopCategory category;
    private final OfferDisplay offer;

    /** EMI keys the recipe by the Offer's id. A Buy Offer's goods are outputs and a Sell Offer's are inputs. */
    ShopEmiRecipe(ShopEmiCategory emiCategory, OfferDisplay offer) {
        super(emiCategory, offer.offer().identifier(), ShopCategory.WIDTH, ShopCategory.HEIGHT);
        this.category = emiCategory.category;
        this.offer = offer;
        if (category.isBuy()) {
            outputs.addAll(goods(offer.goods()));
        } else {
            inputs.add(EmiIngredient.of(goods(offer.goods())));
        }
    }

    /** The goods' stacks. An item or fluid Offer has one, and a tag Offer has one per item in the tag. */
    private static List<EmiStack> goods(ShopGoods goods) {
        return switch (goods) {
            case ShopGoods.Items items -> items.stacks().stream()
                    .map(stack -> EmiStack.of(stack.item().value(), stack.components(), stack.count()))
                    .toList();
            case ShopGoods.Fluid fluid ->
                    List.of(EmiStack.of(fluid.fluid().fluid().value(), fluid.fluid().components(), fluid.fluid().amount()));
        };
    }

    /**
     * EMI places a slot by its 18-pixel frame, one pixel outside the goods, and the background already draws that
     * frame. The price is drawn each frame, as in JEI, so it shows unformatted while Shift is held.
     */
    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(category.background(), 0, 0, ShopCategory.WIDTH, ShopCategory.HEIGHT, 0, 0);
        EmiIngredient goods = category.isBuy() ? EmiIngredient.of(outputs) : inputs.getFirst();
        SlotWidget slot = widgets.addSlot(goods, category.slotX() - 1, ShopCategory.SLOT_Y - 1).drawBack(false);
        if (category.isBuy()) {
            slot.recipeContext(this);
        }
        Font font = Minecraft.getInstance().font;
        widgets.addDrawable(0, 0, ShopCategory.WIDTH, ShopCategory.HEIGHT, (graphics, mouseX, mouseY, delta) ->
                graphics.text(font, category.price(offer), ShopCategory.TEXT_X, ShopCategory.PRICE_Y, ShopCategory.TEXT_COLOR, true));
        category.permit(offer).ifPresent(permit ->
                widgets.addText(permit, ShopCategory.TEXT_X, ShopCategory.PERMIT_Y, ShopCategory.TEXT_COLOR, true));
    }
}
