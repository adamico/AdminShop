package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.BuyerTier;
import com.ammonium.adminshop.blocks.ModBlocks;
import java.util.List;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BuyerEntity>> BUYER_1 =
            BLOCK_ENTITIES.register("buyer_1", () -> new BlockEntityType<>(
                    (pos, state) -> new BuyerEntity(ModBlockEntities.BUYER_1.get(), BuyerTier.ONE, pos, state), ModBlocks.BUYER_1.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BuyerEntity>> BUYER_2 =
            BLOCK_ENTITIES.register("buyer_2", () -> new BlockEntityType<>(
                    (pos, state) -> new BuyerEntity(ModBlockEntities.BUYER_2.get(), BuyerTier.TWO, pos, state), ModBlocks.BUYER_2.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BuyerEntity>> BUYER_3 =
            BLOCK_ENTITIES.register("buyer_3", () -> new BlockEntityType<>(
                    (pos, state) -> new BuyerEntity(ModBlockEntities.BUYER_3.get(), BuyerTier.THREE, pos, state), ModBlocks.BUYER_3.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidBuyerEntity>> FLUID_BUYER =
            BLOCK_ENTITIES.register("fluid_buyer", () -> new BlockEntityType<>(FluidBuyerEntity::new, ModBlocks.FLUID_BUYER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidSellerEntity>> FLUID_SELLER =
            BLOCK_ENTITIES.register("fluid_seller", () -> new BlockEntityType<>(FluidSellerEntity::new, ModBlocks.FLUID_SELLER.get()));

    /** The block entity type of a Buyer of this tier. */
    public static BlockEntityType<BuyerEntity> buyer(BuyerTier tier) {
        return switch (tier) {
            case ONE -> BUYER_1.get();
            case TWO -> BUYER_2.get();
            case THREE -> BUYER_3.get();
        };
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
        eventBus.addListener(ModBlockEntities::registerCapabilities);
    }

    /**
     * Pipes reach a machine's slots through NeoForge's item capability, and a fluid machine's tank through its fluid
     * capability, from every side; a Buyer's and a Fluid Buyer's they can only take from.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, SELLER.get(), (seller, side) -> seller.inventory());
        for (var buyer : List.of(BUYER_1, BUYER_2, BUYER_3)) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, buyer.get(), (entity, side) -> entity.output());
        }
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_BUYER.get(), (buyer, side) -> buyer.output());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_SELLER.get(), (seller, side) -> seller.tank());
    }
}
