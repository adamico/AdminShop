package com.ammonium.adminshop.setup;

import com.ammonium.adminshop.network.PacketBuyRequest;
import com.ammonium.adminshop.network.PacketSellRequest;
import com.ammonium.adminshop.network.PacketSetSellerRecipe;
import com.ammonium.adminshop.network.PacketSyncMoneyToClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** AdminShop's payloads. The client registers the handlers of the clientbound ones, in {@link ClientMessages}. */
public class Messages {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                PacketSyncMoneyToClient.TYPE,
                PacketSyncMoneyToClient.STREAM_CODEC);
        registrar.playToServer(
                PacketBuyRequest.TYPE,
                PacketBuyRequest.STREAM_CODEC,
                PacketBuyRequest::handle);
        registrar.playToServer(
                PacketSellRequest.TYPE,
                PacketSellRequest.STREAM_CODEC,
                PacketSellRequest::handle);
        registrar.playToServer(
                PacketSetSellerRecipe.TYPE,
                PacketSetSellerRecipe.STREAM_CODEC,
                PacketSetSellerRecipe::handle);
    }

    public static <MSG extends CustomPacketPayload> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }
}
