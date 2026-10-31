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
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Offer in the shop screen's grid. It shows the Offer's goods and the count a click trades, in items or in
 * millibuckets of a fluid. Clicking it trades the Offer.
 */
public class ShopButton extends AbstractButton {
    /** How many ticks each item of a tag Offer shows before the next one. */
    public static final int TAG_DISPLAY_ROTATION_TICKS = 30;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int COMPONENTS_COLOR = 0xFFFF55FF;
    private static final int TAG_COLOR = 0xFFFFC921;
    private static final int HOVER_COLOR = 0xFFFFFFDF;

    private final OfferDisplay offer;
    private final List<ItemStack> stacks;
    private final FluidStack fluid;
    private final Consumer<ShopButton> onPress;
    /** Whether the mouse was over the button when it last rendered. */
    public boolean isMouseOn = false;

    /**
     * Creates the button and its item stacks. A stack needs the item components that a loaded world provides, and a
     * world is loaded by the time the shop screen opens.
     */
    public ShopButton(OfferDisplay offer, int x, int y, Consumer<ShopButton> onPress) {
        super(x, y, 16, 16, Component.empty());
        this.offer = offer;
        this.onPress = onPress;
        this.stacks = offer.goods() instanceof ShopGoods.Items items
                ? items.stacks().stream().map(ItemStackTemplate::create).toList()
                : List.of();
        this.fluid = offer.goods() instanceof ShopGoods.Fluid goods ? goods.fluid().create() : FluidStack.EMPTY;
    }

    public OfferDisplay getOffer() {
        return offer;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    /**
     * Draws the goods and a hover highlight. At half size it adds the count a click trades and a mark on tag Offers and
     * on goods that carry components.
     */
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        if (!fluid.isEmpty()) {
            FluidSprites.draw(graphics, fluid, x, y, width, height);
        } else {
            graphics.item(shownStack(), x, y);
        }

        isMouseOn = isHovered();
        if (isMouseOn) {
            graphics.fill(x, y, x + width, y + height, HOVER_COLOR);
        }

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

    /** The stack to show now. A tag Offer cycles through its items, and an empty tag shows a barrier. */
    private ItemStack shownStack() {
        if (stacks.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }
        var level = Minecraft.getInstance().level;
        long turn = level == null ? 0 : level.getGameTime() / TAG_DISPLAY_ROTATION_TICKS;
        return stacks.get((int) (turn % stacks.size()));
    }

    /** How many items, or millibuckets of a fluid, a click trades. */
    private int getNumItems() {
        return offer.count() * getQuantity();
    }

    /**
     * How many trades a click makes. It is one by default, half a stack's worth with shift held and a stack's worth
     * with control held. A fluid Offer always trades once, since one container takes it.
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
        tooltip.add(Component.translatable("shopbutton.description", priceFormatted, getNumItems() + (fluid.isEmpty() ? "x" : "mb"), offer.name()));
        offer.permitName().ifPresent(permit -> tooltip.add(Component.translatable("shopbutton.permit", permit)));
        return tooltip;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
