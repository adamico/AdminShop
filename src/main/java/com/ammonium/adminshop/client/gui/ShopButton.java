package com.ammonium.adminshop.client.gui;

import com.ammonium.adminshop.money.MoneyFormat;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopGoods;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

/** One Offer in the shop screen's grid: its goods, with the count a click trades. Clicking trades it. */
public class ShopButton extends AbstractButton {
    /** How long each item of a tag Offer shows before the next. */
    public static final int TAG_DISPLAY_ROTATION_TICKS = 30;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int COMPONENTS_COLOR = 0xFFFF55FF;
    private static final int TAG_COLOR = 0xFFFFC921;
    private static final int HOVER_COLOR = 0xFFFFFFDF;

    private final OfferDisplay offer;
    private final List<ItemStack> stacks;
    private final Consumer<ShopButton> onPress;
    /** Whether the mouse was over the button when it last rendered. */
    public boolean isMouseOn = false;

    public ShopButton(OfferDisplay offer, int x, int y, Consumer<ShopButton> onPress) {
        super(x, y, 16, 16, Component.empty());
        this.offer = offer;
        this.onPress = onPress;
        // Item stacks only exist once a world has given items their components, which it has by now.
        this.stacks = offer.goods() instanceof ShopGoods.Items items
                ? items.stacks().stream().map(ItemStackTemplate::create).toList()
                : List.of();
    }

    public OfferDisplay getOffer() {
        return offer;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        ItemStack shown = shownStack();
        graphics.item(shown, x, y);

        isMouseOn = isHovered();
        if (isMouseOn) {
            graphics.fill(x, y, x + width, y + height, HOVER_COLOR);
        }

        // The count a click trades, and a mark on tag Offers and on goods that carry components, at half size.
        Font font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        graphics.pose().scale(0.5f, 0.5f);
        String count = String.valueOf(getNumItems());
        graphics.text(font, count, 2 * (x + 16) - font.width(count), 2 * y + 24, TEXT_COLOR);
        if (offer.recipe() instanceof SellItemRecipe sell && sell.sold() instanceof SellItemRecipe.AnyOfTag) {
            graphics.text(font, "#", 2 * x + width * 2 - font.width("#") - 1, 2 * y + 1, TAG_COLOR);
        } else if (offer.goods() instanceof ShopGoods.Items items && items.stacks().size() == 1
                && !items.stacks().getFirst().components().isEmpty()) {
            graphics.text(font, "+NBT", 2 * x + width * 2 - font.width("+NBT") - 1, 2 * y + 1, COMPONENTS_COLOR);
        }
        graphics.pose().popMatrix();
    }

    /** The stack shown now: the only one, or for a tag Offer each in turn; a barrier for an empty tag. */
    private ItemStack shownStack() {
        if (stacks.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }
        var level = Minecraft.getInstance().level;
        long turn = level == null ? 0 : level.getGameTime() / TAG_DISPLAY_ROTATION_TICKS;
        return stacks.get((int) (turn % stacks.size()));
    }

    /** How many items a click trades. */
    private int getNumItems() {
        return offer.count() * getQuantity();
    }

    /**
     * How many trades a click makes: one, half a stack's worth with shift held, a stack's worth with
     * control held.
     */
    public int getQuantity() {
        if (stacks.isEmpty() || offer.count() <= 0) {
            return 1;
        }
        int maxFits = Math.max(stacks.getFirst().getMaxStackSize() / offer.count(), 1);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasControlDown()) {
            return maxFits;
        }
        if (minecraft.hasShiftDown()) {
            return Math.max(maxFits / 2, 1);
        }
        return 1;
    }

    public List<Component> getTooltipContent() {
        int quantity = getQuantity();
        long price = offer.price() * quantity;
        String priceFormatted = Minecraft.getInstance().hasAltDown()
                ? MoneyFormat.forcedFormat(price, MoneyFormat.FormatType.RAW)
                : MoneyFormat.forcedFormat(price, MoneyFormat.FormatType.SHORT);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("shopbutton.description", priceFormatted, getNumItems() + "x", offer.name()));
        offer.permitName().ifPresent(permit -> tooltip.add(Component.translatable("shopbutton.permit", permit)));
        return tooltip;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
