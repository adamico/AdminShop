package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.item.LoreBlockItem;
import com.ammonium.adminshop.item.ModItems;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** AdminShop's blocks, each with a block item that carries a line of lore. */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AdminShop.MODID);

    public static final DeferredBlock<ShopBlock> SHOP = registerLoreBlock("shop", ShopBlock::new, "Buy and Sell Items!");
    public static final DeferredBlock<SellerBlock> SELLER = registerLoreBlock("seller", SellerBlock::new,
            "Automatically sells once every 20 ticks");

    private static <T extends Block> DeferredBlock<T> registerLoreBlock(String name, Function<BlockBehaviour.Properties, T> block,
                                                                       String lore) {
        DeferredBlock<T> registered = BLOCKS.registerBlock(name, block);
        ModItems.ITEMS.registerItem(name, properties -> new LoreBlockItem(registered.get(), properties, lore),
                properties -> properties.useBlockDescriptionPrefix());
        return registered;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
