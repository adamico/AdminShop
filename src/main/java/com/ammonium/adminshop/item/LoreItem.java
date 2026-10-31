package com.ammonium.adminshop.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An item whose tooltip ends with a line of lore. */
public class LoreItem extends Item {
    private final Component loreText;

    public LoreItem(Properties properties, String lore) {
        super(properties);
        this.loreText = Component.literal(lore);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(loreText);
    }
}
