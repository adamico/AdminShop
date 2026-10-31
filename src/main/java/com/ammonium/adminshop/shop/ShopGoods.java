package com.ammonium.adminshop.shop;

import java.util.List;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * What one trade of an Offer hands over, as the shop screen and recipe viewers show it. Templates, not
 * item stacks: they are immutable, and exist before a world's registries give items their components.
 */
public sealed interface ShopGoods {

    /** How much one trade hands over: items, or millibuckets of a fluid. */
    int count();

    /**
     * Items. {@code stacks} are the stacks the Offer accepts, each with the count one trade takes: one
     * for an item Offer, one per item of the tag for a tag Offer, which the screen shows in turn.
     */
    record Items(List<ItemStackTemplate> stacks) implements ShopGoods {

        public Items {
            stacks = List.copyOf(stacks);
        }

        @Override
        public int count() {
            return stacks.isEmpty() ? 0 : stacks.getFirst().count();
        }
    }
}
