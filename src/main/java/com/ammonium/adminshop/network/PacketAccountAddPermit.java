package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.item.PermitItem;
import com.ammonium.adminshop.screen.ShopMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Asks the server to redeem the Trade Permit in main inventory {@code slot} for the player's Account, from a
 * shift-click in the shop screen. See {@link PermitItem#redeem}.
 */
public record PacketAccountAddPermit(int slot) implements CustomPacketPayload {

    public static final Type<PacketAccountAddPermit> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "account_add_permit"));
    public static final StreamCodec<ByteBuf, PacketAccountAddPermit> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(PacketAccountAddPermit::new, PacketAccountAddPermit::slot);

    /** Runs on the server's main thread. The Permit goes to the player's own Account. */
    public static void handle(PacketAccountAddPermit packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ShopMenu) {
            PermitItem.redeem(player, packet.slot);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
