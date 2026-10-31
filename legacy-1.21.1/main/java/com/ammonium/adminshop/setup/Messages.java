// 26.1 port: the Seller (#7), the T1-T3 Buyers (#8) and the Fluid Buyer (#10) are ported (src/main/...); PacketSetSellerRecipe,
// PacketSetItemBuyerRecipe (now src's PacketSetBuyerRecipe), PacketSetFluidBuyerRecipe and PacketAccountAddPermit (#9) are gone from here.
package com.ammonium.adminshop.setup;

import com.ammonium.adminshop.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class Messages {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                PacketUpdateRequest.TYPE,
                PacketUpdateRequest.STREAM_CODEC,
                PacketUpdateRequest::handle);
        registrar.playToServer(
                PacketSetDetectorThreshold.TYPE,
                PacketSetDetectorThreshold.STREAM_CODEC,
                PacketSetDetectorThreshold::handle);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToServer(MSG message) {
        PacketDistributor.sendToServer(message);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }
}
