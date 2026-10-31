package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.screen.ShopMenu;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks the server to buy {@code quantity} trades of a Buy Offer for the player, from the shop screen. */
public record PacketBuyRequest(ResourceKey<Recipe<?>> offer, int quantity) implements CustomPacketPayload {

    public static final Type<PacketBuyRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "buy_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketBuyRequest> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.RECIPE), PacketBuyRequest::offer,
            ByteBufCodecs.VAR_INT, PacketBuyRequest::quantity,
            PacketBuyRequest::new);

    /** Runs on the server's main thread. The player's own Account pays. */
    public static void handle(PacketBuyRequest packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ShopMenu) {
            ShopTrades.buy(player, packet.offer, packet.quantity);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
