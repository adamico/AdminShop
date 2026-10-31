package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.screen.SellerMenu;
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
 * Asks the server to lock the Seller whose screen the player has open to one Sell Offer. Only creative players
 * may; see {@link SellerMenu#lock}.
 */
public record PacketSetSellerRecipe(ResourceKey<Recipe<?>> offer) implements CustomPacketPayload {

    public static final Type<PacketSetSellerRecipe> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "set_seller_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSetSellerRecipe> STREAM_CODEC =
            ResourceKey.streamCodec(Registries.RECIPE).map(PacketSetSellerRecipe::new, PacketSetSellerRecipe::offer).cast();

    /** Runs on the server's main thread. */
    public static void handle(PacketSetSellerRecipe packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            SellerMenu.lock(player, packet.offer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
