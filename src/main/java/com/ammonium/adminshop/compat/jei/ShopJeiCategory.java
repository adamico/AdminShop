package com.ammonium.adminshop.compat.jei;

import com.ammonium.adminshop.compat.ShopCategory;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopGoods;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shop Buy or Shop Sell Offers in JEI: draws a {@link ShopCategory}, one Offer at a time. */
final class ShopJeiCategory implements IRecipeCategory<OfferDisplay> {

    private final ShopCategory category;
    private final IRecipeType<OfferDisplay> type;
    private final IDrawableStatic background;
    private final IDrawable icon;

    ShopJeiCategory(ShopCategory category, IRecipeType<OfferDisplay> type, IGuiHelper guiHelper) {
        this.category = category;
        this.type = type;
        this.background = guiHelper.createDrawable(category.background(), 0, 0, ShopCategory.WIDTH, ShopCategory.HEIGHT);
        this.icon = guiHelper.createDrawableItemLike(category.icon());
    }

    @Override
    public IRecipeType<OfferDisplay> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return category.title();
    }

    @Override
    public int getWidth() {
        return ShopCategory.WIDTH;
    }

    @Override
    public int getHeight() {
        return ShopCategory.HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, OfferDisplay offer, IFocusGroup focuses) {
        // The team receives a Buy Offer's goods and gives up a Sell Offer's.
        RecipeIngredientRole role = category.isBuy() ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
        IRecipeSlotBuilder slot = builder.addSlot(role, category.slotX(), ShopCategory.SLOT_Y);
        switch (offer.goods()) {
            // A tag Offer's stacks, which the slot shows in turn.
            case ShopGoods.Items items -> items.stacks().forEach(slot::add);
            case ShopGoods.Fluid fluid ->
                    slot.add(fluid.fluid().fluid().value(), fluid.fluid().amount(), fluid.fluid().components());
        }
    }

    @Override
    public void draw(OfferDisplay offer, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        background.draw(graphics);
        Font font = Minecraft.getInstance().font;
        graphics.text(font, category.price(offer), ShopCategory.TEXT_X, ShopCategory.PRICE_Y, ShopCategory.TEXT_COLOR, true);
        category.permit(offer).ifPresent(permit ->
                graphics.text(font, permit, ShopCategory.TEXT_X, ShopCategory.PERMIT_Y, ShopCategory.TEXT_COLOR, true));
    }

    @Override
    public Identifier getIdentifier(OfferDisplay offer) {
        return offer.offer().identifier();
    }
}
