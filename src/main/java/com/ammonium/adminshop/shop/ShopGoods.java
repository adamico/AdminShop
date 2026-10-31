package com.ammonium.adminshop.shop;

import java.util.List;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

/**
 * What one trade of an Offer hands over, as shown by the shop screen and the recipe viewers. It holds templates
 * rather than item or fluid stacks, because templates are immutable and can exist before a world's registries give
 * items their components.
 */
public sealed interface ShopGoods {

    /** How much one trade hands over, in items or in millibuckets of fluid. */
    int count();

    /**
     * Items. {@code stacks} are the stacks the Offer accepts, each with the count one trade takes. An item Offer has
     * one stack. A tag Offer has one per item in the tag, and the screen cycles through them.
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

    /** A fluid. Its count is the millibuckets one trade hands over. */
    record Fluid(FluidStackTemplate fluid) implements ShopGoods {

        @Override
        public int count() {
            return fluid.amount();
        }
    }
}
