// 26.1 port: the Seller (#7), the T1-T3 Buyers (#8), the Fluid Buyer and Fluid Seller (#10) and the Creative Buyer and
// Creative Seller (#11) are ported (src/main/...);
// their block entities and capabilities are gone from here.
package com.ammonium.adminshop.blocks.entity;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = AdminShop.MODID)
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AdminShop.MODID);


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicDetectorEntity>> BASIC_DETECTOR =
            BLOCK_ENTITIES.register("detector", () -> BlockEntityType.Builder.of(BasicDetectorEntity::new,
                    ModBlocks.DETECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedDetectorEntity>> ADVANCED_DETECTOR =
            BLOCK_ENTITIES.register("adv_detector", () -> BlockEntityType.Builder.of(AdvancedDetectorEntity::new,
                    ModBlocks.ADVANCED_DETECTOR.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    }
}
