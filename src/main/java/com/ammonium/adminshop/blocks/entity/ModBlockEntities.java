package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AdminShop.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopEntity>> SHOP =
            BLOCK_ENTITIES.register("shop", () -> new BlockEntityType<>(ShopEntity::new, ModBlocks.SHOP.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SellerEntity>> SELLER =
            BLOCK_ENTITIES.register("seller", () -> new BlockEntityType<>(SellerEntity::new, ModBlocks.SELLER.get()));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
        eventBus.addListener(ModBlockEntities::registerCapabilities);
    }

    /** Pipes reach a machine's slots through NeoForge's item capability, from every side. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, SELLER.get(), (seller, side) -> seller.inventory());
    }
}
