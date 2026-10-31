package com.ammonium.adminshop.compat;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.money.MoneyFormat;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopDisplay;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * The Shop Buy and Shop Sell categories shown by the recipe viewers, built from {@link ShopDisplay}. Each
 * category defines which Offers it lists, its title, icon and catalysts, and the layout of one Offer with its goods
 * slot, price and Permit lines. It uses no viewer types, so JEI and EMI read the same categories and look alike.
 * Each viewer's package only draws them.
 */
public enum ShopCategory {

    BUY("buy", 67, ModBlocks.BUYER_1,
            List.of(ModBlocks.SHOP, ModItems.TABLET, ModBlocks.BUYER_1, ModBlocks.BUYER_2, ModBlocks.BUYER_3)),
    SELL("sell", 24, ModBlocks.SELLER,
            List.of(ModBlocks.SHOP, ModItems.TABLET, ModBlocks.SELLER));

    /** The width of one Offer, matching its background texture. */
    public static final int WIDTH = 110;
    /** The height of one Offer, matching its background texture. */
    public static final int HEIGHT = 50;
    /** The top edge of the goods slot, inside its 18-pixel frame. */
    public static final int SLOT_Y = 5;
    public static final int TEXT_X = 4;
    public static final int PRICE_Y = 30;
    public static final int PERMIT_Y = 40;
    public static final int TEXT_COLOR = 0xFF555555;

    private final String name;
    private final int slotX;
    private final ItemLike icon;
    private final List<ItemLike> catalysts;

    ShopCategory(String name, int slotX, ItemLike icon, List<ItemLike> catalysts) {
        this.name = name;
        this.slotX = slotX;
        this.icon = icon;
        this.catalysts = catalysts;
    }

    /** Whether this category lists Buy Offers rather than Sell Offers. */
    public boolean isBuy() {
        return this == BUY;
    }

    /** The Offers this category lists, in the shop screen's order. */
    public List<OfferDisplay> offers(ShopDisplay display) {
        return isBuy() ? display.buyOffers() : display.sellOffers();
    }

    public Component title() {
        return Component.translatable("jei.category." + name + ".title");
    }

    /** The machine shown as the category icon. */
    public ItemLike icon() {
        return icon;
    }

    /** The blocks and items that trade these Offers. These are the Shop, the Shop Tablet and the matching machines. */
    public List<ItemLike> catalysts() {
        return catalysts;
    }

    /** The background drawn behind each Offer, a dollar sign and an arrow pointing to or from the goods slot. */
    public Identifier background() {
        return Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/jei_" + name + "_category.png");
    }

    /** The left edge of the goods slot. It is right of the arrow for Buy Offers and left of it for Sell Offers. */
    public int slotX() {
        return slotX;
    }

    /** The Offer's price in the client's money format, or unformatted while Shift is held. Client only. */
    public Component price(OfferDisplay offer) {
        return Component.translatable("jei.category." + name + ".price",
                MoneyFormat.format(offer.price(), MoneyFormat.FormatType.SHORT));
    }

    /** The line naming the Permit the Offer needs, using the shop screen's name for it, or empty for none. */
    public Optional<Component> permit(OfferDisplay offer) {
        return offer.permitName().map(permit -> Component.translatable("jei.requires_permit", permit));
    }
}
