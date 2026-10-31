// 26.1 port: the Seller (#7), the T1-T3 Buyers (#8), the Fluid Buyer and Fluid Seller (#10) and the Creative Buyer and
// Creative Seller (#11) and the Basic and Advanced Detectors (#12) are ported (src/main/...);
// their registration is gone from here.
package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.item.LoreBlockItem;
import com.ammonium.adminshop.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
//    public static final Material machineBlock = new Material(MaterialColor.METAL, false, true, true, true, false, false, PushReaction.BLOCK);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, AdminShop.MODID);

    private static <T extends Block> DeferredHolder<Block, T> registerBlock(String name, Supplier<T> block) {
        DeferredHolder<Block, T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> DeferredHolder<Item, BlockItem> registerBlockItem(String name, DeferredHolder<Block, T> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties()));
    }

    private static <T extends Block> DeferredHolder<Block, T> registerLoreBlock(String name, Supplier<T> block, String lore) {
        DeferredHolder<Block, T> toReturn = BLOCKS.register(name, block);
        registerLoreBlockItem(name, toReturn, lore);
        return toReturn;
    }

    private static <T extends Block> DeferredHolder<Item, LoreBlockItem> registerLoreBlockItem(String name, DeferredHolder<Block, T> block,
                                                                                String lore) {
        return ModItems.ITEMS.register(name, () -> new LoreBlockItem(block.get(),
                new Item.Properties(), lore));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}