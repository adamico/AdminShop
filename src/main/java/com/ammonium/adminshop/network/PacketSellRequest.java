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

/**
 * Asks the server to sell {@code quantity} trades of a Sell Offer for the player, from the shop screen:
 * from the stack in main inventory {@code slot}, or {@link ShopTrades#ANY_SLOT}. See {@link ShopTrades#sell}.
 */
public record PacketSellRequest(ResourceKey<Recipe<?>> offer, int slot, int quantity) implements CustomPacketPayload {

    public static final Type<PacketSellRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "sell_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSellRequest> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.RECIPE), PacketSellRequest::offer,
            ByteBufCodecs.VAR_INT, PacketSellRequest::slot,
            ByteBufCodecs.VAR_INT, PacketSellRequest::quantity,
            PacketSellRequest::new);

    /** Runs on the server's main thread. The player's own Account is paid. */
    public static void handle(PacketSellRequest packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ShopMenu) {
            ShopTrades.sell(player, packet.offer, packet.slot, packet.quantity);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
