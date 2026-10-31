package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.screen.FluidBuyerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Asks the server to set the Fluid Buyer whose screen the player has open to one fluid Buy Offer. See
 * {@link FluidBuyerMenu#select}.
 */
public record PacketSetFluidBuyerRecipe(ResourceKey<Recipe<?>> offer) implements CustomPacketPayload {

    public static final Type<PacketSetFluidBuyerRecipe> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "set_fluid_buyer_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSetFluidBuyerRecipe> STREAM_CODEC =
            ResourceKey.streamCodec(Registries.RECIPE).map(PacketSetFluidBuyerRecipe::new, PacketSetFluidBuyerRecipe::offer).cast();

    /** Runs on the server's main thread. */
    public static void handle(PacketSetFluidBuyerRecipe packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FluidBuyerMenu.select(player, packet.offer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
