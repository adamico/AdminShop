package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.screen.BuyerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks the server to set the Buyer whose screen the player has open to one Buy Offer. See {@link BuyerMenu#select}. */
public record PacketSetBuyerRecipe(ResourceKey<Recipe<?>> offer) implements CustomPacketPayload {

    public static final Type<PacketSetBuyerRecipe> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "set_item_buyer_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSetBuyerRecipe> STREAM_CODEC =
            ResourceKey.streamCodec(Registries.RECIPE).map(PacketSetBuyerRecipe::new, PacketSetBuyerRecipe::offer).cast();

    /** Runs on the server's main thread. */
    public static void handle(PacketSetBuyerRecipe packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BuyerMenu.select(player, packet.offer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
