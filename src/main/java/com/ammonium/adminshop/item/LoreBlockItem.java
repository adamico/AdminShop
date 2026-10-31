package com.ammonium.adminshop.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** A block item whose tooltip ends with a line of lore. */
public class LoreBlockItem extends BlockItem {
    private final Component loreText;

    public LoreBlockItem(Block block, Properties properties, String loreText) {
        super(block, properties);
        this.loreText = Component.literal(loreText);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(loreText);
    }
}
