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
    public static final DeferredBlock<BuyerBlock> BUYER_1 = registerLoreBlock("buyer_1",
            properties -> new BuyerBlock(BuyerTier.ONE, properties), "Automatically buys once every 40 ticks");
    public static final DeferredBlock<BuyerBlock> BUYER_2 = registerLoreBlock("buyer_2",
            properties -> new BuyerBlock(BuyerTier.TWO, properties), "Automatically buys once every 20 ticks");
    public static final DeferredBlock<BuyerBlock> BUYER_3 = registerLoreBlock("buyer_3",
            properties -> new BuyerBlock(BuyerTier.THREE, properties), "Automatically buys once every 10 ticks");
    public static final DeferredBlock<FluidBuyerBlock> FLUID_BUYER = registerLoreBlock("fluid_buyer", FluidBuyerBlock::new,
            "Automatically buys once every 20 ticks");
    public static final DeferredBlock<FluidSellerBlock> FLUID_SELLER = registerLoreBlock("fluid_seller", FluidSellerBlock::new,
            "Automatically sells once every 20 ticks");
    public static final DeferredBlock<CreativeBuyerBlock> CREATIVE_BUYER = registerLoreBlock("creative_buyer", CreativeBuyerBlock::new,
            "Set its recipe in creative mode, then claim it in survival to buy with that recipe, bypassing permits");
    public static final DeferredBlock<CreativeSellerBlock> CREATIVE_SELLER = registerLoreBlock("creative_seller", CreativeSellerBlock::new,
            "Set its recipe in creative mode, then claim it in survival to sell with that recipe, bypassing permits");

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
